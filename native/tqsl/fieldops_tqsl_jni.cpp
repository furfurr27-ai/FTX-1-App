#include <jni.h>

#include <algorithm>
#include <atomic>
#include <cctype>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <fstream>
#include <memory>
#include <mutex>
#include <string>
#include <unordered_map>
#include <vector>

#include <unistd.h>
#include <zlib.h>

#include <tqsllib.h>
#include <tqslconvert.h>

namespace {

enum Status : int {
    OK = 0,
    INIT_FAILED = 100,
    INIT_DIRECTORY_CONFLICT = 101,
    RESOURCE_CONFIG_MISSING = 102,
    INVALID_ARGUMENT = 110,
    PKCS12_ENCODE_FAILED = 120,
    PKCS12_IMPORT_FAILED = 121,
    LOCATION_NOT_FOUND = 130,
    LOCATION_CALLSIGN_MISMATCH = 131,
    LOCATION_DXCC_MISMATCH = 132,
    CERTIFICATE_SELECT_FAILED = 140,
    NO_CERTIFICATE = 141,
    SIGNING_INIT_FAILED = 150,
    TEMPFILE_FAILED = 160,
    CONVERTER_BEGIN_FAILED = 170,
    CONVERTER_CONFIG_FAILED = 171,
    GABBI_FAILED = 172,
    COMPRESS_FAILED = 173,
    INVALID_HANDLE = 180,
    TERMINAL_STATE = 181,
    CLEANUP_FAILED = 182,
};

thread_local int g_last_status = OK;
std::mutex g_mutex;
std::string g_data_directory;
std::string g_resource_directory;
bool g_initialized = false;
std::atomic<jlong> g_next_handle{1};

struct Session {
    tQSL_Location location = nullptr;
    tQSL_Cert* certs = nullptr;
    int cert_count = 0;
    tQSL_Converter converter = nullptr;
    std::vector<unsigned char> payload;
    bool terminal = false;
};

std::unordered_map<jlong, std::unique_ptr<Session>> g_sessions;

void secure_zero(void* ptr, size_t length) {
    volatile unsigned char* p = static_cast<volatile unsigned char*>(ptr);
    while (length-- > 0) *p++ = 0;
}

void wipe(std::vector<unsigned char>& value) {
    if (!value.empty()) secure_zero(value.data(), value.size());
    value.clear();
}

void wipe(std::vector<char>& value) {
    if (!value.empty()) secure_zero(value.data(), value.size());
    value.clear();
}

std::vector<unsigned char> bytes(JNIEnv* env, jbyteArray array) {
    if (array == nullptr) return {};
    const jsize n = env->GetArrayLength(array);
    std::vector<unsigned char> out(static_cast<size_t>(n));
    if (n > 0) {
        env->GetByteArrayRegion(array, 0, n, reinterpret_cast<jbyte*>(out.data()));
    }
    return out;
}

std::vector<char> password_bytes(JNIEnv* env, jbyteArray array) {
    auto raw = bytes(env, array);
    std::vector<char> out(raw.size() + 1, '\0');
    if (!raw.empty()) std::memcpy(out.data(), raw.data(), raw.size());
    wipe(raw);
    return out;
}

std::string jstring_utf8(JNIEnv* env, jstring value) {
    if (value == nullptr) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) return {};
    std::string out(chars);
    env->ReleaseStringUTFChars(value, chars);
    return out;
}

std::string upper_ascii(std::string value) {
    std::transform(value.begin(), value.end(), value.begin(), [](unsigned char c) {
        return static_cast<char>(std::toupper(c));
    });
    return value;
}

int gzip_gabbi(const std::string& gabbi, std::vector<unsigned char>* out) {
    if (gabbi.empty() || out == nullptr) return COMPRESS_FAILED;

    z_stream stream{};
    if (deflateInit2(
            &stream,
            Z_BEST_COMPRESSION,
            Z_DEFLATED,
            15 + 16,
            8,
            Z_DEFAULT_STRATEGY) != Z_OK) {
        return COMPRESS_FAILED;
    }

    std::vector<unsigned char> compressed(compressBound(gabbi.size()) + 64);
    stream.next_in = reinterpret_cast<Bytef*>(const_cast<char*>(gabbi.data()));
    stream.avail_in = static_cast<uInt>(gabbi.size());
    stream.next_out = compressed.data();
    stream.avail_out = static_cast<uInt>(compressed.size());

    const int rc = deflate(&stream, Z_FINISH);
    if (rc != Z_STREAM_END) {
        deflateEnd(&stream);
        wipe(compressed);
        return COMPRESS_FAILED;
    }

    const size_t used = compressed.size() - stream.avail_out;
    deflateEnd(&stream);
    compressed.resize(used);
    *out = std::move(compressed);
    return OK;
}

int write_temp_adif(const std::string& adif, std::string* path) {
    if (g_data_directory.empty() || adif.empty() || path == nullptr) return TEMPFILE_FAILED;
    std::string pattern = g_data_directory + "/fieldops-tqsl-XXXXXX";
    std::vector<char> writable(pattern.begin(), pattern.end());
    writable.push_back('\0');

    const int fd = mkstemp(writable.data());
    if (fd < 0) return TEMPFILE_FAILED;

    size_t offset = 0;
    while (offset < adif.size()) {
        const ssize_t n = ::write(fd, adif.data() + offset, adif.size() - offset);
        if (n <= 0) {
            ::close(fd);
            ::unlink(writable.data());
            return TEMPFILE_FAILED;
        }
        offset += static_cast<size_t>(n);
    }
    if (::close(fd) != 0) {
        ::unlink(writable.data());
        return TEMPFILE_FAILED;
    }
    *path = writable.data();
    return OK;
}

int cleanup_session(Session* session, bool rollback_if_open) {
    if (session == nullptr) return INVALID_HANDLE;
    int status = OK;

    if (session->converter != nullptr) {
        if (!session->terminal && rollback_if_open) {
            if (tqsl_converterRollBack(session->converter) != 0 && status == OK) {
                status = CLEANUP_FAILED;
            } else {
                session->terminal = true;
            }
        }
        if (tqsl_endConverter(&session->converter) != 0 && status == OK) {
            status = CLEANUP_FAILED;
        }
    }

    if (session->certs != nullptr) {
        for (int i = 0; i < session->cert_count; ++i) {
            (void)tqsl_endSigning(session->certs[i]);
        }
        tqsl_freeCertificateList(session->certs, session->cert_count);
        session->certs = nullptr;
        session->cert_count = 0;
    }

    if (session->location != nullptr) {
        if (tqsl_endStationLocationCapture(&session->location) != 0 && status == OK) {
            status = CLEANUP_FAILED;
        }
    }

    wipe(session->payload);
    return status;
}

int fail_session(std::unique_ptr<Session>& session, int status) {
    if (session) (void)cleanup_session(session.get(), true);
    g_last_status = status;
    return status;
}

}  // namespace

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeInitialize(
        JNIEnv* env,
        jobject,
        jstring data_directory,
        jstring resource_directory) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const std::string data_dir = jstring_utf8(env, data_directory);
    const std::string resource_dir = jstring_utf8(env, resource_directory);
    if (data_dir.empty() || resource_dir.empty()) {
        g_last_status = INVALID_ARGUMENT;
        return g_last_status;
    }

    // tqsl_init() has process-global one-time state. Once initialized, silently
    // switching its certificate/database/resource roots would be unsafe.
    if (g_initialized) {
        if (g_data_directory == data_dir && g_resource_directory == resource_dir) {
            g_last_status = OK;
            return OK;
        }
        g_last_status = INIT_DIRECTORY_CONFLICT;
        return g_last_status;
    }

    const std::string config_path = resource_dir + "/config.xml";
    std::ifstream config(config_path, std::ios::binary);
    if (!config.good()) {
        g_last_status = RESOURCE_CONFIG_MISSING;
        return g_last_status;
    }
    config.close();

    g_data_directory = data_dir;
    g_resource_directory = resource_dir;

    // tqsl_setDirectory() sets the writable tQSL_BaseDir. Android does not
    // have TrustedQSL's desktop install layout, so point the exported resource
    // root at the app-private directory containing the verified config.xml
    // before tqsl_init() performs any resource lookup.
    if (tqsl_setDirectory(g_data_directory.c_str()) != 0) {
        g_data_directory.clear();
        g_resource_directory.clear();
        g_last_status = INIT_FAILED;
        return g_last_status;
    }
    tQSL_RsrcDir = g_resource_directory.c_str();

    if (tqsl_init() != 0) {
        tQSL_RsrcDir = nullptr;
        g_data_directory.clear();
        g_resource_directory.clear();
        g_last_status = INIT_FAILED;
        return g_last_status;
    }

    g_initialized = true;
    g_last_status = OK;
    return OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeImportPkcs12(
        JNIEnv* env, jobject, jbyteArray pkcs12, jbyteArray p12_password, jbyteArray key_password) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto p12 = bytes(env, pkcs12);
    auto p12pass = password_bytes(env, p12_password);
    auto keypass = password_bytes(env, key_password);

    if (p12.empty()) {
        wipe(p12pass);
        wipe(keypass);
        g_last_status = INVALID_ARGUMENT;
        return g_last_status;
    }

    const size_t base64_size = 4 * ((p12.size() + 2) / 3) + 8;
    std::vector<char> base64(base64_size, '\0');
    const int encoded = tqsl_encodeBase64(
        p12.data(),
        static_cast<int>(p12.size()),
        base64.data(),
        static_cast<int>(base64.size()));

    wipe(p12);
    if (encoded != 0) {
        wipe(base64);
        wipe(p12pass);
        wipe(keypass);
        g_last_status = PKCS12_ENCODE_FAILED;
        return g_last_status;
    }

    const int imported = tqsl_importPKCS12Base64(
        base64.data(),
        p12pass.data(),
        keypass.data(),
        nullptr,
        nullptr,
        nullptr);

    wipe(base64);
    wipe(p12pass);
    wipe(keypass);

    g_last_status = imported == 0 ? OK : PKCS12_IMPORT_FAILED;
    return g_last_status;
}

extern "C" JNIEXPORT jlong JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeBeginSigning(
        JNIEnv* env,
        jobject,
        jstring adif_value,
        jstring station_location_name,
        jstring expected_callsign_value,
        jint expected_dxcc,
        jbyteArray key_password) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const std::string adif = jstring_utf8(env, adif_value);
    const std::string location_name = jstring_utf8(env, station_location_name);
    const std::string expected_callsign = upper_ascii(jstring_utf8(env, expected_callsign_value));
    auto keypass = password_bytes(env, key_password);

    if (adif.empty() || location_name.empty() || expected_callsign.empty() || expected_dxcc <= 0) {
        wipe(keypass);
        g_last_status = INVALID_ARGUMENT;
        return 0;
    }

    auto session = std::make_unique<Session>();

    if (tqsl_getStationLocation(&session->location, location_name.c_str()) != 0 ||
        session->location == nullptr) {
        wipe(keypass);
        fail_session(session, LOCATION_NOT_FOUND);
        return 0;
    }

    char location_callsign[128] = {0};
    if (tqsl_getLocationCallSign(
            session->location,
            location_callsign,
            static_cast<int>(sizeof(location_callsign))) != 0) {
        wipe(keypass);
        fail_session(session, LOCATION_NOT_FOUND);
        return 0;
    }
    if (upper_ascii(location_callsign) != expected_callsign) {
        wipe(keypass);
        fail_session(session, LOCATION_CALLSIGN_MISMATCH);
        return 0;
    }

    int location_dxcc = 0;
    if (tqsl_getLocationDXCCEntity(session->location, &location_dxcc) != 0) {
        wipe(keypass);
        fail_session(session, LOCATION_NOT_FOUND);
        return 0;
    }
    if (location_dxcc != expected_dxcc) {
        wipe(keypass);
        fail_session(session, LOCATION_DXCC_MISMATCH);
        return 0;
    }

    if (tqsl_selectCertificates(
            &session->certs,
            &session->cert_count,
            expected_callsign.c_str(),
            expected_dxcc,
            nullptr,
            nullptr,
            0) != 0) {
        wipe(keypass);
        fail_session(session, CERTIFICATE_SELECT_FAILED);
        return 0;
    }
    if (session->cert_count <= 0 || session->certs == nullptr) {
        wipe(keypass);
        fail_session(session, NO_CERTIFICATE);
        return 0;
    }

    for (int i = 0; i < session->cert_count; ++i) {
        if (tqsl_beginSigning(session->certs[i], keypass.data(), nullptr, nullptr) != 0) {
            wipe(keypass);
            fail_session(session, SIGNING_INIT_FAILED);
            return 0;
        }
    }
    wipe(keypass);

    std::string temp_path;
    int status = write_temp_adif(adif, &temp_path);
    if (status != OK) {
        fail_session(session, status);
        return 0;
    }

    if (tqsl_beginADIFConverter(
            &session->converter,
            temp_path.c_str(),
            session->certs,
            session->cert_count,
            session->location) != 0) {
        ::unlink(temp_path.c_str());
        fail_session(session, CONVERTER_BEGIN_FAILED);
        return 0;
    }

    if (tqsl_setConverterAllowDuplicates(session->converter, 0) != 0 ||
        tqsl_setConverterQTHDetails(session->converter, TQSL_LOC_REPORT) != 0 ||
        tqsl_setConverterAppName(session->converter, "FTX1_FieldOps") != 0) {
        ::unlink(temp_path.c_str());
        fail_session(session, CONVERTER_CONFIG_FAILED);
        return 0;
    }

    std::string gabbi;
    tQSL_Error = 0;
    while (true) {
        const char* record = tqsl_getConverterGABBI(session->converter);
        if (record == nullptr) break;
        gabbi.append(record);
    }
    ::unlink(temp_path.c_str());

    if (tQSL_Error != 0 || gabbi.empty()) {
        fail_session(session, GABBI_FAILED);
        return 0;
    }

    status = gzip_gabbi(gabbi, &session->payload);
    secure_zero(gabbi.data(), gabbi.size());
    gabbi.clear();
    if (status != OK || session->payload.empty()) {
        fail_session(session, COMPRESS_FAILED);
        return 0;
    }

    const jlong handle = g_next_handle.fetch_add(1);
    g_sessions.emplace(handle, std::move(session));
    g_last_status = OK;
    return handle;
}

extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeGetPayload(
        JNIEnv* env, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto it = g_sessions.find(handle);
    if (it == g_sessions.end() || it->second->payload.empty()) {
        g_last_status = INVALID_HANDLE;
        return env->NewByteArray(0);
    }

    const auto& payload = it->second->payload;
    jbyteArray out = env->NewByteArray(static_cast<jsize>(payload.size()));
    if (out == nullptr) {
        g_last_status = COMPRESS_FAILED;
        return nullptr;
    }
    env->SetByteArrayRegion(
        out,
        0,
        static_cast<jsize>(payload.size()),
        reinterpret_cast<const jbyte*>(payload.data()));
    g_last_status = OK;
    return out;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeCommit(
        JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        g_last_status = INVALID_HANDLE;
        return g_last_status;
    }
    Session* session = it->second.get();
    if (session->terminal) {
        g_last_status = TERMINAL_STATE;
        return g_last_status;
    }
    if (tqsl_converterCommit(session->converter) != 0) {
        g_last_status = TERMINAL_STATE;
        return g_last_status;
    }
    session->terminal = true;
    g_last_status = OK;
    return OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeRollback(
        JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        g_last_status = INVALID_HANDLE;
        return g_last_status;
    }
    Session* session = it->second.get();
    if (session->terminal) {
        g_last_status = TERMINAL_STATE;
        return g_last_status;
    }
    if (tqsl_converterRollBack(session->converter) != 0) {
        g_last_status = TERMINAL_STATE;
        return g_last_status;
    }
    session->terminal = true;
    g_last_status = OK;
    return OK;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeClose(
        JNIEnv*, jobject, jlong handle) {
    std::lock_guard<std::mutex> lock(g_mutex);
    const auto it = g_sessions.find(handle);
    if (it == g_sessions.end()) {
        g_last_status = INVALID_HANDLE;
        return g_last_status;
    }

    std::unique_ptr<Session> session = std::move(it->second);
    g_sessions.erase(it);
    const int status = cleanup_session(session.get(), true);
    g_last_status = status;
    return status;
}

extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeLastStatus(
        JNIEnv*, jobject) {
    return g_last_status;
}

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
#include <expat.h>
#include <zlib.h>

#include <tqsllib.h>
#include <tqslconvert.h>
#include <tqslerrno.h>

namespace {

enum Status : int {
    OK = 0,
    INIT_FAILED = 100,
    INIT_DIRECTORY_CONFLICT = 101,
    RESOURCE_CONFIG_MISSING = 102,
    INVALID_ARGUMENT = 110,
    PKCS12_ENCODE_FAILED = 120,
    PKCS12_IMPORT_FAILED = 121,
    BACKUP_DECOMPRESS_FAILED = 122,
    BACKUP_PARSE_FAILED = 123,
    BACKUP_IMPORT_FAILED = 124,
    LOCATION_NOT_FOUND = 130,
    LOCATION_CALLSIGN_MISMATCH = 131,
    LOCATION_DXCC_MISMATCH = 132,
    LOCATION_PROFILE_MISMATCH = 133,
    LOCATION_CREATE_FAILED = 134,
    LOCATION_SAVE_FAILED = 135,
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

void wipe(std::string& value) {
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


struct BackupUserCert {
    std::string callsign;
    int dxcc = 0;
    std::string signed_cert;
    std::string private_key;
};

struct BackupRestoreState {
    std::string capture;
    std::string text;
    std::vector<std::string> root_certs;
    std::vector<std::string> ca_certs;
    std::vector<BackupUserCert> user_certs;
    BackupUserCert current_user;
    bool in_user = false;
    bool in_locations = false;
    int location_count = 0;
    std::string station_xml = "<StationDataFile>\n";
    bool invalid = false;
};

std::string trim_copy(const std::string& value) {
    const auto first = value.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return {};
    const auto last = value.find_last_not_of(" \t\r\n");
    return value.substr(first, last - first + 1);
}

std::string xml_escape(const char* raw) {
    if (raw == nullptr) return {};
    std::string out;
    for (const char ch : std::string(raw)) {
        switch (ch) {
            case '&': out += "&amp;"; break;
            case '"': out += "&quot;"; break;
            case '\'': out += "&apos;"; break;
            case '<': out += "&lt;"; break;
            case '>': out += "&gt;"; break;
            default: out.push_back(ch); break;
        }
    }
    return out;
}

const char* xml_attr(const XML_Char** atts, const char* name) {
    if (atts == nullptr) return nullptr;
    for (int i = 0; atts[i] != nullptr && atts[i + 1] != nullptr; i += 2) {
        if (std::strcmp(atts[i], name) == 0) return atts[i + 1];
    }
    return nullptr;
}

void XMLCALL backup_start(void* opaque, const XML_Char* name, const XML_Char** atts) {
    auto* state = static_cast<BackupRestoreState*>(opaque);
    if (state == nullptr || name == nullptr) return;

    if (std::strcmp(name, "UserCert") == 0) {
        state->current_user = BackupUserCert{};
        state->in_user = true;
        const char* call = xml_attr(atts, "CallSign");
        const char* dxcc = xml_attr(atts, "dxcc");
        if (call != nullptr) state->current_user.callsign = call;
        if (dxcc != nullptr) state->current_user.dxcc = std::atoi(dxcc);
        return;
    }

    if (std::strcmp(name, "Locations") == 0) {
        state->in_locations = true;
        return;
    }

    if (std::strcmp(name, "Location") == 0 && state->in_locations) {
        const char* loc_name = xml_attr(atts, "name");
        if (loc_name == nullptr || *loc_name == '\0') {
            state->invalid = true;
            return;
        }
        state->station_xml += "<StationData name=\"" + xml_escape(loc_name) + "\">\n";
        if (atts != nullptr) {
            for (int i = 0; atts[i] != nullptr && atts[i + 1] != nullptr; i += 2) {
                if (std::strcmp(atts[i], "name") == 0) continue;
                state->station_xml += "<";
                state->station_xml += atts[i];
                state->station_xml += ">";
                state->station_xml += xml_escape(atts[i + 1]);
                state->station_xml += "</";
                state->station_xml += atts[i];
                state->station_xml += ">\n";
            }
        }
        state->station_xml += "</StationData>\n";
        state->location_count++;
        return;
    }

    if (std::strcmp(name, "RootCert") == 0 ||
        std::strcmp(name, "CACert") == 0 ||
        std::strcmp(name, "SignedCert") == 0 ||
        std::strcmp(name, "PrivateKey") == 0) {
        state->capture = name;
        state->text.clear();
    }
}

void XMLCALL backup_text(void* opaque, const XML_Char* text, int len) {
    auto* state = static_cast<BackupRestoreState*>(opaque);
    if (state == nullptr || state->capture.empty() || text == nullptr || len <= 0) return;
    state->text.append(text, static_cast<size_t>(len));
}

void XMLCALL backup_end(void* opaque, const XML_Char* name) {
    auto* state = static_cast<BackupRestoreState*>(opaque);
    if (state == nullptr || name == nullptr) return;

    if (!state->capture.empty() && state->capture == name) {
        std::string value = trim_copy(state->text);
        if (state->capture == "RootCert") {
            if (!value.empty()) state->root_certs.push_back(std::move(value));
        } else if (state->capture == "CACert") {
            if (!value.empty()) state->ca_certs.push_back(std::move(value));
        } else if (state->capture == "SignedCert" && state->in_user) {
            state->current_user.signed_cert = std::move(value);
        } else if (state->capture == "PrivateKey" && state->in_user) {
            state->current_user.private_key = std::move(value);
        }
        state->capture.clear();
        state->text.clear();
    }

    if (std::strcmp(name, "UserCert") == 0 && state->in_user) {
        if (state->current_user.callsign.empty() ||
            (state->current_user.signed_cert.empty() && state->current_user.private_key.empty())) {
            state->invalid = true;
        } else {
            state->user_certs.push_back(std::move(state->current_user));
        }
        state->current_user = BackupUserCert{};
        state->in_user = false;
    } else if (std::strcmp(name, "Locations") == 0) {
        state->in_locations = false;
    }
}

int gunzip_bytes(const std::vector<unsigned char>& compressed, std::string* output) {
    if (compressed.empty() || output == nullptr) return BACKUP_DECOMPRESS_FAILED;

    z_stream stream{};
    if (inflateInit2(&stream, 15 + 32) != Z_OK) return BACKUP_DECOMPRESS_FAILED;

    stream.next_in = const_cast<Bytef*>(compressed.data());
    stream.avail_in = static_cast<uInt>(compressed.size());

    std::string decoded;
    std::vector<unsigned char> chunk(16 * 1024);
    int rc = Z_OK;
    while (rc == Z_OK) {
        stream.next_out = chunk.data();
        stream.avail_out = static_cast<uInt>(chunk.size());
        rc = inflate(&stream, Z_NO_FLUSH);
        const size_t produced = chunk.size() - stream.avail_out;
        if (produced > 0) {
            decoded.append(reinterpret_cast<const char*>(chunk.data()), produced);
        }
    }
    inflateEnd(&stream);
    wipe(chunk);

    if (rc != Z_STREAM_END || decoded.empty()) {
        wipe(decoded);
        return BACKUP_DECOMPRESS_FAILED;
    }
    *output = std::move(decoded);
    return OK;
}

int restore_backup_xml(const std::string& xml) {
    BackupRestoreState state;
    XML_Parser parser = XML_ParserCreate(nullptr);
    if (parser == nullptr) return BACKUP_PARSE_FAILED;

    XML_SetUserData(parser, &state);
    XML_SetElementHandler(parser, backup_start, backup_end);
    XML_SetCharacterDataHandler(parser, backup_text);

    const int parsed = XML_Parse(parser, xml.data(), static_cast<int>(xml.size()), XML_TRUE);
    XML_ParserFree(parser);

    if (parsed == XML_STATUS_ERROR || state.invalid || state.user_certs.empty() ||
        state.location_count <= 0) {
        for (auto& user : state.user_certs) wipe(user.private_key);
        wipe(state.current_user.private_key);
        wipe(state.text);
        wipe(state.station_xml);
        return BACKUP_PARSE_FAILED;
    }

    for (const auto& cert : state.root_certs) {
        const int rc = tqsl_importKeyPairEncoded(nullptr, "root", nullptr, cert.c_str());
        if (rc != 0 && tQSL_Error != TQSL_CERT_ERROR) {
            for (auto& user : state.user_certs) wipe(user.private_key);
            wipe(state.station_xml);
            return BACKUP_IMPORT_FAILED;
        }
    }
    for (const auto& cert : state.ca_certs) {
        const int rc = tqsl_importKeyPairEncoded(nullptr, "authorities", nullptr, cert.c_str());
        if (rc != 0 && tQSL_Error != TQSL_CERT_ERROR) {
            for (auto& user : state.user_certs) wipe(user.private_key);
            wipe(state.station_xml);
            return BACKUP_IMPORT_FAILED;
        }
    }

    for (auto& user : state.user_certs) {
        const char* key = user.private_key.empty() ? nullptr : user.private_key.c_str();
        const char* cert = user.signed_cert.empty() ? nullptr : user.signed_cert.c_str();
        const int rc = tqsl_importKeyPairEncoded(user.callsign.c_str(), "user", key, cert);
        wipe(user.private_key);
        if (rc != 0 && tQSL_Error != TQSL_CERT_ERROR) {
            wipe(state.station_xml);
            return BACKUP_IMPORT_FAILED;
        }
    }

    state.station_xml += "</StationDataFile>\n";
    const int merge = tqsl_mergeStationLocations(state.station_xml.c_str());
    wipe(state.station_xml);
    if (merge != 0) return BACKUP_IMPORT_FAILED;

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
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeEnsureStationLocation(
        JNIEnv* env,
        jobject,
        jstring location_name_value,
        jstring callsign_value,
        jint dxcc,
        jstring grid_value,
        jint cq_zone,
        jint itu_zone) {
    std::lock_guard<std::mutex> lock(g_mutex);

    const std::string location_name = jstring_utf8(env, location_name_value);
    const std::string callsign = upper_ascii(jstring_utf8(env, callsign_value));
    const std::string grid = upper_ascii(jstring_utf8(env, grid_value));

    if (!g_initialized) {
        g_last_status = INIT_FAILED;
        return g_last_status;
    }
    if (location_name.empty() || callsign.empty() || grid.size() < 4 ||
        dxcc <= 0 || cq_zone < 1 || cq_zone > 40 || itu_zone < 1 || itu_zone > 90) {
        g_last_status = INVALID_ARGUMENT;
        return g_last_status;
    }

    tQSL_Location existing = nullptr;
    if (tqsl_getStationLocation(&existing, location_name.c_str()) == 0 && existing != nullptr) {
        char existing_call[128] = {0};
        char existing_grid[128] = {0};
        char existing_cq[32] = {0};
        char existing_itu[32] = {0};
        int existing_dxcc = 0;

        const bool readable =
            tqsl_getLocationCallSign(existing, existing_call, static_cast<int>(sizeof(existing_call))) == 0 &&
            tqsl_getLocationDXCCEntity(existing, &existing_dxcc) == 0 &&
            tqsl_getStationLocationField(existing, "GRIDSQUARE", existing_grid, static_cast<int>(sizeof(existing_grid))) == 0 &&
            tqsl_getStationLocationField(existing, "CQZ", existing_cq, static_cast<int>(sizeof(existing_cq))) == 0 &&
            tqsl_getStationLocationField(existing, "ITUZ", existing_itu, static_cast<int>(sizeof(existing_itu))) == 0;

        const bool matches = readable &&
            upper_ascii(existing_call) == callsign &&
            existing_dxcc == dxcc &&
            upper_ascii(existing_grid) == grid &&
            std::atoi(existing_cq) == cq_zone &&
            std::atoi(existing_itu) == itu_zone;

        (void)tqsl_endStationLocationCapture(&existing);
        g_last_status = matches ? OK : LOCATION_PROFILE_MISMATCH;
        return g_last_status;
    }
    if (existing != nullptr) {
        (void)tqsl_endStationLocationCapture(&existing);
    }

    tQSL_Location created = nullptr;
    if (tqsl_initStationLocationCapture(&created) != 0 || created == nullptr) {
        g_last_status = LOCATION_CREATE_FAILED;
        return g_last_status;
    }

    const std::string cq = std::to_string(cq_zone);
    const std::string itu = std::to_string(itu_zone);
    const bool configured =
        tqsl_setLocationCallSign(created, callsign.c_str(), dxcc) == 0 &&
        tqsl_setLocationField(created, "GRIDSQUARE", grid.c_str()) == 0 &&
        tqsl_setLocationField(created, "CQZ", cq.c_str()) == 0 &&
        tqsl_setLocationField(created, "ITUZ", itu.c_str()) == 0 &&
        tqsl_updateStationLocationCapture(created) == 0 &&
        tqsl_setStationLocationCaptureName(created, location_name.c_str()) == 0;

    if (!configured) {
        (void)tqsl_endStationLocationCapture(&created);
        g_last_status = LOCATION_CREATE_FAILED;
        return g_last_status;
    }

    if (tqsl_saveStationLocationCapture(created, 0) != 0) {
        (void)tqsl_endStationLocationCapture(&created);
        g_last_status = LOCATION_SAVE_FAILED;
        return g_last_status;
    }

    if (tqsl_endStationLocationCapture(&created) != 0) {
        g_last_status = CLEANUP_FAILED;
        return g_last_status;
    }

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


extern "C" JNIEXPORT jint JNICALL
Java_dev_n0png_fieldops_android_logbook_TrustedQslJniBridge_nativeImportBackup(
        JNIEnv* env, jobject, jbyteArray backup) {
    std::lock_guard<std::mutex> lock(g_mutex);
    auto compressed = bytes(env, backup);
    if (compressed.empty()) {
        g_last_status = INVALID_ARGUMENT;
        return g_last_status;
    }

    std::string xml;
    int status = gunzip_bytes(compressed, &xml);
    wipe(compressed);
    if (status != OK) {
        g_last_status = status;
        return g_last_status;
    }

    status = restore_backup_xml(xml);
    wipe(xml);
    g_last_status = status;
    return status;
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

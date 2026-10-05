#include "tqsllib.h"
#include "tqslconvert.h"

#include <algorithm>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <fstream>
#include <sstream>
#include <string>
#include <vector>

int tQSL_Error = 0;

namespace {

struct FakeLocation {
    std::string name;
    std::string callsign;
    int dxcc;
};

struct FakeCert {
    std::string callsign;
    int dxcc;
    bool signing = false;
};

struct FakeConverter {
    std::string adif;
    FakeLocation* location = nullptr;
    int cursor = 0;
    bool terminal = false;
    bool duplicates_disabled = false;
    bool qth_report = false;
    std::string app;
    std::string record1;
    std::string record2;
};

bool g_initialized = false;
bool g_imported = false;
std::string g_directory;

void copy_string(const std::string& value, char* buf, int size) {
    if (buf == nullptr || size <= 0) return;
    std::snprintf(buf, static_cast<size_t>(size), "%s", value.c_str());
}

std::string simple_base64(const unsigned char* data, int n) {
    static const char alphabet[] =
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
    std::string out;
    for (int i = 0; i < n; i += 3) {
        unsigned v = static_cast<unsigned>(data[i]) << 16;
        if (i + 1 < n) v |= static_cast<unsigned>(data[i + 1]) << 8;
        if (i + 2 < n) v |= static_cast<unsigned>(data[i + 2]);
        out.push_back(alphabet[(v >> 18) & 63]);
        out.push_back(alphabet[(v >> 12) & 63]);
        out.push_back(i + 1 < n ? alphabet[(v >> 6) & 63] : '=');
        out.push_back(i + 2 < n ? alphabet[v & 63] : '=');
    }
    return out;
}

}  // namespace

extern "C" int tqsl_setDirectory(const char *dir) {
    if (dir == nullptr || *dir == '\0') return 1;
    g_directory = dir;
    return 0;
}

extern "C" int tqsl_init(void) {
    if (g_directory.empty()) return 1;
    g_initialized = true;
    return 0;
}

extern "C" int tqsl_encodeBase64(
        const unsigned char *data, int datalen, char *output, int outputlen) {
    if (!g_initialized || data == nullptr || datalen <= 0 || output == nullptr) return 1;
    const std::string encoded = simple_base64(data, datalen);
    if (static_cast<int>(encoded.size() + 1) > outputlen) return 1;
    std::memcpy(output, encoded.c_str(), encoded.size() + 1);
    return 0;
}

extern "C" int tqsl_importPKCS12Base64(
        const char *base64,
        const char *p12password,
        const char *password,
        int (*)(char*, int, void*),
        int (*)(int, const char*, void*),
        void*) {
    if (!g_initialized || base64 == nullptr || *base64 == '\0') return 1;
    if (p12password == nullptr || std::strcmp(p12password, "p12-pass") != 0) return 1;
    if (password == nullptr || std::strcmp(password, "key-pass") != 0) return 1;
    g_imported = true;
    return 0;
}

extern "C" int tqsl_getStationLocation(tQSL_Location *loc, const char *name) {
    if (!g_initialized || loc == nullptr || name == nullptr) return 1;
    const std::string n(name);
    if (n == "Home") {
        *loc = new FakeLocation{"Home", "N0PNG", 230};
        return 0;
    }
    if (n == "Portable") {
        *loc = new FakeLocation{"Portable", "N0PNG/P", 230};
        return 0;
    }
    return 1;
}

extern "C" int tqsl_getLocationCallSign(tQSL_Location loc, char *buf, int bufsiz) {
    if (loc == nullptr) return 1;
    copy_string(static_cast<FakeLocation*>(loc)->callsign, buf, bufsiz);
    return 0;
}

extern "C" int tqsl_getLocationDXCCEntity(tQSL_Location loc, int *dxcc) {
    if (loc == nullptr || dxcc == nullptr) return 1;
    *dxcc = static_cast<FakeLocation*>(loc)->dxcc;
    return 0;
}

extern "C" int tqsl_endStationLocationCapture(tQSL_Location *loc) {
    if (loc == nullptr || *loc == nullptr) return 0;
    delete static_cast<FakeLocation*>(*loc);
    *loc = nullptr;
    return 0;
}

extern "C" int tqsl_selectCertificates(
        tQSL_Cert **certlist,
        int *ncerts,
        const char *callsign,
        int dxcc,
        const tQSL_Date*,
        const TQSL_PROVIDER*,
        int) {
    if (certlist == nullptr || ncerts == nullptr || callsign == nullptr) return 1;
    if (!g_imported) {
        *certlist = nullptr;
        *ncerts = 0;
        return 0;
    }
    if (std::string(callsign) != "N0PNG" || dxcc != 230) {
        *certlist = nullptr;
        *ncerts = 0;
        return 0;
    }
    tQSL_Cert* list = static_cast<tQSL_Cert*>(std::calloc(1, sizeof(tQSL_Cert)));
    if (list == nullptr) return 1;
    list[0] = new FakeCert{"N0PNG", 230, false};
    *certlist = list;
    *ncerts = 1;
    return 0;
}

extern "C" void tqsl_freeCertificateList(tQSL_Cert* list, int ncerts) {
    if (list == nullptr) return;
    for (int i = 0; i < ncerts; ++i) delete static_cast<FakeCert*>(list[i]);
    std::free(list);
}

extern "C" int tqsl_beginSigning(
        tQSL_Cert cert,
        char *password,
        int (*)(char*, int, void*),
        void*) {
    if (cert == nullptr || password == nullptr || std::strcmp(password, "key-pass") != 0) return 1;
    static_cast<FakeCert*>(cert)->signing = true;
    return 0;
}

extern "C" int tqsl_endSigning(tQSL_Cert cert) {
    if (cert != nullptr) static_cast<FakeCert*>(cert)->signing = false;
    return 0;
}

extern "C" int tqsl_beginADIFConverter(
        tQSL_Converter *conv,
        const char *filename,
        tQSL_Cert *certs,
        int ncerts,
        tQSL_Location loc) {
    if (conv == nullptr || filename == nullptr || certs == nullptr || ncerts != 1 || loc == nullptr) return 1;
    auto* cert = static_cast<FakeCert*>(certs[0]);
    if (!cert->signing) return 1;

    std::ifstream input(filename, std::ios::binary);
    if (!input) return 1;
    std::ostringstream buffer;
    buffer << input.rdbuf();

    auto* c = new FakeConverter();
    c->adif = buffer.str();
    c->location = static_cast<FakeLocation*>(loc);
    c->record1 = "<Rec_Type:5>tCERT<CERT_UID:1>1<EOR>\n";
    c->record2 =
        "<Rec_Type:8>tCONTACT<STATION_UID:1>1"
        "<CALL:4>W1AW<BAND:3>40m<MODE:3>FT8"
        "<SIGN_LOTW_V2.0:12:6>FAKE-SIGNED!"
        "<EOR>\n";
    *conv = c;
    return 0;
}

extern "C" int tqsl_setConverterAllowDuplicates(tQSL_Converter conv, int allow) {
    if (conv == nullptr) return 1;
    static_cast<FakeConverter*>(conv)->duplicates_disabled = (allow == 0);
    return 0;
}

extern "C" int tqsl_setConverterQTHDetails(tQSL_Converter conv, int mode) {
    if (conv == nullptr) return 1;
    static_cast<FakeConverter*>(conv)->qth_report = (mode == TQSL_LOC_REPORT);
    return 0;
}

extern "C" int tqsl_setConverterAppName(tQSL_Converter conv, const char *app) {
    if (conv == nullptr || app == nullptr) return 1;
    static_cast<FakeConverter*>(conv)->app = app;
    return 0;
}

extern "C" const char* tqsl_getConverterGABBI(tQSL_Converter conv) {
    tQSL_Error = 0;
    if (conv == nullptr) {
        tQSL_Error = 1;
        return nullptr;
    }
    auto* c = static_cast<FakeConverter*>(conv);
    if (!c->duplicates_disabled || !c->qth_report || c->app != "FTX1_FieldOps") {
        tQSL_Error = 1;
        return nullptr;
    }
    if (c->adif.find("<CALL:4>W1AW") == std::string::npos) {
        tQSL_Error = 1;
        return nullptr;
    }

    if (c->cursor == 0) {
        c->cursor++;
        return c->record1.c_str();
    }
    if (c->cursor == 1) {
        c->cursor++;
        return c->record2.c_str();
    }
    return nullptr;
}

extern "C" int tqsl_converterRollBack(tQSL_Converter conv) {
    if (conv == nullptr) return 1;
    auto* c = static_cast<FakeConverter*>(conv);
    if (c->terminal) return 1;
    c->terminal = true;
    return 0;
}

extern "C" int tqsl_converterCommit(tQSL_Converter conv) {
    if (conv == nullptr) return 1;
    auto* c = static_cast<FakeConverter*>(conv);
    if (c->terminal) return 1;
    c->terminal = true;
    return 0;
}

extern "C" int tqsl_endConverter(tQSL_Converter *conv) {
    if (conv == nullptr || *conv == nullptr) return 0;
    auto* c = static_cast<FakeConverter*>(*conv);
    if (!c->terminal) return 1;
    delete c;
    *conv = nullptr;
    return 0;
}

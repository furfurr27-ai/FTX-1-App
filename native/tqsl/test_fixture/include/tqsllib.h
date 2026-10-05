#ifndef FIELDOPS_TEST_TQSLLIB_H
#define FIELDOPS_TEST_TQSLLIB_H

#include <stdio.h>
#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif

#define DLLEXPORT
#define DLLEXPORTDATA
#define CALLCONVENTION
#define TQSL_LOC_IGNORE 0
#define TQSL_LOC_REPORT 1
#define TQSL_LOC_UPDATE 2

typedef void* tQSL_Cert;
typedef void* tQSL_Location;
typedef struct { int year; int month; int day; } tQSL_Date;
typedef struct { int _unused; } TQSL_PROVIDER;

extern int tQSL_Error;
extern const char *tQSL_RsrcDir;

int tqsl_init(void);
int tqsl_setDirectory(const char *dir);
int tqsl_encodeBase64(const unsigned char *data, int datalen, char *output, int outputlen);
int tqsl_importPKCS12Base64(
    const char *base64,
    const char *p12password,
    const char *password,
    int (*pwcb)(char *buf, int bufsiz, void *userdata),
    int (*cb)(int type, const char *message, void *userdata),
    void *user);

int tqsl_getStationLocation(tQSL_Location *loc, const char *name);
int tqsl_getLocationCallSign(tQSL_Location loc, char *buf, int bufsiz);
int tqsl_getLocationDXCCEntity(tQSL_Location loc, int *dxcc);
int tqsl_endStationLocationCapture(tQSL_Location *loc);

int tqsl_selectCertificates(
    tQSL_Cert **certlist,
    int *ncerts,
    const char *callsign,
    int dxcc,
    const tQSL_Date *date,
    const TQSL_PROVIDER *issuer,
    int flag);
void tqsl_freeCertificateList(tQSL_Cert* list, int ncerts);
int tqsl_beginSigning(
    tQSL_Cert cert,
    char *password,
    int (*pwcb)(char *pwbuf, int pwsize, void *userdata),
    void *user);
int tqsl_endSigning(tQSL_Cert cert);

#ifdef __cplusplus
}
#endif

#endif

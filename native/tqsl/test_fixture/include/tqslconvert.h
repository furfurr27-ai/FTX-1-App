#ifndef FIELDOPS_TEST_TQSLCONVERT_H
#define FIELDOPS_TEST_TQSLCONVERT_H

#include "tqsllib.h"

#ifdef __cplusplus
extern "C" {
#endif

typedef void* tQSL_Converter;

int tqsl_beginADIFConverter(
    tQSL_Converter *conv,
    const char *filename,
    tQSL_Cert *certs,
    int ncerts,
    tQSL_Location loc);
int tqsl_endConverter(tQSL_Converter *conv);
int tqsl_setConverterQTHDetails(tQSL_Converter conv, int logverify);
int tqsl_setConverterAllowDuplicates(tQSL_Converter conv, int allow);
int tqsl_setConverterAppName(tQSL_Converter conv, const char *app);
int tqsl_converterRollBack(tQSL_Converter conv);
int tqsl_converterCommit(tQSL_Converter conv);
const char* tqsl_getConverterGABBI(tQSL_Converter conv);

#ifdef __cplusplus
}
#endif

#endif

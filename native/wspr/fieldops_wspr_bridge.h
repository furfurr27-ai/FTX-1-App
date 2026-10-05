#pragma once

#ifdef __cplusplus
extern "C" {
#endif

#define FIELDOPS_WSPR_MAX_RESULTS 100
#define FIELDOPS_WSPR_IQ_SAMPLES 45000
#define FIELDOPS_WSPR_SYMBOLS 162

struct fieldops_wspr_result {
    float snr_db;
    float dt_seconds;
    float audio_hz;
    float drift_hz;
    char message[23];
    char call[13];
    char grid[7];
    char power_dbm[3];
};

int fieldops_wspr_decode375(
    const float *i_samples,
    const float *q_samples,
    int sample_count,
    struct fieldops_wspr_result *results,
    int max_results
);

int fieldops_wspr_encode_symbols(
    const char *message,
    unsigned char symbols[FIELDOPS_WSPR_SYMBOLS]
);

#ifdef __cplusplus
}
#endif

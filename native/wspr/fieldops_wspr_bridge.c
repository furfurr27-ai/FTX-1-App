/*
 * FieldOps adaptation layer around the pinned Guenael/rtlsdr-wsprd WSPR codec.
 *
 * FieldOps-specific code added 2026-10-05. The linked upstream decoder is
 * GNU GPL v3. See native/wspr/upstream/LICENSE and UPSTREAM.md.
 */
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "fieldops_wspr_bridge.h"
#include "upstream/wsprd.h"
#include "upstream/wsprsim_utils.h"

/*
 * Upstream wspr_decode() keeps several large decoder buffers on the C stack.
 * A JVM/Android caller thread may have a much smaller stack than the original
 * daemon process. Run the pinned decoder on a dedicated native worker with an
 * explicit stack rather than risking a process-wide stack-overflow crash.
 */
struct fieldops_wspr_worker {
    float *i_samples;
    float *q_samples;
    int sample_count;
    struct decoder_options options;
    struct decoder_results *results;
    int result_count;
    int decoder_rc;
};

static void *fieldops_wspr_decode_worker(void *opaque) {
    struct fieldops_wspr_worker *worker = (struct fieldops_wspr_worker *)opaque;
    worker->decoder_rc = wspr_decode(
        worker->i_samples,
        worker->q_samples,
        worker->sample_count,
        worker->options,
        worker->results,
        &worker->result_count
    );
    return NULL;
}

int fieldops_wspr_decode375(
    const float *i_samples,
    const float *q_samples,
    int sample_count,
    struct fieldops_wspr_result *results,
    int max_results
) {
    if (!i_samples || !q_samples || !results) return -1;
    if (sample_count != FIELDOPS_WSPR_IQ_SAMPLES) return -2;
    if (max_results <= 0) return -3;

    float *i_copy = (float *)malloc(sizeof(float) * (size_t)sample_count);
    float *q_copy = (float *)malloc(sizeof(float) * (size_t)sample_count);
    if (!i_copy || !q_copy) {
        free(i_copy);
        free(q_copy);
        return -4;
    }
    memcpy(i_copy, i_samples, sizeof(float) * (size_t)sample_count);
    memcpy(q_copy, q_samples, sizeof(float) * (size_t)sample_count);

    struct decoder_options options;
    memset(&options, 0, sizeof(options));
    options.freq = 0;
    options.quickmode = 0;
    options.usehashtable = 0;
    options.npasses = 2;
    options.subtraction = 1;

    struct decoder_results native_results[FIELDOPS_WSPR_MAX_RESULTS];
    memset(native_results, 0, sizeof(native_results));
    struct fieldops_wspr_worker worker;
    memset(&worker, 0, sizeof(worker));
    worker.i_samples = i_copy;
    worker.q_samples = q_copy;
    worker.sample_count = sample_count;
    worker.options = options;
    worker.results = native_results;

    pthread_attr_t attr;
    if (pthread_attr_init(&attr) != 0) {
        free(i_copy);
        free(q_copy);
        return -5;
    }

    const size_t decoder_stack_bytes = 8u * 1024u * 1024u;
    if (pthread_attr_setstacksize(&attr, decoder_stack_bytes) != 0) {
        pthread_attr_destroy(&attr);
        free(i_copy);
        free(q_copy);
        return -6;
    }

    pthread_t thread;
    int thread_rc = pthread_create(&thread, &attr, fieldops_wspr_decode_worker, &worker);
    pthread_attr_destroy(&attr);
    if (thread_rc != 0) {
        free(i_copy);
        free(q_copy);
        return -7;
    }

    thread_rc = pthread_join(thread, NULL);
    free(i_copy);
    free(q_copy);
    if (thread_rc != 0) return -8;
    if (worker.decoder_rc != 0) return -9;

    int native_count = worker.result_count;
    if (native_count < 0) return -10;

    int count = native_count;
    if (count > FIELDOPS_WSPR_MAX_RESULTS) count = FIELDOPS_WSPR_MAX_RESULTS;
    if (count > max_results) count = max_results;

    for (int n = 0; n < count; n++) {
        struct fieldops_wspr_result *out = &results[n];
        memset(out, 0, sizeof(*out));
        out->snr_db = native_results[n].snr;
        out->dt_seconds = native_results[n].dt;
        /*
         * With decoder_options.freq == 0, upstream reports:
         * (1500 Hz + decoded baseband offset) / 1e6.
         * Convert that back to the radio audio-frequency domain.
         */
        out->audio_hz = (float)(native_results[n].freq * 1000000.0);
        out->drift_hz = native_results[n].drift;
        snprintf(out->message, sizeof(out->message), "%s", native_results[n].message);
        snprintf(out->call, sizeof(out->call), "%s", native_results[n].call);
        snprintf(out->grid, sizeof(out->grid), "%s", native_results[n].loc);
        snprintf(out->power_dbm, sizeof(out->power_dbm), "%s", native_results[n].pwr);
    }

    return count;
}

int fieldops_wspr_encode_symbols(
    const char *message,
    unsigned char symbols[FIELDOPS_WSPR_SYMBOLS]
) {
    if (!message || !symbols) return 0;

    size_t message_len = strlen(message);
    char *mutable_message = (char *)malloc(message_len + 1);
    if (mutable_message) memcpy(mutable_message, message, message_len + 1);
    char *hashtab = (char *)calloc(HASHTAB_SIZE, HASHTAB_ENTRY_LEN);
    char *loctab = (char *)calloc(HASHTAB_SIZE, LOCTAB_ENTRY_LEN);
    if (!mutable_message || !hashtab || !loctab) {
        free(mutable_message);
        free(hashtab);
        free(loctab);
        return 0;
    }

    int ok = get_wspr_channel_symbols(mutable_message, hashtab, loctab, symbols);

    free(mutable_message);
    free(hashtab);
    free(loctab);
    return ok;
}

// Host-only independent-source FT4 WAV decoder gate.
// Audio originates from the WSJT-X samples/FT4 archive, NOT the FieldOps or
// FT8AF encoder. No claims about over-the-air origin or independent truth text.
// Actual production native FT4 monitor, sync search and LDPC/CRC decode must
// recover >= 1 valid payload from this immutable third-party audio file.
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "ft8/constants.h"
#include "ft8/decode.h"
#include "ft8/message.h"
#include "common/monitor.h"
#include "common/wave.h"

#define MAX_SAMPLES (12000 * 8)
#define MAX_CANDIDATES 200
#define REQUIRED_VALID 1

static bool is_duplicate(const uint8_t* payloads, int count, const uint8_t bytes[10])
{
    for (int j = 0; j < count; ++j)
        if (memcmp(payloads + j * 10, bytes, 10) == 0)
            return true;
    return false;
}

int main(int argc, char** argv)
{
    if (argc != 2)
    {
        fprintf(stderr, "Usage: ft4_independent_wav /path/to/pinned/wsjt_sample.wav\n");
        return 2;
    }
    float* pcm = (float*)calloc(MAX_SAMPLES, sizeof(float));
    if (!pcm) return 2;
    int nsamples = MAX_SAMPLES;
    int sample_rate = 0;
    int rc = load_wav(pcm, &nsamples, &sample_rate, argv[1]);
    if (rc != 0 || sample_rate != 12000 ||
        nsamples < 60000 || nsamples > MAX_SAMPLES)
    {
        fprintf(stderr, "FT4 independent WAV: invalid PCM metadata (rc=%d, rate=%d, samples=%d)\n",
                rc, sample_rate, nsamples);
        free(pcm);
        return 2;
    }

    monitor_config_t cfg = {
        .f_min = 100.0f, .f_max = 3500.0f, .sample_rate = sample_rate,
        .time_osr = 2, .freq_osr = 2, .protocol = FTX_PROTOCOL_FT4
    };
    monitor_t mon;
    memset(&mon, 0, sizeof(mon));
    monitor_init(&mon, &cfg);
    for (int i = 0; i + mon.block_size <= nsamples; i += mon.block_size)
        monitor_process(&mon, pcm + i);

    candidate_t candidates[MAX_CANDIDATES];
    int ncandidates = ft8_find_sync(&mon.wf, MAX_CANDIDATES, candidates, 10);
    int nvalid = 0;
    uint8_t seen[MAX_CANDIDATES][10] = {{0}};
    for (int i = 0; i < ncandidates; ++i)
    {
        ftx_message_t msg;
        decode_status_t status;
        // Mirrors the real FT4/FT2 JNI's 20/30-iteration decoder.
        if (!ft8_decode(&mon.wf, &candidates[i], 30, &msg, &status) ||
            is_duplicate(&seen[0][0], nvalid, msg.payload))
            continue;
        memcpy(seen[nvalid], msg.payload, 10);
        nvalid++;
        char text[FTX_MAX_MESSAGE_LENGTH] = {0};
        if (ftx_message_decode(&msg, NULL, text) == FTX_MESSAGE_RC_OK)
            printf("FT4 independent valid frame: %s\n", text);
        else
            printf("FT4 independent valid frame: CRC/LDPC valid; text unsupported\n");
    }
    printf("RESULT independent_ft4.wav candidates=%d valid=%d required=%d samples=%d rate=%d\n",
           ncandidates, nvalid, REQUIRED_VALID, nsamples, sample_rate);
    monitor_free(&mon);
    free(pcm);
    if (nvalid < REQUIRED_VALID)
    {
        fprintf(stderr, "FAIL: independent-source FT4 audio did not decode. "
                        "Do NOT downgrade floor without investigation.\n");
        return 1;
    }
    return 0;
}

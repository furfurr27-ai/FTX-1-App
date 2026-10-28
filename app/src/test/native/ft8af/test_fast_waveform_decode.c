// Fast FT-family receive regression: actual vendored C modulation -> waterfall
// -> synchronization -> LDPC/CRC demodulation for FT4 and FT2.
//
// Both test waveforms are generated from a pinned 77-bit message using the
// independent stage of the existing production native modem, NOT a fake JNI
// stub. This is a closed-loop / clean-channel fixture and is NOT independently
// recorded on-air FT4/FT2 interoperability evidence, or an Android USB/RF test.
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>
#include "ft8/constants.h"
#include "ft8/pack.h"
#include "ft8/encode.h"
#include "ft8/decode.h"
#include "common/monitor.h"

#define RATE 12000
#define MAX_CANDIDATES 140
#define TEST_CALL "CQ K1ABC FN42"

void synth_gfsk_offset(const uint8_t* symbols, int n_sym, float f0,
                       float symbol_bt, float symbol_period,
                       int signal_rate, float* signal, int offset);

static int same_payload77(const uint8_t* x, const uint8_t* y)
{
    return memcmp(x, y, 9) == 0 && ((x[9] ^ y[9]) & 0xF8u) == 0;
}

static int search_decodes(ftx_protocol_t protocol, const float* pcm,
                          int total_samples, const uint8_t expected[10])
{
    monitor_config_t cfg = {
        .f_min = 100.0f, .f_max = 3500.0f,
        .sample_rate = RATE, .time_osr = 2, .freq_osr = 2,
        .protocol = protocol
    };
    monitor_t monitor;
    memset(&monitor, 0, sizeof(monitor));
    monitor_init(&monitor, &cfg);
    for (int i = 0; i + monitor.block_size <= total_samples; i += monitor.block_size)
        monitor_process(&monitor, pcm + i);

    candidate_t candidates[MAX_CANDIDATES];
    int count = ft8_find_sync(&monitor.wf, MAX_CANDIDATES, candidates, 10);
    int matched = 0;
    for (int i = 0; i < count; ++i)
    {
        ftx_message_t result;
        decode_status_t status;
        // Same 20-iteration first pass as production FT4/FT2 JNI.
        if (ft8_decode(&monitor.wf, &candidates[i], 20, &result, &status) &&
            same_payload77(result.payload, expected))
        {
            matched = 1;
            break;
        }
    }
    monitor_free(&monitor);
    return matched;
}

static int test_mode(ftx_protocol_t protocol, const char* label,
                     float symbol_period, int slot_ms, int expected_block_size)
{
    uint8_t payload[10] = { 0 };
    if (pack77(TEST_CALL, payload) != 0)
    {
        fprintf(stderr, "%s: unable to pack known-good 77-bit message\n", label);
        return 1;
    }
    uint8_t tones[FT4_NN];
    ft4_encode(payload, tones); // FT2 uses exactly FT4's 105-tone payload.
    if (tones[0] != 0 || tones[FT4_NN - 1] != 0 ||
        memcmp(tones + 1, kFT4_Costas_pattern[0], 4) != 0 ||
        memcmp(tones + 34, kFT4_Costas_pattern[1], 4) != 0 ||
        memcmp(tones + 67, kFT4_Costas_pattern[2], 4) != 0 ||
        memcmp(tones + 100, kFT4_Costas_pattern[3], 4) != 0)
    {
        fprintf(stderr, "%s: invalid FT4/FT2 sync or ramp tones\n", label);
        return 1;
    }

    int n = slot_ms * RATE / 1000;
    int pulse_samples = (int)lroundf(symbol_period * RATE) * FT4_NN;
    int offset = RATE / 2;
    if (pulse_samples + offset > n)
    {
        fprintf(stderr, "%s: GFSK burst overruns mode slot\n", label);
        return 1;
    }
    float* pcm = (float*)calloc((size_t)n, sizeof(float));
    float* silence = (float*)calloc((size_t)n, sizeof(float));
    if (!pcm || !silence)
    {
        free(pcm);
        free(silence);
        return 1;
    }

    // Production waveform synthesizer, 1200 Hz baseband, nominal full-scale.
    synth_gfsk_offset(tones, FT4_NN, 1200.0f, 1.0f, symbol_period,
                      RATE, pcm, offset);
    monitor_config_t size_cfg = {
        .f_min = 100.0f, .f_max = 3500.0f, .sample_rate = RATE,
        .time_osr = 2, .freq_osr = 2, .protocol = protocol
    };
    monitor_t size_mon;
    memset(&size_mon, 0, sizeof(size_mon));
    monitor_init(&size_mon, &size_cfg);
    int block_size = size_mon.block_size;
    monitor_free(&size_mon);
    int ok = block_size == expected_block_size &&
             search_decodes(protocol, pcm, n, payload) &&
             !search_decodes(protocol, silence, n, payload);
    free(pcm);
    free(silence);
    if (!ok)
    {
        fprintf(stderr, "%s: clean-channel decode or silence rejection failed "
                        "(block_size=%d expected=%d)\n",
                label, block_size, expected_block_size);
        return 1;
    }
    printf("%s: PASS clean generated waveform -> real synchronized "
           "LDPC/CRC decode, silent slot rejected, %d samples, %d-sample symbols\n",
           label, n, block_size);
    return 0;
}

int main(void)
{
    int failures = 0;
    failures += test_mode(FTX_PROTOCOL_FT4, "FT4", FT4_SYMBOL_PERIOD, 7500, 576);
    failures += test_mode(FTX_PROTOCOL_FT2, "FT2", FT2_SYMBOL_PERIOD, 3750, 288);
    return failures ? 1 : 0;
}

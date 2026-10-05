package com.k1af.ft8af.ft8transmit;

import java.util.Arrays;

/**
 * Host-only deterministic FT8AF TX ABI fixture for CP-0002E.
 * It records adapter choices without loading libft8af.so.
 */
public final class GenerateFT8 {
    public static String lastEncoder = "";
    public static int lastToneCount = 0;
    public static float lastSymbolPeriod = 0.0f;
    public static float lastSymbolBt = 0.0f;
    public static float lastAudioHz = 0.0f;
    public static int lastSampleRate = 0;

    private GenerateFT8() {}

    public static int packFreeTextTo77(String text, byte[] packed77) {
        Arrays.fill(packed77, (byte)0);
        byte[] raw = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(raw, 0, packed77, 0, Math.min(raw.length, packed77.length));
        return 0;
    }

    public static void ft8_encode(byte[] packed77, byte[] tones79) {
        lastEncoder = "FT8";
        for (int i = 0; i < tones79.length; i++) tones79[i] = (byte)(i % 8);
    }

    public static void ft4_encode(byte[] packed77, byte[] tones105) {
        lastEncoder = "FT4";
        for (int i = 0; i < tones105.length; i++) tones105[i] = (byte)(i % 4);
    }

    public static void synth_gfsk(
        byte[] tones,
        int nTones,
        float symbolPeriod,
        float symbolBt,
        float audioHz,
        int sampleRate,
        float[] out,
        int outOffset
    ) {
        lastToneCount = nTones;
        lastSymbolPeriod = symbolPeriod;
        lastSymbolBt = symbolBt;
        lastAudioHz = audioHz;
        lastSampleRate = sampleRate;
        for (int i = outOffset; i < out.length; i++) {
            out[i] = ((i - outOffset) & 1) == 0 ? 0.25f : -0.25f;
        }
    }
}

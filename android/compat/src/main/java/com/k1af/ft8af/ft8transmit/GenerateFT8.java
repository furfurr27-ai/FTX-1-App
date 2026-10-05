package com.k1af.ft8af.ft8transmit;

/** Static JNI ABI shim for encode/synthesis functions in libft8af.so. */
public final class GenerateFT8 {
    static { System.loadLibrary("ft8af"); }
    private GenerateFT8() {}

    public static native int packFreeTextTo77(String text, byte[] packed77);
    public static native void ft8_encode(byte[] packed77, byte[] tones79);
    public static native void ft4_encode(byte[] packed77, byte[] tones105);
    public static native void synth_gfsk(
        byte[] tones, int nTones, float symbolPeriod, float symbolBt,
        float audioHz, int sampleRate, float[] out, int outOffset);
}

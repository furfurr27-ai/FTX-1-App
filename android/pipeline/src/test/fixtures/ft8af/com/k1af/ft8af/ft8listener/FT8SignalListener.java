package com.k1af.ft8af.ft8listener;

import com.k1af.ft8af.Ft8Message;

/**
 * Host-only deterministic FT8AF ABI fixture for CP-0002E.
 * It deliberately has no native library loader.
 */
public final class FT8SignalListener {
    public static String lastInitMode = "";
    public static int deleteCount = 0;

    public long InitDecoder(long slotStartUtcMillis, int sampleRate, int samples, boolean isFt8) {
        lastInitMode = isFt8 ? "FT8" : "FT4";
        return isFt8 ? 8L : 4L;
    }

    public void DecoderMonitorPressFloat(float[] audio, long handle) {}
    public int DecoderFt8FindSync(long handle) { return 1; }

    public boolean DecoderFt8Analysis(int candidate, long handle, Ft8Message out) {
        String mode = handle == 8L ? "FT8" : "FT4";
        fill(out, mode);
        return true;
    }

    public byte[] DecoderGetA91(long handle) { return new byte[0]; }
    public void setDecodeMode(long handle, boolean deep) {}
    public void DeleteDecoder(long handle) { deleteCount++; }

    public long InitDecoderFt2(long slotStartUtcMillis, int sampleRate, int samples, int protocol) {
        lastInitMode = "FT2";
        return 2L;
    }

    public void DecoderFt2MonitorPressFloat(float[] audio, long handle) {}
    public int DecoderFt2FindSync(long handle) { return 1; }

    public boolean DecoderFt2Analysis(int candidate, long handle, Ft8Message out) {
        fill(out, "FT2");
        return true;
    }

    public byte[] DecoderFt2GetA91(long handle) { return new byte[0]; }
    public void setDecodeModeFt2(long handle, boolean deep) {}
    public void DeleteDecoderFt2(long handle) { deleteCount++; }

    private static void fill(Ft8Message out, String mode) {
        out.isValid = true;
        out.callsignFrom = "K1JT";
        out.callsignTo = "N0PNG";
        out.maidenGrid = "FN20";
        out.extraInfo = mode;
        out.freq_hz = 1500.0f;
        out.snr = -12;
        out.time_sec = 0.25f;
    }
}

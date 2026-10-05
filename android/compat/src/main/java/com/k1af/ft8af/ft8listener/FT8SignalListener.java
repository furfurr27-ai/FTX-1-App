package com.k1af.ft8af.ft8listener;

import com.k1af.ft8af.Ft8Message;

/** JNI ABI shim for the user's uploaded FT8AF libft8af.so. */
public final class FT8SignalListener {
    static { System.loadLibrary("ft8af"); }

    public native long InitDecoder(long slotStartUtcMillis, int sampleRate, int samples, boolean isFt8);
    public native void DecoderMonitorPressFloat(float[] audio, long handle);
    public native int DecoderFt8FindSync(long handle);
    public native boolean DecoderFt8Analysis(int candidate, long handle, Ft8Message out);
    public native byte[] DecoderGetA91(long handle);
    public native void setDecodeMode(long handle, boolean deep);
    public native void DeleteDecoder(long handle);

    public native long InitDecoderFt2(long slotStartUtcMillis, int sampleRate, int samples, int protocol);
    public native void DecoderFt2MonitorPressFloat(float[] audio, long handle);
    public native int DecoderFt2FindSync(long handle);
    public native boolean DecoderFt2Analysis(int candidate, long handle, Ft8Message out);
    public native byte[] DecoderFt2GetA91(long handle);
    public native void setDecodeModeFt2(long handle, boolean deep);
    public native void DeleteDecoderFt2(long handle);
}

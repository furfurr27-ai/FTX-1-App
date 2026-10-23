package com.k1af.ft8af;

/**
 * Minimal ABI-compatible DTO for libft8af.so. Field names/types are taken from
 * the uploaded FT8AF APK DEX. Keep these public: JNI resolves them by name.
 */
public class Ft8Message {
    public int i3;
    public int n3;
    public String callsignFrom = "";
    public String callsignTo = "";
    public String extraInfo = "";
    public float freq_hz;
    public boolean isValid;
    public String maidenGrid = "";
    public String modifier = "";
    public int r_flag;
    public int report;
    public int score;
    // The current FT8AF from-source JNI obtains ALL of these fields with
    // GetFieldID. Their absence would raise NoSuchFieldError before decoding,
    // even if the native library loaded successfully.
    public int messageHash;
    public long callFromHash10;
    public long callFromHash12;
    public long callFromHash22;
    public long callToHash10;
    public long callToHash12;
    public long callToHash22;
    public int rtty_tu;
    public String rtty_state = "";
    public int eu_serial;
    public String arrl_class = "";
    public String arrl_rac = "";
    public String dx_call_to2 = "";
    public int signalFormat;
    public int snr;
    public float time_sec;
    public long utcTime;
}

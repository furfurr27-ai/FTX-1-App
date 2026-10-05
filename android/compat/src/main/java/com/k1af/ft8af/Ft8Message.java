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
    public int signalFormat;
    public int snr;
    public float time_sec;
    public long utcTime;
}

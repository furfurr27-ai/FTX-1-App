package dev.n0png.fieldops.android.dsp

/**
 * JNI binding for the vendored WSPR decoder.
 *
 * The native library is GPLv3-derived from the pinned rtlsdr-wsprd source.
 * This class performs no CAT/PTT work and exposes no WSPR waveform TX path.
 */
class WsprJniBridge(
    libraryLoader: () -> Unit = { System.loadLibrary(LIBRARY_NAME) },
) : WsprEngineAdapter.NativeBridge {

    init {
        ensureLoaded(libraryLoader)
    }

    @Synchronized
    override fun decode375(i: FloatArray, q: FloatArray): List<WsprEngineAdapter.NativeDecode> {
        require(i.size == WsprRxFrontEnd.OUTPUT_SAMPLES)
        require(q.size == WsprRxFrontEnd.OUTPUT_SAMPLES)
        return nativeDecode375(i, q).toList()
    }

    /** Encoder-side channel symbols are exposed only for deterministic RX fixtures in CP-0002C. */
    internal fun encodeSymbolsForSelfTest(message: String): ByteArray =
        nativeEncodeSymbols(message)

    private external fun nativeDecode375(
        i: FloatArray,
        q: FloatArray,
    ): Array<WsprEngineAdapter.NativeDecode>

    private external fun nativeEncodeSymbols(message: String): ByteArray

    companion object {
        const val LIBRARY_NAME = "fieldops_wspr"

        @Volatile private var loaded = false

        private fun ensureLoaded(loader: () -> Unit) {
            if (loaded) return
            synchronized(this) {
                if (!loaded) {
                    loader()
                    loaded = true
                }
            }
        }
    }
}

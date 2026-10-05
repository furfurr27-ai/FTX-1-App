package dev.n0png.fieldops.android.dsp

/**
 * JNI binding for the vendored pinned WSPR codec.
 *
 * The native library is GPLv3-derived from the pinned rtlsdr-wsprd source.
 * It performs WSPR decode plus channel-symbol encoding only. CAT/PTT and USB
 * audio remain outside this boundary.
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

    @Synchronized
    override fun encodeSymbols(message: String): ByteArray =
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

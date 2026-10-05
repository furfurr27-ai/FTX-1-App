/*
 * JNI surface for FieldOps WSPR receive integration.
 *
 * FieldOps-specific code added 2026-10-05. The linked upstream decoder is
 * GNU GPL v3. See native/wspr/upstream/LICENSE and UPSTREAM.md.
 */
#include <jni.h>
#include <stdlib.h>

#include "fieldops_wspr_bridge.h"

static void throw_illegal_argument(JNIEnv *env, const char *message) {
    jclass cls = (*env)->FindClass(env, "java/lang/IllegalArgumentException");
    if (cls) (*env)->ThrowNew(env, cls, message);
}

JNIEXPORT jobjectArray JNICALL
Java_dev_n0png_fieldops_android_dsp_WsprJniBridge_nativeDecode375(
    JNIEnv *env,
    jobject thiz,
    jfloatArray i_array,
    jfloatArray q_array
) {
    (void)thiz;

    if (!i_array || !q_array) {
        throw_illegal_argument(env, "WSPR I/Q arrays must be non-null");
        return NULL;
    }

    jsize i_len = (*env)->GetArrayLength(env, i_array);
    jsize q_len = (*env)->GetArrayLength(env, q_array);
    if (i_len != FIELDOPS_WSPR_IQ_SAMPLES || q_len != FIELDOPS_WSPR_IQ_SAMPLES) {
        throw_illegal_argument(env, "WSPR decoder requires exactly 45,000 I/Q samples");
        return NULL;
    }

    jfloat *i_samples = (*env)->GetFloatArrayElements(env, i_array, NULL);
    jfloat *q_samples = (*env)->GetFloatArrayElements(env, q_array, NULL);
    if (!i_samples || !q_samples) {
        if (i_samples) (*env)->ReleaseFloatArrayElements(env, i_array, i_samples, JNI_ABORT);
        if (q_samples) (*env)->ReleaseFloatArrayElements(env, q_array, q_samples, JNI_ABORT);
        return NULL;
    }

    struct fieldops_wspr_result native_results[FIELDOPS_WSPR_MAX_RESULTS];
    int count = fieldops_wspr_decode375(
        i_samples,
        q_samples,
        FIELDOPS_WSPR_IQ_SAMPLES,
        native_results,
        FIELDOPS_WSPR_MAX_RESULTS
    );

    (*env)->ReleaseFloatArrayElements(env, i_array, i_samples, JNI_ABORT);
    (*env)->ReleaseFloatArrayElements(env, q_array, q_samples, JNI_ABORT);

    if (count < 0) {
        jclass cls = (*env)->FindClass(env, "java/lang/IllegalStateException");
        if (cls) (*env)->ThrowNew(env, cls, "Native WSPR decoder failed");
        return NULL;
    }

    jclass result_class = (*env)->FindClass(
        env,
        "dev/n0png/fieldops/android/dsp/WsprEngineAdapter$NativeDecode"
    );
    if (!result_class) return NULL;

    jmethodID ctor = (*env)->GetMethodID(
        env,
        result_class,
        "<init>",
        "(FFFFLjava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V"
    );
    if (!ctor) return NULL;

    jobjectArray array = (*env)->NewObjectArray(env, count, result_class, NULL);
    if (!array) return NULL;

    for (int n = 0; n < count; n++) {
        jstring message = (*env)->NewStringUTF(env, native_results[n].message);
        jstring call = (*env)->NewStringUTF(env, native_results[n].call);
        jstring grid = (*env)->NewStringUTF(env, native_results[n].grid);
        jstring power = (*env)->NewStringUTF(env, native_results[n].power_dbm);
        if (!message || !call || !grid || !power) return NULL;

        jobject item = (*env)->NewObject(
            env,
            result_class,
            ctor,
            native_results[n].snr_db,
            native_results[n].dt_seconds,
            native_results[n].audio_hz,
            native_results[n].drift_hz,
            message,
            call,
            grid,
            power
        );
        if (!item) return NULL;

        (*env)->SetObjectArrayElement(env, array, n, item);
        (*env)->DeleteLocalRef(env, item);
        (*env)->DeleteLocalRef(env, message);
        (*env)->DeleteLocalRef(env, call);
        (*env)->DeleteLocalRef(env, grid);
        (*env)->DeleteLocalRef(env, power);
    }

    return array;
}

JNIEXPORT jbyteArray JNICALL
Java_dev_n0png_fieldops_android_dsp_WsprJniBridge_nativeEncodeSymbols(
    JNIEnv *env,
    jobject thiz,
    jstring message
) {
    (void)thiz;
    if (!message) {
        throw_illegal_argument(env, "WSPR fixture message must be non-null");
        return NULL;
    }

    const char *utf = (*env)->GetStringUTFChars(env, message, NULL);
    if (!utf) return NULL;

    unsigned char symbols[FIELDOPS_WSPR_SYMBOLS];
    int ok = fieldops_wspr_encode_symbols(utf, symbols);
    (*env)->ReleaseStringUTFChars(env, message, utf);

    if (!ok) {
        throw_illegal_argument(env, "WSPR fixture message could not be encoded");
        return NULL;
    }

    jbyteArray out = (*env)->NewByteArray(env, FIELDOPS_WSPR_SYMBOLS);
    if (!out) return NULL;
    (*env)->SetByteArrayRegion(
        env,
        out,
        0,
        FIELDOPS_WSPR_SYMBOLS,
        (const jbyte *)symbols
    );
    return out;
}

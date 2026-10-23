cmake_minimum_required(VERSION 3.22.1)
project(fieldops_ft_family LANGUAGES C CXX)

# Reproducible, in-tree ARM64 FT8/FT4/FT2 codec built from open source.
# Source pin: patrickrb/FT8AF @ c2f63e8b37fcd484fd2eb2049494425dd2414971
# ft8_lib source pin: kgoba/ft8_lib @ 6f528128ee3ebf4d08ba2313f6c5d3913eda5608
# Only decoder, encoder and DSP JNI glue: NEVER vendor FT8AF USB/audio,
# libusb, Hamlib, PTT, or radio transport.
set(SRC ${CMAKE_CURRENT_SOURCE_DIR}/third_party/ft8af)
set(LIB ${SRC}/ft8_lib)
set(GLUE ${SRC}/ft8af_glue)

add_library(ft8af SHARED
    ${LIB}/ft8/encode.c
    ${LIB}/ft8/decode.c
    ${LIB}/ft8/ldpc.c
    ${LIB}/ft8/osd.c
    ${LIB}/ft8/crc.c
    ${LIB}/ft8/message.c
    ${LIB}/ft8/pack.c
    ${LIB}/ft8/unpack.c
    ${LIB}/ft8/text.c
    ${LIB}/ft8/constants.c
    ${LIB}/common/monitor.c
    ${LIB}/fft/kiss_fft.c
    ${LIB}/fft/kiss_fftr.c
    ${GLUE}/gfsk.c
    ${GLUE}/ft8_encode_jni.cpp
    ${GLUE}/ft8_decode_jni.cpp
    ${GLUE}/ft2_decode_jni.cpp
    ${GLUE}/ft8_fine.c
    ${GLUE}/ft8_subtract.c
    ${GLUE}/ft8_xslot.c
)

target_include_directories(ft8af PRIVATE ${LIB} ${GLUE})
target_compile_features(ft8af PRIVATE c_std_11 cxx_std_17)
target_compile_options(ft8af PRIVATE -Wno-unused-function)
# Android 15+ 16 KB native library page size support, S23 Ultra arm64 ABI.
target_link_options(ft8af PRIVATE "-Wl,-z,max-page-size=16384")
find_library(log-lib log)
target_link_libraries(ft8af ${log-lib} m)

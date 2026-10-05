cmake_minimum_required(VERSION 3.22)
project(fieldops_tqsl_android LANGUAGES C CXX)

if(NOT ANDROID)
  message(FATAL_ERROR "This project is only for Android cross-compilation")
endif()

if(NOT DEFINED TQSL_SOURCE_DIR)
  message(FATAL_ERROR "TQSL_SOURCE_DIR must point to the verified TrustedQSL release src directory")
endif()

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

find_package(OpenSSL REQUIRED)
find_package(EXPAT REQUIRED)
find_package(ZLIB REQUIRED)
find_package(SQLite3 REQUIRED)

set(TQSL_SOURCES
  "${TQSL_SOURCE_DIR}/tqsllib.cpp"
  "${TQSL_SOURCE_DIR}/openssl_cert.cpp"
  "${TQSL_SOURCE_DIR}/adif.cpp"
  "${TQSL_SOURCE_DIR}/xml.cpp"
  "${TQSL_SOURCE_DIR}/location.cpp"
  "${TQSL_SOURCE_DIR}/tqslconvert.cpp"
  "${TQSL_SOURCE_DIR}/cabrillo.cpp"
)

add_library(fieldops_tqsl SHARED
  ${TQSL_SOURCES}
  "${CMAKE_CURRENT_LIST_DIR}/../fieldops_tqsl_jni.cpp"
)

target_include_directories(fieldops_tqsl PRIVATE
  "${TQSL_SOURCE_DIR}"
)

# Release 2.8.6 version constants from apps/tqslversion.ver.
# CONFDIR is compile-required on Unix-like platforms, but FieldOps sets
# tQSL_RsrcDir explicitly at runtime to the app-private resource directory.
target_compile_definitions(fieldops_tqsl PRIVATE
  TQSL_VERSION_MAJOR=2
  TQSL_VERSION_MINOR=8
  TQSL_VERSION_UPDATE=6
  TQSL_VERSION_PATCH=0
  TQSLLIB_VERSION_MAJOR=2
  TQSLLIB_VERSION_MINOR=8
  TQSLLIB_VERSION_UPDATE=6
  CONFDIR="."
)

target_compile_options(fieldops_tqsl PRIVATE
  -fvisibility=hidden
  -Wno-deprecated-declarations
)

target_link_libraries(fieldops_tqsl PRIVATE
  OpenSSL::SSL
  OpenSSL::Crypto
  EXPAT::EXPAT
  ZLIB::ZLIB
  SQLite::SQLite3
  dl
  m
)

set_target_properties(fieldops_tqsl PROPERTIES
  OUTPUT_NAME "fieldops_tqsl"
)

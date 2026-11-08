cmake_minimum_required(VERSION 3.22)
project(SentinelStrictJpeg C)
include(ExternalProject)
set(JPEG_SOURCE "${CMAKE_CURRENT_SOURCE_DIR}/../../../vendor/libjpeg-turbo")
set(JPEG_BINARY "${CMAKE_CURRENT_BINARY_DIR}/jpeg")
set(JPEG_ARGS "-DCMAKE_MAKE_PROGRAM=${CMAKE_MAKE_PROGRAM}" -DENABLE_SHARED=OFF -DENABLE_STATIC=ON -DWITH_SIMD=OFF
    -DWITH_TURBOJPEG=OFF -DWITH_TOOLS=OFF -DWITH_TESTS=OFF
    -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DCMAKE_BUILD_TYPE=Release)
if(ANDROID)
    list(APPEND JPEG_ARGS "-DCMAKE_TOOLCHAIN_FILE=${CMAKE_TOOLCHAIN_FILE}"
        "-DANDROID_ABI=${ANDROID_ABI}" "-DANDROID_PLATFORM=${ANDROID_PLATFORM}"
        -DANDROID_STL=none)
endif()
ExternalProject_Add(jpeg_build SOURCE_DIR "${JPEG_SOURCE}" BINARY_DIR "${JPEG_BINARY}"
    CMAKE_ARGS ${JPEG_ARGS} BUILD_COMMAND ${CMAKE_COMMAND} --build <BINARY_DIR> --target jpeg-static --parallel 2
    INSTALL_COMMAND "" BUILD_BYPRODUCTS "${JPEG_BINARY}/libjpeg.a")
add_library(jpeg-static STATIC IMPORTED)
set_target_properties(jpeg-static PROPERTIES IMPORTED_LOCATION "${JPEG_BINARY}/libjpeg.a")
add_dependencies(jpeg-static jpeg_build)
add_library(sentinel_jpeg_validator SHARED strict_jpeg.c)
target_include_directories(sentinel_jpeg_validator PRIVATE "${CMAKE_CURRENT_SOURCE_DIR}/../../../vendor/libjpeg-turbo/src" "${CMAKE_CURRENT_BINARY_DIR}/jpeg")
if(NOT ANDROID)
    find_package(JNI REQUIRED)
    target_include_directories(sentinel_jpeg_validator PRIVATE ${JNI_INCLUDE_DIRS})
endif()
target_compile_options(sentinel_jpeg_validator PRIVATE -Wall -Wextra -Werror -fvisibility=hidden)
target_link_libraries(sentinel_jpeg_validator PRIVATE jpeg-static)
# Export only JNI; keep the statically linked decoder out of the process symbol namespace.
target_link_options(sentinel_jpeg_validator PRIVATE "-Wl,--exclude-libs,ALL")
if(ANDROID)
    target_link_options(sentinel_jpeg_validator PRIVATE "-Wl,-z,max-page-size=16384")
endif()

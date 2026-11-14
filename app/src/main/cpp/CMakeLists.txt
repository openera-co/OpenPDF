cmake_minimum_required(VERSION 3.22.1)
project(reelcore C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Speech recognition is far too slow unoptimized, so debug builds optimize too (keeping symbols).
set(CMAKE_C_FLAGS_DEBUG "-O2 -g")
set(CMAKE_CXX_FLAGS_DEBUG "-O2 -g")

# whisper.cpp (vendored, v1.9.5): static, CPU backend only, portable baseline instructions so the
# same library runs on every supported phone. No OpenMP: ggml uses its own thread pool.
set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_TESTS OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
set(WHISPER_BUILD_SERVER OFF CACHE BOOL "" FORCE)
set(WHISPER_SDL2 OFF CACHE BOOL "" FORCE)
set(WHISPER_CURL OFF CACHE BOOL "" FORCE)
add_subdirectory(third_party/whisper.cpp EXCLUDE_FROM_ALL)

add_library(reelcore_static STATIC looks.cpp audio_analysis.cpp image_ops.cpp captions_core.cpp)
target_compile_options(reelcore_static PRIVATE -Wall -Wextra -ffast-math)
target_link_libraries(reelcore_static PUBLIC whisper)

if(ANDROID)
    add_library(reelcore SHARED jni_bridge.cpp)
    target_link_libraries(reelcore reelcore_static jnigraphics android log)
    target_link_options(reelcore PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,--gc-sections")
else()
    enable_testing()
    add_executable(reelcore_tests tests/core_tests.cpp)
    target_link_libraries(reelcore_tests reelcore_static)
    add_test(NAME reelcore_tests COMMAND reelcore_tests)
    # Needs a model and a 16-bit wav: captions_test <model> <wav>
    add_executable(captions_test tests/captions_test.cpp)
    target_link_libraries(captions_test reelcore_static)
endif()

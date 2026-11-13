cmake_minimum_required(VERSION 3.22.1)
project(reelcore CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(reelcore_static STATIC looks.cpp audio_analysis.cpp image_ops.cpp)
target_compile_options(reelcore_static PRIVATE -Wall -Wextra -ffast-math)

if(ANDROID)
    add_library(reelcore SHARED jni_bridge.cpp)
    target_link_libraries(reelcore reelcore_static jnigraphics log)
    target_link_options(reelcore PRIVATE "-Wl,-z,max-page-size=16384")
else()
    enable_testing()
    add_executable(reelcore_tests tests/core_tests.cpp)
    target_link_libraries(reelcore_tests reelcore_static)
    add_test(NAME reelcore_tests COMMAND reelcore_tests)
endif()

cmake_minimum_required(VERSION 3.22)
project(jarvis_native LANGUAGES C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

add_library(jarvis_native SHARED
    security_bridge.cpp
    alphavideo_gl_engine.cpp
)

target_compile_definitions(jarvis_native PRIVATE GL_GLEXT_PROTOTYPES=1)

# Keep NDK r26b while producing 16 KiB-compatible ELF PT_LOAD alignment for
# modern Android/ColorOS devices. This is the linker-side compatibility path
# when the project is intentionally pinned below NDK r28.
target_link_options(jarvis_native PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")

target_link_libraries(jarvis_native android log EGL GLESv2)

# Optional/pinned 64-bit llama.cpp native inference bridge (arm64-v8a / x86_64).
set(LLAMA_CPP_DIR "${CMAKE_CURRENT_LIST_DIR}/../../../../.llama.cpp")
if((ANDROID_ABI STREQUAL "arm64-v8a" OR ANDROID_ABI STREQUAL "x86_64") AND EXISTS "${LLAMA_CPP_DIR}/CMakeLists.txt")
    if(ANDROID_ABI STREQUAL "arm64-v8a")
        set(GGML_CPU_ARM_ARCH "armv8.2-a+dotprod+fp16" CACHE STRING "" FORCE)
    endif()
    set(BUILD_SHARED_LIBS OFF CACHE BOOL "" FORCE)
    set(GGML_NATIVE OFF CACHE BOOL "" FORCE)
    set(GGML_OPENMP OFF CACHE BOOL "" FORCE)
    set(GGML_LLAMAFILE OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_KLEIDIAI OFF CACHE BOOL "" FORCE)
    set(GGML_CPU_ALL_VARIANTS OFF CACHE BOOL "" FORCE)
    set(GGML_BACKEND_DL OFF CACHE BOOL "" FORCE)
    set(LLAMA_OPENSSL OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_COMMON OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TESTS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_TOOLS OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_SERVER OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_APP OFF CACHE BOOL "" FORCE)
    set(LLAMA_BUILD_UI OFF CACHE BOOL "" FORCE)

    add_subdirectory("${LLAMA_CPP_DIR}" "${CMAKE_CURRENT_BINARY_DIR}/llama.cpp" EXCLUDE_FROM_ALL)

    add_library(jarvis_llama SHARED jarvis_llama_jni.cpp)
    target_include_directories(jarvis_llama PRIVATE
        "${LLAMA_CPP_DIR}/include"
        "${LLAMA_CPP_DIR}/ggml/include"
    )
    target_link_options(jarvis_llama PRIVATE "-Wl,-z,max-page-size=16384" "-Wl,-z,common-page-size=16384")
    target_link_libraries(jarvis_llama PRIVATE llama android log)
endif()

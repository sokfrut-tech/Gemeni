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


# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

# Sets the minimum version of CMake required to build the native library.
cmake_minimum_required(VERSION 3.18.1)

project("kernelsu")

find_package(cxx REQUIRED CONFIG)
link_libraries(cxx::cxx)

# The shared uapi headers live at the repository root, and `cpp/uapi` is a symlink pointing at
# them. That symlink cannot survive a checkout on Windows - git writes an ordinary file holding
# the link target where it should write a link - so `#include "uapi/..."` from jni.cc and ksu.cc
# resolves to a file that is not a directory. Adding the repository root to the include path
# resolves those includes whether the symlink came through or not.
include_directories(${CMAKE_CURRENT_SOURCE_DIR}/../../../../..)

add_library(kernelsu
        SHARED
        jni.cc
        ksu.cc
        )

target_include_directories(kernelsu PRIVATE .)

find_library(log-lib log)

target_link_libraries(kernelsu ${log-lib})

add_library(adbroot SHARED adbroot.cc)
target_link_libraries(adbroot ${log-lib})

cmake_minimum_required(VERSION 3.21)
project(ncatrdp LANGUAGES C)

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)

find_package(JNI REQUIRED COMPONENTS JVM)
find_package(PkgConfig REQUIRED)
find_package(OpenSSL REQUIRED COMPONENTS Crypto)
pkg_check_modules(FREERDP REQUIRED IMPORTED_TARGET freerdp3=3.32.1)
pkg_check_modules(FREERDP_CLIENT REQUIRED IMPORTED_TARGET freerdp-client3=3.32.1)

add_library(ncatrdp SHARED rdp_jni.c)
if(WIN32)
    set_target_properties(ncatrdp PROPERTIES PREFIX "")
endif()
target_include_directories(ncatrdp PRIVATE ${JNI_INCLUDE_DIRS})
if(FREERDP_SOURCE_DIR)
    target_include_directories(ncatrdp PRIVATE
        ${FREERDP_SOURCE_DIR}/include
        ${FREERDP_SOURCE_DIR}/winpr/include)
endif()
target_link_libraries(ncatrdp PRIVATE PkgConfig::FREERDP PkgConfig::FREERDP_CLIENT OpenSSL::Crypto)

if(WIN32 AND CMAKE_C_COMPILER_ID STREQUAL "GNU")
    target_compile_definitions(ncatrdp PRIVATE __STDC_NO_THREADS__=1)
endif()

if(WIN32)
    add_library(ncatrdploader SHARED rdp_loader.c)
    set_target_properties(ncatrdploader PROPERTIES PREFIX "")
    target_include_directories(ncatrdploader PRIVATE ${JNI_INCLUDE_DIRS})
endif()

if(UNIX AND NOT APPLE)
    set_target_properties(ncatrdp PROPERTIES BUILD_RPATH "$ORIGIN" INSTALL_RPATH "$ORIGIN")
endif()

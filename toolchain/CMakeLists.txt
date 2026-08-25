cmake_minimum_required(VERSION 3.20)
project(liban32isa VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler flags
if(MSVC)
    add_compile_options(/W4 /permissive-)
else()
    add_compile_options(-Wall -Wextra -Wpedantic -Wconversion -Wsign-conversion)
endif()

# Core Library
add_library(an32isa STATIC
    src/types.cpp
    src/encoder.cpp
    src/decoder.cpp
    src/disasm.cpp
    src/isa_generated.cpp
)

target_include_directories(an32isa PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)

# CLI Tools
add_executable(an32-encode tools/an32-encode.cpp)
target_link_libraries(an32-encode PRIVATE an32isa)

add_executable(an32-decode tools/an32-decode.cpp)
target_link_libraries(an32-decode PRIVATE an32isa)

add_executable(an32-disasm tools/an32-disasm.cpp)
target_link_libraries(an32-disasm PRIVATE an32isa)

# Tests
enable_testing()

set(TEST_TARGETS
    test_encoder
    test_decoder
    test_immediates
    test_capability_checks
    test_disasm
    test_serialization
)

foreach(test_name IN LISTS TEST_TARGETS)
    add_executable(${test_name} tests/${test_name}.cpp)
    target_link_libraries(${test_name} PRIVATE an32isa)
    add_test(NAME ${test_name} COMMAND ${test_name})
endforeach()

# Python Table Synchronization Check
find_package(Python3 COMPONENTS Interpreter REQUIRED)
add_test(NAME check_isa_tables_sync
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/gen_isa_tables.py --check
)

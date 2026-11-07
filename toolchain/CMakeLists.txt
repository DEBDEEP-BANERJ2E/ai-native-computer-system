cmake_minimum_required(VERSION 3.20)
project(an32_toolchain VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_CXX_EXTENSIONS OFF)

# Compiler flags
if(MSVC)
    add_compile_options(/W4 /permissive-)
else()
    add_compile_options(-Wall -Wextra -Wpedantic)
endif()

# Core ISA Library
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

# Assembler Library
add_library(an32asm STATIC
    assembler/src/source_manager.cpp
    assembler/src/diagnostic.cpp
    assembler/src/token.cpp
    assembler/src/lexer.cpp
    assembler/src/source_expander.cpp
    assembler/src/expression.cpp
    assembler/src/parser.cpp
    assembler/src/symbol.cpp
    assembler/src/fixup.cpp
    assembler/src/fragment.cpp
    assembler/src/section.cpp
    assembler/src/ast.cpp
    assembler/src/pseudo.cpp
    assembler/src/object.cpp
    assembler/src/flat_finalizer.cpp
    assembler/src/assembler.cpp
)

target_include_directories(an32asm PUBLIC
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/assembler/include>
    $<BUILD_INTERFACE:${CMAKE_CURRENT_SOURCE_DIR}/include>
    $<INSTALL_INTERFACE:include>
)
target_link_libraries(an32asm PUBLIC an32isa)

# CLI Tools
add_executable(an32-encode tools/an32-encode.cpp)
target_link_libraries(an32-encode PRIVATE an32isa)

add_executable(an32-decode tools/an32-decode.cpp)
target_link_libraries(an32-decode PRIVATE an32isa)

add_executable(an32-disasm tools/an32-disasm.cpp)
target_link_libraries(an32-disasm PRIVATE an32isa)

add_executable(an32-as tools/an32-as.cpp)
target_link_libraries(an32-as PRIVATE an32asm an32isa)

# Tests
enable_testing()

set(PHASE1_TEST_TARGETS
    test_encoder
    test_decoder
    test_immediates
    test_capability_checks
    test_disasm
    test_serialization
)

foreach(test_name IN LISTS PHASE1_TEST_TARGETS)
    add_executable(${test_name} tests/${test_name}.cpp)
    target_link_libraries(${test_name} PRIVATE an32isa)
    add_test(NAME ${test_name} COMMAND ${test_name})
endforeach()

set(PHASE2_TEST_TARGETS
    test_lexer
    test_macro_include
    test_expression
    test_parser
    test_symbol_table
    test_sections
    test_pseudo
    test_fixups
    test_capability_asm
    test_diagnostics
    test_end_to_end
)

foreach(test_name IN LISTS PHASE2_TEST_TARGETS)
    add_executable(${test_name} assembler/tests/${test_name}.cpp)
    target_link_libraries(${test_name} PRIVATE an32asm an32isa)
    add_test(NAME ${test_name} COMMAND ${test_name})
endforeach()

# Python Table Synchronization Check
find_package(Python3 COMPONENTS Interpreter REQUIRED)
add_test(NAME check_isa_tables_sync
    COMMAND ${Python3_EXECUTABLE} ${CMAKE_CURRENT_SOURCE_DIR}/tools/gen_isa_tables.py --check
)

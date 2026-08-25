CXX ?= clang++
CXXFLAGS ?= -std=c++20 -Wall -Wextra -Wpedantic -O2 -Iinclude -Iassembler/include

# ISA Library
ISA_SRCS = src/types.cpp src/encoder.cpp src/decoder.cpp src/disasm.cpp src/isa_generated.cpp
ISA_OBJS = $(ISA_SRCS:.cpp=.o)
ISA_LIB = liban32isa.a

# Assembler Library
ASM_SRCS = assembler/src/source_manager.cpp \
           assembler/src/diagnostic.cpp \
           assembler/src/token.cpp \
           assembler/src/lexer.cpp \
           assembler/src/source_expander.cpp \
           assembler/src/expression.cpp \
           assembler/src/parser.cpp \
           assembler/src/symbol.cpp \
           assembler/src/fixup.cpp \
           assembler/src/fragment.cpp \
           assembler/src/section.cpp \
           assembler/src/ast.cpp \
           assembler/src/pseudo.cpp \
           assembler/src/object.cpp \
           assembler/src/flat_finalizer.cpp \
           assembler/src/assembler.cpp

ASM_OBJS = $(ASM_SRCS:.cpp=.o)
ASM_LIB = liban32asm.a

TOOLS = bin/an32-encode bin/an32-decode bin/an32-disasm bin/an32-as
PHASE1_TESTS = bin/test_encoder bin/test_decoder bin/test_immediates bin/test_capability_checks bin/test_disasm bin/test_serialization
PHASE2_TESTS = bin/test_lexer bin/test_macro_include bin/test_expression bin/test_parser bin/test_symbol_table bin/test_sections bin/test_pseudo bin/test_fixups bin/test_capability_asm bin/test_diagnostics bin/test_end_to_end

all: $(ISA_LIB) $(ASM_LIB) $(TOOLS) $(PHASE1_TESTS) $(PHASE2_TESTS)

$(ISA_LIB): $(ISA_OBJS)
	@mkdir -p lib
	ar rcs lib/$(ISA_LIB) $(ISA_OBJS)

$(ASM_LIB): $(ASM_OBJS) $(ISA_LIB)
	@mkdir -p lib
	ar rcs lib/$(ASM_LIB) $(ASM_OBJS)

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/an32-as: tools/an32-as.cpp $(ASM_LIB) $(ISA_LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32asm -lan32isa -o $@

bin/%: tools/%.cpp $(ISA_LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

bin/%: tests/%.cpp $(ISA_LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

bin/%: assembler/tests/%.cpp $(ASM_LIB) $(ISA_LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32asm -lan32isa -o $@

verification/generate_vectors: verification/generate_vectors.cpp $(ISA_LIB)
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

test_phase1: $(PHASE1_TESTS)
	@echo "=================================================================="
	@echo "  RUNNING LIBAN32ISA UNIT TEST SUITE"
	@echo "=================================================================="
	@python3 tools/gen_isa_tables.py --check
	@./bin/test_encoder
	@./bin/test_decoder
	@./bin/test_immediates
	@./bin/test_capability_checks
	@./bin/test_disasm
	@./bin/test_serialization
	@echo "=================================================================="
	@echo "  ALL LIBAN32ISA UNIT TESTS PASSED (100%)"
	@echo "=================================================================="

test_phase2: $(PHASE2_TESTS) bin/an32-as
	@echo "=================================================================="
	@echo "  RUNNING LIBAN32ASM & AN32-AS UNIT TEST SUITE"
	@echo "=================================================================="
	@./bin/test_lexer
	@./bin/test_macro_include
	@./bin/test_expression
	@./bin/test_parser
	@./bin/test_symbol_table
	@./bin/test_sections
	@./bin/test_pseudo
	@./bin/test_fixups
	@./bin/test_capability_asm
	@./bin/test_diagnostics
	@./bin/test_end_to_end
	@echo "=================================================================="
	@echo "  ALL LIBAN32ASM UNIT TESTS PASSED (100%)"
	@echo "=================================================================="

test: test_phase1 test_phase2

clean:
	rm -rf $(ISA_OBJS) $(ASM_OBJS) lib bin build

.PHONY: all test test_phase1 test_phase2 clean

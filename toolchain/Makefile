CXX ?= clang++
CXXFLAGS ?= -std=c++20 -Wall -Wextra -Wpedantic -O2 -Iinclude

SRCS = src/types.cpp src/encoder.cpp src/decoder.cpp src/disasm.cpp src/isa_generated.cpp
OBJS = $(SRCS:.cpp=.o)
LIB = liban32isa.a

TOOLS = bin/an32-encode bin/an32-decode bin/an32-disasm
TESTS = bin/test_encoder bin/test_decoder bin/test_immediates bin/test_capability_checks bin/test_disasm bin/test_serialization

all: $(LIB) $(TOOLS) $(TESTS)

$(LIB): $(OBJS)
	@mkdir -p lib
	ar rcs lib/$(LIB) $(OBJS)

%.o: %.cpp
	$(CXX) $(CXXFLAGS) -c $< -o $@

bin/%: tools/%.cpp $(LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

bin/%: tests/%.cpp $(LIB)
	@mkdir -p bin
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

verification/generate_vectors: verification/generate_vectors.cpp $(LIB)
	$(CXX) $(CXXFLAGS) $< -Llib -lan32isa -o $@

test: $(TESTS)
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

clean:
	rm -rf $(OBJS) lib bin build

.PHONY: all test clean

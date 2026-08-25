#!/usr/bin/env bash
set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TOOLCHAIN_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================================="
echo "  AN32 ISA TOOLCHAIN & FROZEN RTL DIFFERENTIAL VERIFICATION"
echo "=================================================================="

# 1. Check ISA table generation synchronization
echo ">>> Checking ISA table synchronization..."
python3 "${TOOLCHAIN_DIR}/tools/gen_isa_tables.py" --check

# 2. Build liban32isa, CLI tools, and unit tests
echo ">>> Building liban32isa and unit tests..."
make -C "${TOOLCHAIN_DIR}" all

# 3. Run all C++ unit tests
echo ">>> Running C++ unit test suite..."
make -C "${TOOLCHAIN_DIR}" test

# 4. Generate differential test vector corpus
echo ">>> Generating structured and randomized differential test vectors..."
make -C "${TOOLCHAIN_DIR}" verification/generate_vectors
"${SCRIPT_DIR}/generate_vectors" "${SCRIPT_DIR}/test_vectors.csv"

# 5. Run frozen Chisel RTL differential decoder oracle
echo ">>> Running frozen RTL differential decoder oracle against Chisel hardware..."
cd "${SCRIPT_DIR}"
sbt "run test_vectors.csv"

echo "=================================================================="
echo "  PHASE 1 ENCODER/DECODER & RTL DIFFERENTIAL VERIFICATION COMPLETE"
echo "=================================================================="

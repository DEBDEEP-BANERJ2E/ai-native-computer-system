#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Phase 1 Release & Verification Master Gate Runner
# ==============================================================================
# This script executes the complete verification and release gating pipeline:
# 1. YAML Specification Synchronization Check (--check)
# 2. C++20 Makefile Build & Clean Compilation
# 3. CMake Out-of-Source Build Verification
# 4. C++20 liban32isa Unit Test Suite (6 suites)
# 5. Exhaustive & Adversarial Differential Vector Generation (80,000+ vectors)
# 6. Frozen Chisel RTL Differential Decoder Oracle (21 ControlSignalsBundle fields)
# 7. Objective 1 RTL Full Regression Suite (Digital Logic)
# 8. Objective 2 RTL Full Regression Suite (RISC-V Core + CapabilityLite)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================================="
echo "  PHASE 1 MASTER RELEASE CHECKS & VERIFICATION RUNNER"
echo "=================================================================="

# 1. Spec table generation sync check
echo ">>> [1/8] Checking deterministic ISA spec synchronization..."
python3 "${SCRIPT_DIR}/tools/gen_isa_tables.py" --check

# 2. Makefile build
echo ">>> [2/8] Building liban32isa and CLI tools via Makefile..."
make -C "${SCRIPT_DIR}" clean
make -C "${SCRIPT_DIR}" all

# 3. CMake build check (if cmake is available)
if command -v cmake >/dev/null 2>&1; then
    echo ">>> [3/8] Verifying CMake build..."
    mkdir -p "${SCRIPT_DIR}/build"
    (cd "${SCRIPT_DIR}/build" && cmake .. -DCMAKE_BUILD_TYPE=Release && make)
else
    echo ">>> [3/8] CMake not found on PATH; skipping optional CMake build verification."
fi

# 4. C++ Unit Tests
echo ">>> [4/8] Running liban32isa unit test suite..."
make -C "${SCRIPT_DIR}" test

# 5. Generate Differential Vectors
echo ">>> [5/8] Generating exhaustive structured & randomized test vectors..."
make -C "${SCRIPT_DIR}" verification/generate_vectors
"${SCRIPT_DIR}/verification/generate_vectors" "${SCRIPT_DIR}/verification/test_vectors.csv"

# 6. Frozen RTL Differential Decoder Oracle
echo ">>> [6/8] Running frozen RTL differential decoder oracle (sbt)..."
(cd "${SCRIPT_DIR}/verification" && sbt "run test_vectors.csv")

# 7. Objective 1 RTL Regression Suite
echo ">>> [7/8] Running Objective 1 RTL regression suite..."
(cd "${PROJECT_ROOT}/objective01-digital-logic" && sbt test)

# 8. Objective 2 RTL Regression Suite
echo ">>> [8/8] Running Objective 2 RTL regression suite..."
(cd "${PROJECT_ROOT}/objective02-riscv-core" && sbt test)

echo "=================================================================="
echo "  ALL PHASE 1 RELEASE GATES PASSED (100% VERIFIED)"
echo "=================================================================="

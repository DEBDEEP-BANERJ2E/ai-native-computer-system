#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# Phase 2 Release & Verification Master Gate Runner
# ==============================================================================
# 1. Spec table generation sync check (Phase 0 YAML spec -> C++ headers)
# 2. Makefile Clean Compilation & Library Build (liban32isa, liban32asm, an32-as)
# 3. CMake Out-of-Source Build Verification
# 4. Phase 1 & Phase 2 C++ Unit Test Suites (17 suites total)
# 5. Exhaustive Differential Vector Verification (85,640 vectors)
# 6. Objective 1 RTL Full Regression Suite (Digital Logic - 24 tests)
# 7. Objective 2 RTL Full Regression Suite (RISC-V Core + CapabilityLite - 108 tests)
# 8. Objective 2 Assembler Hardware Integration Suite (PipelinedCore + an32-as)
# ==============================================================================

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

echo "=================================================================="
echo "  AN32 PHASE 2 MASTER RELEASE CHECKS & VERIFICATION RUNNER"
echo "=================================================================="
echo "Timestamp: $(date -u)"
echo "Host: $(uname -smp)"
echo "Toolchain root: ${SCRIPT_DIR}"
echo ""

# 1. Spec table generation sync check
echo ">>> [1/8] Checking deterministic ISA spec synchronization..."
python3 "${SCRIPT_DIR}/tools/gen_isa_tables.py" --check
echo "Phase 0 ISA specification check passed."
echo ""

# 2. Makefile build
echo ">>> [2/8] Building liban32isa, liban32asm, and CLI tools via Makefile..."
make -C "${SCRIPT_DIR}" clean
make -C "${SCRIPT_DIR}" all -j8
echo "Makefile build successful."
echo ""

# 3. CMake build check (if cmake is available)
if command -v cmake >/dev/null 2>&1; then
    echo ">>> [3/8] Verifying CMake build..."
    mkdir -p "${SCRIPT_DIR}/build"
    (cd "${SCRIPT_DIR}/build" && cmake .. -DCMAKE_BUILD_TYPE=Release && make -j8)
    echo "CMake build successful."
else
    echo ">>> [3/8] CMake not found on PATH; skipping optional CMake build verification."
fi
echo ""

# 4. Phase 1 & Phase 2 C++ Unit Tests (17 suites)
echo ">>> [4/8] Running all Phase 1 and Phase 2 C++ unit test suites (17 suites)..."
make -C "${SCRIPT_DIR}" test
echo "All 17 Phase 1 and Phase 2 unit test suites passed (100%)."
echo ""

# 5. Generate & Run Phase 1 RTL Differential Vector Verification (85,640 vectors)
echo ">>> [5/8] Running Phase 1 RTL Differential Decoder Verification (85,640 vectors)..."
make -C "${SCRIPT_DIR}" verification/generate_vectors
"${SCRIPT_DIR}/verification/generate_vectors" "${SCRIPT_DIR}/verification/test_vectors.csv"
(cd "${SCRIPT_DIR}/verification" && sbt "run test_vectors.csv")
echo "Phase 1 RTL differential decoder oracle passed (85,640 / 85,640 vectors matched)."
echo ""

# 6. Objective 1 RTL Regression Suite
echo ">>> [6/8] Running Objective 1 RTL regression suite..."
(cd "${PROJECT_ROOT}/objective01-digital-logic" && sbt test)
echo "Objective 1 RTL regression suite passed."
echo ""

# 7. Objective 2 RTL Regression Suite
echo ">>> [7/8] Running Objective 2 RTL regression suite..."
(cd "${PROJECT_ROOT}/objective02-riscv-core" && sbt test)
echo "Objective 2 RTL regression suite passed."
echo ""

# 8. Objective 2 Assembler Hardware Integration Suite
echo ">>> [8/8] Running Objective 2 Assembler Hardware Integration spec (an32-as -> PipelinedCore)..."
(cd "${SCRIPT_DIR}/verification" && sbt "testOnly verification.Objective2AssemblerSpec")
echo "Objective 2 Assembler Hardware Integration spec passed."
echo ""

echo "=================================================================="
echo "  ALL PHASE 2 VERIFICATION GATES PASSED (100% SUCCESS)"
echo "=================================================================="

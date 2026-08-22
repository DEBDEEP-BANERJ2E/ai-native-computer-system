#include "VObjective1Subsystem.h"
#include "verilated.h"

#include <cstdint>
#include <iostream>
#include <regex>
#include <stdexcept>
#include <string>

namespace {
constexpr uint32_t REV_ENERGY_ACC = 0x80001000;
constexpr uint32_t CLA_SWITCHING = 0x80001004;
constexpr uint32_t MUL_THERMAL = 0x80001008;
constexpr uint32_t EDP_CURRENT = 0x8000100c;
constexpr uint32_t EDP_CONFIG = 0x80001010;

uint32_t number(const std::string& json, const char* key, uint32_t limit) {
  const std::regex pattern("\\\"" + std::string(key) + "\\\"\\s*:\\s*(0[xX][0-9a-fA-F]+|[0-9]+)");
  std::smatch match;
  if (!std::regex_search(json, match, pattern)) {
    throw std::invalid_argument(std::string("missing numeric field: ") + key);
  }
  const std::string value = match[1].str();
  const uint64_t parsed = std::stoull(value, nullptr, 0);
  if (parsed > limit) {
    throw std::invalid_argument(std::string("numeric field out of range: ") + key);
  }
  return static_cast<uint32_t>(parsed);
}

bool boolean(const std::string& json, const char* key, bool defaultValue) {
  const std::regex pattern("\\\"" + std::string(key) + "\\\"\\s*:\\s*(true|false)");
  std::smatch match;
  if (!std::regex_search(json, match, pattern)) {
    return defaultValue;
  }
  return match[1].str() == "true";
}

bool commandIs(const std::string& json, const char* command) {
  const std::regex pattern("\\\"command\\\"\\s*:\\s*\\\"" + std::string(command) + "\\\"");
  return std::regex_search(json, pattern);
}

void clockCycle(VObjective1Subsystem* simulator) {
  simulator->clock = 0;
  simulator->eval();
  simulator->clock = 1;
  simulator->eval();
  simulator->clock = 0;
  simulator->eval();
}

uint32_t readTelemetry(VObjective1Subsystem* simulator, uint32_t address) {
  simulator->io_telemetryAddress = address;
  simulator->eval();
  return simulator->io_telemetryData;
}

void reset(VObjective1Subsystem* simulator) {
  simulator->reset = 1;
  simulator->io_operationValid = 0;
  clockCycle(simulator);
  simulator->reset = 0;
}

void respond(VObjective1Subsystem* simulator, const std::string& json) {
  if (commandIs(json, "reset")) {
    reset(simulator);
    std::cout << "{\"ok\":true,\"command\":\"reset\"}" << std::endl;
    return;
  }
  if (!commandIs(json, "execute")) {
    throw std::invalid_argument("command must be execute or reset");
  }

  simulator->io_a = number(json, "a", UINT32_MAX);
  simulator->io_b = number(json, "b", UINT32_MAX);
  simulator->io_opcode = number(json, "opcode", 15);
  simulator->io_operationValid = boolean(json, "operation_valid", true);
  const uint32_t telemetryAddress = number(json, "telemetry_address", UINT32_MAX);
  clockCycle(simulator);

  std::cout << "{\"ok\":true"
            << ",\"result\":" << simulator->io_result
            << ",\"zero\":" << (simulator->io_zero ? "true" : "false")
            << ",\"negative\":" << (simulator->io_negative ? "true" : "false")
            << ",\"carry\":" << (simulator->io_carry ? "true" : "false")
            << ",\"overflow\":" << (simulator->io_overflow ? "true" : "false")
            << ",\"busy\":" << (simulator->io_busy ? "true" : "false")
            << ",\"done\":" << (simulator->io_done ? "true" : "false")
            << ",\"valid\":" << (simulator->io_valid ? "true" : "false")
            << ",\"telemetry_data\":" << readTelemetry(simulator, telemetryAddress)
            << ",\"telemetry\":{"
            << "\"rev_energy_acc\":" << readTelemetry(simulator, REV_ENERGY_ACC)
            << ",\"cla_switching\":" << readTelemetry(simulator, CLA_SWITCHING)
            << ",\"mul_thermal\":" << readTelemetry(simulator, MUL_THERMAL)
            << ",\"edp_current\":" << readTelemetry(simulator, EDP_CURRENT)
            << ",\"edp_config\":" << readTelemetry(simulator, EDP_CONFIG)
            << "}}" << std::endl;
}
}  // namespace

int main(int argc, char** argv) {
  Verilated::commandArgs(argc, argv);
  auto simulator = new VObjective1Subsystem;
  reset(simulator);

  std::string line;
  while (std::getline(std::cin, line)) {
    if (line.empty()) {
      continue;
    }
    try {
      respond(simulator, line);
    } catch (const std::exception& error) {
      std::cerr << "simulator error: " << error.what() << std::endl;
      delete simulator;
      return 2;
    }
  }

  delete simulator;
  return 0;
}
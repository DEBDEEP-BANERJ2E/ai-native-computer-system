import {
  SimulationState,
  ScenarioItem,
  AssembleResponse,
  CoreComparisonData,
  SimulatorManifest
} from "./types";

const API_BASE = "";

export async function fetchManifest(): Promise<SimulatorManifest> {
  const res = await fetch(`${API_BASE}/api/manifest`);
  if (!res.ok) throw new Error("Failed to fetch simulator manifest");
  return res.json();
}

export async function fetchHealth(): Promise<any> {
  const res = await fetch(`${API_BASE}/api/health`);
  if (!res.ok) throw new Error("Backend offline");
  return res.json();
}

export async function fetchScenarios(): Promise<{ count: number; scenarios: ScenarioItem[] }> {
  const res = await fetch(`${API_BASE}/api/scenarios`);
  if (!res.ok) throw new Error("Failed to fetch scenarios");
  return res.json();
}

export async function fetchState(): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/state`);
  if (!res.ok) throw new Error("Failed to fetch simulation state");
  return res.json();
}

export async function resetEngine(): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/reset`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to reset engine");
  return res.json();
}

export async function loadScenario(scenarioId: string): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/scenario/load`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scenario_id: scenarioId }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.detail || "Failed to load scenario");
  }
  return res.json();
}

export async function stepScenario(): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/scenario/step`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to step scenario");
  return res.json();
}

export async function runScenario(maxSteps = 100): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/scenario/run?max_steps=${maxSteps}`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to run scenario");
  return res.json();
}

export async function assembleCode(source: string): Promise<AssembleResponse> {
  const res = await fetch(`${API_BASE}/api/reference/assemble`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ source }),
  });
  if (!res.ok) throw new Error("Failed to assemble code");
  return res.json();
}

export async function loadReferenceAssembly(source: string): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/reference/load`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ source }),
  });
  if (!res.ok) {
    const err = await res.json();
    throw new Error(err.detail || "Failed to load reference assembly");
  }
  return res.json();
}

export async function stepReference(): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/reference/step`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to step reference model");
  return res.json();
}

export async function runReference(maxSteps = 100): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/reference/run?max_steps=${maxSteps}`, { method: "POST" });
  if (!res.ok) throw new Error("Failed to run reference model");
  return res.json();
}

export async function writeMMIO(register: string, value: number): Promise<SimulationState> {
  const res = await fetch(`${API_BASE}/api/mmio/write`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ register, value }),
  });
  if (!res.ok) throw new Error("Failed to write MMIO");
  return res.json();
}

export async function compareCores(scenarioId: string): Promise<CoreComparisonData> {
  const res = await fetch(`${API_BASE}/api/compare`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scenario_id: scenarioId }),
  });
  if (!res.ok) throw new Error("Failed to compare cores");
  return res.json();
}

export async function fetchVerificationEvidence(): Promise<any> {
  const res = await fetch(`${API_BASE}/api/verification`);
  if (!res.ok) throw new Error("Failed to fetch verification evidence");
  return res.json();
}

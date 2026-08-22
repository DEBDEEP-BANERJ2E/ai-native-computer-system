import type { SimulationResponse } from "./types";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, { headers: { "Content-Type": "application/json" }, ...init });
  const body = await response.json();
  if (!response.ok) throw new Error(body.detail ?? "Backend request failed");
  return body;
}

export function execute(a: number, b: number, opcode: number) {
  return request<SimulationResponse>("/api/execute", { method: "POST", body: JSON.stringify({ a, b, opcode }) });
}

export function reset() { return request<{ ok: boolean }>("/api/reset", { method: "POST" }); }
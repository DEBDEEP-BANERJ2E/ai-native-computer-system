import React, { useEffect, useState } from "react";
import {
  Layers,
  Play,
  AlertTriangle,
  Cpu,
  Activity,
  Shield,
  ShieldAlert,
  CheckCircle2,
  GitCompare,
  FileCode,
} from "lucide-react";
import { Header } from "./components/Header";
import { Lab1ArchExplorer } from "./components/labs/Lab1ArchExplorer";
import { Lab2LiveExecution } from "./components/labs/Lab2LiveExecution";
import { Lab3HazardVisualizer } from "./components/labs/Lab3HazardVisualizer";
import { Lab4ArithmeticLab } from "./components/labs/Lab4ArithmeticLab";
import { Lab5MMIOTelemetry } from "./components/labs/Lab5MMIOTelemetry";
import { Lab6CapabilityPlayground } from "./components/labs/Lab6CapabilityPlayground";
import { Lab7AttackTrapDemo } from "./components/labs/Lab7AttackTrapDemo";
import { Lab8VerificationEvidence } from "./components/labs/Lab8VerificationEvidence";
import { LabCompareCores } from "./components/labs/LabCompareCores";
import { LabArchitectureInspector } from "./components/labs/LabArchitectureInspector";
import {
  fetchScenarios,
  fetchState,
  loadScenario,
  stepScenario,
  runScenario,
  resetEngine,
  loadReferenceAssembly,
  stepReference,
  runReference,
  writeMMIO,
} from "./api";
import { SimulationState, ScenarioItem } from "./types";

export const App: React.FC = () => {
  const [activeTab, setActiveTab] = useState<string>("lab2");
  const [scenarios, setScenarios] = useState<ScenarioItem[]>([]);
  const [currentScenarioId, setCurrentScenarioId] = useState<string>("canon_prog1_alu");
  const [state, setState] = useState<SimulationState | null>(null);

  // Initialize Scenarios and State
  useEffect(() => {
    fetchScenarios()
      .then((data) => {
        setScenarios(data.scenarios);
        if (data.scenarios.length > 0) {
          handleSelectScenario(data.scenarios[0].id);
        }
      })
      .catch(console.error);
  }, []);

  const handleSelectScenario = async (id: string) => {
    setCurrentScenarioId(id);
    try {
      const newState = await loadScenario(id);
      setState(newState);
    } catch (e) {
      console.error(e);
    }
  };

  const handleStep = async () => {
    if (!state) return;
    try {
      const newState = state.engine === "rtl" ? await stepScenario() : await stepReference();
      setState(newState);
    } catch (e) {
      console.error(e);
    }
  };

  const handleRun = async () => {
    if (!state) return;
    try {
      const newState = state.engine === "rtl" ? await runScenario(100) : await runReference(100);
      setState(newState);
    } catch (e) {
      console.error(e);
    }
  };

  const handleReset = async () => {
    try {
      const newState = await resetEngine();
      setState(newState);
      if (currentScenarioId) {
        handleSelectScenario(currentScenarioId);
      }
    } catch (e) {
      console.error(e);
    }
  };

  const handleLoadCustomAssembly = async (code: string) => {
    try {
      const newState = await loadReferenceAssembly(code);
      setState(newState);
    } catch (e) {
      console.error(e);
    }
  };

  const handleWriteMMIO = async (register: string, value: number) => {
    try {
      const newState = await writeMMIO(register, value);
      setState(newState);
    } catch (e) {
      console.error(e);
    }
  };

  return (
    <div className="app-container">
      {/* Top Header */}
      <Header
        state={state}
        scenarios={scenarios}
        currentScenarioId={currentScenarioId}
        onSelectScenario={handleSelectScenario}
        onReset={handleReset}
      />

      {/* Navigation Tabs Bar */}
      <nav className="nav-tabs-bar">
        <button
          className={`nav-tab ${activeTab === "lab1" ? "active" : ""}`}
          onClick={() => setActiveTab("lab1")}
        >
          <Layers size={14} /> Lab 1: Architecture Explorer
        </button>
        <button
          className={`nav-tab ${activeTab === "lab2" ? "active" : ""}`}
          onClick={() => setActiveTab("lab2")}
        >
          <Play size={14} /> Lab 2: Live RV32IM Execution
        </button>
        <button
          className={`nav-tab ${activeTab === "lab3" ? "active" : ""}`}
          onClick={() => setActiveTab("lab3")}
        >
          <AlertTriangle size={14} /> Lab 3: Hazards & Forwarding
        </button>
        <button
          className={`nav-tab ${activeTab === "lab4" ? "active" : ""}`}
          onClick={() => setActiveTab("lab4")}
        >
          <Cpu size={14} /> Lab 4: RV32M Arithmetic
        </button>
        <button
          className={`nav-tab ${activeTab === "lab5" ? "active" : ""}`}
          onClick={() => setActiveTab("lab5")}
        >
          <Activity size={14} /> Lab 5: MMIO & Telemetry
        </button>
        <button
          className={`nav-tab ${activeTab === "lab6" ? "active" : ""}`}
          onClick={() => setActiveTab("lab6")}
        >
          <Shield size={14} /> Lab 6: CapabilityLite Security
        </button>
        <button
          className={`nav-tab ${activeTab === "lab7" ? "active" : ""}`}
          onClick={() => setActiveTab("lab7")}
        >
          <ShieldAlert size={14} /> Lab 7: Attacks & Precise Traps
        </button>
        <button
          className={`nav-tab ${activeTab === "lab8" ? "active" : ""}`}
          onClick={() => setActiveTab("lab8")}
        >
          <CheckCircle2 size={14} /> Lab 8: Verification Evidence
        </button>
        <button
          className={`nav-tab ${activeTab === "compare" ? "active" : ""}`}
          onClick={() => setActiveTab("compare")}
        >
          <GitCompare size={14} /> Compare Cores
        </button>
        <button
          className={`nav-tab ${activeTab === "inspector" ? "active" : ""}`}
          onClick={() => setActiveTab("inspector")}
        >
          <FileCode size={14} /> Architecture Inspector & Q&A
        </button>
      </nav>

      {/* Main Lab View */}
      <main className="main-content">
        {activeTab === "lab1" && <Lab1ArchExplorer state={state} />}

        {activeTab === "lab2" && (
          <Lab2LiveExecution
            state={state}
            scenarios={scenarios}
            currentScenarioId={currentScenarioId}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
            onLoadCustomAssembly={handleLoadCustomAssembly}
          />
        )}

        {activeTab === "lab3" && (
          <Lab3HazardVisualizer
            state={state}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
          />
        )}

        {activeTab === "lab4" && (
          <Lab4ArithmeticLab
            state={state}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
          />
        )}

        {activeTab === "lab5" && (
          <Lab5MMIOTelemetry
            state={state}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
            onWriteMMIO={handleWriteMMIO}
          />
        )}

        {activeTab === "lab6" && (
          <Lab6CapabilityPlayground
            state={state}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
          />
        )}

        {activeTab === "lab7" && (
          <Lab7AttackTrapDemo
            state={state}
            onSelectScenario={handleSelectScenario}
            onStep={handleStep}
            onRun={handleRun}
            onReset={handleReset}
          />
        )}

        {activeTab === "lab8" && <Lab8VerificationEvidence />}

        {activeTab === "compare" && <LabCompareCores scenarios={scenarios} />}

        {activeTab === "inspector" && <LabArchitectureInspector />}
      </main>
    </div>
  );
};

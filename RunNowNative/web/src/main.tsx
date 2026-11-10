import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import "./styles.css";
import { RunSenseApp } from "@/components/runsense/app";

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <RunSenseApp />
  </StrictMode>,
);

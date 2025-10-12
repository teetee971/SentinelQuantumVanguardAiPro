import React from "react";
import ReactDOM from "react-dom/client";
import "./index.css";
import IADashboard from "./components/IADashboard";
import IAHealthMonitor from "./components/IAHealthMonitor";

ReactDOM.createRoot(document.getElementById("root")).render(
  <React.StrictMode>
    <IADashboard />
    <IAHealthMonitor />
  </React.StrictMode>
);
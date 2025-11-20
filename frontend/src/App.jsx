import { Routes, Route, Link } from "react-router-dom";
// eslint-disable-next-line no-unused-vars
import { motion } from "framer-motion";
import { Shield } from "lucide-react";

import Journal from "./pages/Journal";
import PegasusScan from "./pages/PegasusScan";
import TestIA from "./pages/TestIA";
import Telechargement from "./pages/Telechargement";
import ThreatMap from "./pages/ThreatMap";
import About from "./pages/About";
import Pricing from "./pages/Pricing";
import VerificationParticulier from "./pages/VerificationParticulier";
import VerificationProfessionnel from "./pages/VerificationProfessionnel";
import Layout from "./components/Layout";
import Home from "./pages/Home";

export default function App() {
  return (
    <Layout>
      <Home />
    </Layout>
  );
}
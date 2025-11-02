import { useState, useEffect } from "react";

/**
 * SecureFormWatcher - MODULE 12
 * Surveille et protège les formulaires web contre l'injection de scripts,
 * la fraude ou le phishing
 */

export function useSecureFormWatcher(formId) {
  const [isSecure, setIsSecure] = useState(true);
  const [threats, setThreats] = useState([]);
  const [validationErrors, setValidationErrors] = useState({});

  // Input Validator - Validate and sanitize inputs
  const validateInput = (name, value) => {
    const threats = [];
    
    // XSS Detection
    if (/<script|javascript:|onerror=|onclick=/i.test(value)) {
      threats.push({ type: "XSS", field: name, severity: "high" });
      return false;
    }

    // SQL Injection Detection
    if (/('|(--)|;|\*|xp_|sp_|exec|execute|select|insert|update|delete|drop|create|alter)/i.test(value)) {
      threats.push({ type: "SQL_INJECTION", field: name, severity: "critical" });
      return false;
    }

    // Path Traversal Detection
    if (/\.\.|\/etc\/|\/root\/|C:\\|\\windows\\/i.test(value)) {
      threats.push({ type: "PATH_TRAVERSAL", field: name, severity: "high" });
      return false;
    }

    if (threats.length > 0) {
      setThreats((prev) => [...prev, ...threats]);
      setIsSecure(false);
      return false;
    }

    return true;
  };

  // Form Integrity Scanner
  const scanFormIntegrity = (formElement) => {
    if (!formElement) return true;

    const inputs = formElement.querySelectorAll("input, textarea, select");
    let isIntegrityValid = true;

    inputs.forEach((input) => {
      // Check for hidden fields with suspicious names
      if (
        input.type === "hidden" &&
        /admin|root|password|token/i.test(input.name)
      ) {
        setThreats((prev) => [
          ...prev,
          {
            type: "SUSPICIOUS_FIELD",
            field: input.name,
            severity: "medium",
          },
        ]);
        isIntegrityValid = false;
      }

      // Check for auto-fill manipulation
      if (input.autocomplete === "off" && input.value) {
        console.warn("⚠️ SecureFormWatcher: Suspicious auto-fill behavior");
      }
    });

    return isIntegrityValid;
  };

  // Spam Filter Node - Simple bot detection
  const isLikelyBot = (submissionTime) => {
    // If form submitted in less than 2 seconds, likely a bot
    return submissionTime < 2000;
  };

  return {
    isSecure,
    threats,
    validationErrors,
    validateInput,
    scanFormIntegrity,
    isLikelyBot,
    setValidationErrors,
  };
}

export default function SecureFormWatcher({ children, onThreatDetected }) {
  const [formStartTime] = useState(Date.now());
  const { isSecure, threats, validateInput, scanFormIntegrity, isLikelyBot } =
    useSecureFormWatcher();

  useEffect(() => {
    // Monitor form inputs
    const handleInput = (e) => {
      if (e.target.tagName === "INPUT" || e.target.tagName === "TEXTAREA") {
        const isValid = validateInput(e.target.name, e.target.value);
        
        if (!isValid && onThreatDetected) {
          onThreatDetected(threats);
        }
      }
    };

    document.addEventListener("input", handleInput);

    return () => {
      document.removeEventListener("input", handleInput);
    };
  }, [validateInput, threats, onThreatDetected]);

  useEffect(() => {
    // Scan all forms on mount
    const forms = document.querySelectorAll("form");
    forms.forEach((form) => {
      scanFormIntegrity(form);
      
      form.addEventListener("submit", (e) => {
        const submissionTime = Date.now() - formStartTime;
        
        if (isLikelyBot(submissionTime)) {
          e.preventDefault();
          console.warn("⚠️ SecureFormWatcher: Bot submission blocked");
          if (onThreatDetected) {
            onThreatDetected([
              { type: "BOT_DETECTED", severity: "high" }
            ]);
          }
        }
      });
    });
  }, [scanFormIntegrity, isLikelyBot, formStartTime, onThreatDetected]);

  if (!isSecure && threats.length > 0) {
    return (
      <div className="p-4 bg-red-900/20 border border-red-800 rounded-lg">
        <div className="flex items-center gap-2 mb-2">
          <svg
            className="w-5 h-5 text-red-400"
            fill="none"
            stroke="currentColor"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              strokeLinejoin="round"
              strokeWidth={2}
              d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z"
            />
          </svg>
          <span className="font-semibold text-red-400">
            SecureFormWatcher: Menace Détectée
          </span>
        </div>
        <p className="text-sm text-zinc-300">
          Une tentative d'attaque a été bloquée. Veuillez vérifier vos entrées.
        </p>
        {threats.map((threat, index) => (
          <div
            key={index}
            className="mt-2 text-xs bg-zinc-900 px-3 py-2 rounded"
          >
            <span className="text-red-400">{threat.type}</span>
            {threat.field && (
              <span className="text-zinc-400"> sur le champ: {threat.field}</span>
            )}
          </div>
        ))}
      </div>
    );
  }

  return <>{children}</>;
}

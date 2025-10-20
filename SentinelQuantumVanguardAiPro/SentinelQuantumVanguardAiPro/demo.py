import logging
import sqlite3
from modules.quantum_reflex.reflex_engine import EmergencyResponse

# Logger de base
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("QuantumReflexDemo")

# Configuration de test
config = {
    'quantum_emergency_mode': True,
    'quantum_min_confidence': 0.6,
    'quantum_enable_lockdown': False
}

# Connexion SQLite temporaire
db = sqlite3.connect(':memory:')

# Instanciation
reflex = EmergencyResponse(config, logger, db)
reflex.start()

print("🔄 Simulation en cours... Appuyez sur Ctrl+C pour arrêter.")
try:
    while True:
        pass
except KeyboardInterrupt:
    print("\nArrêt manuel.")
    reflex.stop()

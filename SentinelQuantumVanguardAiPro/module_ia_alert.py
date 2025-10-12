
# module_ia_alert.py
# Module de détection comportementale par IA (fictif)

def detect_behavior(events):
    print("Détection comportementale intelligente...")
    for event in events:
        if event.get("niveau") == "élevé":
            print("🔴 Alerte critique :", event["message"])

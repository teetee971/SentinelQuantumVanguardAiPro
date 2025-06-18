
# module_osint.py
# Surveillance OSINT géolocalisée et détection de mots-clés

def analyse_sources(publiques):
    print("Analyse des sources ouvertes en cours...")
    for source in publiques:
        if "danger" in source.lower():
            print("⚠️ Menace détectée :", source)

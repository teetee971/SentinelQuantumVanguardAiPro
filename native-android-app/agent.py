import os
import subprocess
import requests
import json

API_KEY = os.environ.get("AI_API_KEY")
API_URL = "https://api.groq.com/openai/v1/chat/completions"

def ask_ai_for_fix(error_log):
    if not API_KEY:
        print("Erreur : La variable d'environnement AI_API_KEY n'est pas définie.")
        return None
    
    headers = {
        "Authorization": f"Bearer {API_KEY}",
        "Content-Type": "application/json"
    }
    
    prompt = f"""
Tu es un agent expert en ingénierie logicielle Android, Gradle et Termux ARM64. 
Voici l'erreur de compilation rencontrée :
{error_log}

Donne-moi UNIQUEMENT un bloc de code JSON valide avec les corrections à apporter sous ce format strict, sans texte autour :
{{
  "file_path": "chemin/du/fichier/a/modifier",
  "action": "overwrite",
  "content": "Le contenu complet corrigé du fichier"
}}
Si aucune modification de fichier n'est nécessaire et qu'une commande bash suffit, réponds avec :
{{
  "file_path": "",
  "action": "command",
  "content": "commande bash a executer"
}}
"""

    payload = {
        "model": "openai/gpt-oss-120b",
        "messages": [
            {"role": "system", "content": "Tu réponds uniquement en JSON valide pour automatiser des corrections de code."},
            {"role": "user", "content": prompt}
        ],
        "temperature": 0.1,
        "response_format": {"type": "json_object"}
    }
    
    try:
        response = requests.post(API_URL, json=payload, headers=headers)
        res_json = response.json()
        if 'choices' not in res_json:
            print(f"Réponse API inattendue : {res_json}")
            return None
        content = res_json['choices'][0]['message']['content']
        return json.loads(content)
    except Exception as e:
        print(f"Erreur de communication avec l'IA : {e}")
        return None

def run_build_loop(max_attempts=3):
    for attempt in range(1, max_attempts + 1):
        print(f"\n--- Tentative de build {attempt}/{max_attempts} ---")
        process = subprocess.run(["./gradlew", "assembleDebug", "--no-daemon"], capture_output=True, text=True)
        
        if process.returncode == 0:
            print("\n[✓] Succès ! L'APK a été généré avec succès.")
            return True
        
        print("\n[!] Échec du build. Analyse par l'agent IA...")
        error_output = process.stderr[-3000:] if process.stderr else process.stdout[-3000:]
        
        fix_instruction = ask_ai_for_fix(error_output)
        if not fix_instruction:
            print("Impossible d'obtenir une correction de l'IA.")
            break
            
        action = fix_instruction.get("action")
        target_file = fix_instruction.get("file_path")
        content = fix_instruction.get("content")
        
        if action == "overwrite" and target_file:
            print(f"[IA] Modification automatique du fichier : {target_file}")
            with open(target_file, "w", encoding="utf-8") as f:
                f.write(content)
        elif action == "command" and content:
            print(f"[IA] Exécution de la commande : {content}")
            os.system(content)
        else:
            print(f"[IA Recommandation] : {content}")
            
    print("\n[x] Nombre maximal de tentatives atteint sans succès complet.")
    return False

if __name__ == "__main__":
    run_build_loop()

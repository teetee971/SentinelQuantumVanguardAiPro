import re

with open("agent.py", "r", encoding="utf-8") as f:
    code = f.read()

# Remplacement de la gestion de l'erreur pour afficher le JSON brut en cas de souci
old_code = """    try:
        response = requests.post(API_URL, json=payload, headers=headers)
        res_json = response.json()
        content = res_json['choices'][0]['message']['content']
        return json.loads(content)
    except Exception as e:
        print(f"Erreur de communication avec l'IA : {e}")
        return None"""

new_code = """    try:
        response = requests.post(API_URL, json=payload, headers=headers)
        res_json = response.json()
        if 'choices' not in res_json:
            print(f"Réponse API inattendue : {res_json}")
            return None
        content = res_json['choices'][0]['message']['content']
        return json.loads(content)
    except Exception as e:
        print(f"Erreur de communication avec l'IA : {e}")
        return None"""

if old_code in code:
    code = code.replace(old_code, new_code)
    with open("agent.py", "w", encoding="utf-8") as f:
        f.write(code)
    print("Mise à jour du script réussie !")
else:
    print("Mise à jour manuelle conseillée ou bloc déjà modifié.")

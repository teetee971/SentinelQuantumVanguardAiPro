import sqlite3
import pandas as pd

print("Lecture des fichiers locaux...")

# Lecture directe des fichiers présents dans le dossier courant
df_num = pd.read_csv("MAJNUM.csv", sep=";", encoding="latin1", dtype=str)
df_porta = pd.read_csv("MAJPORTA.csv", sep=";", encoding="latin1", dtype=str)

# Normalisation des identifiants
df_num["EZABPQM"] = df_num["EZABPQM"].str.strip()
df_porta["EZABPQM"] = df_porta["EZABPQM"].str.strip()

# Fusion et gestion de la portabilité
df_consolidated = pd.merge(df_num, df_porta, on="EZABPQM", how="left", suffixes=("_orig", "_porta"))
df_consolidated["Mnémo_final"] = df_consolidated["Mnémo_porta"].fillna(df_consolidated["Mnémo_orig"])

# Export de la base de données SQLite finale
conn = sqlite3.connect("annuaire_telecom.db")
df_consolidated.to_sql("telecom_consolidated", conn, if_exists="replace", index=False)
conn.close()

print("Succès ! La base SQLite 'annuaire_telecom.db' est prête pour votre application.")


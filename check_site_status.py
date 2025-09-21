#!/usr/bin/env python3
"""
Script pour vérifier l'état du site Sentinel Quantum Vanguard AI Pro
Usage: python3 check_site_status.py
"""

import requests
import json
import time
import os
import sys

def print_header():
    print("=" * 60)
    print("🛡️  SENTINEL QUANTUM VANGUARD AI PRO - ÉTAT DU SITE")
    print("=" * 60)
    print()

def check_web_interface():
    """Vérifie l'état de l'interface web Vite"""
    try:
        response = requests.get("http://localhost:5173", timeout=5)
        if response.status_code == 200:
            return {"status": "✅ EN LIGNE", "port": "5173", "server": "Vite Dev"}
        else:
            return {"status": "⚠️ PROBLÈME", "port": "5173", "server": "Vite Dev"}
    except:
        return {"status": "❌ HORS LIGNE", "port": "5173", "server": "Vite Dev"}

def check_flask_backend():
    """Vérifie l'état du backend Flask"""
    try:
        response = requests.get("http://localhost:8080", timeout=5)
        if response.status_code == 200:
            return {"status": "✅ EN LIGNE", "port": "8080", "server": "Flask"}
        else:
            return {"status": "⚠️ PROBLÈME", "port": "8080", "server": "Flask"}
    except:
        return {"status": "❌ HORS LIGNE", "port": "8080", "server": "Flask"}

def get_system_status():
    """Récupère le statut détaillé du système via l'API Flask"""
    try:
        response = requests.get("http://localhost:8080/status", timeout=5)
        if response.status_code == 200:
            return response.json()
        else:
            return None
    except:
        return None

def get_initialization_logs():
    """Récupère les logs d'initialisation"""
    try:
        response = requests.get("http://localhost:8080/initialize", timeout=5)
        if response.status_code == 200:
            return response.json()
        else:
            return None
    except:
        return None

def print_status_summary():
    """Affiche un résumé de l'état du système"""
    print_header()
    
    # Vérification interface web
    web_status = check_web_interface()
    print(f"🌐 Interface Web (Vite): {web_status['status']} - Port {web_status['port']}")
    
    # Vérification backend Flask
    flask_status = check_flask_backend()
    print(f"⚙️  Backend Flask: {flask_status['status']} - Port {flask_status['port']}")
    
    print()
    
    # Statut détaillé si le backend est accessible
    system_status = get_system_status()
    if system_status:
        print("📊 ÉTAT DÉTAILLÉ DU SYSTÈME:")
        print(f"   Système: {system_status.get('system', 'N/A')}")
        print(f"   Version: {system_status.get('version', 'N/A')}")
        print(f"   Statut: {system_status.get('status', 'N/A').upper()}")
        print(f"   Mode: {system_status.get('mode', 'N/A')}")
        print(f"   Niveau de sécurité: {system_status.get('security_level', 'N/A')}")
        print(f"   Timestamp: {system_status.get('timestamp', 'N/A')}")
        
        print("\n🔧 MODULES ACTIFS:")
        modules = system_status.get('modules', {})
        for module_name, module_info in modules.items():
            status_emoji = "✅" if module_info.get('status') == 'active' else "❌"
            print(f"   {status_emoji} {module_name.replace('_', ' ').title()}: {module_info.get('description', module_info.get('status', 'N/A'))}")
    else:
        print("❌ Impossible de récupérer l'état détaillé du système (Backend inaccessible)")
    
    print()
    
    # URLs d'accès
    print("🔗 ACCÈS AU SYSTÈME:")
    print(f"   Interface principale: http://localhost:5173/public/index.html")
    print(f"   Tableau de bord: http://localhost:5173/public/status.html")
    print(f"   API Backend: http://localhost:8080")
    print(f"   Statut JSON: http://localhost:8080/status")
    
    print()

def print_detailed_logs():
    """Affiche les logs détaillés d'initialisation"""
    logs = get_initialization_logs()
    if logs:
        print("📋 LOGS D'INITIALISATION:")
        for i, log_entry in enumerate(logs, 1):
            timestamp = time.strftime("%H:%M:%S")
            print(f"   [{timestamp}] {log_entry}")
    else:
        print("❌ Impossible de récupérer les logs (Backend inaccessible)")
    print()

def main():
    """Fonction principale"""
    if len(sys.argv) > 1 and sys.argv[1] in ['--logs', '-l']:
        print_header()
        print_detailed_logs()
    elif len(sys.argv) > 1 and sys.argv[1] in ['--json', '-j']:
        # Mode JSON pour intégration avec d'autres outils
        web_status = check_web_interface()
        flask_status = check_flask_backend()
        system_status = get_system_status()
        
        result = {
            "web_interface": web_status,
            "flask_backend": flask_status,
            "system_details": system_status,
            "timestamp": time.strftime("%Y-%m-%d %H:%M:%S"),
            "access_urls": {
                "main": "http://localhost:5173/public/index.html",
                "status_dashboard": "http://localhost:5173/public/status.html",
                "api": "http://localhost:8080",
                "status_json": "http://localhost:8080/status"
            }
        }
        print(json.dumps(result, indent=2, ensure_ascii=False))
    else:
        print_status_summary()

if __name__ == "__main__":
    main()
@echo off
echo === Installation de Sentinel Quantum Vanguard AI Pro ===
python -m venv env
call env\Scripts\activate
pip install -r requirements.txt
echo Installation terminée.
pause

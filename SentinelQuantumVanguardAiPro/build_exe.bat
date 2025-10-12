
@echo off
echo Compilation de Sentinel Quantum Vanguard AI Pro...
pip install -r requirements.txt
pyinstaller --onefile --noconsole --name=Sentinel_Quantum_Vanguard_AI_Pro main.py
pause

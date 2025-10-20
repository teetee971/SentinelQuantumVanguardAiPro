#!/usr/bin/env python3
# demo_cognitive_shield.py
from modules.cognitive_shield.cognitive_shield import ContentAnalyzer
import sqlite3
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger("DemoCognitiveShield")

db = sqlite3.connect(":memory:")
config = {
    'cognitive_manipulation_detection': True,
    'cognitive_auto_block': True,
    'cognitive_user_warnings': True,
    'cognitive_visual_analysis': False  # OCR désactivé pour la démo
}

analyzer = ContentAnalyzer(config, logger, db)
text = "Your account is suspended. Please click here immediately to verify."
analyzer.analyze_content(text, content_type="email", source="demo@phishing.com")
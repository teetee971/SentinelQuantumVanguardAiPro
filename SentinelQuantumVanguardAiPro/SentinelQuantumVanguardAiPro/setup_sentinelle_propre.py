
from setuptools import setup, find_packages

setup(
    name='sentinelle_modules',
    version='1.0.0',
    author='Thierry Naud',
    author_email='contact@sentinelle-ai.dev',  # Adresse générique fictive
    description='Modules de cybersécurité, IA et OSINT pour le projet Sentinelle Quantum Vanguard AI Pro',
    packages=find_packages(),
    classifiers=[
        'Programming Language :: Python :: 3',
        'Operating System :: OS Independent',
    ],
    python_requires='>=3.6',
)

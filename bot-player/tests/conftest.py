"""Pytest configuration for bot-player tests.

Adds the bot-player root directory to sys.path so that imports work
without requiring package installation.
"""
import sys
import os

# Add the bot-player directory to the path so we can import modules directly
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

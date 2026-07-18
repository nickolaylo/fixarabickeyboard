#!/usr/bin/env python3
"""Compatibility notice for the old Patch 13 builder.

The centralized builder is now:
    tools/dictionaries/build_language_dictionary.py
"""

raise SystemExit(
    "Use tools/dictionaries/build_language_dictionary.py; "
    "the legacy single-file Arabic builder is retired."
)

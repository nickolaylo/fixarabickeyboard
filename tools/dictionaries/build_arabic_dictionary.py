#!/usr/bin/env python3
"""Build the bundled Arabic suggestion dictionary from AyaSpell ar.dic.

Usage:
    python tools/dictionaries/build_arabic_dictionary.py \
        INPUT_AR_DIC OUTPUT_AR_TXT \
        --seed tools/dictionaries/ar_seed_words.txt

The output is deterministic UTF-8, one display word per line, sorted by the
same normalized key used by DictionaryManager on Android.
"""

from __future__ import annotations

import argparse
import hashlib
import unicodedata
from pathlib import Path

ARABIC_BLOCKS = (
    (0x0600, 0x06FF),
    (0x0750, 0x077F),
    (0x08A0, 0x08FF),
    (0xFB50, 0xFDFF),
    (0xFE70, 0xFEFF),
)

ALEF_EQUIVALENTS = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا"})


def remove_marks(value: str) -> str:
    normalized = unicodedata.normalize("NFC", value)
    return "".join(
        char
        for char in normalized
        if unicodedata.category(char) not in {"Mn", "Mc", "Me"} and char != "ـ"
    )


def normalize_for_search(value: str) -> str:
    return remove_marks(value).translate(ALEF_EQUIVALENTS)


def is_arabic_letter(char: str) -> bool:
    codepoint = ord(char)
    return unicodedata.category(char).startswith("L") and any(
        start <= codepoint <= end for start, end in ARABIC_BLOCKS
    )


def extract_entry(line: str) -> str:
    entry = line.split("\t", 1)[0].split("/", 1)[0].strip()
    return remove_marks(entry)


def build_dictionary(source: Path, destination: Path, seed_path: Path | None) -> tuple[int, int, int, str]:
    lines = source.read_text(encoding="utf-8-sig").splitlines()
    if lines and lines[0].split("\t", 1)[0].isdigit():
        lines = lines[1:]

    accepted: set[str] = set()
    rejected = 0
    for line in lines:
        word = extract_entry(line)
        if not word or not all(is_arabic_letter(char) for char in word):
            rejected += 1
            continue
        accepted.add(word)

    before_seed = len(accepted)
    if seed_path is not None:
        for line in seed_path.read_text(encoding="utf-8-sig").splitlines():
            word = remove_marks(line.strip())
            if word and all(is_arabic_letter(char) for char in word):
                accepted.add(word)
    seed_additions = len(accepted) - before_seed

    ordered = sorted(accepted, key=lambda word: (normalize_for_search(word), word))
    payload = ("\n".join(ordered) + "\n").encode("utf-8")
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_bytes(payload)

    return len(ordered), rejected, seed_additions, hashlib.sha256(payload).hexdigest()


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("--seed", type=Path)
    args = parser.parse_args()

    count, rejected, seed_additions, digest = build_dictionary(
        source=args.source,
        destination=args.destination,
        seed_path=args.seed,
    )
    print(f"words={count}")
    print(f"rejected_entries={rejected}")
    print(f"seed_additions={seed_additions}")
    print(f"sha256={digest}")
    print(f"output={args.destination}")


if __name__ == "__main__":
    main()

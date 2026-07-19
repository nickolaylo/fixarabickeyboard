#!/usr/bin/env python3
"""Build a compact, bucketed keyboard dictionary from Hunspell sources.

The generated lexicon contains every clean source entry and every direct
prefix/suffix derivative allowed by its Hunspell flags. Output is split into
small gzip buckets so Android loads only the active prefix range.
"""
from __future__ import annotations

import argparse
import gzip
import hashlib
import json
import re
import shutil
import subprocess
import tempfile
import unicodedata
from dataclasses import dataclass
from pathlib import Path
from typing import Iterable, TextIO

ARABIC_BLOCKS = (
    (0x0600, 0x06FF),
    (0x0750, 0x077F),
    (0x08A0, 0x08FF),
    (0xFB50, 0xFDFF),
    (0xFE70, 0xFEFF),
)
ALEF_EQUIVALENTS = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا"})
REMOVE_MARKS = {0x0640: None}
for _cp in range(0x0600, 0x0900):
    if unicodedata.category(chr(_cp)) in {"Mn", "Mc", "Me"}:
        REMOVE_MARKS[_cp] = None


def remove_marks(value: str) -> str:
    return value.translate(REMOVE_MARKS)


def normalize_for_search(value: str, language: str) -> str:
    clean = remove_marks(value)
    return clean.translate(ALEF_EQUIVALENTS) if language == "ar" else clean.casefold()


def is_valid_word(value: str, language: str) -> bool:
    if not value:
        return False
    if language == "ar":
        return value.isalpha() and all(
            any(start <= ord(c) <= end for start, end in ARABIC_BLOCKS) for c in value
        )
    return all(c.isalpha() or c in {"'", "’", "-"} for c in value)


def split_long_flags(raw: str) -> tuple[str, ...]:
    return tuple(raw[i:i + 2] for i in range(0, len(raw), 2))


@dataclass(frozen=True)
class AffixRule:
    kind: str
    strip: str
    add: str
    condition: re.Pattern[str]

    def apply(self, word: str) -> str | None:
        if not self.condition.search(word):
            return None
        if self.kind == "PFX":
            if self.strip and not word.startswith(self.strip):
                return None
            return self.add + (word[len(self.strip):] if self.strip else word)
        if self.strip and not word.endswith(self.strip):
            return None
        return (word[:-len(self.strip)] if self.strip else word) + self.add


class HunspellAffixes:
    def __init__(self, aff_path: Path) -> None:
        lines = aff_path.read_text(encoding="utf-8-sig").splitlines()
        self.aliases: list[tuple[str, ...]] = [tuple()]
        self.rules_by_flag: dict[str, list[AffixRule]] = {}

        alias_header_seen = False
        for raw_line in lines:
            parts = raw_line.strip().split()
            if not parts or parts[0] != "AF":
                continue
            if len(parts) == 2 and parts[1].isdigit() and not alias_header_seen:
                alias_header_seen = True
                continue
            self.aliases.append(split_long_flags(parts[1]))

        index = 0
        while index < len(lines):
            parts = lines[index].strip().split()
            index += 1
            if (
                len(parts) < 4
                or parts[0] not in {"PFX", "SFX"}
                or parts[2] not in {"Y", "N"}
                or not parts[3].isdigit()
            ):
                continue
            kind, flag, count = parts[0], parts[1], int(parts[3])
            rules: list[AffixRule] = []
            for _ in range(count):
                rule_parts = lines[index].strip().split()
                index += 1
                if len(rule_parts) < 4:
                    continue
                strip = "" if rule_parts[2] == "0" else remove_marks(rule_parts[2])
                add = rule_parts[3].split("/", 1)[0]
                add = "" if add == "0" else remove_marks(add)
                condition_text = remove_marks(rule_parts[4]) if len(rule_parts) >= 5 else "."
                expression = ("^" if kind == "PFX" else "") + condition_text + ("$" if kind == "SFX" else "")
                rules.append(AffixRule(kind, strip, add, re.compile(expression)))
            self.rules_by_flag[flag] = rules

    def flags(self, raw: str) -> tuple[str, ...]:
        if not raw:
            return tuple()
        if raw.isdigit():
            alias = int(raw)
            return self.aliases[alias] if 0 < alias < len(self.aliases) else tuple()
        return split_long_flags(raw)

    def direct_derivatives(self, word: str, flags: Iterable[str]) -> Iterable[str]:
        for flag in flags:
            for rule in self.rules_by_flag.get(flag, ()):
                derived = rule.apply(word)
                if derived and derived != word:
                    yield derived


def dictionary_entries(dic_path: Path) -> Iterable[tuple[str, str]]:
    lines = dic_path.read_text(encoding="utf-8-sig").splitlines()
    if lines and lines[0].split("\t", 1)[0].split(" ", 1)[0].isdigit():
        lines = lines[1:]
    for line in lines:
        core = line.split("\t", 1)[0].strip()
        if not core or core.startswith("#") or core.startswith(":"):
            continue
        if "/" in core:
            word, raw_flags = core.rsplit("/", 1)
        else:
            word, raw_flags = core, ""
        yield word.strip(), raw_flags.strip()


def bucket_key(normalized: str) -> str:
    return normalized[:2]


def bucket_file_name(key: str) -> str:
    return "_".join(f"{ord(c):04x}" for c in key) + ".txt.gz"


def emit_candidate(
    handle: TextIO,
    language: str,
    display: str,
    priority: int,
    source_rank: int | None,
) -> None:
    normalized = display.translate(ALEF_EQUIVALENTS) if language == "ar" else display.casefold()
    rank_value = source_rank if source_rank is not None else 2147483647
    handle.write(f"{normalized}\t{display}\t{priority}\t{rank_value}\n")

def build(args: argparse.Namespace) -> dict[str, object]:
    output: Path = args.output
    if output.exists():
        shutil.rmtree(output)
    output.mkdir(parents=True, exist_ok=True)

    affixes = HunspellAffixes(args.aff)
    source_entries = generated_entries = rejected_entries = 0

    with tempfile.TemporaryDirectory(prefix="keyboard_dictionary_") as temp_name:
        temp_dir = Path(temp_name)
        raw_path = temp_dir / "candidates.tsv"
        sorted_path = temp_dir / "candidates.sorted.tsv"

        with raw_path.open("w", encoding="utf-8", newline="\n", buffering=1024 * 1024) as raw_out:
            for raw_word, raw_flags in dictionary_entries(args.dic):
                source_entries += 1
                word = remove_marks(raw_word)
                if not is_valid_word(word, args.language):
                    rejected_entries += 1
                    continue
                emit_candidate(raw_out, args.language, word, 0, source_entries)
                for derived in affixes.direct_derivatives(word, affixes.flags(raw_flags)):
                    # Generated forms stay below source entries during ranking.
                    emit_candidate(raw_out, args.language, derived, 1, None)
                    generated_entries += 1
                if source_entries % 50000 == 0:
                    print(f"processed_source_entries={source_entries}", flush=True)
            if args.seed and args.seed.exists():
                for line in args.seed.read_text(encoding="utf-8-sig").splitlines():
                    if line.strip():
                        emit_candidate(raw_out, args.language, remove_marks(line.strip()), 0, 0)

        subprocess.run(
            [
                "sort", "-S", "512M", "--parallel=4", "-t", "\t",
                "-k1,1", "-k2,2", "-k3,3n", "-k4,4n", str(raw_path), "-o", str(sorted_path),
            ],
            check=True,
            env={"LC_ALL": "C"},
        )

        index_records: list[tuple[str, str, int, int]] = []
        digest = hashlib.sha256()
        total_unique = base_unique = derived_unique = 0
        current_bucket = ""
        bucket_handle = None
        bucket_count = 0
        bucket_base = 0
        bucket_path: Path | None = None
        current_normalized = ""
        normalized_group: dict[str, tuple[int, int]] = {}

        def close_bucket() -> None:
            nonlocal bucket_handle, bucket_count, bucket_base, bucket_path
            if bucket_handle is None or bucket_path is None:
                return
            bucket_handle.close()
            payload = bucket_path.read_bytes()
            file_name = bucket_path.name
            digest.update(file_name.encode("utf-8"))
            digest.update(payload)
            index_records.append((current_bucket, file_name, bucket_count, len(payload)))
            bucket_handle = None
            bucket_path = None
            bucket_count = 0
            bucket_base = 0

        def flush_normalized_group() -> None:
            nonlocal current_bucket, bucket_handle, bucket_count, bucket_base, bucket_path
            nonlocal total_unique, base_unique, derived_unique
            if not current_normalized or not normalized_group:
                return
            key = bucket_key(current_normalized)
            if key != current_bucket:
                close_bucket()
                current_bucket = key
                bucket_path = output / bucket_file_name(key)
                bucket_handle = gzip.open(bucket_path, "wt", encoding="utf-8", newline="\n", compresslevel=6)
            assert bucket_handle is not None
            for word, (priority, source_rank) in sorted(
                normalized_group.items(),
                key=lambda item: (item[1][0], item[1][1], item[0]),
            ):
                if source_rank == 2147483647:
                    bucket_handle.write(f"{priority}\t{word}\n")
                else:
                    bucket_handle.write(f"{priority}\t{source_rank}\t{word}\n")
                bucket_count += 1
                total_unique += 1
                if priority == 0:
                    bucket_base += 1
                    base_unique += 1
                else:
                    derived_unique += 1

        with sorted_path.open("r", encoding="utf-8") as sorted_in:
            for line in sorted_in:
                normalized, word, raw_priority, raw_source_rank = line.rstrip("\n").split("\t", 3)
                priority = int(raw_priority)
                source_rank = int(raw_source_rank)
                if normalized != current_normalized:
                    flush_normalized_group()
                    current_normalized = normalized
                    normalized_group = {}
                previous = normalized_group.get(word)
                candidate = (priority, source_rank)
                if previous is None or candidate < previous:
                    normalized_group[word] = candidate
            flush_normalized_group()
        close_bucket()

    with (output / "index.tsv").open("w", encoding="utf-8", newline="\n") as handle:
        for key, file_name, count, compressed_size in index_records:
            handle.write(f"{key}\t{file_name}\t{count}\t{compressed_size}\n")

    metadata = {
        "format": 3,
        "ranking": "embedded_source_order",
        "language": args.language,
        "source": args.source_name,
        "source_entries": source_entries,
        "generated_direct_entries": generated_entries,
        "unique_entries": total_unique,
        "unique_source_entries": base_unique,
        "unique_generated_entries": derived_unique,
        "rejected_entries": rejected_entries,
        "bucket_count": len(index_records),
        "sha256": digest.hexdigest(),
    }
    (output / "meta.json").write_text(json.dumps(metadata, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    return metadata


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--language", required=True)
    parser.add_argument("--dic", type=Path, required=True)
    parser.add_argument("--aff", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--seed", type=Path)
    parser.add_argument("--source-name", default="Hunspell")
    args = parser.parse_args()
    print(json.dumps(build(args), ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()

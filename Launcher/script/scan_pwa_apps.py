#!/usr/bin/env python3
"""Scan repository metadata for PWA-style Android package names.

The repository stores app metadata in JSON files under ``data/``. This script
walks those files, looks for package names containing ``pwa`` (for example
``com.xbox.pwa``), and prints the matching records.

**Expectation: Run from a clone of threethan/MetaMetadata.**
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Any, Iterable


@dataclass(frozen=True)
class PwaMatch:
    source_file: str
    app_name: str | None
    package_name: str
    app_id: str | None
    match_reason: str


def iter_json_files(root: Path) -> Iterable[Path]:
    for path in root.rglob("*.json"):
        if path.is_file():
            yield path


def load_json(path: Path) -> Any:
    with path.open("r", encoding="utf-8") as handle:
        return json.load(handle)


def extract_app_fields(node: dict[str, Any]) -> tuple[str | None, str | None, str | None]:
    app_name = node.get("appName") or node.get("app_name") or node.get("name")
    package_name = node.get("packageName") or node.get("package_name")
    app_id = node.get("id")

    if isinstance(app_name, str):
        app_name = app_name.strip() or None
    else:
        app_name = None

    if isinstance(package_name, str):
        package_name = package_name.strip() or None
    else:
        package_name = None

    if isinstance(app_id, str):
        app_id = app_id.strip() or None
    elif app_id is not None:
        app_id = str(app_id)

    return app_name, package_name, app_id


def is_pwa_package(package_name: str) -> bool:
    lowered = package_name.lower()
    return "oculuspwa" in lowered or re.search(r"(^|[._-])pwa([._-]|$)|([._-])horizon$", lowered) is not None


def infer_package_from_filename(source_file: Path) -> str | None:
    stem = source_file.stem.strip()
    if not stem:
        return None

    # Use a strict package-like pattern so versioned files (for example 1.2.3.apk)
    # are not interpreted as Android package names.
    if re.fullmatch(r"[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z0-9_]+)+", stem) is None:
        return None

    return stem


def walk_nodes(node: Any, source_file: Path, repo_root: Path, results: list[PwaMatch]) -> None:
    if isinstance(node, dict):
        app_name, package_name, app_id = extract_app_fields(node)
        if package_name and is_pwa_package(package_name):
            results.append(
                PwaMatch(
                    source_file=str(source_file.relative_to(repo_root)),
                    app_name=app_name,
                    package_name=package_name,
                    app_id=app_id,
                    match_reason="package name matches PWA pattern",
                )
            )

        for value in node.values():
            walk_nodes(value, source_file, repo_root, results)
        return

    if isinstance(node, list):
        for item in node:
            walk_nodes(item, source_file, repo_root, results)


def scan_repository(root: Path) -> list[PwaMatch]:
    results: list[PwaMatch] = []
    seen: set[str] = set()
    data_root = root / "data"
    if not data_root.exists():
        return results

    for json_file in iter_json_files(data_root):
        inferred_package = infer_package_from_filename(json_file)
        if inferred_package and is_pwa_package(inferred_package):
            results.append(
                PwaMatch(
                    source_file=str(json_file.relative_to(root)),
                    app_name=None,
                    package_name=inferred_package,
                    app_id=None,
                    match_reason="package inferred from filename",
                )
            )

        try:
            payload = load_json(json_file)
        except (OSError, json.JSONDecodeError):
            continue
        walk_nodes(payload, json_file, root, results)

    deduped: list[PwaMatch] = []
    for match in results:
        key = match.package_name.lower()
        if key in seen:
            continue
        seen.add(key)
        deduped.append(match)

    deduped.sort(key=lambda match: (match.package_name.lower(), match.source_file.lower()))
    return deduped


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Scan MetaMetadata JSON files for PWA apps.")
    parser.add_argument(
        "--root",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Repository root to scan. Defaults to the script's parent directory.",
    )
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print matches as JSON instead of a human-readable list.",
    )
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    root = args.root.resolve()
    matches = scan_repository(root)

    if args.json:
        json.dump([asdict(match) for match in matches], sys.stdout, indent=2)
        sys.stdout.write("\n")
        return 0

    if not matches:
        print("No PWA apps found.")
        return 0

    print(f"Found {len(matches)} PWA app(s):")
    for match in matches:
        app_part = f"{match.app_name} - " if match.app_name else ""
        id_part = f" ({match.app_id})" if match.app_id else ""
        print(f"- {app_part}{match.package_name}{id_part} [{match.source_file}]")

    print()

    with open("manifest_chunk.xml", "w") as f:
        for match in matches:
            f.write(f'        <package android:name="{match.package_name}" />\n')
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
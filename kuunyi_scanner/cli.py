"""Command-line interface for kuunyi-scanner."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import List, Optional, Sequence

from . import __version__
from .scanner import DEFAULT_EXCLUDES, Finding, Scanner

EXIT_OK = 0
EXIT_FINDINGS = 1
EXIT_ERROR = 2


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="kuunyi-scanner",
        description="Scan a directory tree for risky patterns (e.g. hardcoded secrets).",
    )
    parser.add_argument(
        "paths",
        nargs="+",
        help="One or more files or directories to scan.",
    )
    parser.add_argument(
        "--format",
        choices=("text", "json"),
        default="text",
        help="Output format (default: text).",
    )
    parser.add_argument(
        "--exclude",
        action="append",
        default=[],
        metavar="NAME",
        help="Directory name to exclude (repeatable). Added to the defaults.",
    )
    parser.add_argument(
        "--max-bytes",
        type=int,
        default=None,
        metavar="N",
        help="Skip files larger than N bytes.",
    )
    parser.add_argument(
        "--version",
        action="version",
        version=f"%(prog)s {__version__}",
    )
    return parser


def _render_text(findings: List[Finding]) -> str:
    if not findings:
        return "No findings."
    lines = []
    for f in findings:
        lines.append(
            f"{f.path}:{f.line_number}: [{f.severity}] {f.rule_name}: {f.line.strip()}"
        )
    lines.append("")
    lines.append(f"{len(findings)} finding(s).")
    return "\n".join(lines)


def _render_json(findings: List[Finding]) -> str:
    return json.dumps([f.to_dict() for f in findings], indent=2)


def main(argv: Optional[Sequence[str]] = None) -> int:
    parser = build_parser()
    args = parser.parse_args(argv)

    excludes = DEFAULT_EXCLUDES | set(args.exclude)
    scanner_kwargs = {"excludes": frozenset(excludes)}
    if args.max_bytes is not None:
        scanner_kwargs["max_bytes"] = args.max_bytes
    scanner = Scanner(**scanner_kwargs)

    findings: List[Finding] = []
    for raw in args.paths:
        path = Path(raw)
        if not path.exists():
            print(f"error: path does not exist: {path}", file=sys.stderr)
            return EXIT_ERROR
        findings.extend(scanner.scan_path(path))

    if args.format == "json":
        print(_render_json(findings))
    else:
        print(_render_text(findings))

    return EXIT_FINDINGS if findings else EXIT_OK


if __name__ == "__main__":
    sys.exit(main())

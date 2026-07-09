"""Core scanning logic for kuunyi-scanner.

The scanner walks a directory tree and matches each text line against a set of
:class:`Rule` objects. Matches are returned as :class:`Finding` objects. The
default rule set targets common hardcoded-secret patterns; extend
``DEFAULT_RULES`` or pass your own rules to :class:`Scanner` to customize.
"""

from __future__ import annotations

import re
from dataclasses import dataclass, field
from pathlib import Path
from typing import Iterable, Iterator, List, Sequence

# Directories that rarely contain source worth scanning and only add noise.
DEFAULT_EXCLUDES = frozenset(
    {
        ".git",
        ".hg",
        ".svn",
        "__pycache__",
        ".venv",
        "venv",
        "env",
        "node_modules",
        "dist",
        "build",
        ".pytest_cache",
        ".mypy_cache",
    }
)

# Skip files larger than this many bytes (binaries / vendored blobs).
DEFAULT_MAX_BYTES = 2_000_000


@dataclass(frozen=True)
class Rule:
    """A named regular-expression rule with a severity."""

    name: str
    pattern: "re.Pattern[str]"
    severity: str = "medium"

    @classmethod
    def compile(cls, name: str, pattern: str, severity: str = "medium") -> "Rule":
        return cls(name=name, pattern=re.compile(pattern), severity=severity)


@dataclass(frozen=True)
class Finding:
    """A single rule match at a specific location."""

    path: Path
    line_number: int
    rule_name: str
    severity: str
    line: str

    def to_dict(self) -> dict:
        return {
            "path": str(self.path),
            "line_number": self.line_number,
            "rule": self.rule_name,
            "severity": self.severity,
            "line": self.line.strip(),
        }


# A pragmatic starter set. These favor obvious wins over exhaustiveness.
DEFAULT_RULES: Sequence[Rule] = (
    Rule.compile(
        "aws-access-key-id",
        r"\b(?:AKIA|ASIA)[0-9A-Z]{16}\b",
        severity="high",
    ),
    Rule.compile(
        "private-key-block",
        r"-----BEGIN (?:RSA |EC |OPENSSH |DSA |PGP )?PRIVATE KEY-----",
        severity="high",
    ),
    Rule.compile(
        "generic-api-key-assignment",
        r"(?i)\b(?:api[_-]?key|apikey|secret|token)\b\s*[:=]\s*['\"][^'\"]{8,}['\"]",
        severity="medium",
    ),
    Rule.compile(
        "password-assignment",
        r"(?i)\bpassword\b\s*[:=]\s*['\"][^'\"]{4,}['\"]",
        severity="medium",
    ),
    Rule.compile(
        "slack-token",
        r"\bxox[baprs]-[0-9A-Za-z-]{10,}\b",
        severity="high",
    ),
    Rule.compile(
        "github-token",
        r"\bgh[pousr]_[0-9A-Za-z]{36,}\b",
        severity="high",
    ),
)


@dataclass
class Scanner:
    """Walks a tree and yields findings for lines matching its rules."""

    rules: Sequence[Rule] = field(default_factory=lambda: tuple(DEFAULT_RULES))
    excludes: frozenset = field(default_factory=lambda: DEFAULT_EXCLUDES)
    max_bytes: int = DEFAULT_MAX_BYTES

    def scan_path(self, root: Path) -> Iterator[Finding]:
        """Yield findings for ``root`` (a file or directory)."""
        root = Path(root)
        if root.is_file():
            yield from self._scan_file(root)
            return
        for file_path in self._iter_files(root):
            yield from self._scan_file(file_path)

    def _iter_files(self, root: Path) -> Iterator[Path]:
        for path in sorted(root.rglob("*")):
            if not path.is_file():
                continue
            if any(part in self.excludes for part in path.parts):
                continue
            yield path

    def _scan_file(self, path: Path) -> Iterator[Finding]:
        try:
            if path.stat().st_size > self.max_bytes:
                return
        except OSError:
            return
        try:
            with path.open("r", encoding="utf-8", errors="strict") as handle:
                for line_number, line in enumerate(handle, start=1):
                    for rule in self.rules:
                        if rule.pattern.search(line):
                            yield Finding(
                                path=path,
                                line_number=line_number,
                                rule_name=rule.name,
                                severity=rule.severity,
                                line=line.rstrip("\n"),
                            )
        except (UnicodeDecodeError, OSError):
            # Binary or unreadable file; skip quietly.
            return


def scan(paths: Iterable[Path], **kwargs) -> List[Finding]:
    """Convenience wrapper: scan every path and return a flat list."""
    scanner = Scanner(**kwargs)
    results: List[Finding] = []
    for p in paths:
        results.extend(scanner.scan_path(Path(p)))
    return results

"""Tests for the scanner core and CLI."""

from pathlib import Path

from kuunyi_scanner.cli import main
from kuunyi_scanner.scanner import Rule, Scanner, scan


def test_detects_aws_key(tmp_path: Path):
    target = tmp_path / "config.py"
    target.write_text('aws_key = "AKIAIOSFODNN7EXAMPLE"\n')
    findings = scan([tmp_path])
    assert any(f.rule_name == "aws-access-key-id" for f in findings)


def test_detects_password_assignment(tmp_path: Path):
    target = tmp_path / "settings.ini"
    target.write_text('password = "hunter2"\n')
    findings = scan([tmp_path])
    assert any(f.rule_name == "password-assignment" for f in findings)


def test_clean_file_has_no_findings(tmp_path: Path):
    target = tmp_path / "clean.py"
    target.write_text("x = 1 + 2\nprint(x)\n")
    findings = scan([tmp_path])
    assert findings == []


def test_excludes_directories(tmp_path: Path):
    hidden = tmp_path / ".git"
    hidden.mkdir()
    (hidden / "leak.txt").write_text('token = "abcdefghijklmnop"\n')
    findings = scan([tmp_path])
    assert findings == []


def test_skips_large_files(tmp_path: Path):
    target = tmp_path / "big.txt"
    target.write_text('password = "hunter2"\n')
    scanner = Scanner(max_bytes=5)
    findings = list(scanner.scan_path(tmp_path))
    assert findings == []


def test_custom_rule(tmp_path: Path):
    target = tmp_path / "note.txt"
    target.write_text("TODO: remove this before launch\n")
    rule = Rule.compile("todo", r"TODO", severity="low")
    findings = list(Scanner(rules=[rule]).scan_path(tmp_path))
    assert len(findings) == 1
    assert findings[0].rule_name == "todo"
    assert findings[0].line_number == 1


def test_cli_exit_codes(tmp_path: Path, capsys):
    clean = tmp_path / "clean.py"
    clean.write_text("x = 1\n")
    assert main([str(clean)]) == 0

    dirty = tmp_path / "dirty.py"
    dirty.write_text('api_key = "supersecretvalue123"\n')
    assert main([str(dirty)]) == 1

    assert main([str(tmp_path / "missing")]) == 2


def test_cli_json_output(tmp_path: Path, capsys):
    dirty = tmp_path / "dirty.py"
    dirty.write_text('api_key = "supersecretvalue123"\n')
    main([str(dirty), "--format", "json"])
    out = capsys.readouterr().out
    assert '"rule"' in out
    assert "generic-api-key-assignment" in out

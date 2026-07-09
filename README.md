# kuunyi-scanner

A small, extensible command-line scanner that walks a directory tree and flags
lines matching a set of built-in patterns (potential hardcoded secrets, tokens,
and other risky strings). It's designed as a clean starting point you can grow
into a fuller security / code scanner.

## Features

- Recursively scans a directory (skipping common noise like `.git`, virtualenvs,
  `node_modules`).
- Built-in rule set for common secret patterns (API keys, private keys, AWS
  keys, generic `password = ...` assignments, etc.).
- Human-readable and JSON output.
- Configurable exclusions and file-size limit.
- Zero third-party runtime dependencies (standard library only).

## Install

```bash
git clone https://github.com/YeHtutAung/kuunyi-scanner.git
cd kuunyi-scanner
pip install -e .
```

## Usage

```bash
# Scan the current directory
kuunyi-scanner .

# Scan a path and emit JSON
kuunyi-scanner /path/to/project --format json

# Exclude extra directories
kuunyi-scanner . --exclude dist --exclude build
```

Exit code is `0` when no findings, `1` when findings are reported, and `2` on
a usage/IO error — handy for CI gates.

## Development

```bash
pip install -e .
python -m pytest
```

## License

MIT — see [LICENSE](LICENSE).

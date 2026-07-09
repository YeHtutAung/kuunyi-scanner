"""Enable ``python -m kuunyi_scanner``."""

import sys

from .cli import main

if __name__ == "__main__":
    sys.exit(main())

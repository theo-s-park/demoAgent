from __future__ import annotations

from pathlib import Path

from dotenv import load_dotenv


def load_tool_server_env() -> None:
    """
    Load environment variables for tool-server processes.

    Priority: existing process env > .env.local > .env
    Search order:
    - tool-server/.env(.local)
    - repo-root/.env(.local)  (tool-server/..)
    """
    here = Path(__file__).resolve().parent
    repo_root = here.parent

    candidates = [
        here / ".env.local",
        here / ".env",
        repo_root / ".env.local",
        repo_root / ".env",
    ]

    for p in candidates:
        if p.exists():
            load_dotenv(dotenv_path=p, override=False)


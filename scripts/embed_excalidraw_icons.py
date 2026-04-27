"""Embed Simple Icons SVGs into architecture-demoagent.excalidraw as base64 files."""
from __future__ import annotations

import base64
import json
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
DOC = ROOT / "docs/superpowers/architecture-demoagent.excalidraw"
# SVGs: Simple Icons (MIT) — https://github.com/simple-icons/simple-icons
# OpenAI from raw.githubusercontent.com/simple-icons/simple-icons (CDN slug empty for openai).
_ICONS_DIR = ROOT / "docs/superpowers/icons-source"
ICONS = {
    "si-docker": _ICONS_DIR / "icon-docker.svg",
    "si-springboot": _ICONS_DIR / "icon-springboot.svg",
    "si-fastapi": _ICONS_DIR / "icon-fastapi.svg",
    "si-openai": _ICONS_DIR / "icon-openai.svg",
    "si-chrome": _ICONS_DIR / "icon-googlechrome.svg",
    "si-git": _ICONS_DIR / "icon-git.svg",
}

REMOVE_DECO_IDS = {"deco-docker-corner", "deco-browser-badge", "deco-openai-badge"}
CREATED = 1745800000000


def _strip_previous_run(elements: list) -> list:
    out = []
    for e in elements:
        eid = str(e.get("id", ""))
        if eid in REMOVE_DECO_IDS or eid.startswith("img-icon-"):
            continue
        out.append(e)
    return out


def data_url(svg_path: Path) -> str:
    b64 = base64.standard_b64encode(svg_path.read_bytes()).decode("ascii")
    return f"data:image/svg+xml;base64,{b64}"


def image_element(
    eid: str,
    file_id: str,
    x: float,
    y: float,
    w: float,
    h: float,
    seed: int,
) -> dict:
    return {
        "type": "image",
        "version": 1,
        "versionNonce": seed + 900000,
        "isDeleted": False,
        "id": eid,
        "fillStyle": "hachure",
        "strokeWidth": 1,
        "strokeStyle": "solid",
        "roughness": 1,
        "opacity": 100,
        "angle": 0,
        "x": x,
        "y": y,
        "strokeColor": "transparent",
        "backgroundColor": "transparent",
        "width": w,
        "height": h,
        "seed": seed,
        "groupIds": [],
        "frameId": None,
        "roundness": None,
        "boundElements": None,
        "updated": CREATED,
        "link": None,
        "locked": False,
        "status": "saved",
        "fileId": file_id,
        "scale": [1, 1],
        "crop": None,
    }


def main() -> None:
    for p in ICONS.values():
        if not p.exists():
            raise SystemExit(f"Missing icon file: {p}")

    data = json.loads(DOC.read_text(encoding="utf-8"))

    files: dict = {}
    for fid, path in ICONS.items():
        files[fid] = {
            "mimeType": "image/svg+xml",
            "id": fid,
            "dataURL": data_url(path),
            "created": CREATED,
        }
    data["files"] = files

    data["elements"] = _strip_previous_run(data["elements"])

    try:
        idx = next(i for i, e in enumerate(data["elements"]) if e.get("id") == "arrow-browser-spring")
    except StopIteration:
        raise SystemExit("arrow-browser-spring not found")

    images = [
        image_element("img-icon-docker", "si-docker", 1254, 112, 48, 48, 2001),
        image_element("img-icon-chrome", "si-chrome", 52, 306, 40, 40, 2002),
        image_element("img-icon-openai", "si-openai", 448, 124, 40, 40, 2003),
        image_element("img-icon-springboot", "si-springboot", 408, 266, 44, 44, 2004),
        image_element("img-icon-fastapi", "si-fastapi", 1242, 226, 46, 46, 2005),
        image_element("img-icon-git", "si-git", 406, 536, 36, 36, 2006),
    ]
    for im in reversed(images):
        data["elements"].insert(idx, im)

    for e in data["elements"]:
        if e.get("id") == "title-doc":
            e["text"] = "demoAgent — 아키텍처 플로우\n동적 도구 · Spring · FastAPI · Docker"
            e["originalText"] = e["text"]
        if e.get("id") == "label-zone-docker":
            e["text"] = "Docker Compose\nbridge network · 서비스 DNS → 상호 HTTP"
            e["originalText"] = e["text"]
        if e.get("id") == "text-spring":
            t = e["text"].replace("☕🍃 Spring Boot · :8080", "Spring Boot · :8080", 1)
            e["text"] = t
            e["originalText"] = t
        if e.get("id") == "text-tools":
            t = e["text"].replace("⚡🐍 FastAPI Tool servers", "FastAPI Tool servers", 1)
            e["text"] = t
            e["originalText"] = t
        if e.get("id") == "text-openai":
            t = e["text"].replace("🤖✨ OpenAI API", "OpenAI API", 1)
            e["text"] = t
            e["originalText"] = t
        if e.get("id") == "text-browser":
            t = e["text"].replace("🌐 Browser", "Browser", 1)
            e["text"] = t
            e["originalText"] = t

    DOC.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    size_kb = DOC.stat().st_size / 1024
    print(f"Wrote {DOC} ({size_kb:.1f} KB)")


if __name__ == "__main__":
    main()

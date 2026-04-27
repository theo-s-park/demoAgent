"""
Regenerate docs/superpowers/architecture-demoagent.excalidraw.
Each concern has its own labeled zone: User, 외부 LLM, VPC, EC2, Docker Compose,
Spring, FastAPI, Runtime State (files + sqlite). Wide gaps; minimal labels.
Icons: Simple Icons (MIT) in docs/superpowers/icons-source/
"""
from __future__ import annotations

import base64
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "docs/superpowers/architecture-demoagent.excalidraw"
ICONS_DIR = ROOT / "docs/superpowers/icons-source"
UPD = 1745800000000

ICON_FILES: dict[str, str] = {
    "si-docker": "icon-docker.svg",
    "si-springboot": "icon-springboot.svg",
    "si-fastapi": "icon-fastapi.svg",
    "si-openai": "icon-openai.svg",
    "si-chrome": "icon-googlechrome.svg",
    "si-git": "icon-git.svg",
}


def data_url(svg_path: Path) -> str:
    b64 = base64.standard_b64encode(svg_path.read_bytes()).decode("ascii")
    return f"data:image/svg+xml;base64,{b64}"


def build_files() -> dict:
    files: dict = {}
    for fid, name in ICON_FILES.items():
        p = ICONS_DIR / name
        if not p.exists():
            raise SystemExit(f"Missing icon: {p}")
        files[fid] = {
            "mimeType": "image/svg+xml",
            "id": fid,
            "dataURL": data_url(p),
            "created": UPD,
        }
    return files


def rect(
    eid: str,
    x: float,
    y: float,
    w: float,
    h: float,
    stroke: str,
    bg: str,
    *,
    sw: int = 2,
    dashed: bool = False,
    rough: int = 1,
    seed: int,
) -> dict:
    return {
        "id": eid,
        "type": "rectangle",
        "x": x,
        "y": y,
        "width": w,
        "height": h,
        "angle": 0,
        "strokeColor": stroke,
        "backgroundColor": bg,
        "fillStyle": "solid",
        "strokeWidth": sw,
        "strokeStyle": "dashed" if dashed else "solid",
        "roughness": rough,
        "opacity": 100,
        "groupIds": [],
        "frameId": None,
        "roundness": None if dashed and rough == 0 else {"type": 3},
        "seed": seed,
        "version": 1,
        "versionNonce": seed + 10000,
        "isDeleted": False,
        "boundElements": [],
        "updated": UPD,
        "link": None,
        "locked": False,
    }


def text_el(
    eid: str,
    x: float,
    y: float,
    w: float,
    h: float,
    content: str,
    color: str,
    *,
    fs: int = 14,
    align: str = "left",
    seed: int,
) -> dict:
    baseline = int(fs * 0.85)
    return {
        "id": eid,
        "type": "text",
        "x": x,
        "y": y,
        "width": w,
        "height": h,
        "angle": 0,
        "strokeColor": color,
        "backgroundColor": "transparent",
        "fillStyle": "solid",
        "strokeWidth": 1,
        "strokeStyle": "solid",
        "roughness": 1,
        "opacity": 100,
        "groupIds": [],
        "frameId": None,
        "roundness": None,
        "seed": seed,
        "version": 1,
        "versionNonce": seed + 20000,
        "isDeleted": False,
        "boundElements": None,
        "updated": UPD,
        "link": None,
        "locked": False,
        "text": content,
        "fontSize": fs,
        "fontFamily": 1,
        "textAlign": align,
        "verticalAlign": "top",
        "baseline": baseline,
        "containerId": None,
        "originalText": content,
        "lineHeight": 1.25,
    }


def image_el(eid: str, fid: str, x: float, y: float, w: float, h: float, seed: int) -> dict:
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
        "updated": UPD,
        "link": None,
        "locked": False,
        "status": "saved",
        "fileId": fid,
        "scale": [1, 1],
        "crop": None,
    }


def arrow(
    eid: str,
    ax: float,
    ay: float,
    pts: list[list[float]],
    color: str,
    *,
    dashed: bool = False,
    start_id: str | None,
    end_id: str | None,
    seed: int,
    label_id: str | None = None,
) -> dict:
    xs = [ax + p[0] for p in pts]
    ys = [ay + p[1] for p in pts]
    w = max(xs) - min(xs)
    h = max(ys) - min(ys)
    be = []
    if label_id:
        be.append({"type": "text", "id": label_id})
    return {
        "id": eid,
        "type": "arrow",
        "x": ax,
        "y": ay,
        "width": w,
        "height": h,
        "angle": 0,
        "strokeColor": color,
        "backgroundColor": "transparent",
        "fillStyle": "solid",
        "strokeWidth": 2,
        "strokeStyle": "dashed" if dashed else "solid",
        "roughness": 1,
        "opacity": 100,
        "groupIds": [],
        "frameId": None,
        "roundness": {"type": 2},
        "seed": seed,
        "version": 1,
        "versionNonce": seed + 30000,
        "isDeleted": False,
        "boundElements": be,
        "updated": UPD,
        "link": None,
        "locked": False,
        "points": pts,
        "lastCommittedPoint": None,
        "startBinding": {"elementId": start_id, "focus": 0, "gap": 8} if start_id else None,
        "endBinding": {"elementId": end_id, "focus": 0, "gap": 8} if end_id else None,
        "startArrowhead": None,
        "endArrowhead": "arrow",
    }


def arrow_label(
    eid: str, arrow_id: str, txt: str, color: str, x: float, y: float, w: float, h: float, seed: int
):
    return {
        "id": eid,
        "type": "text",
        "x": x,
        "y": y,
        "width": w,
        "height": h,
        "angle": 0,
        "strokeColor": color,
        "backgroundColor": "transparent",
        "fillStyle": "solid",
        "strokeWidth": 1,
        "strokeStyle": "solid",
        "roughness": 1,
        "opacity": 100,
        "groupIds": [],
        "frameId": None,
        "roundness": None,
        "seed": seed,
        "version": 1,
        "versionNonce": seed + 40000,
        "isDeleted": False,
        "boundElements": None,
        "updated": UPD,
        "link": None,
        "locked": False,
        "text": txt,
        "fontSize": 11,
        "fontFamily": 1,
        "textAlign": "center",
        "verticalAlign": "middle",
        "baseline": 9,
        "containerId": arrow_id,
        "originalText": txt,
        "lineHeight": 1.2,
    }


def bind(r: dict, arrow_id: str):
    r.setdefault("boundElements", []).append({"type": "arrow", "id": arrow_id})


def main():
    els: list = []

    # --- Layout (generous gaps; nested zones) ---
    # More breathing room between zones for arrow readability
    USER_X, USER_Y = 36, 500
    USER_W, USER_H = 215, 260
    GAP_U_V = 180
    VPC_X = USER_X + USER_W + GAP_U_V
    # Widen canvas to give Tools area more space
    VPC_Y, VPC_W, VPC_H = 150, 1880, 980
    # LLM 박스: VPC 상단 오른쪽, 캔버스 안에 유지
    LLM_W, LLM_H = 300, 108
    LLM_Y = 22
    LLM_X = VPC_X + VPC_W - LLM_W - 48

    EC2_X = VPC_X + 60
    EC2_Y = VPC_Y + 72
    EC2_W, EC2_H = 1320, 860

    # NOTE: SQLite state lives in Runtime State zone (tools.db); no separate big DB zone.

    DOCK_X = EC2_X + 52
    DOCK_Y = EC2_Y + 90
    DOCK_W = EC2_W - 104
    DOCK_H = EC2_H - 230

    WD_H = 108
    ROW_H = DOCK_H - 55 - WD_H - 28

    SPRING_ZONE_X = DOCK_X + 42
    SPRING_ZONE_Y = DOCK_Y + 52
    # Give FastAPI area more horizontal room
    # Make Spring narrower; give FastAPI / Dynamic Tools more width
    SPRING_ZONE_W = 380
    SPRING_ZONE_H = ROW_H

    FA_ZONE_X = SPRING_ZONE_X + SPRING_ZONE_W + 140
    FA_ZONE_Y = SPRING_ZONE_Y
    FA_ZONE_W = DOCK_X + DOCK_W - FA_ZONE_X - 20
    FA_ZONE_H = ROW_H

    WD_X = DOCK_X + 42
    WD_Y = SPRING_ZONE_Y + SPRING_ZONE_H + 28
    WD_W = DOCK_W - 84

    LEGEND_X = VPC_X + VPC_W + 55
    LEGEND_Y = VPC_Y + 20
    LEGEND_W, LEGEND_H = 360, 300

    # --- Title & legend ---
    els.append(
        text_el(
            "txt-title",
            32,
            16,
            640,
            52,
            "demoAgent — Service Architecture\n"
            "User · LLM · VPC · EC2 · Docker · Spring · FastAPI · Runtime State",
            "#1e1e1e",
            fs=16,
            seed=10,
        )
    )
    legend = (
        "Use-cases\n"
        "① Chat\n"
        "② Add Tool\n"
        "③ Patch Prompt\n\n"
        "Internal\n"
        "④ LLM\n"
        "⑤ Tools\n"
        "⑥ State"
    )
    els.append(rect("legend-box", LEGEND_X, LEGEND_Y, LEGEND_W, LEGEND_H, "#495057", "#f8f9fa", seed=11))
    els.append(
        text_el(
            "txt-legend",
            LEGEND_X + 14,
            LEGEND_Y + 12,
            LEGEND_W - 28,
            LEGEND_H - 24,
            legend,
            "#212529",
            fs=12,
            seed=12,
        )
    )

    # VPC 영역
    vpc = rect("vpc-zone", VPC_X, VPC_Y, VPC_W, VPC_H, "#6741d9", "transparent", dashed=True, rough=0, seed=20)
    els.append(vpc)
    els.append(text_el("txt-vpc", VPC_X + 20, VPC_Y + 10, 720, 34, "VPC 영역 (AWS Cloud)", "#6741d9", fs=17, seed=21))

    # 외부 LLM 영역 (VPC 위 · Internet)
    llm = rect("llm-zone", LLM_X, LLM_Y, LLM_W, LLM_H, "#f08c00", "#fff9db", dashed=False, seed=50)
    els.append(llm)
    els.append(text_el("txt-llm-zone", LLM_X + 12, LLM_Y + 8, LLM_W - 24, 26, "외부 LLM 영역", "#e67700", fs=13, seed=53))
    els.append(image_el("img-openai", "si-openai", LLM_X + 14, LLM_Y + 38, 36, 36, 51))
    els.append(
        text_el(
            "txt-openai",
            LLM_X + 56,
            LLM_Y + 36,
            LLM_W - 70,
            LLM_H - 44,
            "OpenAI 등\n"
            "HTTPS API\n"
            "② chat / completion",
            "#e67700",
            fs=11,
            seed=52,
        )
    )

    # EC2 영역
    ec2 = rect("ec2-zone", EC2_X, EC2_Y, EC2_W, EC2_H, "#e8590c", "#fff4e6", seed=30)
    els.append(ec2)
    els.append(text_el("txt-ec2-zone", EC2_X + 16, EC2_Y + 10, EC2_W - 32, 30, "EC2 영역 (컴퓨트 호스트)", "#c2410c", fs=14, seed=32))
    els.append(
        text_el(
            "txt-ec2-sub",
            EC2_X + 16,
            EC2_Y + 38,
            EC2_W - 32,
            36,
            "Docker Engine · AMI · 보안 그룹",
            "#c2410c",
            fs=11,
            seed=31,
        )
    )

    # Docker Compose 영역
    dock = rect("docker-compose-zone", DOCK_X, DOCK_Y, DOCK_W, DOCK_H, "#1971c2", "#e7f5ff", dashed=True, rough=0, seed=40)
    els.append(dock)
    els.append(
        text_el(
            "txt-docker-zone",
            DOCK_X + 16,
            DOCK_Y + 10,
            DOCK_W - 32,
            34,
            "Docker Compose (optional)",
            "#1971c2",
            fs=13,
            seed=41,
        )
    )

    # Spring 영역
    spring_zone = rect(
        "spring-zone", SPRING_ZONE_X, SPRING_ZONE_Y, SPRING_ZONE_W, SPRING_ZONE_H, "#1864ab", "#d0ebff", seed=70
    )
    els.append(spring_zone)
    els.append(
        text_el(
            "txt-spring-zone",
            SPRING_ZONE_X + 12,
            SPRING_ZONE_Y + 8,
            SPRING_ZONE_W - 24,
            26,
            "Spring 영역",
            "#1864ab",
            fs=13,
            seed=73,
        )
    )
    els.append(image_el("img-spring", "si-springboot", SPRING_ZONE_X + 14, SPRING_ZONE_Y + 36, 38, 38, 71))
    els.append(
        text_el(
            "txt-spring",
            SPRING_ZONE_X + 58,
            SPRING_ZONE_Y + 34,
            SPRING_ZONE_W - 72,
            SPRING_ZONE_H - 44,
            "Agent Server :8080\n"
            "Agent loop (SSE)\n"
            "ToolCreator / Delete\n"
            "OpenAI + ToolClient\n"
            "spawns uvicorn",
            "#1864ab",
            fs=10,
            seed=72,
        )
    )

    # FastAPI 영역 (고정 도구 컨테이너)
    fa_zone = rect("fastapi-zone", FA_ZONE_X, FA_ZONE_Y, FA_ZONE_W, FA_ZONE_H, "#0b7285", "#e3fafc", dashed=True, seed=100)
    els.append(fa_zone)
    els.append(
        text_el(
            "txt-fa-zone",
            FA_ZONE_X + 10,
            FA_ZONE_Y + 8,
            FA_ZONE_W - 20,
            28,
            "FastAPI Tools (static containers)",
            "#0b7285",
            fs=12,
            seed=101,
        )
    )

    TOOL_X = FA_ZONE_X + 22
    TOOL_Y0 = FA_ZONE_Y + 44
    # Keep static tools compact; give Dynamic Tools most of the space
    TOOL_W, TOOL_H = 200, 84
    TOOL_VGAP = 112

    els.append(image_el("img-fastapi-col", "si-fastapi", TOOL_X, TOOL_Y0 - 36, 28, 28, 140))

    def tool_box(y: int, slug: str, title: str, port: str, seed_base: int) -> str:
        tid = f"tool-{slug}"
        r = rect(tid, TOOL_X, y, TOOL_W, TOOL_H, "#0b7285", "#ffffff", seed=seed_base)
        els.append(r)
        els.append(image_el(f"img-{tid}", "si-docker", TOOL_X + 10, y + 10, 24, 24, seed_base + 1))
        els.append(
            text_el(
                f"txt-{tid}",
                TOOL_X + 40,
                y + 8,
                TOOL_W - 48,
                TOOL_H - 14,
                f"Docker\n{title}\n{port}",
                "#0b7285",
                fs=10,
                seed=seed_base + 2,
            )
        )
        return tid

    t_random = tool_box(TOOL_Y0, "random", "random", ":8081", 80)
    t_curr = tool_box(TOOL_Y0 + TOOL_VGAP, "currency", "currency", ":8082", 90)
    tool_box(TOOL_Y0 + TOOL_VGAP * 2, "weather", "weather", ":8083", 100)

    DYN_X = TOOL_X + TOOL_W + 28
    DYN_Y = TOOL_Y0
    DYN_W = FA_ZONE_X + FA_ZONE_W - DYN_X - 18
    DYN_H = FA_ZONE_Y + FA_ZONE_H - DYN_Y - 18
    dyn = rect("dyn-zone", DYN_X, DYN_Y, DYN_W, DYN_H, "#2b8a3e", "#ebfbee", dashed=True, seed=110)
    els.append(dyn)
    els.append(image_el("img-dyn-fastapi", "si-fastapi", DYN_X + 10, DYN_Y + 10, 24, 24, 111))
    els.append(
        text_el(
            "txt-dyn",
            DYN_X + 38,
            DYN_Y + 8,
            DYN_W - 48,
            DYN_H - 16,
            "Dynamic Tools (runtime)\n"
            "1) generate *.py\n"
            "2) spawn uvicorn :8090+\n"
            "3) inject env (.env*)\n"
            "4) wait /health\n"
            "5) save tools.db\n"
            "6) restore on boot\n"
            "delete → kill pid",
            "#2b8a3e",
            fs=10,
            seed=112,
        )
    )

    # 워킹 디렉터리 영역
    wd = rect("workdir-zone", WD_X, WD_Y, WD_W, WD_H, "#495057", "#f1f3f5", seed=130)
    els.append(wd)
    els.append(text_el("txt-wd-zone", WD_X + 12, WD_Y + 8, WD_W - 24, 24, "워킹 디렉터리 영역", "#495057", fs=12, seed=133))
    els.append(image_el("img-git", "si-git", WD_X + 14, WD_Y + 36, 26, 26, 131))
    els.append(
        text_el(
            "txt-wd",
            WD_X + 46,
            WD_Y + 34,
            WD_W - 58,
            WD_H - 42,
            "runtime state\n"
            "tool-server/*.py\n"
            "system-prompt.txt\n"
            ".env · .env.local",
            "#495057",
            fs=10,
            seed=132,
        )
    )

    # SQLite card (small, not an empty huge zone)
    SQLITE_W, SQLITE_H = 260, 96
    SQLITE_X = WD_X + WD_W - SQLITE_W - 16
    SQLITE_Y = WD_Y + 12
    sqlite_card = rect("sqlite-card", SQLITE_X, SQLITE_Y, SQLITE_W, SQLITE_H, "#0d9488", "#f0fdfa", seed=115)
    els.append(sqlite_card)
    els.append(text_el("txt-sqlite-title", SQLITE_X + 12, SQLITE_Y + 10, SQLITE_W - 24, 22, "SQLite tools.db", "#0f766e", fs=12, seed=116))
    els.append(
        text_el(
            "txt-sqlite",
            SQLITE_X + 12,
            SQLITE_Y + 34,
            SQLITE_W - 24,
            SQLITE_H - 40,
            "dynamic_tool\n"
            "tool_name · port · pid",
            "#0f766e",
            fs=10,
            seed=117,
        )
    )

    # User 영역 (특정 파일명 없이 — HTTPS 엔드포인트로 접근)
    user_z = rect("user-zone", USER_X, USER_Y, USER_W, USER_H, "#7048e8", "#f3f0ff", dashed=True, seed=60)
    els.append(user_z)
    els.append(text_el("txt-user-zone", USER_X + 12, USER_Y + 10, USER_W - 24, 28, "User 영역", "#5f3dc4", fs=13, seed=63))
    els.append(image_el("img-chrome", "si-chrome", USER_X + 14, USER_Y + 42, 30, 30, 61))
    els.append(
        text_el(
            "txt-user",
            USER_X + 52,
            USER_Y + 40,
            USER_W - 66,
            USER_H - 52,
            "웹 / 모바일 / API 클라이언트\n\n"
            "HTTPS → ALB 또는\n"
            "EC2 공인 IP :8080\n"
            "(정적 리소스 여부와 무관)\n\n"
            "① 질의 · ⑤ 응답(SSE)",
            "#5f3dc4",
            fs=11,
            seed=62,
        )
    )

    # --- Arrows ---
    mid_spring_y = SPRING_ZONE_Y + SPRING_ZONE_H / 2
    mid_user_y = USER_Y + USER_H / 2

    # User → Agent use-cases (separate lines for readability)
    u_to_s_dx = SPRING_ZONE_X - (USER_X + USER_W)
    a1_y = mid_user_y - 42
    a2_y = mid_user_y
    a3_y = mid_user_y + 42

    a_uc_chat = arrow(
        "a-uc-chat",
        USER_X + USER_W,
        a1_y,
        [[0, 0], [u_to_s_dx, 0]],
        "#7048e8",
        start_id="user-zone",
        end_id="spring-zone",
        seed=200,
        label_id="lbl-uc-chat",
    )
    bind(user_z, "a-uc-chat")
    bind(spring_zone, "a-uc-chat")
    els.append(a_uc_chat)
    els.append(
        arrow_label(
            "lbl-uc-chat",
            "a-uc-chat",
            "① chat (SSE)",
            "#7048e8",
            USER_X + USER_W + 44,
            a1_y - 28,
            130,
            30,
            201,
        )
    )

    a_uc_tool = arrow(
        "a-uc-tool-create",
        USER_X + USER_W,
        a2_y,
        [[0, 0], [u_to_s_dx, 0]],
        "#5f3dc4",
        dashed=True,
        start_id="user-zone",
        end_id="spring-zone",
        seed=202,
        label_id="lbl-uc-tool-create",
    )
    bind(user_z, "a-uc-tool-create")
    bind(spring_zone, "a-uc-tool-create")
    els.append(a_uc_tool)
    els.append(
        arrow_label(
            "lbl-uc-tool-create",
            "a-uc-tool-create",
            "② add tool (SSE)",
            "#5f3dc4",
            USER_X + USER_W + 44,
            a2_y - 28,
            150,
            30,
            203,
        )
    )

    a_uc_prompt = arrow(
        "a-uc-prompt-patch",
        USER_X + USER_W,
        a3_y,
        [[0, 0], [u_to_s_dx, 0]],
        "#7048e8",
        dashed=True,
        start_id="user-zone",
        end_id="spring-zone",
        seed=204,
        label_id="lbl-uc-prompt-patch",
    )
    bind(user_z, "a-uc-prompt-patch")
    bind(spring_zone, "a-uc-prompt-patch")
    els.append(a_uc_prompt)
    els.append(
        arrow_label(
            "lbl-uc-prompt-patch",
            "a-uc-prompt-patch",
            "③ patch prompt (HTTP)",
            "#7048e8",
            USER_X + USER_W + 44,
            a3_y - 28,
            170,
            30,
            205,
        )
    )

    # LLM request / response (separate arrows; orthogonal to avoid overlaps)
    llm_cx = LLM_X + LLM_W // 2
    llm_by = LLM_Y + LLM_H
    spring_cx = SPRING_ZONE_X + SPRING_ZONE_W // 2

    # request: Spring → LLM (up, over, then into LLM)
    req_sy = SPRING_ZONE_Y + 6
    req_mid_y = llm_by + 28
    req = arrow(
        "a-llm-req",
        spring_cx,
        req_sy,
        [
            [0, 0],
            [0, req_mid_y - req_sy],
            [llm_cx - spring_cx, req_mid_y - req_sy],
            [llm_cx - spring_cx, llm_by - req_sy],
        ],
        "#e67700",
        start_id="spring-zone",
        end_id="llm-zone",
        seed=210,
        label_id="lbl-llm-req",
    )
    bind(spring_zone, "a-llm-req")
    bind(llm, "a-llm-req")
    els.append(req)
    els.append(
        arrow_label(
            "lbl-llm-req",
            "a-llm-req",
            "④ request",
            "#e67700",
            spring_cx + (llm_cx - spring_cx) * 0.28,
            req_sy + (llm_by - req_sy) * 0.28 - 18,
            120,
            28,
            211,
        )
    )

    # response: LLM → Spring (down, over, then into Spring)
    res_sy = SPRING_ZONE_Y + 24
    res_mid_y = llm_by + 52
    res = arrow(
        "a-llm-res",
        llm_cx,
        llm_by,
        [
            [0, 0],
            [0, res_mid_y - llm_by],
            [spring_cx - llm_cx, res_mid_y - llm_by],
            [spring_cx - llm_cx, res_sy - llm_by],
        ],
        "#f08c00",
        start_id="llm-zone",
        end_id="spring-zone",
        seed=212,
        label_id="lbl-llm-res",
    )
    bind(llm, "a-llm-res")
    bind(spring_zone, "a-llm-res")
    els.append(res)
    els.append(
        arrow_label(
            "lbl-llm-res",
            "a-llm-res",
            "④ response",
            "#e67700",
            llm_cx + (spring_cx - llm_cx) * 0.34 - 10,
            llm_by + (res_sy - llm_by) * 0.42 - 10,
            130,
            28,
            213,
        )
    )

    # Use-case specific internal notes (minimal, but clarifying)
    # Place near Spring left edge, aligned with the three incoming use-case lines.
    els.append(text_el("txt-uc1", SPRING_ZONE_X + 14, a1_y - 18, 220, 22, "① run agent loop", "#7048e8", fs=11, seed=310))
    els.append(text_el("txt-uc2", SPRING_ZONE_X + 14, a2_y - 18, 260, 22, "② ToolCreator (spawn + health)", "#5f3dc4", fs=11, seed=311))
    els.append(text_el("txt-uc3", SPRING_ZONE_X + 14, a3_y - 18, 260, 22, "③ prompt patch / save", "#7048e8", fs=11, seed=312))

    # Use-case ②/③ explicitly touch Runtime State
    # (short arrows to avoid over-labeling)
    # ② add tool → state
    uc2_state = arrow(
        "a-uc2-state",
        SPRING_ZONE_X + 210,
        a2_y + 8,
        [[0, 0], [0, WD_Y - (a2_y + 8) - 12]],
        "#868e96",
        dashed=True,
        start_id="spring-zone",
        end_id="workdir-zone",
        seed=313,
        label_id="lbl-uc2-state",
    )
    bind(spring_zone, "a-uc2-state")
    bind(wd, "a-uc2-state")
    els.append(uc2_state)
    els.append(arrow_label("lbl-uc2-state", "a-uc2-state", "⑥ write", "#495057", SPRING_ZONE_X + 226, a2_y + 40, 70, 26, 314))

    # ③ patch prompt → state
    uc3_state = arrow(
        "a-uc3-state",
        SPRING_ZONE_X + 260,
        a3_y + 8,
        [[0, 0], [0, WD_Y - (a3_y + 8) - 12]],
        "#868e96",
        dashed=True,
        start_id="spring-zone",
        end_id="workdir-zone",
        seed=315,
        label_id="lbl-uc3-state",
    )
    bind(spring_zone, "a-uc3-state")
    bind(wd, "a-uc3-state")
    els.append(uc3_state)
    els.append(arrow_label("lbl-uc3-state", "a-uc3-state", "⑥ update", "#495057", SPRING_ZONE_X + 276, a3_y + 40, 84, 26, 316))

    # Agent ↔ SQLite state (explicit, so DB relation is obvious)
    db_sx = SPRING_ZONE_X + SPRING_ZONE_W - 18
    db_sy = SPRING_ZONE_Y + SPRING_ZONE_H - 52
    db_ex = SQLITE_X
    db_ey = SQLITE_Y + SQLITE_H / 2
    a_state = arrow(
        "a-spring-sqlite",
        db_sx,
        db_sy,
        [
            [0, 0],
            [0, (db_ey - db_sy) * 0.35],
            [db_ex - db_sx, (db_ey - db_sy) * 0.35],
            [db_ex - db_sx, db_ey - db_sy],
        ],
        "#0d9488",
        dashed=True,
        start_id="spring-zone",
        end_id="sqlite-card",
        seed=317,
        label_id="lbl-state",
    )
    bind(spring_zone, "a-spring-sqlite")
    bind(sqlite_card, "a-spring-sqlite")
    els.append(a_state)
    els.append(
        arrow_label(
            "lbl-state",
            "a-spring-sqlite",
            "⑥ read/write",
            "#0f766e",
            db_sx + (db_ex - db_sx) * 0.55 - 40,
            db_sy + (db_ey - db_sy) * 0.35 - 22,
            120,
            28,
            318,
        )
    )

    # Agent ↔ Tools (route with a horizontal "bus" to avoid crossing)
    a5_sx = SPRING_ZONE_X + SPRING_ZONE_W
    a5_sy = SPRING_ZONE_Y + 120
    a5_ex = TOOL_X
    a5_ey = TOOL_Y0 + TOOL_H // 2
    tool_bus_y = SPRING_ZONE_Y + 96
    a5 = arrow(
        "a-spring-tool",
        a5_sx,
        a5_sy,
        [
            [0, 0],
            [0, tool_bus_y - a5_sy],
            [a5_ex - a5_sx, tool_bus_y - a5_sy],
            [a5_ex - a5_sx, a5_ey - a5_sy],
        ],
        "#1971c2",
        start_id="spring-zone",
        end_id=t_random,
        seed=220,
        label_id="lbl-a5",
    )
    bind(spring_zone, "a-spring-tool")
    for e in els:
        if e.get("id") == t_random:
            bind(e, "a-spring-tool")
            break
    els.append(a5)
    els.append(
        arrow_label(
            "lbl-a5",
            "a-spring-tool",
            "⑤ /execute",
            "#1971c2",
            a5_sx + (a5_ex - a5_sx) * 0.42,
            a5_sy + (a5_ey - a5_sy) * 0.32 - 22,
            120,
            30,
            221,
        )
    )

    a6_sx = TOOL_X
    a6_sy = TOOL_Y0 + TOOL_VGAP + TOOL_H // 2
    a6_ex = SPRING_ZONE_X + SPRING_ZONE_W - 24
    a6_ey = SPRING_ZONE_Y + SPRING_ZONE_H - 90
    tool_bus_y2 = SPRING_ZONE_Y + 132
    a6 = arrow(
        "a-tool-spring",
        a6_sx,
        a6_sy,
        [
            [0, 0],
            [0, tool_bus_y2 - a6_sy],
            [a6_ex - a6_sx, tool_bus_y2 - a6_sy],
            [a6_ex - a6_sx, a6_ey - a6_sy],
        ],
        "#1098ad",
        dashed=True,
        start_id=t_curr,
        end_id="spring-zone",
        seed=230,
        label_id="lbl-a6",
    )
    for e in els:
        if e.get("id") == t_curr:
            bind(e, "a-tool-spring")
            break
    bind(spring_zone, "a-tool-spring")
    els.append(a6)
    els.append(
        arrow_label(
            "lbl-a6",
            "a-tool-spring",
            "⑤ JSON",
            "#1098ad",
            a6_sx + (a6_ex - a6_sx) * 0.22,
            a6_sy + (a6_ey - a6_sy) * 0.32,
            100,
            30,
            231,
        )
    )

    # Agent → User (chat stream)
    a7_sx = SPRING_ZONE_X
    a7_sy = mid_spring_y
    a7_ex = USER_X + USER_W
    a7_ey = mid_user_y - 42
    a7 = arrow(
        "a-spring-user",
        a7_sx,
        a7_sy,
        [[0, 0], [a7_ex - a7_sx, a7_ey - a7_sy]],
        "#7048e8",
        start_id="spring-zone",
        end_id="user-zone",
        seed=240,
        label_id="lbl-a7",
    )
    bind(spring_zone, "a-spring-user")
    bind(user_z, "a-spring-user")
    els.append(a7)
    els.append(
        arrow_label(
            "lbl-a7",
            "a-spring-user",
            "① SSE",
            "#7048e8",
            a7_sx + (a7_ex - a7_sx) * 0.38,
            a7_sy + (a7_ey - a7_sy) * 0.48 - 34,
            100,
            30,
            241,
        )
    )

    # (state is shown via Runtime State arrows to workdir-zone and the sqlite card inside it)

    a7_sx = SPRING_ZONE_X + 100
    a7_sy = SPRING_ZONE_Y + SPRING_ZONE_H
    a7 = arrow(
        "a-spring-wd",
        a7_sx,
        a7_sy,
        [[0, 0], [0, WD_Y - a7_sy + 6]],
        "#868e96",
        dashed=True,
        start_id="spring-zone",
        end_id="workdir-zone",
        seed=260,
        label_id="lbl-a7",
    )
    bind(spring_zone, "a-spring-wd")
    bind(wd, "a-spring-wd")
    els.append(a7)
    els.append(
        arrow_label(
            "lbl-a7",
            "a-spring-wd",
            "ToolCreator\n→ 파일",
            "#495057",
            a7_sx + 14,
            a7_sy + (WD_Y - a7_sy) * 0.42,
            100,
            40,
            261,
        )
    )

    a8_sx = DYN_X
    a8_sy = DYN_Y + DYN_H // 2
    a8_ex = SPRING_ZONE_X + SPRING_ZONE_W - 12
    a8_ey = SPRING_ZONE_Y + SPRING_ZONE_H // 2 + 36
    a8 = arrow(
        "a-dyn-spring",
        a8_sx,
        a8_sy,
        [[0, 0], [a8_ex - a8_sx, a8_ey - a8_sy]],
        "#2b8a3e",
        dashed=True,
        start_id="dyn-zone",
        end_id="spring-zone",
        seed=270,
        label_id="lbl-a8",
    )
    bind(dyn, "a-dyn-spring")
    bind(spring_zone, "a-dyn-spring")
    els.append(a8)
    els.append(
        arrow_label(
            "lbl-a8",
            "a-dyn-spring",
            "③④ 동적",
            "#2b8a3e",
            a8_sx + (a8_ex - a8_sx) * 0.32,
            a8_sy - 34,
            100,
            30,
            271,
        )
    )

    back = [e for e in els if e.get("type") != "arrow" and e.get("containerId") is None]
    arrw = [e for e in els if e.get("type") == "arrow"]
    lbls = [e for e in els if e.get("type") == "text" and e.get("containerId")]
    ordered = back + arrw + lbls

    doc = {
        "type": "excalidraw",
        "version": 2,
        "source": "https://excalidraw.com",
        "elements": ordered,
        "appState": {"gridSize": None, "viewBackgroundColor": "#ffffff"},
        "files": build_files(),
    }
    OUT.write_text(json.dumps(doc, ensure_ascii=False, indent=2), encoding="utf-8")
    print(f"Wrote {OUT} ({OUT.stat().st_size // 1024} KB)")


if __name__ == "__main__":
    main()

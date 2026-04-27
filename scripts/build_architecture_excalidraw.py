"""
Regenerate docs/superpowers/architecture-demoagent.excalidraw.
Each concern has its own labeled zone: User, 외부 LLM, VPC, EC2, Docker Compose,
Spring, FastAPI, 워킹 디렉터리, DB(RDS MySQL). Wide gaps; ①~⑥ flows.
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
    "si-mysql": "icon-mysql.svg",
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
    USER_X, USER_Y = 36, 480
    USER_W, USER_H = 215, 260
    GAP_U_V = 115
    VPC_X = USER_X + USER_W + GAP_U_V
    VPC_Y, VPC_W, VPC_H = 138, 1540, 940
    # LLM 박스: VPC 상단 오른쪽, 캔버스 안에 유지
    LLM_W, LLM_H = 300, 108
    LLM_Y = 22
    LLM_X = VPC_X + VPC_W - LLM_W - 48

    EC2_X = VPC_X + 52
    EC2_Y = VPC_Y + 62
    EC2_W, EC2_H = 1020, 820

    GAP_E_R = 85
    DB_X = EC2_X + EC2_W + GAP_E_R
    DB_Y = EC2_Y
    DB_W, DB_H = 340, EC2_H

    DOCK_X = EC2_X + 48
    DOCK_Y = EC2_Y + 78
    DOCK_W = EC2_W - 96
    DOCK_H = EC2_H - 210

    WD_H = 108
    ROW_H = DOCK_H - 55 - WD_H - 28

    SPRING_ZONE_X = DOCK_X + 42
    SPRING_ZONE_Y = DOCK_Y + 52
    SPRING_ZONE_W = 500
    SPRING_ZONE_H = ROW_H

    FA_ZONE_X = SPRING_ZONE_X + SPRING_ZONE_W + 70
    FA_ZONE_Y = SPRING_ZONE_Y
    FA_ZONE_W = DOCK_X + DOCK_W - FA_ZONE_X - 38
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
            620,
            52,
            "demoAgent — 영역별 구분\n"
            "User · LLM · VPC · EC2 · Docker · Spring · FastAPI · WD · DB · ①~⑥",
            "#1e1e1e",
            fs=16,
            seed=10,
        )
    )
    legend = (
        "플로우\n"
        "① User → Spring\n"
        "② Spring ↔ LLM\n"
        "③ Spring → Tool\n"
        "④ Tool → Spring\n"
        "⑤ Spring → User\n"
        "⑥ Spring ↔ RDS"
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
            36,
            "Docker Compose 영역\n"
            "bridge network · 서비스 DNS",
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
            "Boot :8080 (agent-server)\n\n"
            "발화 → LLM JSON\n"
            "(call / final)\n\n"
            "Agent / Tools /\n"
            "ToolCreator SSE\n\n"
            "OpenAiClient · ToolClient",
            "#1864ab",
            fs=10,
            seed=72,
        )
    )

    # FastAPI 영역 (고정 도구 + 동적 슬롯 모두 포함)
    fa_zone = rect("fastapi-zone", FA_ZONE_X, FA_ZONE_Y, FA_ZONE_W, FA_ZONE_H, "#0b7285", "#e3fafc", dashed=True, seed=100)
    els.append(fa_zone)
    els.append(
        text_el(
            "txt-fa-zone",
            FA_ZONE_X + 10,
            FA_ZONE_Y + 8,
            FA_ZONE_W - 20,
            28,
            "FastAPI 영역 (도구 컨테이너)",
            "#0b7285",
            fs=12,
            seed=101,
        )
    )

    TOOL_X = FA_ZONE_X + 22
    TOOL_Y0 = FA_ZONE_Y + 44
    TOOL_W, TOOL_H = 178, 80
    TOOL_VGAP = 96

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

    DYN_X = TOOL_X + TOOL_W + 36
    DYN_Y = FA_ZONE_Y + 40
    DYN_W = FA_ZONE_X + FA_ZONE_W - DYN_X - 16
    DYN_H = FA_ZONE_Y + FA_ZONE_H - DYN_Y - 14
    dyn = rect("dyn-zone", DYN_X, DYN_Y, DYN_W, DYN_H, "#2b8a3e", "#ebfbee", dashed=True, seed=110)
    els.append(dyn)
    els.append(image_el("img-dyn-docker", "si-docker", DYN_X + 10, DYN_Y + 10, 24, 24, 111))
    els.append(
        text_el(
            "txt-dyn",
            DYN_X + 38,
            DYN_Y + 8,
            DYN_W - 48,
            DYN_H - 16,
            "동적 (ToolCreator)\n\n"
            "uvicorn :8090+\n"
            "/health → final\n"
            "compose 추가\n"
            "또는 호스트 포트",
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
            "EC2 볼륨 mount\n"
            "tool-server/*.py\n"
            "system-prompt.txt · .env.local\n"
            "(런타임 파일 — DB와 역할 분리)",
            "#495057",
            fs=10,
            seed=132,
        )
    )

    # DB 영역 (RDS MySQL)
    db = rect("db-zone", DB_X, DB_Y, DB_W, DB_H, "#0d9488", "#f0fdfa", seed=115)
    els.append(db)
    els.append(text_el("txt-db-zone", DB_X + 12, DB_Y + 10, DB_W - 24, 28, "DB 영역 (RDS · MySQL)", "#0f766e", fs=13, seed=118))
    els.append(image_el("img-mysql", "si-mysql", DB_X + 14, DB_Y + 42, 34, 34, 116))
    rds_body = (
        "private subnet\n\n"
        "저장 예시\n"
        "──────\n"
        "· tools 메타\n"
        "· system_prompt_versions\n"
        "· agent_sessions / runs\n"
        "· tool_creator_jobs\n"
        "· (선택) users / keys\n\n"
        "3306 TCP\n"
        "⑥ read/write"
    )
    els.append(
        text_el(
            "txt-db",
            DB_X + 54,
            DB_Y + 40,
            DB_W - 68,
            DB_H - 52,
            rds_body,
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

    a1 = arrow(
        "a-user-spring",
        USER_X + USER_W,
        mid_user_y,
        [[0, 0], [SPRING_ZONE_X - (USER_X + USER_W), 0]],
        "#7048e8",
        start_id="user-zone",
        end_id="spring-zone",
        seed=200,
        label_id="lbl-a1",
    )
    bind(user_z, "a-user-spring")
    bind(spring_zone, "a-user-spring")
    els.append(a1)
    els.append(
        arrow_label(
            "lbl-a1",
            "a-user-spring",
            "① HTTP/SSE",
            "#7048e8",
            USER_X + USER_W + 50,
            mid_user_y - 40,
            120,
            36,
            201,
        )
    )

    a2_sx = SPRING_ZONE_X + SPRING_ZONE_W // 2
    a2_sy = SPRING_ZONE_Y
    a2_dx = LLM_X + LLM_W // 2 - a2_sx
    a2_dy = LLM_Y + LLM_H - a2_sy
    a2 = arrow(
        "a-spring-llm",
        a2_sx,
        a2_sy,
        [[0, 0], [a2_dx, a2_dy]],
        "#e67700",
        start_id="spring-zone",
        end_id="llm-zone",
        seed=210,
        label_id="lbl-a2",
    )
    bind(spring_zone, "a-spring-llm")
    bind(llm, "a-spring-llm")
    els.append(a2)
    els.append(
        arrow_label(
            "lbl-a2",
            "a-spring-llm",
            "② LLM",
            "#e67700",
            a2_sx + a2_dx * 0.3,
            a2_sy + a2_dy * 0.28 - 18,
            90,
            30,
            211,
        )
    )

    a3_sx = SPRING_ZONE_X + SPRING_ZONE_W
    a3_sy = SPRING_ZONE_Y + 110
    a3_ex = TOOL_X
    a3_ey = TOOL_Y0 + TOOL_H // 2
    a3 = arrow(
        "a-spring-tool",
        a3_sx,
        a3_sy,
        [[0, 0], [a3_ex - a3_sx, a3_ey - a3_sy]],
        "#1971c2",
        start_id="spring-zone",
        end_id=t_random,
        seed=220,
        label_id="lbl-a3",
    )
    bind(spring_zone, "a-spring-tool")
    for e in els:
        if e.get("id") == t_random:
            bind(e, "a-spring-tool")
            break
    els.append(a3)
    els.append(
        arrow_label(
            "lbl-a3",
            "a-spring-tool",
            "③ /execute",
            "#1971c2",
            a3_sx + (a3_ex - a3_sx) * 0.42,
            a3_sy + (a3_ey - a3_sy) * 0.32 - 22,
            120,
            30,
            221,
        )
    )

    a4_sx = TOOL_X
    a4_sy = TOOL_Y0 + TOOL_VGAP + TOOL_H // 2
    a4_ex = SPRING_ZONE_X + SPRING_ZONE_W - 24
    a4_ey = SPRING_ZONE_Y + SPRING_ZONE_H - 90
    a4 = arrow(
        "a-tool-spring",
        a4_sx,
        a4_sy,
        [[0, 0], [a4_ex - a4_sx, a4_ey - a4_sy]],
        "#1098ad",
        dashed=True,
        start_id=t_curr,
        end_id="spring-zone",
        seed=230,
        label_id="lbl-a4",
    )
    for e in els:
        if e.get("id") == t_curr:
            bind(e, "a-tool-spring")
            break
    bind(spring_zone, "a-tool-spring")
    els.append(a4)
    els.append(
        arrow_label(
            "lbl-a4",
            "a-tool-spring",
            "④ JSON",
            "#1098ad",
            a4_sx + (a4_ex - a4_sx) * 0.22,
            a4_sy + (a4_ey - a4_sy) * 0.32,
            100,
            30,
            231,
        )
    )

    a5_sx = SPRING_ZONE_X
    a5_sy = mid_spring_y
    a5_ex = USER_X + USER_W
    a5_ey = mid_user_y
    a5 = arrow(
        "a-spring-user",
        a5_sx,
        a5_sy,
        [[0, 0], [a5_ex - a5_sx, a5_ey - a5_sy]],
        "#7048e8",
        start_id="spring-zone",
        end_id="user-zone",
        seed=240,
        label_id="lbl-a5",
    )
    bind(spring_zone, "a-spring-user")
    bind(user_z, "a-spring-user")
    els.append(a5)
    els.append(
        arrow_label(
            "lbl-a5",
            "a-spring-user",
            "⑤ SSE",
            "#7048e8",
            a5_sx + (a5_ex - a5_sx) * 0.38,
            a5_sy + (a5_ey - a5_sy) * 0.48 - 34,
            100,
            30,
            241,
        )
    )

    a6_sx = SPRING_ZONE_X + SPRING_ZONE_W
    a6_sy = SPRING_ZONE_Y + SPRING_ZONE_H - 90
    a6_ex = DB_X
    a6_ey = DB_Y + DB_H // 2
    a6 = arrow(
        "a-spring-db",
        a6_sx,
        a6_sy,
        [[0, 0], [a6_ex - a6_sx, a6_ey - a6_sy]],
        "#0d9488",
        dashed=True,
        start_id="spring-zone",
        end_id="db-zone",
        seed=250,
        label_id="lbl-a6",
    )
    bind(spring_zone, "a-spring-db")
    bind(db, "a-spring-db")
    els.append(a6)
    els.append(
        arrow_label(
            "lbl-a6",
            "a-spring-db",
            "⑥ MySQL",
            "#0d9488",
            a6_sx + (a6_ex - a6_sx) * 0.48 - 50,
            a6_sy + (a6_ey - a6_sy) * 0.45 - 24,
            110,
            30,
            251,
        )
    )

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

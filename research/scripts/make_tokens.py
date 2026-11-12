"""从 APK 提取的 ARSC 数据生成 token JSON（仅保留设计系统相关命名空间）。"""
import json
import os
import re

ROOT = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(os.path.dirname(ROOT), "research", "tokens")
os.makedirs(OUT, exist_ok=True)


def load(tag):
    with open(os.path.join(ROOT, f"out/{tag}/report.json"), encoding="utf-8") as f:
        return json.load(f)


def numeric(v, unit):
    if not isinstance(v, str):
        return None
    m = re.fullmatch(r"([0-9.]+)" + unit, v)
    if not m:
        return None
    f = float(m.group(1))
    return int(f) if f == int(f) else round(f, 2)


def collect(d, prefixes, exclude_prefixes=()):
    dims = d["arsc"]["dimens"]
    out = {}
    for name, value in dims.items():
        if prefixes and not any(name.startswith(p) for p in prefixes):
            continue
        if any(name.startswith(p) for p in exclude_prefixes):
            continue
        for unit in ("dip", "dp", "sp", "px"):
            n = numeric(value, unit)
            if n is not None:
                out[name] = {"value": n, "unit": unit}
                break
    return out


def group(dims, keywords):
    g = {}
    for name, v in dims.items():
        for kw in keywords:
            if kw in name:
                g.setdefault(kw, {})[name] = f'{v["value"]}{v["unit"]}'
                break
    return g


def main():
    # ---------- Spotify ----------
    sp = load("spotify")
    enc = collect(sp, ("encore_",))
    spotify = {
        "source": {
            "app": "Spotify",
            "package": "com.spotify.music",
            "version": sp["manifest"]["version_name"],
            "version_code": sp["manifest"]["version_code"],
            "artifact": "APKPure 分发包（base.apk + config splits）",
            "extraction": "androguard 4.1.4 解析 resources.arsc（默认配置）",
            "note": "encore_* 为 Spotify 自有设计系统命名空间；颜色条目在本包中为动态引用，未解析出字面色值。",
        },
        "dimensions": enc,
        "groups": group(
            enc,
            ["corner_radius", "spacer", "text_size", "control_size", "layout_margin", "border_width"],
        ),
        "typography": {
            "font_files": [f for f in sp["zip"]["font_files"] if f.startswith("res/font/")],
            "text_size_scale": sorted(
                {v["value"] for k, v in enc.items() if k.endswith("text_size") or "text_size" in k}
            ),
        },
        "measured_palette": json.load(
            open(os.path.join(ROOT, "out/palette_spotify.json"), encoding="utf-8")
        )["aggregate"],
    }
    with open(os.path.join(OUT, "spotify-encore.json"), "w", encoding="utf-8") as f:
        json.dump(spotify, f, ensure_ascii=False, indent=2)

    # ---------- Apple Music ----------
    am = load("applemusic")
    lib = re.compile(
        r"^(abc|mtrl|m3|design|material|cast|exo|glance|emoji|carrier|compat|support|notification|browser|preference|cardview|clock_)"
    )
    dims = collect(am, ())
    app_dims = {k: v for k, v in dims.items() if not lib.match(k)}
    applemusic = {
        "source": {
            "app": "Apple Music",
            "package": "com.apple.android.music",
            "version": am["manifest"]["version_name"],
            "version_code": am["manifest"]["version_code"],
            "artifact": "APKPure 分发包（base.apk + arm64 + mdpi splits）",
            "extraction": "androguard 4.1.4 解析 resources.arsc（默认配置）",
            "note": "已排除 AndroidX / Material3 / Cast / ExoPlayer 等第三方命名空间；颜色为动态引用，未解析出字面色值。",
        },
        "dimensions": app_dims,
        "groups": group(
            app_dims,
            ["compact_player", "bottom_navigation", "lyrics_", "corner_radius", "artwork", "header_"],
        ),
        "typography": {
            "font_files": [f for f in am["zip"]["font_files"] if f.startswith("res/font/")],
            "text_sizes_sp": {
                k: v["value"] for k, v in app_dims.items() if v["unit"] == "sp"
            },
        },
        "measured_palette": json.load(
            open(os.path.join(ROOT, "out/palette_applemusic.json"), encoding="utf-8")
        )["aggregate"],
    }
    with open(os.path.join(OUT, "applemusic.json"), "w", encoding="utf-8") as f:
        json.dump(applemusic, f, ensure_ascii=False, indent=2)

    # ---------- 音流 ----------
    sm = load("streammusic")
    smd = collect(sm, ())
    streammusic = {
        "source": {
            "app": "音流 / Musiver（StreamMusic 重写版）",
            "package": "cn.aqzscn.stream_music",
            "version": sm["manifest"]["version_name"],
            "version_code": sm["manifest"]["version_code"],
            "artifact": "GitHub Release musiver_1.3.9_arm64-v8a.apk",
            "extraction": "androguard 4.1.4 + Flutter asset/FontManifest 解析",
            "note": "Flutter 应用，视觉 token 编译进 lib/app.so，ARSC 中仅有 Android 壳与第三方库资源；"
            "尺寸资源几乎全部来自 AndroidX，不能代表其设计体系。",
        },
        "flutter": {
            "icon_font_families": sm.get("flutter_font_manifest") and [
                f["family"] for f in sm["flutter_font_manifest"]
            ],
            "bundled_images": [
                a for a in sm.get("flutter_assets", []) if re.search(r"\.(png|jpg|webp)$", a)
            ],
        },
        "dimensions_from_arsc": smd,
        "measured_palette": json.load(
            open(os.path.join(ROOT, "out/palette_streammusic.json"), encoding="utf-8")
        )["aggregate"],
    }
    with open(os.path.join(OUT, "streammusic.json"), "w", encoding="utf-8") as f:
        json.dump(streammusic, f, ensure_ascii=False, indent=2)

    print("spotify dims", len(enc), "| apple dims", len(app_dims), "| streammusic dims", len(smd))


if __name__ == "__main__":
    main()

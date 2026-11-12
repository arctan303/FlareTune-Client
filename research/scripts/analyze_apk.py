"""APK 设计资源提取：ARSC 颜色/尺寸/样式、字体、图标清单、Flutter 资源清单。

用法: python analyze_apk.py <apk> <out_dir>
产出 <out_dir>/report.json
"""
import json
import logging
import os
import re
import sys
import zipfile
from collections import Counter

logging.disable(logging.CRITICAL)

from androguard.core.apk import APK

DEFAULT_LOCALE = "\x00\x00"


def hexcolor(v):
    try:
        v = int(v)
    except Exception:
        return str(v)
    if v < 0:
        v += 1 << 32
    if v > 0xFFFFFFFF:
        return str(v)
    a = (v >> 24) & 0xFF
    r = (v >> 16) & 0xFF
    g = (v >> 8) & 0xFF
    b = v & 0xFF
    if a == 0xFF:
        return f"#{r:02X}{g:02X}{b:02X}"
    return f"#{r:02X}{g:02X}{b:02X}/{a:02X}"


def main(apk_path, out_dir):
    os.makedirs(out_dir, exist_ok=True)
    a = APK(apk_path)
    report = {}

    report["file"] = {
        "name": os.path.basename(apk_path),
        "size_mb": round(os.path.getsize(apk_path) / 1048576, 2),
    }
    def safe(fn, default=None):
        try:
            return fn()
        except Exception:
            return default

    report["manifest"] = {
        "package": safe(a.get_package),
        "app_name": safe(a.get_app_name),
        "version_name": safe(a.get_androidversion_name),
        "version_code": safe(a.get_androidversion_code),
        "min_sdk": safe(a.get_min_sdk_version),
        "target_sdk": safe(a.get_target_sdk_version),
        "main_activity": safe(a.get_main_activity),
        "permissions": len(safe(a.get_permissions, []) or []),
        "activities": len(safe(a.get_activities, []) or []),
        "features": safe(a.get_features, []),
    }

    z = zipfile.ZipFile(apk_path)
    names = z.namelist()
    by_prefix = Counter()
    for n in names:
        parts = n.split("/")
        by_prefix["/".join(parts[:2]) if len(parts) > 1 else parts[0]] += 1
    report["zip"] = {
        "entries": len(names),
        "top_dirs": by_prefix.most_common(25),
        "dex": [n for n in names if re.match(r"classes\d*\.dex$", n)],
        "native_libs": [n for n in names if n.startswith("lib/")],
        "font_files": [n for n in names if n.lower().endswith((".ttf", ".otf", ".ttc"))],
        "vector_drawables": len([n for n in names if n.startswith("res/") and n.endswith(".xml")]),
        "png_jpg_webp": len([n for n in names if n.lower().endswith((".png", ".jpg", ".jpeg", ".webp"))]),
        "flutter": any(n.startswith("assets/flutter_assets") for n in names),
    }

    if report["zip"]["flutter"]:
        fa = [n for n in names if n.startswith("assets/flutter_assets")]
        report["flutter_assets"] = fa
        for key, path in (
            ("flutter_font_manifest", "assets/flutter_assets/FontManifest.json"),
            ("flutter_asset_manifest", "assets/flutter_assets/AssetManifest.json"),
        ):
            try:
                report[key] = json.loads(z.read(path).decode("utf-8", "replace"))
            except Exception as e:
                report[key + "_error"] = repr(e)

    # ---- ARSC ----
    arsc_out = {"error": None}
    try:
        arsc = a.get_android_resources()
        pkgs = arsc.get_packages_names()
        arsc_out["packages"] = pkgs
        # 触发解析
        for p in pkgs:
            arsc.get_color_resources(p)

        all_types = Counter()
        colors = {}
        dimens = {}
        styles = {}
        strings = {}
        for p in pkgs:
            data = arsc.values.get(p, {})
            arsc_out.setdefault("locales", list(data.keys()))
            loc = data.get(DEFAULT_LOCALE, {})
            for typ, entries in loc.items():
                all_types[typ] += len(entries)
                for entry in entries:
                    if not isinstance(entry, (list, tuple)) or len(entry) < 2:
                        continue
                    name, value = entry[0], entry[1]
                    if typ == "color":
                        colors[name] = hexcolor(value) if not isinstance(value, (tuple, list)) else str(value)
                    elif typ == "dimen":
                        dimens[name] = str(value)
                    elif typ == "style":
                        styles[name] = value if isinstance(value, (dict, list)) else str(value)
                    elif typ == "string":
                        strings[name] = str(value)

        arsc_out["type_counts"] = dict(all_types.most_common())
        arsc_out["colors"] = colors
        arsc_out["dimens"] = dimens
        arsc_out["style_names"] = sorted(styles.keys())
        arsc_out["strings_count"] = len(strings)
        # 只保留可能描述 UI 文案的字符串样本
        arsc_out["strings_sample"] = dict(list(strings.items())[:600])
    except Exception as e:
        arsc_out["error"] = repr(e)

    report["arsc"] = arsc_out

    with open(os.path.join(out_dir, "report.json"), "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print(json.dumps(report["manifest"], ensure_ascii=False, indent=2))
    print("colors:", len(arsc_out.get("colors", {})),
          "dimens:", len(arsc_out.get("dimens", {})),
          "styles:", len(arsc_out.get("style_names", [])),
          "strings:", arsc_out.get("strings_count"),
          "err:", arsc_out.get("error"))
    print("entries:", report["zip"]["entries"], "flutter:", report["zip"]["flutter"],
          "fonts:", len(report["zip"]["font_files"]))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

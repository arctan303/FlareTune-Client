"""整理证据目录：截图缩放、调色板、精简后的 APK 报告、脚本归档。"""
import glob
import json
import os
import shutil

from PIL import Image

ROOT = os.path.dirname(os.path.abspath(__file__))  # .research
REPO = os.path.dirname(ROOT)
EV = os.path.join(REPO, "research", "evidence")


def shrink(src, dst, width=720):
    im = Image.open(src).convert("RGB")
    if im.width > width:
        im = im.resize((width, round(im.height * width / im.width)), Image.LANCZOS)
    os.makedirs(os.path.dirname(dst), exist_ok=True)
    im.save(dst, "JPEG", quality=86, optimize=True)
    return im.size


def main():
    # 截图
    mapping = {
        "streammusic": "out/shots/streammusic/*.png",
        "spotify": "out/shots/spotify/*.png",
        "applemusic": "out/shots/applemusic/*.png",
    }
    manifest = {}
    for app, pattern in mapping.items():
        files = sorted(glob.glob(os.path.join(ROOT, pattern)))
        items = []
        for f in files:
            name = os.path.splitext(os.path.basename(f))[0]
            dst = os.path.join(EV, "screenshots", app, name + ".jpg")
            w, h = shrink(f, dst)
            items.append({"file": f"screenshots/{app}/{name}.jpg", "w": w, "h": h})
        manifest[app] = items
        print(app, len(items))

    # 音流资产
    for f in glob.glob(os.path.join(ROOT, "out/assets/streammusic/*.png")):
        dst = os.path.join(EV, "assets", "streammusic", os.path.basename(f))
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        shutil.copy2(f, dst)

    # 调色板
    pal_dir = os.path.join(EV, "palettes")
    os.makedirs(pal_dir, exist_ok=True)
    for f in glob.glob(os.path.join(ROOT, "out/palette_*.json")):
        shutil.copy2(f, os.path.join(pal_dir, os.path.basename(f)))

    # 精简 APK 报告
    rep_dir = os.path.join(EV, "apk-reports")
    os.makedirs(rep_dir, exist_ok=True)
    for tag in ("streammusic", "spotify", "applemusic"):
        src = os.path.join(ROOT, f"out/{tag}/report.json")
        d = json.load(open(src, encoding="utf-8"))
        slim = {
            "file": d.get("file"),
            "manifest": d.get("manifest"),
            "zip": {k: v for k, v in d.get("zip", {}).items() if k != "font_files"},
            "font_files": d.get("zip", {}).get("font_files", []),
            "flutter_font_manifest": [
                f.get("family") for f in (d.get("flutter_font_manifest") or [])
            ],
            "arsc": {
                "type_counts": d.get("arsc", {}).get("type_counts"),
                "colors_count": len(d.get("arsc", {}).get("colors", {})),
                "dimens_count": len(d.get("arsc", {}).get("dimens", {})),
                "strings_count": d.get("arsc", {}).get("strings_count"),
            },
        }
        with open(os.path.join(rep_dir, f"{tag}.json"), "w", encoding="utf-8") as fh:
            json.dump(slim, fh, ensure_ascii=False, indent=2)

    with open(os.path.join(EV, "screenshots", "index.json"), "w", encoding="utf-8") as fh:
        json.dump(manifest, fh, ensure_ascii=False, indent=2)
    print("done")


if __name__ == "__main__":
    main()

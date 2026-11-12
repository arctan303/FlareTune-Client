"""从 Google Play 页面抓取应用截图（按竖屏比例过滤）。"""
import io
import json
import os
import re
import sys
import urllib.request
from collections import OrderedDict

from PIL import Image

UA = (
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"
)


def get(url, binary=False):
    req = urllib.request.Request(url, headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    return data if binary else data.decode("utf-8", "replace")


def main(pkg, out_dir, limit=10):
    os.makedirs(out_dir, exist_ok=True)
    html = get(f"https://play.google.com/store/apps/details?id={pkg}&hl=en&gl=US")
    urls = list(OrderedDict.fromkeys(re.findall(r"https://play-lh\.googleusercontent\.com/[A-Za-z0-9_\-]+", html)))
    print(f"{pkg}: {len(urls)} candidate images")
    kept = []
    for i, u in enumerate(urls):
        if len(kept) >= limit:
            break
        try:
            raw = get(u + "=w1080", binary=True)
            im = Image.open(io.BytesIO(raw))
            w, h = im.size
            if w >= 900 and h / w > 1.6:
                path = os.path.join(out_dir, f"{pkg}_{len(kept)+1:02d}_{w}x{h}.png")
                with open(path, "wb") as f:
                    f.write(raw)
                kept.append({"file": os.path.basename(path), "w": w, "h": h, "src": u})
                print("  keep", os.path.basename(path), f"{len(raw)/1024:.0f}KB")
        except Exception as e:
            print("  skip", i, repr(e)[:80])
    with open(os.path.join(out_dir, "index.json"), "w", encoding="utf-8") as f:
        json.dump({"package": pkg, "screenshots": kept}, f, ensure_ascii=False, indent=2)
    print(f"{pkg}: kept {len(kept)}")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

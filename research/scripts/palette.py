"""从截图测量主色（中位裁剪 + 量化），输出每个应用的调色统计。"""
import glob
import json
import os
import sys
from collections import Counter

from PIL import Image


def measure(path, colors=8):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    # 取中间 70% 区域，尽量避开营销边框
    im = im.crop((int(w * 0.15), int(h * 0.15), int(w * 0.85), int(h * 0.85)))
    q = im.quantize(colors=colors, method=Image.MEDIANCUT)
    pal = q.getpalette()
    counts = Counter(q.getdata())
    total = sum(counts.values())
    out = []
    for idx, cnt in counts.most_common(colors):
        r, g, b = pal[idx * 3 : idx * 3 + 3]
        out.append({"hex": f"#{r:02X}{g:02X}{b:02X}", "pct": round(cnt * 100 / total, 1)})
    return out


def main(pattern, out_path):
    files = sorted(glob.glob(pattern))
    result = {}
    for f in files:
        result[os.path.basename(f)] = measure(f)
    # 汇总
    agg = Counter()
    for pal in result.values():
        for c in pal:
            agg[c["hex"]] += c["pct"]
    total = sum(agg.values()) or 1
    summary = [{"hex": k, "pct": round(v * 100 / total, 1)} for k, v in agg.most_common(12)]
    with open(out_path, "w", encoding="utf-8") as fh:
        json.dump({"files": result, "aggregate": summary}, fh, ensure_ascii=False, indent=2)
    print(os.path.basename(out_path), "->", [f"{c['hex']} {c['pct']}%" for c in summary[:8]])


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])

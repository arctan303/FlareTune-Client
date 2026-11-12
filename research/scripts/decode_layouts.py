"""把 Spotify 布局里的 @7Fxxxxxx 资源 ID 全部反查成名字，dimens 附加真实数值。

产物：
  out/spotify_layouts_decoded/*.xml   可读布局
  out/spotify_layout_tokens.json      布局里出现的资源使用频次 + 具体值
"""
import collections
import json
import logging
import os
import re
import zipfile

logging.disable(logging.CRITICAL)
from androguard.core.apk import APK  # noqa: E402

APK_PATH = "raw/extracted/spotify/com.spotify.music.apk"
OUT = "out/spotify_layouts_decoded"
RID_RE = re.compile(r"@([0-9A-Fa-f]{8})")


def main() -> None:
    a = APK(APK_PATH)
    arsc = a.get_android_resources()
    pkg = "com.spotify.music"
    # 触发各类型解析，填充 values
    arsc.get_dimen_resources(pkg)
    arsc.get_color_resources(pkg)

    # dimens 名 -> 值（取默认 locale）
    dim_val: dict[str, str] = {}
    for loc, types in arsc.values.get(pkg, {}).items():
        for typ, entries in types.items():
            if typ != "dimen":
                continue
            for name, value in entries:
                dim_val.setdefault(name, str(value))

    cache: dict[int, str] = {}

    def name_of(rid: int) -> str:
        if rid in cache:
            return cache[rid]
        try:
            n = arsc.get_resource_xml_name(rid, pkg) or ""
        except Exception:  # noqa: BLE001
            n = ""
        n = n.lstrip("@")
        cache[rid] = n
        return n

    with zipfile.ZipFile(APK_PATH) as z:
        allx = sorted(x for x in z.namelist() if re.match(r"res/layout/.*\.xml$", x))

    from androguard.core.axml import AXMLPrinter

    os.makedirs(OUT, exist_ok=True)
    usage = collections.Counter()
    detail: dict[str, list[str]] = {}
    decoded = 0

    for path in allx:
        with zipfile.ZipFile(APK_PATH) as z:
            raw = z.read(path)
        try:
            xml = AXMLPrinter(raw).get_xml().decode("utf-8", "replace")
        except Exception:  # noqa: BLE001
            continue

        def sub(m: re.Match) -> str:
            rid = int(m.group(1), 16)
            n = name_of(rid)
            if not n:
                return m.group(0)
            usage[n] += 1
            if n.startswith("dimen/"):
                short = n.split("/", 1)[1]
                v = dim_val.get(short)
                detail.setdefault(short, [])
                if v and v not in detail[short]:
                    detail[short].append(v)
                return f"@{n}" + (f"[{v}]" if v else "")
            return f"@{n}"

        xml = RID_RE.sub(sub, xml)
        # 可读化枚举值
        xml = xml.replace('android:orientation="1"', 'android:orientation="vertical"')
        xml = xml.replace('android:orientation="0"', 'android:orientation="horizontal"')
        xml = xml.replace('android:layout_width="-1"', 'android:layout_width="match_parent"')
        xml = xml.replace('android:layout_height="-1"', 'android:layout_height="match_parent"')
        xml = xml.replace('android:layout_width="-2"', 'android:layout_width="wrap_content"')
        xml = xml.replace('android:layout_height="-2"', 'android:layout_height="wrap_content"')
        xml = re.sub(r'android:gravity="0x00000010"', 'android:gravity="center_vertical"', xml)
        xml = re.sub(r'android:gravity="0x00000001"', 'android:gravity="center_horizontal"', xml)
        xml = re.sub(r'android:layout_gravity="0x00800013"', 'android:layout_gravity="start|center_vertical"', xml)
        xml = re.sub(r"(\d+)\.000000dip", r"\1dp", xml)
        xml = re.sub(r"android:translationZ=\"16777215\.000000dp\"", 'android:translationZ="max"', xml)

        with open(os.path.join(OUT, os.path.basename(path)), "w", encoding="utf-8") as f:
            f.write(xml)
        decoded += 1

    report = {
        "layouts_decoded": decoded,
        "usage": usage.most_common(),
        "dimen_values": {k: v for k, v in sorted(detail.items())},
    }
    with open("out/spotify_layout_tokens.json", "w", encoding="utf-8") as f:
        json.dump(report, f, ensure_ascii=False, indent=2)

    print("decoded", decoded, "layouts;", len(usage), "distinct resources")
    print("\n--- 布局里出现最多的 dimens ---")
    for n, c in usage.most_common(400):
        if not n.startswith("dimen/"):
            continue
        short = n.split("/", 1)[1]
        print(f"  {c:4d}x  {short:52s} {detail.get(short, ['?'])}")


if __name__ == "__main__":
    main()

"""通用：把 APK 里任意 res XML（layout/drawable/xml/anim）反编译成可读 XML。

用法：python dump_any.py res/drawable/floating_layout_bg.xml res/xml/now_playing_bar_scene.xml
"""
import logging
import os
import re
import sys
import zipfile

logging.disable(logging.CRITICAL)
from androguard.core.apk import APK  # noqa: E402
from androguard.core.axml import AXMLPrinter  # noqa: E402

APK_PATH = "raw/extracted/spotify/com.spotify.music.apk"
OUT = "out/spotify_layouts_decoded"
RID_RE = re.compile(r"@([0-9A-Fa-f]{8})")


def main(paths: list[str]) -> None:
    a = APK(APK_PATH)
    arsc = a.get_android_resources()
    pkg = "com.spotify.music"
    arsc.get_dimen_resources(pkg)
    arsc.get_color_resources(pkg)

    dim_val: dict[str, str] = {}
    for types in arsc.values.get(pkg, {}).values():
        for typ, entries in types.items():
            if typ == "dimen":
                for name, value in entries:
                    dim_val.setdefault(name, str(value))

    def name_of(rid: int) -> str:
        try:
            return (arsc.get_resource_xml_name(rid, pkg) or "").lstrip("@")
        except Exception:  # noqa: BLE001
            return ""

    def sub(m: re.Match) -> str:
        n = name_of(int(m.group(1), 16))
        if not n:
            return m.group(0)
        if n.startswith("dimen/"):
            v = dim_val.get(n.split("/", 1)[1])
            return f"@{n}" + (f"[{v}]" if v else "")
        return f"@{n}"

    os.makedirs(OUT, exist_ok=True)
    with zipfile.ZipFile(APK_PATH) as z:
        names = set(z.namelist())
        for p in paths:
            if p not in names:
                print("MISSING", p)
                continue
            try:
                xml = AXMLPrinter(z.read(p)).get_xml().decode("utf-8", "replace")
            except Exception as e:  # noqa: BLE001
                print("FAIL", p, repr(e)[:70])
                continue
            xml = RID_RE.sub(sub, xml)
            xml = xml.replace('android:orientation="1"', 'android:orientation="vertical"')
            xml = xml.replace('android:orientation="0"', 'android:orientation="horizontal"')
            xml = xml.replace('android:layout_width="-1"', 'android:layout_width="match_parent"')
            xml = xml.replace('android:layout_height="-1"', 'android:layout_height="match_parent"')
            xml = xml.replace('android:layout_width="-2"', 'android:layout_width="wrap_content"')
            xml = xml.replace('android:layout_height="-2"', 'android:layout_height="wrap_content"')
            xml = re.sub(r'android:gravity="0x00000011"', 'android:gravity="center"', xml)
            xml = re.sub(r'android:gravity="0x00000010"', 'android:gravity="center_vertical"', xml)
            xml = re.sub(r'android:gravity="0x00000001"', 'android:gravity="center_horizontal"', xml)
            xml = re.sub(r"(\d+)\.000000dip", r"\1dp", xml)
            out = os.path.join(OUT, os.path.basename(p))
            with open(out, "w", encoding="utf-8") as f:
                f.write(xml)
            print(f"ok {p} -> {out} ({len(xml)}B)")


if __name__ == "__main__":
    main(sys.argv[1:])

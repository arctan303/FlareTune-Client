"""反编译 Apple Music 的播放器相关布局 / 动画，并把资源 ID 反查成名字 + 真实值。

用法（cwd = .research/）：
  python ../research/scripts/dump_am.py            # 默认一组关键文件
  python ../research/scripts/dump_am.py a.xml b.xml

产物：out/am_decoded/*.xml
"""
import json
import logging
import os
import re
import sys
import zipfile

logging.disable(logging.CRITICAL)
logging.getLogger("androguard").setLevel(logging.CRITICAL)
logging.getLogger("androguard.core.axml").setLevel(logging.CRITICAL)
from androguard.core.apk import APK  # noqa: E402
from androguard.core.axml import AXMLPrinter  # noqa: E402

APK_PATH = "raw/extracted/applemusic/com.apple.android.music.apk"
PKG = "com.apple.android.music"
OUT = "out/am_decoded"
RID_RE = re.compile(r"@([0-9A-Fa-f]{8})")

DEFAULT = [
    "res/layout/activity_now_playing_full_screen.xml",
    "res/layout/fragment_player_main.xml",
    "res/layout/fragment_player_lyrics_sheet.xml",
    "res/layout/fragment_static_lyrics.xml",
    "res/layout/lyrics_body.xml",
    "res/layout/lyrics_line.xml",
    "res/layout/lyrics_line_karaoke.xml",
    "res/layout/lyrics_line_static.xml",
    "res/layout/lyrics_word_karaoke.xml",
    "res/layout/lyrics_word_karaoke_bg.xml",
    "res/layout/lyrics_translation_line_karaoke.xml",
    "res/layout/lyrics_bg_translation_line_karaoke.xml",
    "res/layout/lyrics_header.xml",
    "res/layout/mini_player.xml",
    "res/layout/player.xml",
    "res/layout/music_player.xml",
    "res/layout/layout_player_button.xml",
    "res/layout/layout_player_buttons.xml",
    "res/layout/constraint_player_media_controls.xml",
    "res/layout/player_progress_seek_bar.xml",
    "res/anim/rotation_anim.xml",
    "res/anim/slide_up.xml",
    "res/anim/slide_down.xml",
    "res/anim/activity_slide_up.xml",
    "res/anim/activity_slide_down.xml",
    "res/anim/activity_fade_in.xml",
    "res/anim/activity_fade_out.xml",
    "res/anim/activity_hold.xml",
    "res/anim/share_lyrics_sheet_fade_in.xml",
]


def main() -> None:
    targets = sys.argv[1:] or DEFAULT
    a = APK(APK_PATH)
    arsc = a.get_android_resources()
    for t in ("dimen", "color", "integer", "fraction", "string", "bool", "style"):
        try:
            getattr(arsc, f"get_{t}_resources")(PKG)
        except Exception:  # noqa: BLE001
            pass

    values: dict[str, dict[str, str]] = {}
    for loc, types in arsc.values.get(PKG, {}).items():
        for typ, entries in types.items():
            d = values.setdefault(typ, {})
            for entry in entries:
                if not entry:
                    continue
                name = entry[0]
                value = entry[1] if len(entry) > 1 else None
                if value is None:
                    continue
                d.setdefault(name, str(value))

    cache: dict[int, str] = {}

    def name_of(rid: int) -> str:
        if rid in cache:
            return cache[rid]
        try:
            n = arsc.get_resource_xml_name(rid, PKG) or ""
        except Exception:  # noqa: BLE001
            n = ""
        n = n.lstrip("@")
        cache[rid] = n
        return n

    os.makedirs(OUT, exist_ok=True)
    z = zipfile.ZipFile(APK_PATH)
    names = set(z.namelist())

    for path in targets:
        if path not in names:
            print("!! missing", path)
            continue
        raw = z.read(path)
        try:
            xml = AXMLPrinter(raw).get_xml().decode("utf-8", "replace")
        except Exception as e:  # noqa: BLE001
            print("!! decode fail", path, e)
            continue

        def sub(m: re.Match) -> str:
            rid = int(m.group(1), 16)
            n = name_of(rid)
            if not n:
                return m.group(0)
            typ, _, short = n.partition("/")
            v = values.get(typ, {}).get(short)
            return f"@{n}" + (f"[{v}]" if v and typ in ("dimen", "color", "integer") else "")

        xml = RID_RE.sub(sub, xml)
        xml = xml.replace('android:orientation="1"', 'android:orientation="vertical"')
        xml = xml.replace('android:orientation="0"', 'android:orientation="horizontal"')
        for a_, b_ in (
            ('android:layout_width="-1"', 'android:layout_width="match_parent"'),
            ('android:layout_height="-1"', 'android:layout_height="match_parent"'),
            ('android:layout_width="-2"', 'android:layout_width="wrap_content"'),
            ('android:layout_height="-2"', 'android:layout_height="wrap_content"'),
        ):
            xml = xml.replace(a_, b_)
        xml = re.sub(r"(\d+)\.000000dip", r"\1dp", xml)
        xml = re.sub(r"(\d+)\.000000px", r"\1px", xml)

        out = os.path.join(OUT, os.path.basename(path))
        with open(out, "w", encoding="utf-8") as f:
            f.write(xml)
        print(f"ok {len(xml):7d}B  {path}")

    # 顺手把 dimen 里跟播放器/歌词有关的都吐出来
    keep = re.compile(r"(player|lyric|now_playing|mini|seek|artwork|blur|gradient|lyrics)", re.I)
    hits = {k: v for k, v in values.get("dimen", {}).items() if keep.search(k)}
    with open(os.path.join(OUT, "_dimens.json"), "w", encoding="utf-8") as f:
        json.dump(hits, f, ensure_ascii=False, indent=2, sort_keys=True)
    print(f"\ndimens matched: {len(hits)} -> {OUT}/_dimens.json")


if __name__ == "__main__":
    main()

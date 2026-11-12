"""解析 Apple Music 的颜色 / 尺寸 / 浮点常量。

androguard 对 complex 类型（float）会把位模式当整数吐出来，
这里按 IEEE754 float32 重新解一遍，并把 @dimen/@color 引用链解开。
"""
import json
import logging
import struct

logging.disable(logging.CRITICAL)
logging.getLogger("androguard").setLevel(logging.CRITICAL)
from androguard.core.apk import APK  # noqa: E402

APK_PATH = "raw/extracted/applemusic/com.apple.android.music.apk"
PKG = "com.apple.android.music"

KEEP = ("player", "lyrics", "now_playing", "nowplaying", "miniplayer", "mini_player",
        "seekbar", "seek_bar", "artwork", "blur", "motion", "karaoke", "vibrant",
        "gradient", "corner_radius", "control_buttons", "side_control", "badge",
        "current_player", "queue_current")


def f32(n: int):
    """把整数位模式当 float32 解；不像合法 float 就返回 None。"""
    try:
        v = struct.unpack("<f", struct.pack("<I", n & 0xFFFFFFFF))[0]
    except Exception:  # noqa: BLE001
        return None
    if v != v or abs(v) > 1e6 or abs(v) < 1e-6:
        return None
    return round(v, 6)


def main() -> None:
    a = APK(APK_PATH)
    arsc = a.get_android_resources()
    for t in ("dimen", "color", "integer", "fraction", "bool", "style", "array"):
        try:
            getattr(arsc, f"get_{t}_resources")(PKG)
        except Exception:  # noqa: BLE001
            pass

    out = {}
    for loc, types in arsc.values.get(PKG, {}).items():
        for typ, entries in types.items():
            if typ not in ("dimen", "color", "integer", "fraction"):
                continue
            d = out.setdefault(typ, {})
            for entry in entries:
                if not entry or len(entry) < 2:
                    continue
                name, value = entry[0], entry[1]
                if name in d:
                    continue
                d[name] = value

    # 引用链：dimen 指向 dimen 的情况
    def resolve(typ: str, name: str, depth: int = 0):
        v = out.get(typ, {}).get(name)
        if v is None or depth > 6:
            return v
        s = str(v)
        if s.isdigit():
            n = int(s)
            f = f32(n)
            if f is not None:
                return f"≈{f} (float32 of {n})"
            if 0x7F000000 <= n <= 0x7FFFFFFF:
                try:
                    ref = arsc.get_resource_xml_name(n, PKG) or ""
                except Exception:  # noqa: BLE001
                    ref = ""
                if ref:
                    rt, _, rs = ref.lstrip("@").partition("/")
                    return f"-> {ref} = {resolve(rt, rs, depth + 1)}"
        return s

    print("=" * 78)
    print("DIMEN")
    print("=" * 78)
    for name in sorted(out.get("dimen", {})):
        if any(k in name for k in KEEP):
            print(f"{name:52s} {resolve('dimen', name)}")

    print()
    print("=" * 78)
    print("COLOR")
    print("=" * 78)
    for name in sorted(out.get("color", {})):
        if any(k in name for k in KEEP):
            print(f"{name:52s} {resolve('color', name)}")

    print()
    print("=" * 78)
    print("INTEGER / FRACTION")
    print("=" * 78)
    for typ in ("integer", "fraction"):
        for name in sorted(out.get(typ, {})):
            if any(k in name for k in KEEP + ("lyrics", "player", "rotation", "slide")):
                print(f"{typ}/{name:46s} {resolve(typ, name)}")

    with open("out/am_decoded/_resolved.json", "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=2, sort_keys=True)


if __name__ == "__main__":
    main()

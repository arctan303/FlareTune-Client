"""按类/方法 dump Apple Music 的 dalvik 指令（只看常量与方法调用，够读逻辑了）。

用法（cwd = .research/）：
  python ../research/scripts/dump_am_code.py <dex> <类名> [方法名...]
例：
  python ../research/scripts/dump_am_code.py classes2.dex \
      Lcom/apple/android/music/player/LyricsBackgroundLayerView; b onDraw
"""
import logging
import sys
import zipfile

logging.disable(logging.CRITICAL)
logging.getLogger("androguard").setLevel(logging.CRITICAL)
from androguard.core.dex import DEX  # noqa: E402

APK_PATH = "raw/extracted/applemusic/com.apple.android.music.apk"


def main() -> None:
    dex_name = sys.argv[1]
    cls = sys.argv[2]
    wanted = set(sys.argv[3:])

    z = zipfile.ZipFile(APK_PATH)
    d = DEX(z.read(dex_name))
    c = d.get_class(cls)
    if c is None:
        print("class not found")
        return

    for m in c.get_methods():
        name = m.get_name()
        if wanted and name not in wanted:
            continue
        code = m.get_code()
        print(f"\n{'=' * 76}\n{name}{m.get_descriptor()}")
        if code is None:
            print("  (no code)")
            continue
        print(f"  registers={code.get_registers_size()} ins={code.get_ins_size()}")
        for i, ins in enumerate(code.get_bc().get_instructions()):
            op = ins.get_name()
            out = ins.get_output()
            # 只保留有信息量的：常量、方法调用、字段访问、分支
            if op.startswith(("const", "invoke", "sget", "sput", "iget", "iput",
                              "new-instance", "new-array", "filled-new-array",
                              "if-", "goto", "return", "cmp", "aget", "aput",
                              "check-cast", "instance-of", "add-", "sub-", "mul-",
                              "div-", "int-to-", "float-to-", "neg-", "and-", "or-")):
                print(f"    [{i:04d}] {op:22s} {out}")


if __name__ == "__main__":
    main()

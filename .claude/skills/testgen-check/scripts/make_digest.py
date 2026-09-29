"""フレーム監査用のケース別ダイジェスト（ケースNo・名称・期待結果）を仕様書ダンプから生成する。"""
import re
import sys
from pathlib import Path

spec_dump = Path(sys.argv[1])
out = Path(sys.argv[2])

lines = spec_dump.read_text(encoding="utf-8").splitlines()
blocks = []
for line in lines:
    m = re.match(r"^A\d+:(\d+-\d+) \|", line)
    if not m:
        continue
    no = m.group(1)
    cell = {}
    for part in line.split(" | "):
        km = re.match(r"^([A-I])\d+:(.*)$", part)
        if km:
            cell[km.group(1)] = km.group(2)
    name = cell.get("C", "")
    expected = cell.get("F", "").replace("⏎", "\n  - ")
    blocks.append(f"## No.{no} {name}\n期待する結果:\n  - {expected}\n")
out.write_text("\n".join(blocks), encoding="utf-8")
print("digest cases:", len(blocks), "->", out)

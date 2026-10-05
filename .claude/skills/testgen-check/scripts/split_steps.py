"""steps.json をフレーム監査用にケース別テキストへ分割する。"""
import json
import sys
from pathlib import Path

evidence = Path(sys.argv[1])
out_dir = Path(sys.argv[2])
out_dir.mkdir(parents=True, exist_ok=True)

steps = json.loads((evidence / "steps.json").read_text(encoding="utf-8"))
for case in steps.get("cases", []):
    no = case.get("no")
    lines = [f"# No.{no} {case.get('name', '')}", ""]
    for s in case.get("steps", []):
        kind = s.get("kind")
        verify = s.get("verify") or ""
        desc = s.get("desc") or ""
        frame = s.get("frame") or "-"
        attach = s.get("attachFrame")
        exp = s.get("expected")
        act = s.get("actual")
        status = s.get("status") or ""
        line = f"[{s.get('seq')}] {kind}{('/' + verify) if verify else ''} {status} frame={frame}"
        if attach:
            line += f" attach={attach}"
        lines.append(line)
        lines.append(f"    説明: {desc}")
        if exp is not None or act is not None:
            lines.append(f"    期待: {exp}")
            lines.append(f"    実測: {act}")
        note = s.get("note")
        if note:
            lines.append(f"    注記: {note}")
    (out_dir / f"{no}.txt").write_text("\n".join(lines), encoding="utf-8")
print("split done:", len(steps.get("cases", [])))

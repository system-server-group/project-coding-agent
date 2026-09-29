"""証跡V2の機械突合（testgen-check 手順2）。

steps.json / results.json / フレーム実体 / 仕様書ダンプを機械照合し、結果をテキストで出力する。
判定はしない（事実の列挙）。
"""
import json
import re
import sys
from pathlib import Path

evidence = Path(sys.argv[1])
spec_dump = Path(sys.argv[2])

steps = json.loads((evidence / "steps.json").read_text(encoding="utf-8"))
results = json.loads((evidence / "results.json").read_text(encoding="utf-8"))

print("=== results.json ===")
print("entries:", len(results))
by_status = {}
for r in results:
    by_status.setdefault(r.get("status"), []).append(r.get("no"))
for st, nos in sorted(by_status.items()):
    print(f"  {st}: {len(nos)}", ("=> " + ", ".join(nos)) if st != "pass" else "")
nos = [r.get("no") for r in results]
dup = {n for n in nos if nos.count(n) > 1}
print("  No重複:", dup if dup else "なし")

print("=== spec cases vs results ===")
spec_cases = []
for line in spec_dump.read_text(encoding="utf-8").splitlines():
    m = re.match(r"^A\d+:(\d+-\d+) \|", line)
    if m:
        spec_cases.append(m.group(1))
print("spec cases:", len(spec_cases))
missing = [c for c in spec_cases if c not in nos]
extra = [n for n in nos if n not in spec_cases]
print("  resultsに無い仕様ケース:", missing if missing else "なし")
print("  仕様に無いresults:", extra if extra else "なし")

print("=== steps.json ===")
cases = steps.get("cases", [])
print("cases:", len(cases), "feature:", steps.get("feature"), "target:", steps.get("target"))

jpgs = {p.name for p in evidence.glob("*.jpg")}
referenced = set()
issues = []
machine_counts = {}
verify_counts = {}
frame_counts = {}
for case in cases:
    no = case.get("no")
    case_steps = case.get("steps", [])
    verify_counts[no] = sum(1 for s in case_steps if s.get("kind") == "verify")
    machine_counts[no] = sum(1 for s in case_steps if s.get("kind") == "machine")
    frames_of_case = set()
    prev_kinds = []
    for i, s in enumerate(case_steps):
        if not s.get("ts"):
            issues.append(f"{no}: step[{i}] ts なし")
        for key in ("frame", "attachFrame"):
            f = s.get(key)
            if f:
                referenced.add(f)
                frames_of_case.add(f)
                if f not in jpgs:
                    issues.append(f"{no}: step[{i}] {key}={f} が実在しない")
        if s.get("kind") == "machine" and (s.get("expected") is None or s.get("actual") is None):
            issues.append(f"{no}: machine step[{i}] に期待/実測が無い")
        # 遷移部品の構造: hidden/cleared verify の前に visible verify と op があること
        if s.get("kind") == "verify" and s.get("verify") in ("hidden", "cleared"):
            window = case_steps[max(0, i - 6):i]
            has_visible = any(w.get("verify") == "visible" for w in window)
            has_op = any(w.get("kind") == "op" for w in window)
            if not (has_visible and has_op):
                issues.append(f"{no}: step[{i}] {s.get('verify')} の直前に visible+op が揃っていない")
    frame_counts[no] = len(frames_of_case)

orphans = sorted(jpgs - referenced)
print("jpg総数:", len(jpgs), "参照済:", len(referenced), "孤児:", len(orphans))
for o in orphans[:10]:
    print("  孤児:", o)

print("=== 4-5 送信失敗warnログ ===")
for case in cases:
    if case.get("no") == "4-5":
        logs = case.get("logs", [])
        hits = [l for l in logs if l.get("level") in ("WARN", "ERROR") and "app" in (l.get("src") or "")]
        print("  app WARN/ERROR:", len(hits))
        for l in hits[:5]:
            print("   ", l.get("ts"), l.get("logger"), str(l.get("message"))[:90])

print("=== 機械検証ステップ数（ケース別・0以外） ===")
print({k: v for k, v in machine_counts.items() if v})
print("=== verifyステップ0のケース ===")
zero = [k for k, v in verify_counts.items() if v == 0]
print(zero if zero else "なし")

total_bytes = sum(p.stat().st_size for p in evidence.glob("*.jpg"))
print("=== 容量 ===")
print(f"jpg合計: {total_bytes/1024/1024:.1f}MB / steps.json: {(evidence/'steps.json').stat().st_size/1024:.0f}KB")
print("フレーム最多ケース:", sorted(frame_counts.items(), key=lambda kv: -kv[1])[:5])

print("=== 指摘 ===")
if issues:
    for msg in issues:
        print(" -", msg)
else:
    print("なし")

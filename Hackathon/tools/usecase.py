"""Writes <project>/docs/USECASE.md from cases/All_Cases.docx.   python tools/usecase.py <case number> <project folder>"""
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
number, project = int(sys.argv[1]), sys.argv[2]

xml = zipfile.ZipFile(os.path.join(ROOT, "cases", "All_Cases.docx")).read("word/document.xml").decode("utf8")
lines = ["".join(re.findall(r"<w:t[^>]*>([^<]*)</w:t>", p)) for p in re.findall(r"<w:p[ >].*?</w:p>", xml, re.S)]

cases, current = {}, None
for line in lines[1:]:
    m = re.match(r"^(\d+)\. (.+)$", line)
    if m and int(m.group(1)) <= 20 and (current is None or int(m.group(1)) == current + 1):
        current = int(m.group(1))
        cases[current] = [m.group(2)]
    elif current:
        cases[current].append(line)

text = cases[number]
out = f"# Use case {number}: {text[0]}\n\nSource: `cases/All_Cases.docx`\n\n"
for line in text[1:]:
    if not line.strip():
        continue
    m = re.match(r"^(Business Problem|Users / Roles|Business Rules / Validation|Optional Bonus|7-Hour Scope Guidance|Suggested JPA Entities):\s*(.*)", line)
    if m:
        out += f"\n**{m.group(1)}:** {m.group(2)}\n\n"
    elif line.strip() == "Core Features:":
        out += "**Core Features:**\n\n"
    else:
        out += f"- {line.strip()}\n"
out = re.sub(r"\n{3,}", "\n\n", out)

path = os.path.join(ROOT, project, "docs", "USECASE.md")
os.makedirs(os.path.dirname(path), exist_ok=True)
with open(path, "w", encoding="utf-8", newline="\n") as f:
    f.write(out)
print("wrote", path)

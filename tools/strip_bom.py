"""Strip a UTF-8 BOM from source files (PowerShell's Set-Content adds one)."""
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
EXTS = {'.kt', '.kts', '.xml', '.md', '.yml', '.yaml', '.py', '.ps1', '.properties', '.gitignore', '.gitattributes'}

BOM = b'\xef\xbb\xbf'
fixed = []

for base, dirs, names in os.walk(ROOT):
    dirs[:] = [d for d in dirs if d not in {'.git', 'build', '.gradle', '__pycache__'}]
    for name in names:
        path = os.path.join(base, name)
        ext = os.path.splitext(name)[1].lower()
        if ext not in EXTS and name not in {'.gitignore', '.gitattributes'}:
            continue
        with open(path, 'rb') as fh:
            data = fh.read()
        if data.startswith(BOM):
            with open(path, 'wb') as fh:
                fh.write(data[len(BOM):])
            fixed.append(os.path.relpath(path, ROOT))

print(f'stripped BOM from {len(fixed)} file(s)')
for f in fixed:
    print('  -', f)

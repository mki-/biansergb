"""
selfcheck_kotlin.py — poor-man's sanity check when no Kotlin compiler is available.

Verifies, without needing a JDK:
  1. bracket balance per file (strings/comments stripped)
  2. every `import com.juzhen.<pkg>.<Name>` actually has a matching top-level
     declaration in some file of package `<pkg>`
  3. every wildcard import points at a package that exists

It cannot type-check; it only catches typos and missing declarations.
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

DECL = re.compile(
    r'^(?:@\w+(?:\([^)]*\))?\s*)*'
    r'(?:public|internal|private|protected|expect|actual|inline|value|data|sealed|abstract|open|annotation|enum|companion|fun|suspend|operator|infix|tailrec|external|const|lateinit|override|@JvmStatic\s)*\s*'
    r'(class|interface|object|fun|val|var|typealias)\s+'
    r'(?:interface\s+)?'
    r'([A-Za-z_][A-Za-z0-9_]*)',
)


def strip_code(text: str) -> str:
    """Remove comments and string literals so bracket counting is meaningful."""
    out = []
    i = 0
    n = len(text)
    while i < n:
        c = text[i]
        if c == '/' and i + 1 < n and text[i + 1] == '/':
            while i < n and text[i] != '\n':
                i += 1
        elif c == '/' and i + 1 < n and text[i + 1] == '*':
            i += 2
            while i + 1 < n and not (text[i] == '*' and text[i + 1] == '/'):
                i += 1
            i += 2
        elif c == '"':
            # triple-quoted
            if text.startswith('"""', i):
                i += 3
                while i < n and not text.startswith('"""', i):
                    i += 1
                i += 3
            else:
                i += 1
                while i < n:
                    if text[i] == '\\':
                        i += 2
                        continue
                    if text[i] == '"':
                        i += 1
                        break
                    if text[i] == '\n':
                        break
                    i += 1
        elif c == "'":
            i += 1
            while i < n:
                if text[i] == '\\':
                    i += 2
                    continue
                if text[i] == "'":
                    i += 1
                    break
                i += 1
        else:
            out.append(c)
            i += 1
    return ''.join(out)


def main() -> int:
    packages: dict[str, set[str]] = {}
    files: dict[str, str] = {}

    for base, _dirs, names in os.walk(os.path.join(ROOT, 'app')):
        for name in names:
            if name.endswith('.kt'):
                files[os.path.join(base, name)] = 'app'
    for base, _dirs, names in os.walk(os.path.join(ROOT, 'protocol')):
        for name in names:
            if name.endswith('.kt'):
                files[os.path.join(base, name)] = 'protocol'

    problems: list[str] = []

    # ---- pass 1: packages + declarations + bracket balance
    for path in files:
        text = open(path, encoding='utf-8').read()
        rel = os.path.relpath(path, ROOT)

        m = re.search(r'^package\s+([\w.]+)', text, re.MULTILINE)
        pkg = m.group(1) if m else ''
        packages.setdefault(pkg, set())

        code = strip_code(text)
        for opener, closer in (('{', '}'), ('(', ')'), ('[', ']')):
            if code.count(opener) != code.count(closer):
                problems.append(
                    f'{rel}: unbalanced {opener}{closer} '
                    f'({code.count(opener)} vs {code.count(closer)})'
                )

        for line in text.splitlines():
            line = line.strip()
            if not line or line.startswith(('//', '*', '/*', '@')):
                continue
            if line.startswith('import ') or line.startswith('package '):
                continue
            dm = DECL.match(line)
            if dm:
                kind, decl_name = dm.group(1), dm.group(2)
                if kind == 'val' and '=' not in line and '(' in line:
                    continue
                packages[pkg].add(decl_name)
            # extension functions: `fun Rgb.toComposeColor(...)` — the generic
            # DECL regex would capture the receiver type instead of the name.
            ext = re.search(r'\bfun\s+(?:<[^>]*>\s*)?[\w.]+\.([A-Za-z_]\w*)\s*[<(]', line)
            if ext:
                packages[pkg].add(ext.group(1))
            # enum entries declared as `NAME(...)` inside enum bodies are not needed

    # ---- pass 2: imports resolve
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding='utf-8').read()
        for imp in re.findall(r'^import\s+([\w.*]+)', text, re.MULTILINE):
            if not imp.startswith('com.juzhen.'):
                continue
            if imp.endswith('.*'):
                pkg = imp[:-2]
                if pkg not in packages:
                    problems.append(f'{rel}: wildcard import of unknown package {pkg}')
                continue
            pkg, _, name = imp.rpartition('.')
            # BuildConfig and R are generated by the Android Gradle plugin, not source.
            if name in ('BuildConfig', 'R'):
                continue
            if pkg not in packages:
                problems.append(f'{rel}: import from unknown package {pkg} ({name})')
            elif name not in packages[pkg]:
                problems.append(f'{rel}: imported `{name}` not declared in {pkg}')

    # ---- pass 3: types that some file imports but this file forgot to import
    # Catches the classic "forgot import androidx.compose.foundation.layout.Box" bug,
    # which pass 1 (brackets) and pass 2 (com.juzhen imports) both miss.
    # Strategy: only consider names that appear as an `import` somewhere in the repo,
    # which keeps kotlin-default imports (ArrayList, Charsets, …) and enum entries out.
    api_names: set[str] = set()
    imports_by_file: dict[str, set[str]] = {}
    for path in files:
        text = open(path, encoding='utf-8').read()
        names = set()
        for imp in re.findall(r'^import\s+([\w.*]+)', text, re.MULTILINE):
            if imp.endswith('.*'):
                continue
            names.add(imp.rpartition('.')[2])
        imports_by_file[path] = names
        api_names |= names

    ident_re = re.compile(r'(?<![.\w])[A-Z][A-Za-z0-9_]*')
    for path in files:
        rel = os.path.relpath(path, ROOT)
        text = open(path, encoding='utf-8').read()
        code = strip_code(text)
        imported = imports_by_file[path]

        declared_here = set(
            re.findall(r'\b(?:class|interface|object|typealias)\s+([A-Za-z_]\w*)', text)
        )
        declared_here |= set(re.findall(r'\b(?:const\s+)?va[lr]\s+([A-Za-z_]\w*)', text))
        declared_here |= set(
            re.findall(r'\bfun\s+(?:<[^>]*>\s*)?(?:[\w.]+\.)?([A-Za-z_]\w*)\s*[<(]', text)
        )
        for em in re.finditer(r'enum\s+class\s+\w+[^{]*\{([^}]*)', text):
            for tok in re.split(r'[,\n]', em.group(1)):
                mm = re.match(r'\s*([A-Z][A-Za-z0-9_]*)', tok)
                if mm:
                    declared_here.add(mm.group(1))

        m = re.search(r'^package\s+([\w.]+)', text, re.MULTILINE)
        pkg = m.group(1) if m else ''
        same_pkg = packages.get(pkg, set())

        for ident in sorted(set(ident_re.findall(code))):
            if ident not in api_names or ident in imported:
                continue
            if ident in declared_here or ident in same_pkg:
                continue
            problems.append(f'{rel}: `{ident}` used but not imported/declared')

    # ---- pass 4: report
    print(f'kotlin files scanned: {len(files)}')
    print(f'packages: {len(packages)}')
    if problems:
        print(f'\n{len(problems)} problem(s):')
        for p in problems:
            print('  -', p)
        return 1
    print('\nOK: brackets balanced, all com.juzhen.* imports resolve')
    return 0

if __name__ == '__main__':
    sys.exit(main())

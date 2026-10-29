"""
check_resources.py — verifies that every resource reference in the Android module
actually resolves, without needing aapt.

Checks:
  * res/**/*.xml + AndroidManifest.xml: @drawable/@layout/@xml/@string/@style refs
  * layouts: @id/... references must have a matching @+id/... declaration
  * Kotlin: R.id / R.layout / R.string / R.drawable / R.xml constants

This catches the widget-specific class of bug where a layout is written by hand and
an id, string or drawable is misspelled — the compiler would only complain at build
time, which for this project happens in CI.
"""

from __future__ import annotations

import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MAIN = os.path.join(ROOT, 'app', 'src', 'main')
RES = os.path.join(MAIN, 'res')

SKIP_PREFIX = ('android:', '^', '?attr', '@null', '@empty')


def basenames(*dirs: str) -> set[str]:
    names: set[str] = set()
    for d in dirs:
        if not os.path.isdir(d):
            continue
        for _base, _sub, files in os.walk(d):
            for f in files:
                names.add(os.path.splitext(f)[0])
    return names


def main() -> int:
    layouts = basenames(*(os.path.join(RES, d) for d in os.listdir(RES)
                          if d == 'layout' or d.startswith('layout-')))
    drawables = basenames(*(os.path.join(RES, d) for d in os.listdir(RES)
                            if d == 'drawable' or d.startswith('drawable-')))
    xmls = basenames(*(os.path.join(RES, d) for d in os.listdir(RES)
                       if d == 'xml' or d.startswith('xml-')))
    anims = basenames(*(os.path.join(RES, d) for d in os.listdir(RES)
                        if d == 'anim' or d.startswith('anim-')))

    strings: set[str] = set()
    styles: set[str] = set()
    colors: set[str] = set()
    for d in os.listdir(RES):
        if not d.startswith('values'):
            continue
        base = os.path.join(RES, d)
        for f in os.listdir(base):
            if not f.endswith('.xml'):
                continue
            text = open(os.path.join(base, f), encoding='utf-8').read()
            strings |= set(re.findall(r'<string\s+name="([^"]+)"', text))
            styles |= set(re.findall(r'<style\s+name="([^"]+)"', text))
            colors |= set(re.findall(r'<color\s+name="([^"]+)"', text))

    ids: set[str] = set()
    xml_files: list[str] = []
    for d in os.listdir(RES):
        base = os.path.join(RES, d)
        if not os.path.isdir(base):
            continue
        for f in os.listdir(base):
            if f.endswith('.xml'):
                p = os.path.join(base, f)
                xml_files.append(p)
                ids |= set(re.findall(r'@\+id/([A-Za-z0-9_]+)',
                                      open(p, encoding='utf-8').read()))

    problems: list[str] = []

    # ---- XML references
    xml_files.append(os.path.join(MAIN, 'AndroidManifest.xml'))
    tables = {
        'drawable': drawables,
        'layout': layouts,
        'xml': xmls,
        'string': strings,
        'style': styles,
        'color': colors,
        'anim': anims,
    }
    for path in xml_files:
        if not os.path.exists(path):
            continue
        text = open(path, encoding='utf-8').read()
        rel = os.path.relpath(path, ROOT)

        for kind, name in re.findall(r'@(drawable|layout|xml|string|style|color|anim)/([A-Za-z0-9_.]+)', text):
            if name.startswith('android'):
                continue
            if name not in tables[kind]:
                problems.append(f'{rel}: missing @{kind}/{name}')

        for name in re.findall(r'(?<!\+)@id/([A-Za-z0-9_]+)', text):
            if name not in ids:
                problems.append(f'{rel}: @id/{name} has no @+id/{name} declaration')

    # ---- Kotlin R.* references
    kt_files: list[str] = []
    for base, _sub, files in os.walk(os.path.join(ROOT, 'app', 'src')):
        for f in files:
            if f.endswith('.kt'):
                kt_files.append(os.path.join(base, f))

    for path in kt_files:
        text = open(path, encoding='utf-8').read()
        rel = os.path.relpath(path, ROOT)
        for kind, name in re.findall(r'\bR\.(id|layout|string|drawable|xml|color|style|anim)\.([A-Za-z0-9_]+)', text):
            if kind == 'id' and name not in ids:
                problems.append(f'{rel}: R.id.{name} is not declared in any layout')
            elif kind in tables and name not in tables[kind]:
                problems.append(f'{rel}: R.{kind}.{name} does not exist')

    print(f'layouts={len(layouts)} drawables={len(drawables)} xml={len(xmls)} '
          f'strings={len(strings)} ids={len(ids)}')
    print(f'xml files checked={len(xml_files)} kotlin files checked={len(kt_files)}')

    if problems:
        print(f'\n{len(problems)} problem(s):')
        for p in problems:
            print('  -', p)
        return 1
    print('\nOK: all resource references resolve')
    return 0


if __name__ == '__main__':
    sys.exit(main())

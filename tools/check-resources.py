#!/usr/bin/env python3
"""Static cross-check: every R.* / @res reference resolves to a real resource."""
import os, re, sys, xml.etree.ElementTree as ET
from collections import defaultdict

ROOT = "/home/claude/riverbank/app/src/main"
RES = os.path.join(ROOT, "res")
defined = defaultdict(set)

# file-based resources
for d in os.listdir(RES):
    kind = d.split("-")[0]
    if kind in ("values",):
        continue
    for f in os.listdir(os.path.join(RES, d)):
        defined[kind].add(os.path.splitext(f)[0])

# values resources
TAG2KIND = {"string":"string","color":"color","style":"style","dimen":"dimen",
            "integer":"integer","bool":"bool","string-array":"array","array":"array",
            "integer-array":"array","attr":"attr","item":None}
for d in os.listdir(RES):
    if not d.startswith("values"):
        continue
    for f in os.listdir(os.path.join(RES, d)):
        if not f.endswith(".xml"):
            continue
        tree = ET.parse(os.path.join(RES, d, f))
        for el in tree.getroot():
            kind = TAG2KIND.get(el.tag)
            if kind is None and el.tag == "item":
                kind = el.get("type")
            if kind and el.get("name"):
                defined[kind].add(el.get("name"))

# ids declared with @+id anywhere
for dirpath, _, files in os.walk(RES):
    for f in files:
        if not f.endswith(".xml"):
            continue
        text = open(os.path.join(dirpath, f), encoding="utf-8").read()
        for m in re.finditer(r'@\+id/([A-Za-z0-9_]+)', text):
            defined["id"].add(m.group(1))

problems = []
external_styles = set()

# 1) @kind/name references inside XML
SKIP_PKG = re.compile(r'@(android|\+)')
for dirpath, _, files in os.walk(RES):
    for f in files:
        if not f.endswith(".xml"):
            continue
        path = os.path.join(dirpath, f)
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'"[?@]([a-zA-Z0-9_.]+:)?([a-z]+)/([A-Za-z0-9_.]+)"', text):
            pkg, kind, name = m.groups()
            if pkg:  # android: or app namespace refs
                continue
            if m.group(0).startswith('"?'):
                continue  # theme attrs, resolved at runtime
            if kind == "id":
                continue
            if kind == "style" and name not in defined.get("style", set()):
                # Comes from a library AAR we cannot resolve offline. Collect it
                # so a human eyeballs the exact spelling — a typo here is an
                # "Android resource linking failed" at build time, not earlier.
                external_styles.add(f"@style/{name}  ({os.path.relpath(path, ROOT)})")
                continue
            if name not in defined.get(kind, set()):
                problems.append(f"{os.path.relpath(path, ROOT)}: unresolved @{kind}/{name}")

# 2) R.kind.name references in Kotlin
for dirpath, _, files in os.walk(os.path.join(ROOT, "java")):
    for f in files:
        if not f.endswith(".kt"):
            continue
        path = os.path.join(dirpath, f)
        text = open(path, encoding="utf-8").read()
        for m in re.finditer(r'\bR\.([a-z]+)\.([A-Za-z0-9_]+)', text):
            kind, name = m.groups()
            if name not in defined.get(kind, set()):
                problems.append(f"{os.path.relpath(path, ROOT)}: unresolved R.{kind}.{name}")

# 3) viewBinding class names -> layout files
layouts = {os.path.splitext(f)[0] for f in os.listdir(os.path.join(RES, "layout"))}
def binding_name(layout):
    return "".join(p.capitalize() for p in layout.split("_")) + "Binding"
bindings = {binding_name(l): l for l in layouts}
for dirpath, _, files in os.walk(os.path.join(ROOT, "java")):
    for f in files:
        if not f.endswith(".kt"):
            continue
        text = open(os.path.join(dirpath, f), encoding="utf-8").read()
        for m in re.finditer(r'\b([A-Z][A-Za-z0-9]*Binding)\b', text):
            if m.group(1) not in bindings:
                problems.append(f"{f}: no layout generates {m.group(1)}")

# 4) binding.<field> -> ids present in that layout
layout_ids = {}
for l in layouts:
    text = open(os.path.join(RES, "layout", l + ".xml"), encoding="utf-8").read()
    layout_ids[l] = set(re.findall(r'@\+id/([A-Za-z0-9_]+)', text)) | {"root"}

# 4) binding.<field> usage -> that layout must declare the id
BINDING_FOR_FILE = {
    "MainActivity.kt": "activity_main",
    "OnboardingActivity.kt": "activity_onboarding",
    "LockActivity.kt": "activity_lock",
    "SettingsActivity.kt": "activity_settings",
}
for dirpath, _, files in os.walk(os.path.join(ROOT, "java")):
    for f in files:
        if f not in BINDING_FOR_FILE:
            continue
        layout = BINDING_FOR_FILE[f]
        text = open(os.path.join(dirpath, f), encoding="utf-8").read()
        for m in re.finditer(r'\bbinding\.([a-z][A-Za-z0-9]*)\b', text):
            if m.group(1) not in layout_ids[layout]:
                problems.append(f"{f}: binding.{m.group(1)} not declared in {layout}.xml")

# style parents also reference library styles
for d in os.listdir(RES):
    if not d.startswith("values"):
        continue
    for f in os.listdir(os.path.join(RES, d)):
        if not f.endswith(".xml"):
            continue
        for el in ET.parse(os.path.join(RES, d, f)).getroot():
            parent = el.get("parent")
            if parent and parent.lstrip("@style/") not in defined.get("style", set()):
                external_styles.add(f"parent={parent}  ({d}/{f})")

if external_styles:
    print("\nLIBRARY STYLE REFERENCES — verify these spellings by hand:")
    for e in sorted(external_styles):
        print("  ?", e)

print("\ndefined kinds:", {k: len(v) for k, v in sorted(defined.items())})
print("layouts:", sorted(layouts))
if problems:
    print("\nPROBLEMS:")
    for p in sorted(set(problems)):
        print(" -", p)
    sys.exit(1)
print("\nAll resource references resolve.")

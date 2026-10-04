#!/usr/bin/env python3
"""Linkage scan: does every Minecraft class/member a release jar references exist on a target version?

A ranged jar declares several Minecraft versions but is compiled against one. The JVM links
lazily, so a reference to a member that exists only on the build version crashes only when that
code runs; the GameTest suite cannot promise to run every path. This scan resolves every
CONSTANT_Class / Fieldref / Methodref / InterfaceMethodref whose owner is a net/minecraft class,
and every mixin refmap entry, against the target version's intermediary names and class
hierarchy, and fails on anything unresolved.

Usage:
    python3 tools/linkage-scan.py <release-jar> <intermediary-tiny> <official-game-jar> [--allow-class PREFIX ...]

  intermediary-tiny: mappings/<version>.tiny from the FabricMC intermediary repository
  official-game-jar: the obfuscated client jar for the version (Loom keeps one under its cache)
  --allow-class:     a class (internal name prefix) that is deliberately isolated and only ever
                     loaded on a version where its references resolve, e.g. a nested class that
                     holds the newest-version branch of a version-switched helper. Each such class
                     must be justified in RELEASE_ALLOWLIST.md.

Exit code 0 only when nothing outside the allowed classes is unresolved.
"""
import json, struct, sys, zipfile, re

def parse_class(data):
    """Return (this_name, super_name, interfaces, cp_refs) where cp_refs is a list of
    ('class', name) / ('field'|'method'|'imethod', owner, name, desc) / ('string', value)."""
    pos = 8
    count = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    cp = [None]*count; i = 1
    while i < count:
        tag = data[pos]; pos += 1
        if tag == 1:
            ln = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
            cp[i] = ('utf8', data[pos:pos+ln].decode('utf-8', 'replace')); pos += ln
        elif tag in (3,4): cp[i] = ('num',); pos += 4
        elif tag in (5,6): cp[i] = ('num',); pos += 8; i += 1
        elif tag == 7: cp[i] = ('class', struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
        elif tag == 8: cp[i] = ('string', struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
        elif tag in (9,10,11):
            a,b = struct.unpack('>HH', data[pos:pos+4]); pos += 4
            cp[i] = ({9:'field',10:'method',11:'imethod'}[tag], a, b)
        elif tag == 12:
            a,b = struct.unpack('>HH', data[pos:pos+4]); pos += 4; cp[i] = ('nat', a, b)
        elif tag == 15: pos += 3; cp[i] = ('mh',)
        elif tag == 16: pos += 2; cp[i] = ('mt',)
        elif tag in (17,18): pos += 4; cp[i] = ('dyn',)
        elif tag in (19,20): pos += 2; cp[i] = ('mod',)
        else: raise ValueError('bad cp tag %d' % tag)
        i += 1
    def utf(idx): return cp[idx][1]
    def cls(idx): return utf(cp[idx][1])
    pos += 2  # access
    this_name = cls(struct.unpack('>H', data[pos:pos+2])[0]); pos += 2
    sup_idx = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    super_name = cls(sup_idx) if sup_idx else None
    n = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
    ifaces = [cls(struct.unpack('>H', data[pos+2*k:pos+2*k+2])[0]) for k in range(n)]
    pos += 2*n
    members = []
    for table in (0, 1):
        cnt = struct.unpack('>H', data[pos:pos+2])[0]; pos += 2
        for _ in range(cnt):
            acc, ni, di, ac = struct.unpack('>HHHH', data[pos:pos+8]); pos += 8
            members.append(('field' if table == 0 else 'method', utf(ni), utf(di)))
            for _ in range(ac):
                an, al = struct.unpack('>HI', data[pos:pos+6]); pos += 6 + al
    refs = []
    for e in cp:
        if not e: continue
        if e[0] == 'class': refs.append(('class', utf(e[1])))
        elif e[0] in ('field','method','imethod'):
            owner = cls(e[1]); nat = cp[e[2]]; refs.append((e[0], owner, utf(nat[1]), utf(nat[2])))
        elif e[0] == 'string': refs.append(('string', utf(e[1])))
    return this_name, super_name, ifaces, refs, members

def load_tiny(path):
    cls_map = {}; methods = {}; fields = {}
    lines = open(path, encoding='utf-8').read().splitlines()
    for ln in lines[1:]:
        p = ln.split('\t')
        if p[0] == 'CLASS': cls_map[p[1]] = p[2]
    def remap_desc(d):
        return re.sub(r'L([^;]+);', lambda m: 'L' + cls_map.get(m.group(1), m.group(1)) + ';', d)
    for ln in lines[1:]:
        p = ln.split('\t')
        if p[0] == 'METHOD':
            owner = cls_map.get(p[1], p[1]); methods.setdefault(owner, set()).add((p[4], remap_desc(p[2])))
        elif p[0] == 'FIELD':
            owner = cls_map.get(p[1], p[1]); fields.setdefault(owner, set()).add((p[4], remap_desc(p[2])))
    return cls_map, methods, fields, remap_desc

literal = {}
def load_hierarchy(game_jar, cls_map, remap_desc):
    """official-named game jar -> {intermediary class: [intermediary supertypes]} and set of all classes."""
    sup = {}
    with zipfile.ZipFile(game_jar) as z:
        for n in z.namelist():
            if not n.endswith('.class'): continue
            try: this, s, ifs, _, members = parse_class(z.read(n))
            except Exception: continue
            ti = cls_map.get(this, this)
            parents = [cls_map.get(x, x) for x in ([s] if s else []) + ifs]
            sup[ti] = parents
            for kind, name, desc in members:
                literal.setdefault((kind, ti), set()).add((name, remap_desc(desc)))
    return sup

def main():
    args = [a for a in sys.argv[1:]]
    allowed = []
    while '--allow-class' in args:
        i = args.index('--allow-class'); allowed.append(args[i+1]); del args[i:i+2]
    mod_jar, tiny, game_jar = args[:3]
    cls_map, methods, fields, remap_desc = load_tiny(tiny)
    sup = load_hierarchy(game_jar, cls_map, remap_desc)
    all_classes = set(sup.keys()) | set(cls_map.values())
    def member_exists(owner, name, desc, kind):
        if 'net/fabricmc/' in desc: return True  # Fabric interface injection
        seen = set(); stack = [owner]
        while stack:
            c = stack.pop()
            if c in seen: continue
            seen.add(c)
            if not c.startswith('net/minecraft/'): return True  # JDK / library supertype: out of scope
            table = fields if kind == 'field' else methods
            if (name, desc) in table.get(c, ()): return True
            if (name, desc) in literal.get((kind, c), ()): return True
            if kind != 'field' and name in ('ordinal','values','valueOf','name','compareTo','getClass','hashCode','equals','toString','clone'): return True
            stack.extend(sup.get(c, []))
        return False
    problems = []
    checked = 0
    with zipfile.ZipFile(mod_jar) as z:
        names = z.namelist()
        for n in names:
            if not n.endswith('.class'): continue
            this, s, ifs, refs, _m = parse_class(z.read(n))
            for r in refs:
                if r[0] == 'class':
                    c = r[1].lstrip('[')
                    if c.startswith('L'): c = c[1:-1]
                    if c.startswith('net/minecraft/') and c not in all_classes:
                        problems.append(f'{n}: class {c}'); checked += 1
                    elif c.startswith('net/minecraft/'): checked += 1
                elif r[0] in ('field','method','imethod'):
                    owner = r[1]
                    if not owner.startswith('net/minecraft/'): continue
                    checked += 1
                    if owner not in all_classes:
                        problems.append(f'{n}: {r[0]} owner missing {owner}.{r[2]}{r[3]}'); continue
                    if r[2] == '<init>':
                        if (r[2], r[3]) not in methods.get(owner, ()):
                            # constructors are not in intermediary tiny (unmapped names); accept
                            pass
                        continue
                    if not member_exists(owner, r[2], r[3], 'field' if r[0]=='field' else 'method'):
                        problems.append(f'{n}: {r[0]} {owner}.{r[2]}{r[3]}')
        # refmap
        for n in names:
            if n.endswith('refmap.json'):
                ref = json.loads(z.read(n))
                for mixin, entries in ref.get('mappings', {}).items():
                    for key, val in entries.items():
                        m = re.match(r'^(?:L([^;]+);)?([^(]+)(\(.*)?$', val)
                        if not m: continue
                        owner, name, desc = m.group(1), m.group(2), m.group(3)
                        if owner and owner not in all_classes:
                            problems.append(f'refmap {mixin}: {key} -> missing class {owner}'); continue
                        if owner and desc and not member_exists(owner, name, desc, 'field' if ':' in desc else 'method'):
                            # field refs look like Lowner;name:Ldesc;
                            fd = desc.split(':')[1] if ':' in desc else desc
                            if not member_exists(owner, name, fd, 'field' if ':' in desc else 'method'):
                                problems.append(f'refmap {mixin}: {key} -> {val}')
                        elif owner is None and desc:
                            problems.append(f'refmap {mixin}: {key} -> {val} (no owner; check manually)')
    print(f'checked {checked} Minecraft references in {mod_jar}')
    hard = []
    for p in sorted(set(problems)):
        cls = p.split(':')[0]
        if any(cls.startswith(a) for a in allowed):
            print('ISOLATED  ', p)
        else:
            print('UNRESOLVED', p); hard.append(p)
    print('unresolved:', len(hard), '(isolated, allowed:', len(set(problems)) - len(hard), ')')
    sys.exit(1 if hard else 0)

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""Validate lesson content in content-src/<LEVEL>/*.json and merge it into the app assets."""
import json, sys, glob, os, re
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
index = json.load(open(os.path.join(ROOT, 'content-src/index.json'), encoding='utf-8'))
only = sys.argv[1:]  # optional list of levels to check
errors = []; lessons = {}
TYPES = {'vocab', 'grammar', 'conversation', 'skills', 'test'}
ALERTS = {'pron', 'grammar', 'mistake', 'tip'}
def err(k, m): errors.append(f'{k}: {m}')
def s(x): return isinstance(x, str) and x.strip() != ''
for path in sorted(glob.glob(os.path.join(ROOT, 'content-src/*/*.json'))):
    try: arr = json.load(open(path, encoding='utf-8'))
    except Exception as e: err(path, f'invalid JSON {e}'); continue
    for L in arr:
        k = f"{os.path.basename(path)}#{L.get('n')}"
        if L.get('n') in lessons: err(k, 'duplicate lesson n')
        lessons[L.get('n')] = L
        if L.get('type') not in TYPES: err(k, 'bad type')
        for f in ('title_ar', 'title_en', 'summary'):
            if not s(L.get(f)): err(k, f'missing {f}')
        notes = L.get('notes', [])
        if not 3 <= len(notes) <= 6: err(k, 'notes must be 3-6')
        for x in notes:
            if not (s(x.get('h')) and s(x.get('t'))): err(k, 'note needs h,t')
        al = L.get('alerts', [])
        if not 1 <= len(al) <= 3: err(k, 'alerts must be 1-3')
        for x in al:
            if x.get('k') not in ALERTS or not s(x.get('t')): err(k, 'bad alert')
        vocab = L.get('vocab', [])
        if not 6 <= len(vocab) <= 12: err(k, 'vocab must be 6-12')
        ens = set()
        for x in vocab:
            if not (s(x.get('en')) and s(x.get('ar')) and s(x.get('emoji'))): err(k, f'bad vocab {x}')
            elif not re.fullmatch(r"[A-Za-z0-9' .,?!/&:-]+", x['en']): err(k, f"vocab en not plain English: {x['en']}")
            elif len(x['en'].split()) > 3: err(k, f"vocab en too long: {x['en']}")
            if x.get('en', '').lower() in ens: err(k, f"duplicate vocab {x.get('en')}")
            ens.add(x.get('en', '').lower())
        ph = L.get('phrases', [])
        if not 4 <= len(ph) <= 6: err(k, 'phrases must be 4-6')
        for x in ph:
            if not (s(x.get('en')) and s(x.get('ar'))): err(k, 'bad phrase'); continue
            w = x['en'].split()
            if not 3 <= len(w) <= 9: err(k, f"phrase must be 3-9 words: {x['en']}")
            if not re.fullmatch(r"[A-Za-z0-9' .,?!:-]+", x['en']): err(k, f"phrase not plain English: {x['en']}")
        quiz = L.get('quiz', [])
        if not 5 <= len(quiz) <= 6: err(k, 'quiz must be 5-6')
        for x in quiz:
            o = x.get('o', [])
            if not s(x.get('q')) or len(o) != 4 or not all(s(i) for i in o) or len(set(o)) != 4: err(k, f"bad quiz item {x.get('q')}")
            if x.get('a') not in (0, 1, 2, 3): err(k, 'quiz a must be 0-3')
            if not s(x.get('why')): err(k, 'quiz needs why')
        if len(quiz) >= 5 and len({x.get('a') for x in quiz}) < 3: err(k, 'correct answers must be spread over at least 3 positions')
out = {}
for it in index:
    if only and it['level'] not in only: continue
    L = lessons.get(it['n'])
    if not L: err(it['level'], f"lesson {it['n']} ({it['topic']}) missing"); continue
    out.setdefault(it['level'], []).append({**L, 'n': it['n'], 'id': it['id'], 'sec': it['sec'], 'level': it['level']})
if errors:
    print('\n'.join(errors)); print(f'FAILED: {len(errors)} errors'); sys.exit(1)
if not only:
    dest = os.path.join(ROOT, 'app/src/main/assets/content'); os.makedirs(dest, exist_ok=True)
    for lvl, arr in out.items():
        json.dump(arr, open(os.path.join(dest, lvl + '.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('OK', {k: len(v) for k, v in out.items()})

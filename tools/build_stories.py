#!/usr/bin/env python3
"""Validate story translations (content-src/stories/ar/<id>.json) and merge them with the story texts into the app."""
import json, os, glob, re, sys
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
only = sys.argv[1:]
errors = []; out = []
def s(x): return isinstance(x, str) and x.strip() != ''
for path in sorted(glob.glob(os.path.join(ROOT, 'content-src/stories/text/*.json'))):
    t = json.load(open(path, encoding='utf-8')); sid = t['id']
    if only and sid not in only: continue
    ap = os.path.join(ROOT, f'content-src/stories/ar/{sid}.json')
    if not os.path.exists(ap): errors.append(f'{sid}: translation missing'); continue
    try: a = json.load(open(ap, encoding='utf-8'))
    except Exception as e: errors.append(f'{sid}: invalid JSON {e}'); continue
    for f in ('title_ar', 'summary', 'emoji'):
        if not s(a.get(f)): errors.append(f'{sid}: missing {f}')
    pages = a.get('pages_ar', [])
    if len(pages) != len(t['pages']) or not all(s(p) for p in pages):
        errors.append(f"{sid}: pages_ar must have exactly {len(t['pages'])} non-empty items (has {len(pages)})")
    if t['pages'] and t['pages'][-1].strip(): errors.append(f'{sid}: last source page should be empty')
    vocab = a.get('vocab', [])
    if not 5 <= len(vocab) <= 10: errors.append(f'{sid}: vocab must be 5-10')
    text = ' '.join(t['pages']).lower()
    for v in vocab:
        if not (s(v.get('en')) and s(v.get('ar'))): errors.append(f'{sid}: bad vocab {v}'); continue
        if not re.search(r"(?<![a-z'])" + re.escape(v['en'].lower()) + r"(?![a-z'])", text):
            errors.append(f"{sid}: vocab '{v['en']}' does not appear in the story")
    quiz = a.get('quiz', [])
    if not 4 <= len(quiz) <= 5: errors.append(f'{sid}: quiz must be 4-5')
    for q in quiz:
        o = q.get('o', [])
        if not s(q.get('q')) or len(o) != 4 or len(set(o)) != 4 or not all(s(i) for i in o) or q.get('a') not in (0, 1, 2, 3) or not s(q.get('why')):
            errors.append(f"{sid}: bad quiz item {q.get('q')}")
    if len(quiz) >= 4 and len({q.get('a') for q in quiz}) < 3: errors.append(f'{sid}: spread correct answers over 3+ positions')
    # The narration's first track reads the title and the source text ends with an empty page,
    # so track i+1 belongs to the title (i = 0) or to text page i-1.
    pairs = [(e, r) for e, r in zip(t['pages'], pages) if e.strip()]
    if len(pairs) != len(t['pages']) - 1: errors.append(f'{sid}: expected exactly one empty (last) page')
    out.append({**t, **a, 'pages': [{'en': t['title'], 'ar': a.get('title_ar', '')}] + [{'en': e, 'ar': r} for e, r in pairs]})
    for k in ('pages_ar',): out[-1].pop(k, None)
if errors:
    print('\n'.join(errors)); print(f'FAILED: {len(errors)} errors'); sys.exit(1)
if not only:
    dest = os.path.join(ROOT, 'app/src/main/assets/stories'); os.makedirs(dest, exist_ok=True)
    json.dump(out, open(os.path.join(dest, 'stories.json'), 'w', encoding='utf-8'), ensure_ascii=False, separators=(',', ':'))
print('OK', len(out), 'stories')

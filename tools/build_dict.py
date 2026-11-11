#!/usr/bin/env python3
"""Validate the learner dictionary in content-src/dict and write it to the app assets.

done/part_<k>.tsv lines: word <TAB> arabic meaning <TAB> English example sentence <TAB> Arabic translation
"""
import glob, os, re, sys, json
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
D = os.path.join(ROOT, 'content-src/dict')
only = sys.argv[1] if len(sys.argv) > 1 else None   # optional part number to check
errors = []; entries = {}
for path in sorted(glob.glob(os.path.join(D, 'done/part_*.tsv'))):
    k = re.search(r'part_(\d+)', path).group(1)
    if only and k != only: continue
    allowed = set(open(os.path.join(D, f'todo/words_{k}.txt'), encoding='utf-8').read().split())
    for i, line in enumerate(open(path, encoding='utf-8').read().split('\n'), 1):
        if not line.strip(): continue
        f = line.split('\t'); tag = f'part_{k}.tsv:{i}'
        if len(f) != 4 or not all(x.strip() for x in f): errors.append(f'{tag}: need 4 non-empty tab-separated fields'); continue
        w, ar, s, sar = [x.strip() for x in f]
        if w not in allowed: errors.append(f'{tag}: "{w}" is not in words_{k}.txt')
        if w in entries: errors.append(f'{tag}: duplicate word {w}')
        if not re.fullmatch(r"[A-Za-z0-9' .,?!:-]+", s): errors.append(f'{tag}: sentence must be plain English: {s}')
        if not 3 <= len(s.split()) <= 10: errors.append(f'{tag}: sentence must be 3-10 words: {s}')
        tokens = re.findall(r"[a-z]+(?:'[a-z]+)?", s.lower())
        if tokens.count(w) != 1: errors.append(f'{tag}: sentence must contain "{w}" exactly once as a whole word: {s}')
        if not re.search(r'[؀-ۿ]', ar) or not re.search(r'[؀-ۿ]', sar): errors.append(f'{tag}: Arabic fields must be Arabic')
        entries[w] = (ar, s, sar)
    if only:
        print(f'part {k}: {len([w for w in allowed if w in entries])} of {len(allowed)} words have entries')
if errors:
    print('\n'.join(errors[:60])); print(f'FAILED: {len(errors)} errors'); sys.exit(1)
if only: print('OK'); sys.exit(0)

# Words the course itself teaches are always valid, and its sentences fill gaps in the examples.
words = {}
for line in open(os.path.join(D, 'wordlist.tsv'), encoding='utf-8'):
    w, t = line.split(); words[w] = int(t)
for w in entries: words.setdefault(w, 10)
for path in glob.glob(os.path.join(ROOT, 'content-src/[ABC]*/*.json')):
    for L in json.load(open(path, encoding='utf-8')):
        for v in L['vocab']:
            w = v['en'].lower()
            if re.fullmatch(r"[a-z]+(?:'[a-z]+)?", w): words.setdefault(w, 10)
        for p in L['phrases']:
            tokens = re.findall(r"[a-z]+(?:'[a-z]+)?", p['en'].lower())
            for w in tokens: words.setdefault(w, 10)
            for v in L['vocab']:
                w = v['en'].lower()
                if w not in entries and tokens.count(w) == 1: entries[w] = (v['ar'], p['en'], p['ar'])
dest = os.path.join(ROOT, 'app/src/main/assets/dict'); os.makedirs(dest, exist_ok=True)
with open(os.path.join(dest, 'words.tsv'), 'w', encoding='utf-8') as f:
    for w in sorted(words): f.write(f'{w}\t{words[w]}\n')
with open(os.path.join(dest, 'examples.tsv'), 'w', encoding='utf-8') as f:
    for w in sorted(entries): f.write('\t'.join((w,) + entries[w]) + '\n')
print('OK', len(words), 'words,', len(entries), 'with meaning and example')

"""Check that every Korean UI string in index.html has English and Japanese translations.
Usage: python3 tools/i18n_check.py app/src/main/assets/index.html
Korean text in the markup and in TX`...` templates must appear as a key of the table D
inside the i18n <script> (placeholders {0}, {1}... stand for ${...}). Exit code 1 if any is missing.
"""
import re, sys, json
from html.parser import HTMLParser
HAN = re.compile('[가-힣]')
src = open(sys.argv[1], encoding='utf-8').read()
# --- split: markup part is before the first engine <script> after </style>; JS parts are <script> bodies
def js_literals(code, base):
    """yield (start, end, kind, parts) for ' and ` literals; parts = literal text pieces of a template"""
    out = []; i = 0; n = len(code)
    def tmpl(i):  # i at opening backtick; returns end index (after closing backtick), parts, inner spans
        parts = ['']; j = i + 1
        while j < n:
            c = code[j]
            if c == '\\': parts[-1] += code[j:j+2]; j += 2; continue
            if c == '`': return j + 1, parts
            if c == '$' and code[j+1] == '{':
                j = expr(j + 2); parts.append(''); continue
            parts[-1] += c; j += 1
        raise Exception('unterminated template at %d' % i)
    def expr(j):  # scan JS expression until matching }
        depth = 0
        while j < n:
            c = code[j]
            if c == '}' and depth == 0: return j + 1
            if c in '{(': depth += 1
            elif c in '})': depth -= 1
            elif c == "'": j = squote(j); continue
            elif c == '`':
                e, parts = tmpl(j); out.append((base + j, base + e, 't', parts)); j = e; continue
            j += 1
        raise Exception('unterminated expr')
    def squote(j):
        k = j + 1
        while code[k] != "'":
            k += 2 if code[k] == '\\' else 1
        out.append((base + j, base + k + 1, 's', [code[j+1:k]])); return k + 1
    while i < n:
        c = code[i]
        if code.startswith('//', i) and (i == 0 or code[i-1] != ':' ) and not code.startswith('//', i) is False:
            pass
        if code.startswith('/*', i): i = code.index('*/', i) + 2; continue
        if code.startswith('//', i) and code[i-1] in ' \n\t;{(,)': i = code.index('\n', i); continue
        if c == "'": i = squote(i); continue
        if c == '`':
            e, parts = tmpl(i); out.append((base + i, base + e, 't', parts)); i = e; continue
        i += 1
    return out
lits = []
for m in re.finditer(r'<script>(.*?)</script>', src, re.S):
    if len(m.group(1)) < 40 or 'const D = ' in m.group(1): continue
    lits += js_literals(m.group(1), m.start(1))
han = [l for l in lits if any(HAN.search(p) for p in l[3])]

keys = []
for st, en, k, parts in han:
    if k == 's': continue                      # plain '...' strings are data (e.g. '미지정'), shown via tx()
    if src[st-2:st] != 'TX':
        print('Korean template without TX tag:', src[st:st+60].replace('\n', ' ')); keys.append(None); continue
    keys.append(''.join(p + ('{%d}' % i if i < len(parts) - 1 else '') for i, p in enumerate(parts)))
head = src[:src.index('<script>\n/* ===== Timegrapher engine')]
head = re.sub(r'<script>.*?</script>', '', head, flags=re.S)
class P(HTMLParser):
    def __init__(s): super().__init__(); s.t = []; s.skip = False; s.lang = False
    def handle_starttag(s, tag, a):
        s.skip = tag in ('style', 'script')
        if tag == 'select' and dict(a).get('id') == 'lang': s.lang = True
        for k, v in a:
            if k in ('placeholder', 'aria-label') and v and HAN.search(v): s.t.append(v)
    def handle_endtag(s, tag):
        s.skip = False
        if tag == 'select': s.lang = False
    def handle_data(s, d):
        if not s.skip and not s.lang and HAN.search(d): s.t.append(' '.join(d.split()))
p = P(); p.feed(head)
D = json.loads(re.search(r'const D = (\{.*?\});\n', src, re.S).group(1).replace('<\\/', '</'))
missing = [k for k in p.t + keys if k is not None and k not in D]
bad = [k for k in D if any(sorted(re.findall(r'\{\d\}', t)) != sorted(re.findall(r'\{\d\}', k)) for t in D[k])]
for k in missing: print('missing translation:', k)
for k in bad: print('placeholder mismatch:', k)
print(len(D), 'entries,', len(p.t) + len(keys), 'strings checked')
sys.exit(1 if missing or bad or None in keys else 0)

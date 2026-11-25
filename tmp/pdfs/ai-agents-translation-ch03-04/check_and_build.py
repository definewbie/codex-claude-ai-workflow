from pathlib import Path
import ast
import hashlib
import html
import json
import re
import fitz
from markdown_it import MarkdownIt

ROOT = Path(__file__).resolve().parents[3]
WORK = Path(__file__).parent
OUT = ROOT / 'output/ai-agents-zh-ch03-04'
PDF = Path('/tmp/codex-remote-attachments/01a1ae70-77ef-7a11-9295-c33e80fe121b/76B6A373-B80A-4AF4-8487-F055DF5292F0/1-AI-Agents-The-Definitive-Guide-Design-Deployment-and-Evaluation-for-Production-Nicole-Koenigstein-z-library.sk-1lib.sk-z-lib.sk-.pdf')
doc = fitz.open(PDF)
inventory = json.loads((WORK / 'inventory.json').read_text())
parser = MarkdownIt('commonmark', {'html': True}).enable('table')
report = {
    'source_pdf': PDF.name,
    'source_pdf_sha256': hashlib.sha256(PDF.read_bytes()).hexdigest(),
    'scope': 'PDF physical pages 101–184, chapters 3 and 4',
    'source_limitations': [
        'Tables 3-3, 4-4, and 4-5 are horizontally clipped in the supplied PDF. Visible content is retained and original page screenshots are included. Missing cells cannot be recovered from this source.',
        'Example 3-9 contains try without except/finally in the source. The syntax error is preserved and annotated.',
        'Original APIs, output fragments, omissions, and inconsistent claims are retained; no provider calls or model code were executed.',
    ],
    'chapters': [],
}
texts = []
for ch, start, end, n_examples, n_figures, n_tables in [(3,101,146,19,10,7),(4,147,184,7,6,5)]:
    path = next(OUT.glob(f'第{ch}章*.md'))
    text = path.read_text()
    texts.append(text)
    tokens = parser.parse(text)
    fences = [t for t in tokens if t.type == 'fence']
    examples = re.findall(r'^\*\*示例 (\d+-\d+)', text, re.M)
    assert examples == [f'{ch}-{i}' for i in range(1,n_examples+1)]
    figures = re.findall(r'!\[[^\]]*\]\(assets/figure-([\d-]+)\.png\)', text)
    assert figures == [f'{ch}-{i}' for i in range(1,n_figures+1)]
    table_shapes = []
    current = None
    for t in tokens:
        if t.type == 'table_open': current=[]
        elif t.type == 'tr_open' and current is not None: current.append(0)
        elif t.type in ('th_open','td_open') and current is not None: current[-1]+=1
        elif t.type == 'table_close':
            assert len(set(current)) == 1
            table_shapes.append({'rows_including_header':len(current),'columns':current[0]})
            current=None
    assert len(table_shapes)==n_tables
    refs=set(re.findall(r'\[\^(ch\d+-\d+)\](?!:)',text))
    defs=set(re.findall(r'^\[\^(ch\d+-\d+)\]:',text,re.M))
    assert refs==defs
    assert len(defs)==(4 if ch==3 else 7)
    links={v['uri'] for v in inventory['links'] if start<=v['page']<=end}
    missing_links=[v for v in links if v not in text]
    assert not missing_links,missing_links
    for rel in re.findall(r'\]\((assets/[^)]+)\)',text): assert (OUT/rel).is_file(),rel
    coverage=set()
    for a,b in re.findall(r'<!-- source: PDF (\d+)(?:[–-](\d+))? -->',text):
        coverage.update(range(int(a),int(b or a)+1))
    assert not set(range(start,end+1))-coverage
    syntax=[]
    for i,t in enumerate(fences):
        if t.info=='python':
            try:
                ast.parse(t.content)
                syntax.append({'fence_index':i,'status':'parsed'})
            except SyntaxError as e:
                assert ch==3 and 'try:' in t.content and 'messages_and_choices' in t.content
                syntax.append({'fence_index':i,'status':'source_syntax_error_preserved','message':str(e)})
    # Extract standalone monospaced lines in original PDF order. Page 173
    # contains only a clipped table, whose identifiers are not code/output.
    mono=[]
    for pn in range(start,end+1):
        if pn==173: continue
        for block in doc[pn-1].get_text('dict')['blocks']:
            for line in block.get('lines',[]):
                spans=[s for s in line['spans'] if s['text'].strip()]
                if spans and all('Mono' in s['font'] for s in spans):
                    mono.append(''.join(s['text'] for s in spans))
    normalized_source=re.sub(r'\s','', '\n'.join(mono))
    normalized_target=re.sub(r'\s','', '\n'.join(t.content for t in fences))
    assert normalized_source==normalized_target
    report['chapters'].append({
        'chapter':ch,'markdown':path.name,'pdf_pages':[start,end],
        'figures':len(figures),'numbered_examples':len(examples),
        'tables':table_shapes,'fenced_blocks':len(fences),
        'python_blocks':len(syntax),'python_syntax':syntax,
        'code_and_output_nonwhitespace_equality':True,
        'code_comparison_excludes':'Page 173 clipped table identifiers' if ch==4 else None,
        'external_source_link_targets_preserved':len(links),
        'footnote_definitions_and_references_match':True,'footnotes':len(defs),
        'all_source_pages_have_range_markers':True,
        'local_image_links_resolve':True,
        'display_math_blocks':len(re.findall(r'\$\$.*?\$\$',text,re.S)),
    })

for item in inventory['figures']:
    image=doc.extract_image(item['xref'])['image']
    actual=(OUT/'assets'/item['asset']).read_bytes()
    assert image==actual,item['asset']
report['original_figures_byte_identical_to_pdf']=len(inventory['figures'])
report['supplementary_source_table_page_screenshots']=3

cover='''# AI 智能体：权威指南

## 面向生产环境的设计、部署与评估

**Nicole Koenigstein 著 · 第 3–4 章简体中文译文**

依据用户提供的原书 PDF，第 101–184 页。沿用第 1、2 章术语及排版；原图保留，图注、表格和代码讲解译为中文，代码与输出保留原文。

- [第 3 章　智能体中的高级规划、推理与可扩展执行](#chapter-3)
- [第 4 章　智能体背后的模型：能力与优化](#chapter-4)
- [阅读与译校说明](README.md)

> 阅读提示：请将本文与 `assets/` 目录放在一起。公式和脚注需要阅读器支持相应 Markdown 扩展。原 PDF 的表 3-3、4-4、4-5 存在右侧裁切，译文已逐处标注，并附原页截图，没有补造缺失内容。
'''
parts=[cover]
for ch,text in zip((3,4),texts):
    parts.append(f'\n---\n\n<a id="chapter-{ch}"></a>\n\n')
    inside=False
    fence_marker=''
    lines=[]
    for line in text.splitlines():
        match=re.match(r'^(`{3,}|~{3,})',line)
        if match:
            marker=match[1]
            if not inside:
                inside=True;fence_marker=marker
            elif marker[0]==fence_marker[0] and len(marker)>=len(fence_marker):inside=False
        if not inside and re.match(r'^#{1,5} ',line):line='#'+line
        lines.append(line)
    assert not inside
    parts.append('\n'.join(lines)+'\n')
combined=''.join(parts)
(OUT/'AI-Agents-第3-4章-中文译文.md').write_text(combined)
# Confirm title adjustments did not alter any code or output block.
orig=[t.content for s in texts for t in parser.parse(s) if t.type=='fence']
merged=[t.content for t in parser.parse(combined) if t.type=='fence']
assert orig==merged
report['combined_code_blocks_unchanged']=True
(OUT/'validation.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')

# Offline visual preview; footnotes are expanded for inspection. Math remains
# visible as literal LaTeX in this preview, rather than silently losing escapes.
protected={}
def protect(match):
    key=f'MATHPLACEHOLDER{len(protected)}END'
    display=match.group().startswith('$$')
    value=match.group()[2:-2] if display else match.group()[1:-1]
    tag='div' if display else 'code'
    protected[key]=f'<{tag} class="math-source">{html.escape(value.strip())}</{tag}>'
    return key
preview=re.sub(r'\$\$.*?\$\$|(?<!\$)\$[^\n$]+\$(?!\$)',protect,combined,flags=re.S)
preview=re.sub(r'^\[\^(ch\d+-\d+)\]:',r'<a id="fn-\1"></a> **[\1]**',preview,flags=re.M)
preview=re.sub(r'\[\^(ch\d+-\d+)\]',r'<sup><a href="#fn-\1">[\1]</a></sup>',preview)
rendered=parser.render(preview)
for key,value in protected.items():rendered=rendered.replace(key,value)
old=(ROOT/'tmp/pdfs/ai-agents-translation/preview.html').read_text()
css=re.search(r'<style>(.*?)</style>',old,re.S).group(1)
css+=' .math-source{font-family:Menlo,monospace;font-size:13px;white-space:pre-wrap;overflow-wrap:anywhere} div.math-source{background:#f6f8fa;padding:16px;border:1px solid #ddd} '
(WORK/'preview.html').write_text(f'<!doctype html><html lang="zh-CN"><meta charset="utf-8"><base href="{OUT.as_uri()}/"><title>第3、4章译文排版核对</title><style>{css}</style><main>{rendered}</main></html>')
print(json.dumps(report,ensure_ascii=False,indent=2))

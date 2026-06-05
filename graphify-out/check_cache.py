import json
from graphify.cache import check_semantic_cache
from pathlib import Path

raw = open('graphify-out/.graphify_detect.json', 'rb').read()
for enc in ['utf-8', 'utf-8-sig', 'utf-16']:
    try:
        detect = json.loads(raw.decode(enc))
        break
    except:
        continue

all_files = []
for ftype in ['document', 'paper', 'image']:
    all_files.extend(detect['files'].get(ftype, []))

cached_nodes, cached_edges, cached_hyperedges, uncached = check_semantic_cache(all_files)

if cached_nodes or cached_edges or cached_hyperedges:
    Path('graphify-out/.graphify_cached.json').write_text(json.dumps({'nodes': cached_nodes, 'edges': cached_edges, 'hyperedges': cached_hyperedges}, ensure_ascii=False), encoding='utf-8')
Path('graphify-out/.graphify_uncached.txt').write_text('\n'.join(uncached), encoding='utf-8')
print(f'Cache: {len(all_files)-len(uncached)} files hit, {len(uncached)} files need extraction')

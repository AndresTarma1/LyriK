import json, glob
from graphify.cache import save_semantic_cache
from pathlib import Path

# Save new results to cache
new = json.loads(Path('graphify-out/.graphify_semantic_new.json').read_text(encoding='utf-8'))
saved = save_semantic_cache(new.get('nodes', []), new.get('edges', []), new.get('hyperedges', []))
print(f'Cached {saved} files')

# Merge cached + new
cached = {'nodes':[], 'edges':[], 'hyperedges':[]}
if Path('graphify-out/.graphify_cached.json').exists():
    cached = json.loads(Path('graphify-out/.graphify_cached.json').read_text(encoding='utf-8'))

all_nodes = cached['nodes'] + new.get('nodes', [])
all_edges = cached['edges'] + new.get('edges', [])
all_hyperedges = cached.get('hyperedges', []) + new.get('hyperedges', [])
seen = set()
deduped = []
for n in all_nodes:
    if n['id'] not in seen:
        seen.add(n['id'])
        deduped.append(n)

merged = {
    'nodes': deduped,
    'edges': all_edges,
    'hyperedges': all_hyperedges,
    'input_tokens': new.get('input_tokens', 0),
    'output_tokens': new.get('output_tokens', 0),
}
Path('graphify-out/.graphify_semantic.json').write_text(json.dumps(merged, indent=2, ensure_ascii=False), encoding='utf-8')
print(f'Semantic total - {len(deduped)} nodes, {len(all_edges)} edges ({len(cached["nodes"])} from cache, {len(new.get("nodes",[]))} new)')

# Part C: Merge AST + semantic
ast = json.loads(Path('graphify-out/.graphify_ast.json').read_text(encoding='utf-8'))
sem = json.loads(Path('graphify-out/.graphify_semantic.json').read_text(encoding='utf-8'))

seen = {n['id'] for n in ast['nodes']}
merged_nodes = list(ast['nodes'])
for n in sem['nodes']:
    if n['id'] not in seen:
        merged_nodes.append(n)
        seen.add(n['id'])

merged_edges = ast['edges'] + sem['edges']
merged_hyperedges = sem.get('hyperedges', [])
final = {
    'nodes': merged_nodes,
    'edges': merged_edges,
    'hyperedges': merged_hyperedges,
    'input_tokens': sem.get('input_tokens', 0),
    'output_tokens': sem.get('output_tokens', 0),
}
Path('graphify-out/.graphify_extract.json').write_text(json.dumps(final, indent=2, ensure_ascii=False), encoding='utf-8')
total = len(merged_nodes)
edges = len(merged_edges)
print(f'Final extract: {total} nodes, {edges} edges ({len(ast["nodes"])} AST + {len(sem["nodes"])} semantic)')

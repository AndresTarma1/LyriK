import json
from pathlib import Path
from collections import Counter

extraction = json.loads(Path('graphify-out/.graphify_extract.json').read_text(encoding='utf-8'))
analysis = json.loads(Path('graphify-out/.graphify_analysis.json').read_text(encoding='utf-8'))

node_map = {n['id']: n for n in extraction['nodes']}

comms = {int(k): v for k, v in analysis['communities'].items()}
sorted_comms = sorted(comms.items(), key=lambda x: len(x[1]), reverse=True)

for cid, node_ids in sorted_comms[:20]:
    labels = []
    file_types = Counter()
    for nid in node_ids[:30]:
        node = node_map.get(nid, {})
        labels.append(node.get('label', nid))
        file_types[node.get('file_type', 'unknown')] += 1
    
    sample_labels = [l for l in labels if l != nid][:8]
    type_summary = ', '.join(f'{k}={v}' for k, v in file_types.most_common())
    print(f'Community {cid} ({len(node_ids)} nodes): [{type_summary}]')
    if sample_labels:
        print(f'  Sample: {"; ".join(sample_labels)}')
    print()

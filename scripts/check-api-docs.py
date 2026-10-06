#!/usr/bin/env python3
"""Check documentation coverage against Controller routes, without executing any API."""
import json,pathlib,re
root=pathlib.Path(__file__).resolve().parent.parent
spec=json.loads((root/'openapi.json').read_text())
documented={(method.upper(),path) for path,methods in spec['paths'].items() for method in methods}
actual=set()
def expand(path):
 m=re.search(r'{\w+:([^}]+)}',path)
 if not m:return [path]
 return [route for option in m.group(1).split('|') for route in expand(path[:m.start()]+option+path[m.end():])]
for source in (root/'src/main/java/com/jada/severe/controller').glob('*.java'):
 text=source.read_text()
 prefixmatch=re.search(r'@RequestMapping\("([^"]+)"\)',text)
 prefix=prefixmatch.group(1) if prefixmatch else ''
 for match in re.finditer(r'@(Get|Post|Put|Patch|Delete)Mapping(?:\((.*?)\))?',text,re.S):
  urls=[s for s in re.findall(r'"([^"]*)"',match.group(2) or '') if s.startswith('/')]
  for url in urls or ['']:
   for route in expand(prefix+url):actual.add((match.group(1).upper(),route))
assert actual==documented,{'missing':sorted(actual-documented),'extra':sorted(documented-actual)}
ids=[]
for path,methods in spec['paths'].items():
 for method,operation in methods.items():
  ids.append(operation['operationId'])
  assert set(re.findall(r'{(\w+)}',path))=={p['name'] for p in operation.get('parameters',[]) if p['in']=='path'},path
  assert '200' in operation['responses']
assert len(ids)==len(set(ids))
def refs(node):
 if isinstance(node,dict):
  if '$ref' in node:
   value=spec
   for key in node['$ref'][2:].split('/'):value=value[key]
  for child in node.values():refs(child)
 elif isinstance(node,list):
  for child in node:refs(child)
refs(spec)
assert len(spec['x-module-metadata'])==35
# Every embedded JSON example is parseable.
doc=(root/'API接口文档.md').read_text()
examples=re.findall(r'```json\n(.*?)\n```',doc,re.S)
for example in examples:json.loads(example)
print(f'PASS {len(actual)} Controller operations covered, {len(spec["paths"])} paths, 35 modules, {len(examples)} valid JSON examples')

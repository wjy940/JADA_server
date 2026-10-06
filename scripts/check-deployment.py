#!/usr/bin/env python3
"""Read-only post-deployment gate. Requires HTTPS domain and real Bearer token."""
import os,json,urllib.request,urllib.error,urllib.parse
base=os.environ['API_BASE'].rstrip('/')
token=os.environ['API_TOKEN']
parsed=urllib.parse.urlparse(base)
if parsed.scheme!='https' and parsed.hostname not in ('127.0.0.1','localhost'):
 raise SystemExit('Production domain must use HTTPS')
def request(path,headers=None,expected=200):
 req=urllib.request.Request(base+path,headers=headers or {})
 try:
  with urllib.request.urlopen(req,timeout=15) as response:code=response.status;data=json.load(response)
 except urllib.error.HTTPError as error:code=error.code;data=json.load(error)
 assert code==expected,(path,code,data)
 return data
# API_BASE excludes /api, e.g. https://assets.example.com.
request('/api/auth/me',{'X-User-Id':'admin'},403)
request('/api/auth/me',expected=401)
headers={'Authorization':'Bearer '+token}
me=request('/api/auth/me',headers)['data']
modules=request('/api/modules',headers)['data']
assert me['id']!='anonymous' and len(modules)>0
if any(m['module_id']=='engineeringEquipment' for m in modules):
 result=request('/api/modules/engineeringEquipment/list?page=1&pageSize=1',headers)
 assert 'list' in result and 'total' in result
print(json.dumps({'authenticatedUser':me['id'],'authorizedModules':len(modules),'headerSpoofDenied':True,'anonymousDenied':True,'apiOrigin':base},ensure_ascii=False))

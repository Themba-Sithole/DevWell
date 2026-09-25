import json,urllib.request,urllib.error,concurrent.futures
from pathlib import Path
f=json.loads(Path('tmp/backend/fixtures.json').read_text())
props=dict(line.strip().split('=',1) for line in Path('local.properties').read_text().splitlines() if '=' in line and not line.startswith('#'))
url=props['SUPABASE_URL'].replace('\\:',':').rstrip('/')
key=props['SUPABASE_ANON_KEY'];tokens={};passed=[]
def request(path,method='GET',body=None,role=None,token=None):
 headers={'apikey':key,'Content-Type':'application/json'}
 if token or role:headers['Authorization']='Bearer '+(token or tokens[role])
 data=json.dumps(body).encode() if body is not None else None
 try:
  with urllib.request.urlopen(urllib.request.Request(url+path,data=data,headers=headers,method=method),timeout=35) as r:status=r.status;raw=r.read()
 except urllib.error.HTTPError as e:status=e.code;raw=e.read()
 try:result=json.loads(raw) if raw else None
 except:result={'error':'non-JSON response'}
 return status,result

def check(ok,label):
 if not ok:raise AssertionError(label)
 passed.append(label);print('PASS:',label,flush=True)
def edge(name,body,role=None,token=None):return request('/functions/v1/'+name,'POST',body,role,token)
try:
 for role,email in f['emails'].items():
  status,data=request('/auth/v1/token?grant_type=password','POST',{'email':email,'password':f['password']})
  check(status==200,'Auth login '+role)
  tokens[role]=data['access_token']
 status,profile=request('/rest/v1/user?select=*',role='engineer')
 check(status==200 and len(profile)==1 and profile[0]['id']==f['engineer'] and profile[0]['role']=='engineer','own profile and metadata privilege protection')
 status,companies=request('/rest/v1/company?select=id',role='engineer')
 check(status==200 and f['company'] in [c['id'] for c in companies] and f['hidden_company'] not in [c['id'] for c in companies],'active company directory')
 status,teams=request('/rest/v1/team?select=id',role='engineer')
 check(status==200 and f['hidden_team'] not in [t['id'] for t in teams],'inactive teams hidden')
 status,_=request('/rest/v1/user?select=id')
 check(status in [401,403],'anonymous database access denied')
 status,_=request('/rest/v1/user?id=eq.'+f['engineer'],'PATCH',{'role':'admin'},'engineer')
 check(status in [401,403],'self promotion denied')
 body={'company_id':f['company'],'team_id':f['team']}
 status,_=edge('submit-join-request',body)
 check(status==401,'Edge Function requires login')
 status,_=edge('submit-join-request',body,token='not-a-valid-jwt')
 check(status==401,'Edge Function rejects forged token')
 status,_=edge('submit-join-request',dict(body,role='admin'),'engineer')
 check(status==400,'Edge Function rejects injected role')
 status,_=edge('submit-join-request',{'company_id':f['other_company'],'team_id':f['team']},'engineer')
 check(status==400,'mismatched team rejected')
 with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
  results=list(pool.map(lambda _:edge('submit-join-request',body,'engineer'),range(3)))
 check(all(s==200 for s,d in results),'concurrent submissions succeed idempotently')
 ids={d['request_id'] for s,d in results};check(len(ids)==1,'concurrent submissions create one request')
 rid=next(iter(ids))
 status,data=request('/rest/v1/user?select=status,onboarding_done,team_id',role='engineer')
 check(status==200 and data[0]=={'status':'pending','onboarding_done':True,'team_id':f['team']},'submission updates profile atomically')
 status,data=request('/rest/v1/join_request?select=id',role='engineer2')
 check(status==200 and data==[],'other engineer cannot read request')
 status,data=request('/rest/v1/join_request?select=id',role='wrong_manager')
 check(status==200 and data==[],'wrong manager cannot read request')
 status,_=edge('review-join-request',{'request_id':rid,'approve':True},'wrong_manager')
 check(status==403,'wrong manager cannot approve')
 status,_=edge('review-join-request',{'request_id':rid,'approve':True},'engineer')
 check(status==403,'engineer cannot self approve')
 status,data=edge('review-join-request',{'request_id':rid,'approve':True},'manager')
 check(status==200 and data['status']=='approved','correct manager approves')
 status,data=request('/rest/v1/user?select=status',role='engineer')
 check(status==200 and data[0]['status']=='active','approval activates profile')
 status,_=edge('review-join-request',{'request_id':rid,'approve':True},'manager')
 check(status==200,'approval retry is safe')
 status,_=edge('review-join-request',{'request_id':rid,'approve':False},'manager')
 check(status==409,'contradictory review rejected')
 status,data=edge('submit-join-request',body,'engineer2');check(status==200,'second engineer submits')
 status,data=edge('review-join-request',{'request_id':data['request_id'],'approve':False},'manager')
 check(status==200 and data['status']=='rejected','manager rejects request')
 status,data=request('/rest/v1/user?select=status',role='engineer2')
 check(status==200 and data[0]['status']=='rejected','rejection updates profile')
 status,_=edge('submit-join-request',body,'engineer2');check(status==403,'rejected engineer cannot bypass gate')
finally:
 for role in tokens:
  request('/auth/v1/logout','POST',{},role)
 Path('tmp/backend/http-results.json').write_text(json.dumps({'passed':passed,'count':len(passed)},indent=2))
print(f'All {len(passed)} live API checks passed.')

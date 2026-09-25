import json,secrets,uuid
from pathlib import Path
p=Path('tmp/backend');p.mkdir(parents=True,exist_ok=True)
f={k:str(uuid.uuid4()) for k in ['engineer','engineer2','manager','wrong_manager','company','other_company','hidden_company','team','other_team','hidden_team']}
f['password']=secrets.token_urlsafe(32)
f['emails']={k:f'devwell-test-{f[k]}@example.invalid' for k in ['engineer','engineer2','manager','wrong_manager']}
(p/'fixtures.json').write_text(json.dumps(f))
rows=[]
for role,email in f['emails'].items():
 rows.append(f"('{f[role]}','00000000-0000-0000-0000-000000000000','authenticated','authenticated','{email}',extensions.crypt('{f['password']}',extensions.gen_salt('bf')),now(),'{{\"provider\":\"email\",\"providers\":[\"email\"]}}','{{\"name\":\"Temporary test\",\"role\":\"admin\"}}',now(),now(),'','','','')")
sql='begin;\ninsert into auth.users(id,instance_id,aud,role,email,encrypted_password,email_confirmed_at,raw_app_meta_data,raw_user_meta_data,created_at,updated_at,confirmation_token,recovery_token,email_change_token_new,email_change) values '+',\n'.join(rows)+';\n'
for role,email in f['emails'].items():
 sql+=f"insert into auth.identities(user_id,provider_id,identity_data,provider,created_at,updated_at) values ('{f[role]}','{f[role]}',jsonb_build_object('sub','{f[role]}','email','{email}','email_verified',true),'email',now(),now());\n"
for k,status in [('company','active'),('other_company','active'),('hidden_company','pending')]:
 sql+=f"insert into public.company(id,name,status) values ('{f[k]}','Temporary integration {k}','{status}');\n"
for role,co in [('manager','company'),('wrong_manager','other_company')]:
 sql+=f"update public.\"user\" set role='manager',status='active',onboarding_done=true,company_id='{f[co]}' where id='{f[role]}';\n"
for team,co,manager in [('team','company','manager'),('other_team','other_company','wrong_manager'),('hidden_team','hidden_company','manager')]:
 sql+=f"insert into public.team(id,company_id,name,manager_id) values ('{f[team]}','{f[co]}','Temporary integration {team}','{f[manager]}');\n"
sql+='commit;'
(p/'setup.sql').write_text(sql)
ids=','.join("'"+f[k]+"'" for k in f['emails'])
cleanup=f"begin; delete from auth.users where id in ({ids}); delete from public.team where id in ('{f['team']}','{f['other_team']}','{f['hidden_team']}'); delete from public.company where id in ('{f['company']}','{f['other_company']}','{f['hidden_company']}'); commit;"
(p/'cleanup.sql').write_text(cleanup)
print('Prepared temporary test fixtures and exact-ID cleanup.')

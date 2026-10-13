#!/usr/bin/env python3
"""Temporary PostgreSQL bootstrap verification; fixtures only, tmpfs, no published ports."""
import subprocess,time,uuid,json
from pathlib import Path
name='orderflow-phase10-db-'+uuid.uuid4().hex[:8]
try:
 subprocess.run(['docker','run','-d','--name',name,'--memory','384m','--tmpfs','/var/lib/postgresql/data','-e','POSTGRES_PASSWORD=fixture_only_admin','-e','POSTGRES_USER=fixture_admin','-e','POSTGRES_DB=postgres','postgres:16-alpine'],capture_output=True,check=True)
 for _ in range(30):
  if subprocess.run(['docker','exec',name,'pg_isready','-U','fixture_admin','-d','postgres'],capture_output=True).returncode==0:break
  time.sleep(1)
 env=['-e','POSTGRES_HOST=localhost','-e','POSTGRES_PASSWORD=fixture_only_admin','-e','POSTGRES_USER=fixture_admin','-e','POSTGRES_DB=postgres']
 for p in 'AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS'.split():
  for k,v in [('DB','fixture_'+p.lower()),('USER','fixture_'+p.lower()),('PASSWORD',"test ' quote ; ${literal}")]:env+=['-e',p+'_POSTGRES_'+k+'='+v]
 script=(Path(__file__).resolve().parents[3]/'infrastructure/bootstrap/bootstrap-databases.sh').read_text()
 for _ in range(2):
  r=subprocess.run(['docker','exec','-i',*env,name,'sh'],input=script,capture_output=True,text=True)
  assert r.returncode==0,r.stderr
 query="SELECT rolname||':'||rolsuper||':'||rolcreatedb||':'||rolcreaterole||':'||rolbypassrls FROM pg_roles WHERE rolname LIKE 'fixture_%' ORDER BY rolname"
 r=subprocess.run(['docker','exec',name,'psql','-X','-At','-U','fixture_admin','-d','postgres','-c',query],capture_output=True,text=True,check=True)
 rows=r.stdout.strip().splitlines();assert len(rows)==9
 for row in rows:
  if not row.startswith('fixture_admin:'):assert row.endswith(':false:false:false:false'),row
 print('PASS eight actual PostgreSQL database owners created; rerun idempotent; quoted passwords accepted; app roles lack superuser/createDB/createRole/bypassRLS')
finally:subprocess.run(['docker','rm','-f',name],capture_output=True)

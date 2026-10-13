#!/usr/bin/env python3
"""Explicit operator-only fake fixture seed; never expose a public role-grant API."""
import argparse
import fcntl
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
from cloud_common import Client, ROLES, SafetyError, VerificationError, accounts, identity, target
from verify_journey import page_items
ROOT=Path(__file__).resolve().parents[2]


def compose_configuration(env, configuration):
    runtime=Path(env.get('ORDERFLOW_RUNTIME_ENV_FILE','/opt/orderflow/config/runtime.env'))
    try:
        fd=os.open(runtime,os.O_RDONLY|os.O_NONBLOCK|getattr(os,'O_NOFOLLOW',0))
        with os.fdopen(fd) as f:
            info=os.fstat(f.fileno())
            if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode)!=0o600 or info.st_uid!=os.geteuid() or info.st_size>131072:
                raise SafetyError('Host runtime file must be protected current-user owned mode0600')
            values={}
            for line in f:
                if '=' in line and not line.lstrip().startswith('#'):
                    key,value=line.strip().split('=',1);values[key]=value.strip('"\'')
    except OSError:
        raise SafetyError('Protected host runtime file is unavailable') from None
    if values.get('AWS_ACCOUNT_ID')!=configuration['account'] or values.get('ORDERFLOW_ENVIRONMENT')!=configuration['environment']:
        raise SafetyError('Host runtime account/environment does not match the approved API target')
    from urllib.parse import urlsplit
    approved_origin=urlsplit(configuration['url'])
    expected_domain=approved_origin.netloc if configuration.get('local') else approved_origin.hostname
    if values.get('DOMAIN_NAME')!=expected_domain:
        raise SafetyError('Host runtime domain does not match the approved API target')
    database=values.get('AUTH_POSTGRES_DB','')
    if not re.fullmatch(r'[A-Za-z_][A-Za-z0-9_]{0,62}',database):
        raise SafetyError('Auth database identifier invalid')
    project=env.get('ORDERFLOW_COMPOSE_PROJECT_NAME','orderflow-cloud')
    if not re.fullmatch(r'[a-z0-9][a-z0-9_-]{0,62}',project):
        raise SafetyError('Invalid explicit Compose project name')
    return ['docker','compose','--project-name',project,'--env-file',str(runtime),'-f',str(ROOT/'docker-compose.yml'),'-f',str(ROOT/'docker-compose.cloud.yml')],database,runtime.parent


def grant_role(command,database,user_id,email,role):
    # Values are validated, nonsecret and psql-quoted; admin credentials remain
    # inside the existing private postgres container. No shell-generated SQL.
    identity(user_id)
    if role not in ROLES or not re.fullmatch(r'[a-z0-9][a-z0-9._+-]{0,120}@example\.test',email):
        raise SafetyError('Only the exact registered fake identities may receive supported fixture roles')
    sql="""SELECT EXISTS(SELECT FROM users WHERE id=:'fixture_id'::uuid AND email=:'fixture_email' AND status='ACTIVE') AS registered \\gset
\\if :registered
INSERT INTO user_roles(user_id,role)
SELECT id, :'fixture_role' FROM users WHERE id=:'fixture_id'::uuid AND email=:'fixture_email' AND status='ACTIVE'
ON CONFLICT DO NOTHING;
\\else
\\echo 'Fixture identity mismatch; refusing role grant'
\\quit 1
\\endif
"""
    args=command+['exec','-T','postgres','sh','-ec','export PGPASSWORD="$POSTGRES_PASSWORD" PGCONNECT_TIMEOUT=5; exec psql -X -q -U "$POSTGRES_USER" -d "$1" -v ON_ERROR_STOP=1 -v fixture_id="$2" -v fixture_email="$3" -v fixture_role="$4"','sh',database,user_id,email,role]
    try:
        result=subprocess.run(args,input=sql,text=True,capture_output=True,timeout=20)
    except (OSError,subprocess.TimeoutExpired):
        raise VerificationError('Trusted host role grant unavailable or timed out') from None
    if result.returncode:
        raise VerificationError('Trusted host role grant rejected the exact fixture identity')


def fixture_client(configuration,credentials,role,command,database,initial_only=False):
    client=Client(configuration)
    client.csrf_token()
    response=client.request('/api/auth/register','POST',{**credentials,'displayName':'Synthetic demo '+role},auth=False,expected=(201,) if initial_only else (201,409))
    if isinstance(response,dict) and response.get('accessToken'):
        client.authenticated(response,'CUSTOMER')
    else:
        client.login(credentials,'CUSTOMER')
    if client.user.get('email')!=credentials['email']:
        raise VerificationError('Registered/login identity mismatch')
    grant_role(command,database,client.user['id'],credentials['email'],role)
    client.login(credentials,role) # mint roles through normal auth after grant
    return client


def find_page(client,path,predicate):
    for page in range(20):
        body=client.request(path+('?'+ 'page='+str(page)+'&size=100'))
        for row in page_items(body):
            if predicate(row):return row
        if page+1>=body.get('totalPages',1):return None
    raise VerificationError('Seed lookup exceeded bounded pagination; no duplicate fixture will be created')


def seed(configuration,operators,command,database,initial_only=False):
    clients={role:fixture_client(configuration,operators[role],role,command,database,initial_only=initial_only) for role in ROLES}
    admin,vendor,agent=(clients[r] for r in ('ADMIN','VENDOR','DELIVERY_AGENT'))
    category=find_page(admin,'/api/admin/categories',lambda row:row.get('slug')=='orderflow-demo-seed')
    if category is None:
        category=admin.request('/api/admin/categories','POST',{'name':'OrderFlow synthetic demo','slug':'orderflow-demo-seed','description':'Fake portfolio fixtures only'},expected=(201,))
    category_id=identity(category['id'])
    product=find_page(vendor,'/api/vendor/products',lambda row:row.get('sku')=='ORDERFLOW-DEMO-001')
    new=product is None
    if new:
        product=vendor.request('/api/vendor/products','POST',{'sku':'ORDERFLOW-DEMO-001','name':'Synthetic demo desk item','description':'Fake demo stock; no real commerce','priceAmount':'12.50','currency':'USD','categoryId':category_id,'slug':'orderflow-synthetic-demo-item'},expected=(201,))
        product_id=identity(product['id'])
        product=vendor.request('/api/vendor/products/'+product_id+'/activate','PATCH',{'version':product['version']})
        vendor.request('/api/inventory/products/'+product_id+'/adjustments','POST',{'quantityDelta':10,'reason':'Initial synthetic demo stock','reference':'orderflow-demo-seed-v1'},expected=(200,201))
    else:
        product_id=identity(product['id'])
        # Rerun never changes existing product state, password or stock count.
    profile=agent.request('/api/delivery/agents/me',expected=(200,404))
    if not isinstance(profile,dict) or not profile.get('profileId'):
        profile=agent.request('/api/delivery/agents/me/availability','PUT',{'availability':'OFFLINE'})
    return {'result':'PASS','scope':'local-verified-TLS-simulation' if configuration['local'] else 'authorized-demo-seed','categoryId':category_id,'productId':product_id,'newProductCreated':new,'existingStockChanged':False,'agentProfileId':identity(profile['profileId'])}


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--dry-run',action='store_true');parser.add_argument('--initial-only',action='store_true',help='first installation only: refuse existing account conflicts before role grant');args=parser.parse_args()
    try:
        if os.environ.get('ORDERFLOW_DEMO_SEED')!='true' or os.environ.get('ORDERFLOW_TRUSTED_HOST_ROLE_GRANT')!='true':
            raise SafetyError('Seed requires explicit ORDERFLOW_DEMO_SEED=true and ORDERFLOW_TRUSTED_HOST_ROLE_GRANT=true')
        configuration=target();operators=accounts(os.environ.get('ORDERFLOW_CLOUD_ACCOUNTS_FILE'))
        command,database,config_dir=compose_configuration(os.environ,configuration)
        if args.dry_run:
            print('DRY RUN validated: only registered fake accounts, fixed role allowlist and new synthetic category/product/initial stock; no network or SQL executed');return 0
        fd=os.open(config_dir/'demo-seed.lock',os.O_CREAT|os.O_RDWR|getattr(os,'O_NOFOLLOW',0),0o600)
        with os.fdopen(fd,'w') as lock:
            try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
            except BlockingIOError:raise SafetyError('Another seed is running; no concurrent initial-stock changes allowed') from None
            print(json.dumps(seed(configuration,operators,command,database,initial_only=args.initial_only),indent=2))
        return 0
    except (SafetyError,VerificationError) as error:
        print('FAIL '+str(error),file=sys.stderr);return 1
    except Exception:
        print('FAIL unexpected seed error; no automatic data reset or cleanup performed',file=sys.stderr);return 1

if __name__=='__main__':raise SystemExit(main())

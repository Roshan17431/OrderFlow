#!/usr/bin/env python3
"""Fail-closed release tooling. Never prints command output that could contain secrets."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import hashlib
import ipaddress
import json
import os
from pathlib import Path, PurePosixPath
import re
import shlex
import shutil
import subprocess
import sys
import tarfile
import tempfile
import time

SERVICES = ('service-registry', 'api-gateway', 'auth-service', 'product-service', 'cart-service',
            'inventory-service', 'order-service', 'payment-service', 'delivery-service',
            'notification-service', 'operations-service', 'frontend')
OWNERS = ('AUTH', 'PRODUCT', 'INVENTORY', 'ORDER', 'PAYMENT', 'DELIVERY', 'NOTIFICATION', 'OPERATIONS')
INFRA_IMAGE_VARIABLES = {'caddy':'INFRA_IMAGE_CADDY', 'kafka':'INFRA_IMAGE_KAFKA',
                         'node-exporter':'INFRA_IMAGE_NODE_EXPORTER', 'grafana':'INFRA_IMAGE_GRAFANA'}
REPO = Path(__file__).resolve().parents[2]

class SafetyError(Exception):
    pass

def require(ok, message):
    if not ok:
        raise SafetyError(message)

def run(command, *, timeout=120, env=None, cwd=None, input=None, binary=False):
    try:
        result = subprocess.run([str(c) for c in command], input=input, capture_output=True,
                                text=not binary, timeout=timeout, env=env, cwd=cwd)
    except subprocess.TimeoutExpired:
        raise SafetyError(f'{Path(command[0]).name} timed out after {timeout}s') from None
    except OSError:
        raise SafetyError(f'{Path(command[0]).name} unavailable') from None
    require(result.returncode == 0, f'{Path(command[0]).name} failed (exit {result.returncode}); output suppressed to protect secrets')
    return result.stdout

def read_json(path):
    try:
        return json.loads(Path(path).read_text())
    except (OSError, ValueError):
        raise SafetyError(f'JSON metadata missing or invalid: {path}') from None

def atomic_json(path, data):
    path = Path(path); path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix='.metadata-', dir=path.parent)
    try:
        with os.fdopen(fd, 'w') as stream:
            json.dump(data, stream, indent=2, sort_keys=True); stream.write('\n'); stream.flush(); os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if Path(name).exists(): Path(name).unlink()

@contextmanager
def lock(state):
    state = Path(state); state.mkdir(parents=True, exist_ok=True, mode=0o700)
    with (state / '.deployment.lock').open('a') as stream:
        try:
            fcntl.flock(stream, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            raise SafetyError('another deployment or recovery operation holds the filesystem lock') from None
        try: yield
        finally: fcntl.flock(stream, fcntl.LOCK_UN)

def private_file(path):
    path = Path(path)
    require(path.is_file() and not path.is_symlink(), f'required private file missing: {path.name}')
    require(path.stat().st_mode & 0o077 == 0, f'{path.name} permissions must exclude group/other')
    require(path.stat().st_size > 0, f'{path.name} is empty')
    return path

def validate_domain(domain):
    require(bool(re.fullmatch(r'(?=.{1,253}$)(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]{2,63}', domain or '')), 'explicit DNS domain required (without URL or port)')

def validate_manifest(data):
    require(isinstance(data, dict) and type(data.get('schemaVersion')) is int and data.get('schemaVersion') == 1, 'unsupported release schema')
    require(set(data)=={'schemaVersion','releaseVersion','gitSha','builtAt','platform','awsAccountId','awsRegion','environment','images','migrationCompatibility'},'unknown or missing manifest fields forbidden')
    require(all(isinstance(data.get(key),str) for key in ('releaseVersion','gitSha','builtAt','awsAccountId','awsRegion')),'manifest target metadata must use string values')
    require(bool(re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?', data.get('releaseVersion',''))), 'explicit semantic release required; floating tags forbidden')
    require(bool(re.fullmatch(r'[0-9a-f]{40}', data.get('gitSha',''))), 'full Git SHA required')
    require(bool(re.fullmatch(r'[0-9]{12}', data.get('awsAccountId',''))), 'explicit AWS account required')
    require(bool(re.fullmatch(r'[a-z]{2}(?:-[a-z]+)+-[0-9]', data.get('awsRegion',''))), 'explicit AWS region required')
    require(data.get('environment') == 'cloud-demo', 'only explicitly selected cloud-demo is supported')
    require(data.get('platform') in ('linux/amd64', 'linux/arm64'), 'explicit supported image architecture required')
    try:
        timestamp=datetime.fromisoformat(data['builtAt'].replace('Z','+00:00'))
        require(timestamp.tzinfo is not None,'build timestamp must include UTC offset')
    except (KeyError, TypeError, ValueError): raise SafetyError('valid build timestamp required') from None
    require(isinstance(data.get('images'),dict) and set(data['images']) == set(SERVICES), 'exactly all twelve release images required')
    registry = f"{data['awsAccountId']}.dkr.ecr.{data['awsRegion']}.amazonaws.com/orderflow/"
    for service, image in data['images'].items():
        require(isinstance(image,dict) and set(image)=={'uri','digest'}, 'invalid image record')
        digest=image.get('digest','')
        require(bool(re.fullmatch(r'sha256:[0-9a-f]{64}', digest)), 'sha256 image digest required')
        require(image.get('uri') == registry+service+'@'+digest, f'image repository/digest mismatch: {service}')
    compatibility=data.get('migrationCompatibility',{})
    require(isinstance(compatibility,dict) and set(compatibility)=={'backwardCompatible','files'},'unknown migration compatibility fields forbidden')
    require(type(compatibility.get('backwardCompatible')) is bool, 'explicit migration backward compatibility review required')
    require(isinstance(compatibility.get('files'),dict) and compatibility['files'], 'migration hashes required')
    for name,digest in compatibility['files'].items():
        require(isinstance(name,str) and isinstance(digest,str),'migration paths/hashes must be strings')
        require(not name.startswith('/') and '..' not in Path(name).parts and name.endswith('.sql'), 'invalid migration path')
        require(bool(re.fullmatch('[0-9a-f]{64}',digest)), 'invalid migration SHA256')
    return data

def validate_infrastructure_target(data, release):
    fields={'schemaVersion','releaseVersion','gitSha','platform','awsAccountId','awsRegion','environment','images'}
    require(isinstance(data,dict) and set(data)==fields,'unknown or missing infrastructure manifest fields forbidden')
    require(type(data['schemaVersion']) is int and data['schemaVersion']==1,'unsupported infrastructure image schema')
    for key in fields-{'schemaVersion','images'}:
        require(isinstance(data[key],str) and data[key]==release[key],f'infrastructure {key} does not match release manifest')
    require(isinstance(data['images'],dict) and set(data['images'])==set(INFRA_IMAGE_VARIABLES),
            'exactly caddy, kafka, node-exporter and grafana infrastructure images required')
    registry=f"{release['awsAccountId']}.dkr.ecr.{release['awsRegion']}.amazonaws.com/orderflow/"
    for service,image in data['images'].items():
        require(isinstance(image,dict) and set(image)=={'uri','digest'},'invalid infrastructure image record')
        digest=image['digest']
        require(isinstance(digest,str) and bool(re.fullmatch(r'sha256:[0-9a-f]{64}',digest)),
                'infrastructure sha256 registry manifest digest required')
        require(isinstance(image['uri'],str) and image['uri'] in {registry+repository+'@'+digest for repository in SERVICES},
                f'infrastructure repository/target/digest mismatch: {service}')
    return data

def validate_infrastructure_manifest(data, release):
    validate_manifest(release)
    return validate_infrastructure_target(data,release)

def infrastructure_required(compose_text):
    slots=set(re.findall(r'\$(?:\{)?(INFRA_IMAGE_[A-Z_]+)',compose_text))
    require(not slots or slots==set(INFRA_IMAGE_VARIABLES.values()),'all four infrastructure image slots required')
    return bool(slots)

def cloud_compose_text(repo):
    try: return (Path(repo)/'docker-compose.cloud.yml').read_text()
    except (OSError,UnicodeError): raise SafetyError('release cloud Compose missing or invalid') from None

def infrastructure_images(repo, release):
    if not infrastructure_required(cloud_compose_text(repo)): return {}
    path=Path(repo)/'deploy/infrastructure-images.json'
    require(path.is_file() and not path.is_symlink() and path.stat().st_size>0,
            'approved release infrastructure-images.json missing, empty or not a regular file')
    return validate_infrastructure_manifest(read_json(path),release)['images']

def migration_hashes(repo):
    return {str(p.relative_to(repo)):hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(Path(repo).glob('*/src/main/resources/db/migration/*.sql'))}

def check_append_only(previous, candidate):
    old=previous['migrationCompatibility']['files']; new=candidate['migrationCompatibility']['files']
    require(all(new.get(path)==digest for path,digest in old.items()), 'unsafe schema change: applied migration modified or removed')

def check_rollback(target, schema_release):
    validate_manifest(target); validate_manifest(schema_release)
    require(target['awsAccountId']==schema_release['awsAccountId'] and target['awsRegion']==schema_release['awsRegion'] and target['environment']==schema_release['environment'], 'rollback target environment mismatch')
    require(schema_release['migrationCompatibility']['backwardCompatible'] is True, 'unsafe schema rollback: compatibility not explicitly approved')
    check_append_only(target, schema_release)

def parse_env(path):
    values={}
    for line in private_file(path).read_text().splitlines():
        if not line.strip() or line.lstrip().startswith('#'): continue
        require('=' in line, 'invalid runtime.env line')
        key,value=line.split('=',1)
        require(bool(re.fullmatch('[A-Z][A-Z0-9_]*',key)) and key not in values, 'invalid or duplicate runtime variable')
        if value.startswith(('"',"'")):
            try: parts=shlex.split(value); require(len(parts)==1, 'invalid quoted runtime value'); value=parts[0]
            except ValueError: raise SafetyError('invalid quoted runtime value') from None
        values[key]=value
    return values

def target(args, data=None):
    require(bool(re.fullmatch('[0-9]{12}',args.account or '')), 'explicit AWS account required')
    require(bool(re.fullmatch(r'[a-z]{2}(?:-[a-z]+)+-[0-9]',args.region or '')), 'explicit AWS region required')
    require(args.environment=='cloud-demo', 'explicit environment cloud-demo required')
    validate_domain(args.domain)
    require(bool(re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?',args.release or '')), 'explicit semantic release required')
    if data:
        for attribute,key in [('account','awsAccountId'),('region','awsRegion'),('environment','environment'),('release','releaseVersion')]:
            require(getattr(args,attribute)==data[key], f'target {attribute} does not match release manifest')
    identity=json.loads(run(['aws','sts','get-caller-identity','--region',args.region,'--output','json']))
    require(identity.get('Account')==args.account, 'authenticated AWS account differs from explicit target')

def compose(args, data):
    infrastructure=infrastructure_images(args.repo,data)
    values=parse_env(args.runtime_env)
    required=['POSTGRES_PASSWORD','REDIS_PASSWORD','APP_PAYMENT_SIMULATOR_WEBHOOK_SECRET','GRAFANA_ADMIN_PASSWORD']
    required += [f'{owner}_POSTGRES_{suffix}' for owner in OWNERS for suffix in ('DB','USER','PASSWORD')]
    require(all(values.get(key) for key in required), 'required runtime secret or database configuration missing')
    require(values.get('ORDERFLOW_ENVIRONMENT')==args.environment and values.get('DOMAIN_NAME')==args.domain, 'runtime environment/domain mismatch')
    for name in ['jwt-private.pem','jwt-public.pem','backup.key','cloud-accounts.json']: private_file(Path(args.runtime_env).parent/name)
    for name in ('jwt-private.pem','jwt-public.pem'):
        key=Path(args.runtime_env).parent/name
        require(key.stat().st_uid==10001 and key.stat().st_gid==10001 and key.stat().st_mode & 0o777==0o400,'JWT files must be UID/GID10001 and mode0400')
    private_key=Path(args.runtime_env).parent/'jwt-private.pem'
    public_key=Path(args.runtime_env).parent/'jwt-public.pem'
    run(['openssl','rsa','-in',str(private_key),'-check','-noout'])
    derived=run(['openssl','pkey','-in',str(private_key),'-pubout','-outform','DER'],binary=True)
    configured=run(['openssl','pkey','-pubin','-in',str(public_key),'-outform','DER'],binary=True)
    require(derived==configured,'persistent JWT private/public keys do not match')
    env=os.environ.copy(); env.update(values)
    env.update({'AWS_ACCOUNT_ID':args.account,'AWS_REGION':args.region,'ORDERFLOW_ENVIRONMENT':args.environment,
                'RELEASE_VERSION':data['releaseVersion'],'DOMAIN_NAME':args.domain,
                'ECR_REGISTRY':f'{args.account}.dkr.ecr.{args.region}.amazonaws.com'})
    env.update({'IMAGE_'+name.upper().replace('-','_'):image['uri'] for name,image in data['images'].items()})
    # Frozen release metadata wins over ambient values and protected runtime.env alike.
    env.update({INFRA_IMAGE_VARIABLES[name]:image['uri'] for name,image in infrastructure.items()})
    command=['docker','compose','--profile','observability','--project-name','orderflow-cloud','--env-file',args.runtime_env,
             '-f',str(Path(args.repo)/'docker-compose.yml'),'-f',str(Path(args.repo)/'docker-compose.cloud.yml')]
    return command,env

def preflight(args, data):
    target(args,data)
    require(migration_hashes(Path(args.repo))==data['migrationCompatibility']['files'], 'release artifact migration hashes do not match manifest')
    require(shutil.disk_usage(args.state_dir).free >= args.minimum_free_gb*1024**3, 'insufficient disk space')
    cmd,env=compose(args,data)
    run(cmd+['config','--quiet'],env=env,cwd=args.repo)
    require(bool(args.verification_script) and Path(args.verification_script).is_file() and os.access(args.verification_script,os.X_OK), 'executable deployed verification hook required')
    return cmd,env

def check_records(records, expected):
    for service in expected:
        found=[r for r in records if r.get('Service')==service]
        require(len(found)==1, f'{service}: container missing or ambiguous')
        record=found[0]
        if service=='database-bootstrap':
            require(record.get('State')=='exited' and record.get('ExitCode')==0, 'database bootstrap has not completed successfully')
        else:
            require(record.get('State')=='running' and record.get('Health')=='healthy', f'{service}: not running and healthy')

def health(args, data):
    cmd,env=compose(args,data)
    expected=run(cmd+['config','--services'],env=env,cwd=args.repo).splitlines()
    require(set(SERVICES).issubset(expected), 'Compose omitted release services')
    deadline=time.monotonic()+args.timeout
    while True:
        text=run(cmd+['ps','--all','--format','json'],env=env,cwd=args.repo,timeout=min(30,args.timeout))
        try:
            records=json.loads(text) if text.lstrip().startswith('[') else [json.loads(line) for line in text.splitlines() if line]
            check_records(records,expected)
            return
        except (SafetyError,ValueError):
            require(time.monotonic()<deadline, 'deployment timeout: containers did not become healthy')
            time.sleep(min(3,max(0,deadline-time.monotonic())))

def private_prometheus_address(networks, expected_network):
    require(isinstance(expected_network,str) and expected_network and set(networks)=={expected_network},'Prometheus must use only the intended private Compose network')
    address=networks[expected_network].get('IPAddress','')
    try: ip=ipaddress.IPv4Address(address)
    except ipaddress.AddressValueError: raise SafetyError('Prometheus IPv4 missing') from None
    require(any(ip in ipaddress.ip_network(prefix) for prefix in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16')),'Prometheus address must be RFC1918 private IPv4')
    return address

def validate_accounts_value(value):
    try: accounts=json.loads(value)
    except ValueError: raise SafetyError('SSM cloud accounts file is invalid JSON') from None
    require(isinstance(accounts,dict) and set(accounts)=={'VENDOR','DELIVERY_AGENT','ADMIN'},'SSM journey account roles must be exactly VENDOR/DELIVERY_AGENT/ADMIN')
    emails=set()
    for item in accounts.values():
        require(isinstance(item,dict) and set(item)=={'email','password'},'journey credentials must contain only email/password')
        email=item['email']; password=item['password']
        require(isinstance(email,str) and bool(re.fullmatch(r'[a-z0-9][a-z0-9._+-]{0,120}@example\.test',email)) and email not in emails,'journey accounts must use distinct synthetic example.test emails')
        require(isinstance(password,str) and 16<=len(password)<=128,'journey passwords require 16..128 characters')
        emails.add(email)

def verify(args, data):
    health(args,data)
    cmd,env=compose(args,data)
    response=run(cmd+['exec','-T','service-registry','curl','--fail','--silent','--max-time','10','-H','Accept: application/json','http://localhost:8761/eureka/apps'],env=env,cwd=args.repo)
    registry=json.loads(response).get('applications',{}).get('application',[])
    if isinstance(registry,dict): registry=[registry]
    names={item.get('name') for item in registry if any(instance.get('status')=='UP' for instance in (item.get('instance',[]) if isinstance(item.get('instance',[]),list) else [item['instance']]))}
    require({service.upper() for service in SERVICES if service not in ('service-registry','frontend')}.issubset(names), 'Eureka registration missing or DOWN')
    for path in ['/','/api/products','/api/auth/.well-known/jwks.json']:
        run(['curl','--fail','--silent','--show-error','--max-time','20','--proto','=https','https://'+args.domain+path],timeout=25)
    require(args.verification_script and Path(args.verification_script).is_file(), 'deployed verification hook required for authentication/workflows/outbox/DLT')
    # Hook must use authorized accounts and fail on any critical check. No body/log output is printed.
    prometheus_id=run(cmd+['ps','--quiet','prometheus'],env=env,cwd=args.repo).strip()
    require(bool(prometheus_id),'private Prometheus container missing')
    addresses=json.loads(run(['docker','inspect','--format','{{json .NetworkSettings.Networks}}',prometheus_id]))
    configuration=json.loads(run(cmd+['config','--format','json'],env=env,cwd=args.repo))
    expected_network=configuration.get('networks',{}).get('private',{}).get('name')
    address=private_prometheus_address(addresses,expected_network)
    env.update({'ORDERFLOW_PROMETHEUS_URL':'http://'+address+':9090',
                'ORDERFLOW_CLOUD_ACCOUNTS_FILE':str(Path(args.runtime_env).parent/'cloud-accounts.json'),
                'ORDERFLOW_TARGET_ENVIRONMENT':args.environment})
    env.update({'E2E_BASE_URL':'https://'+args.domain,'ORDERFLOW_BASE_URL':'https://'+args.domain,'API_BASE_URL':'https://'+args.domain,'ORDERFLOW_PUBLIC_URL':'https://'+args.domain,'ORDERFLOW_CLOUD_E2E':'true'})
    run([args.verification_script],timeout=args.timeout,env=env,cwd=args.repo)

def record_healthy(state, data, verifier, repo=None, domain=None):
    verifier()
    current=Path(state)/'healthy.json'
    if current.exists() and read_json(current)!=data: atomic_json(Path(state)/'previous.json',read_json(current))
    atomic_json(current,data)
    if repo is not None:
        validate_domain(domain)
        pointer={'repo':str(Path(repo).resolve()),'gitSha':data['gitSha'],'domain':domain}
        atomic_json(Path(state)/'runtime-release.json',pointer)
        atomic_json(Path(state)/'boot-ready.json',pointer)

def backup_predeploy(args,data,cmd,env):
    # First deployment creates only private infrastructure/bootstrap before dumping all eight DBs.
    run(cmd+['up','-d','--no-build','--wait','--wait-timeout',str(args.timeout),'postgres'],env=env,cwd=args.repo,timeout=args.timeout+30)
    run(cmd+['up','--no-build','--no-deps','--abort-on-container-exit','--exit-code-from','database-bootstrap','database-bootstrap'],env=env,cwd=args.repo,timeout=args.timeout)
    backup_command=[sys.executable,str(REPO/'scripts/backup/backup.py'),'backup','--runtime-env',args.runtime_env,
                    '--repo',args.repo,'--output',args.backup_dir,
                    '--key-file',str(Path(args.runtime_env).parent/'backup.key')]
    if not Path(args.state_dir,'healthy.json').exists() and not Path(args.state_dir,'schema-release.json').exists():
        backup_command.append('--allow-uninitialized')
    schema_source=Path(args.state_dir)/'schema-source.json'
    migration_repo=read_json(schema_source)['repo'] if schema_source.exists() else args.repo
    backup_command.extend(['--migration-repo',migration_repo])
    # IMAGE variables inherited by backup so Compose parses all required substitutions.
    backup=json.loads(run(backup_command,env=env,timeout=args.timeout))
    require(Path(backup['backup']).is_dir(),'predeployment backup missing')
    backup.update({'releaseVersion':data['releaseVersion'],'candidateGitSha':data['gitSha']})
    atomic_json(Path(args.state_dir)/'predeploy-backup.json',backup)

def pull(args,data):
    cmd,env=compose(args,data)
    token=run(['aws','ecr','get-login-password','--region',args.region])
    run(['docker','login','--username','AWS','--password-stdin',f'{args.account}.dkr.ecr.{args.region}.amazonaws.com'],input=token)
    run(cmd+['pull'],timeout=args.timeout,env=env,cwd=args.repo)
    for service,image in data['images'].items():
        platform=run(['docker','image','inspect','--format','{{.Os}}/{{.Architecture}}',image['uri']]).strip()
        require(platform==data['platform'],f'pulled image architecture mismatch: {service}')
    if infrastructure_required(cloud_compose_text(args.repo)):
        for service,variable in INFRA_IMAGE_VARIABLES.items():
            uri=env[variable]
            platform=run(['docker','image','inspect','--format','{{.Os}}/{{.Architecture}}',uri]).strip()
            require(platform==data['platform'],f'pulled infrastructure image architecture mismatch: {service}')
            digests=json.loads(run(['docker','image','inspect','--format','{{json .RepoDigests}}',uri]))
            require(isinstance(digests,list) and uri in digests,f'pulled infrastructure registry manifest digest mismatch: {service}')
    uid=run(['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL','--entrypoint','id',data['images']['auth-service']['uri'],'-u']).strip()
    gid=run(['docker','run','--rm','--network','none','--read-only','--cap-drop','ALL','--entrypoint','id',data['images']['auth-service']['uri'],'-g']).strip()
    require(uid=='10001' and gid=='10001','auth image runtime UID/GID must match persistent JWT files')

def initial_seed_guard(args):
    if not args.initial_demo_seed: return
    require(args.environment=='cloud-demo','initial synthetic seed is restricted to explicitly selected cloud-demo')
    require(not Path(args.state_dir,'healthy.json').exists(),'initial seed refused: a healthy release already exists')
    require(not Path(args.state_dir,'initial-seed.json').exists(),'initial seed already completed; deploy with default seed disabled')

def initial_seed(args,data,env):
    if not args.initial_demo_seed: return
    initial_seed_guard(args)
    credentials=private_file(Path(args.runtime_env).parent/'cloud-accounts.json')
    validate_accounts_value(credentials.read_text())
    script=Path(args.repo)/'scripts/cloud/seed-demo.sh'
    require(script.is_file() and os.access(script,os.X_OK),'guarded synthetic seed executable missing')
    approved=env.copy()
    approved.update({'ORDERFLOW_DEMO_SEED':'true','ORDERFLOW_TRUSTED_HOST_ROLE_GRANT':'true',
                     'ORDERFLOW_CLOUD_E2E':'true','ORDERFLOW_PUBLIC_URL':'https://'+args.domain,
                     'ORDERFLOW_TARGET_ENVIRONMENT':args.environment,'ORDERFLOW_CLOUD_ACCOUNTS_FILE':str(credentials),
                     'ORDERFLOW_RUNTIME_ENV_FILE':args.runtime_env,'ORDERFLOW_COMPOSE_PROJECT_NAME':'orderflow-cloud'})
    run([str(script),'--initial-only'],timeout=args.timeout,env=approved,cwd=args.repo)
    atomic_json(Path(args.state_dir)/'initial-seed.json',{'status':'SUCCESS','releaseVersion':data['releaseVersion'],'gitSha':data['gitSha'],'scope':'explicit initial synthetic fixture seed'})

def deploy(args,data):
    with lock(args.state_dir):
        initial_seed_guard(args)
        cmd,env=preflight(args,data)
        current=Path(args.state_dir)/'healthy.json'
        if current.exists(): check_append_only(validate_manifest(read_json(current)),data)
        schema=Path(args.state_dir)/'schema-release.json'
        if schema.exists(): check_append_only(validate_manifest(read_json(schema)),data)
        # Persist attempted schema BEFORE application startup: partially applied migrations make
        # rollback safety depend on this candidate even when healthy.json remains the old release.
        pull(args,data)
        backup_predeploy(args,data,cmd,env)
        atomic_json(Path(args.state_dir)/'schema-release.json',data)
        atomic_json(Path(args.state_dir)/'schema-source.json',{'repo':str(Path(args.repo).resolve())})
        # Invalidate Docker boot replay before replacing any application containers.
        # A failed candidate cannot auto-start at the next Console boot.
        (Path(args.state_dir)/'boot-ready.json').unlink(missing_ok=True)
        run(cmd+['up','-d','--no-build','--wait','--wait-timeout',str(args.timeout)],env=env,cwd=args.repo,timeout=args.timeout+30)
        if args.initial_demo_seed:
            health(args,data)
            initial_seed(args,data,env)
        record_healthy(args.state_dir,data,lambda:verify(args,data),args.repo,args.domain)

def rollback(args,data):
    with lock(args.state_dir):
        require(Path(args.state_dir,'healthy.json').exists(),'last healthy release metadata missing')
        schema=validate_manifest(read_json(Path(args.state_dir)/'schema-release.json'))
        check_rollback(data,schema)
        cmd,env=preflight(args,data)
        pull(args,data)
        backup_predeploy(args,data,cmd,env)
        # Invalidate Docker boot replay before replacing any application containers.
        # A failed candidate cannot auto-start at the next Console boot.
        (Path(args.state_dir)/'boot-ready.json').unlink(missing_ok=True)
        run(cmd+['up','-d','--no-build','--wait','--wait-timeout',str(args.timeout)],env=env,cwd=args.repo,timeout=args.timeout+30)
        record_healthy(args.state_dir,data,lambda:verify(args,data),args.repo,args.domain)
        # Keep schema-release.json at the newest attempted schema, even after reverting code.

def fetch_secrets(args):
    target(args)
    prefix=f'/orderflow/{args.environment}/runtime/'
    response=json.loads(run(['aws','ssm','get-parameters-by-path','--path',prefix,'--recursive','--with-decryption','--region',args.region,'--output','json']))
    parameters=response.get('Parameters',[])
    require(all(item.get('Type')=='SecureString' for item in parameters),'runtime SSM parameters must be SecureString')
    values={item['Name'][len(prefix):]:item['Value'] for item in parameters if item['Name'].startswith(prefix)}
    require('runtime.env' in values and all(key in values for key in ('jwt-private.pem','jwt-public.pem','backup.key','cloud-accounts.json')), 'SSM runtime configuration, persistent JWT keys, backup key or journey credentials missing')
    require(len(parameters)==len(values),'duplicate or out-of-prefix SSM runtime parameters')
    validate_accounts_value(values['cloud-accounts.json'])
    require(len(values['backup.key'].encode())>=32,'SSM backup key must contain at least32 random bytes')
    require(os.geteuid()==0,'secret installation requires root on SSM host for UID10001 ownership')
    directory=Path(args.runtime_env).parent; directory.mkdir(parents=True,exist_ok=True,mode=0o700)
    require(directory.stat().st_mode & 0o077 == 0, 'secret directory must be mode 0700')
    # Never silently rotate JWT/backup keys. Existing bytes must match SSM exactly.
    for name in ('jwt-private.pem','jwt-public.pem','backup.key'):
        path=directory/name
        if path.exists():
            require(private_file(path).read_text()==values[name], f'{name} changed; explicit key rotation procedure required')
    for name in ('runtime.env','jwt-private.pem','jwt-public.pem','backup.key','cloud-accounts.json'):
        path=Path(args.runtime_env) if name=='runtime.env' else directory/name
        if name in ('jwt-private.pem','jwt-public.pem','backup.key') and path.exists():
            if name.startswith('jwt-'):
                require(path.stat().st_uid==10001 and path.stat().st_gid==10001 and path.stat().st_mode & 0o777==0o400,'existing JWT file ownership or mode incorrect')
            continue
        fd,temp=tempfile.mkstemp(prefix='.secret-',dir=directory)
        try:
            with os.fdopen(fd,'w') as stream: stream.write(values[name])
            if name.startswith('jwt-'):
                require(os.geteuid()==0,'JWT key installation requires root on SSM host for UID10001 file ownership')
                os.chown(temp,10001,10001); os.chmod(temp,0o400)
            else: os.chmod(temp,0o600)
            os.replace(temp,path)
        finally:
            if Path(temp).exists(): Path(temp).unlink()

def generate(args):
    target(args)
    require(args.platform in ('linux/amd64','linux/arm64'), 'explicit platform required')
    require(args.compatibility in ('true','false'),'explicit migration compatibility review required')
    sha=run(['git','rev-parse','HEAD'],cwd=args.repo).strip()
    images={}
    for service in SERVICES:
        result=json.loads(run(['aws','ecr','describe-images','--registry-id',args.account,'--repository-name','orderflow/'+service,
                             '--image-ids','imageTag='+args.release+'-'+sha,'--region',args.region,'--output','json']))
        details=result.get('imageDetails',[]); require(len(details)==1,'ECR image digest missing or ambiguous')
        digest=details[0]['imageDigest']
        images[service]={'digest':digest,'uri':f'{args.account}.dkr.ecr.{args.region}.amazonaws.com/orderflow/{service}@{digest}'}
    data={'schemaVersion':1,'releaseVersion':args.release,'gitSha':sha,'builtAt':datetime.now(timezone.utc).isoformat(),
          'platform':args.platform,'awsAccountId':args.account,'awsRegion':args.region,'environment':args.environment,
          'images':images,'migrationCompatibility':{'backwardCompatible':args.compatibility=='true','files':migration_hashes(Path(args.repo))}}
    validate_manifest(data)
    require(not Path(args.manifest).exists(),'immutable release manifest already exists')
    atomic_json(args.manifest,data)

def check_invocation(invocation):
    status=invocation.get('Status')
    if status=='Success':
        require(invocation.get('ResponseCode')==0,'SSM command status/exit code inconsistent')
        return True
    require(status in ('Pending','InProgress','Delayed'), 'SSM remote execution failed: '+str(status))
    return False

def validate_artifact(path, data, initial_seed=False):
    require(Path(path).is_file() and not Path(path).is_symlink(),'release artifact missing or symlink')
    required={'deploy/release-manifest.json','scripts/deploy/remote-entrypoint.sh','scripts/deploy/cloud.py',
              'scripts/cloud/verify-journey.sh','scripts/cloud/verify_journey.py','docker-compose.yml','docker-compose.cloud.yml'}
    if initial_seed: required.update({'scripts/cloud/seed-demo.sh','scripts/cloud/seed_demo.py'})
    try:
        with tarfile.open(path,'r:gz') as archive:
            files={}; total=0
            for member in archive.getmembers():
                part=PurePosixPath(member.name)
                require(not part.is_absolute() and '..' not in part.parts and not member.issym() and not member.islnk(),'unsafe release archive path/link')
                require(member.isfile() or member.isdir(),'special file forbidden in release archive')
                name=str(part)
                require(name not in files,'duplicate path in release archive')
                require(member.size<=64*1024**2,'release member exceeds size bound')
                total+=member.size; require(total<=1024**3,'release archive exceeds size bound')
                files[name]=member
            require(required.issubset(files),'release archive incomplete')
            compose_member=files['docker-compose.cloud.yml']
            require(compose_member.isfile(),'release archive cloud Compose must be a regular file')
            compose_text=archive.extractfile(compose_member).read().decode('utf-8')
            if '/opt/orderflow/kafka-safe-bootstrap.sh' in compose_text:
                guard=files.get('infrastructure/kafka/safe-bootstrap.sh')
                require(guard is not None and guard.isfile() and guard.size>0,
                        'release archive Kafka storage guard missing or empty')
            embedded=json.load(archive.extractfile(files['deploy/release-manifest.json']))
            require(validate_manifest(embedded)==data,'release archive manifest does not match external immutable manifest')
            if infrastructure_required(compose_text):
                infrastructure=files.get('deploy/infrastructure-images.json')
                require(infrastructure is not None and infrastructure.isfile() and infrastructure.size>0,
                        'release archive approved infrastructure-images.json missing, empty or not a regular file')
                validate_infrastructure_manifest(json.load(archive.extractfile(infrastructure)),embedded)
            migration_files={name:hashlib.sha256(archive.extractfile(member).read()).hexdigest()
                             for name,member in files.items() if member.isfile() and re.fullmatch(r'[^/]+/src/main/resources/db/migration/[^/]+\.sql',name)}
            require(migration_files==data['migrationCompatibility']['files'],'archive SQL hashes differ from manifest')
    except (tarfile.TarError,OSError,ValueError,TypeError):
        raise SafetyError('release archive invalid') from None

def remote(args):
    data=validate_manifest(read_json(args.manifest)); target(args,data)
    require(bool(re.fullmatch(r'i-[0-9a-f]{8,17}',args.instance_id or '')), 'explicit SSM instance required')
    require(bool(re.fullmatch(r'[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]',args.artifact_bucket or '')), 'private release artifact bucket required')
    validate_artifact(args.artifact,data,args.initial_demo_seed)
    digest=hashlib.sha256(Path(args.artifact).read_bytes()).hexdigest()
    key=f'releases/{args.release}/{data["gitSha"]}/{digest}.tar.gz'
    run(['aws','s3','cp',args.artifact,f's3://{args.artifact_bucket}/{key}','--region',args.region,'--sse','AES256','--only-show-errors'],timeout=args.timeout)
    directory=f'/opt/orderflow/releases/{args.release}-{data["gitSha"]}'
    # Artifact includes remote-entrypoint.sh and release-manifest.json. Runtime instance role
    # fetches transport from a private bucket; no signed URL or long-lived SSH secret in logs.
    commands=['set -eu','umask 077',f'test ! -e {shlex.quote(directory)}',f'mkdir -p {shlex.quote(directory)}',
              f'aws s3 cp {shlex.quote("s3://"+args.artifact_bucket+"/"+key)} {shlex.quote(directory+"/release.tar.gz")} --region {shlex.quote(args.region)} --only-show-errors',
              f'echo {shlex.quote(digest+"  "+directory+"/release.tar.gz")} | sha256sum -c - >/dev/null',
              f'tar --extract --gzip --file {shlex.quote(directory+"/release.tar.gz")} --directory {shlex.quote(directory)} --no-same-owner',
              'bash '+shlex.quote(directory+'/scripts/deploy/remote-entrypoint.sh')+' '+ ' '.join(shlex.quote(value) for value in [args.account,args.region,args.environment,args.domain,args.release,directory,'true' if args.initial_demo_seed else 'false'])]
    response=json.loads(run(['aws','ssm','send-command','--instance-ids',args.instance_id,'--document-name','AWS-RunShellScript',
                            '--parameters',json.dumps({'commands':commands,'executionTimeout':[str(args.timeout)]}),
                            '--timeout-seconds','60','--region',args.region,'--output','json']))
    command_id=response['Command']['CommandId']; deadline=time.monotonic()+args.timeout+60
    while time.monotonic()<deadline:
        try:
            invocation=json.loads(run(['aws','ssm','get-command-invocation','--command-id',command_id,'--instance-id',args.instance_id,'--region',args.region,'--output','json']))
        except SafetyError:
            time.sleep(3); continue  # bounded eventual consistency; status output remains suppressed
        if check_invocation(invocation):
            print(json.dumps({'commandId':command_id,'status':'Success','responseCode':0})); return
        time.sleep(3)
    raise SafetyError('SSM remote deployment timeout; inspect invocation before retrying, remote may still be running')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['validate','validate-artifact','preflight','fetch-secrets','generate','pull','health','verify','deploy','rollback','backup-predeploy','remote'])
    for key in ('account','region','environment','domain','release'): parser.add_argument('--'+key,default='')
    parser.add_argument('--manifest',default='deploy/release-manifest.json')
    parser.add_argument('--runtime-env',default='/opt/orderflow/config/runtime.env')
    parser.add_argument('--state-dir',default='/opt/orderflow/releases/state')
    parser.add_argument('--backup-dir',default='/opt/orderflow/backups')
    parser.add_argument('--initial-demo-seed',action='store_true',help='explicit first cloud-demo deployment only; register new synthetic role accounts before full journey verification')
    parser.add_argument('--repo',default=str(REPO))
    parser.add_argument('--timeout',type=int,default=600)
    parser.add_argument('--minimum-free-gb',type=int,default=5)
    parser.add_argument('--verification-script',default='')
    parser.add_argument('--platform',default='')
    parser.add_argument('--compatibility',default='')
    parser.add_argument('--instance-id',default='')
    parser.add_argument('--artifact-bucket',default='')
    parser.add_argument('--artifact',default='')
    args=parser.parse_args()
    require(1<=args.timeout<=3600,'timeout must be 1..3600 seconds')
    require(args.minimum_free_gb>=1,'minimum free disk space must be positive')
    if args.action=='validate': validate_manifest(read_json(args.manifest)); print('release manifest valid; no deployment performed'); return
    if args.action=='validate-artifact':
        validate_artifact(args.artifact,validate_manifest(read_json(args.manifest)),args.initial_demo_seed)
        print('release artifact valid; no deployment performed'); return
    if args.action in ('fetch-secrets','generate','remote'):
        if args.action=='fetch-secrets':
            with lock(args.state_dir): fetch_secrets(args)
        else: {'generate':generate,'remote':remote}[args.action](args)
        return
    data=validate_manifest(read_json(args.manifest))
    Path(args.state_dir).mkdir(parents=True,exist_ok=True,mode=0o700)
    if args.action in ('deploy','rollback'): {'deploy':deploy,'rollback':rollback}[args.action](args,data)
    else:
        target(args,data)
        if args.action=='preflight': preflight(args,data)
        elif args.action=='pull': pull(args,data)
        elif args.action=='health': health(args,data)
        elif args.action=='verify': verify(args,data)
        elif args.action=='backup-predeploy':
            cmd,env=preflight(args,data); backup_predeploy(args,data,cmd,env)
    print(f'{args.action} completed for {data["releaseVersion"]}; deployed customer verification is credited only by verify/deploy')

if __name__=='__main__':
    try: main()
    except (SafetyError,KeyError,ValueError,TypeError,AttributeError) as error:
        # Parsing values or private command output are never interpolated in unexpected errors.
        print('ERROR: '+str(error) if isinstance(error,SafetyError) else 'ERROR: malformed external metadata',file=sys.stderr)
        sys.exit(1)

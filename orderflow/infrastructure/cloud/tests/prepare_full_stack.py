#!/usr/bin/env python3
"""Prepare a fresh disposable local TLS fixture; never start/stop an app stack.
Only a new /tmp/orderflow-phase10-local-* directory is created. Optional permission
preparation runs one bounded container against this freshly generated directory.
"""
import argparse
import base64
import json
import os
from pathlib import Path
import re
import secrets
import shlex
import shutil
import subprocess
import tempfile
import uuid

ROOT=Path(__file__).resolve().parents[3]
APPS='service-registry api-gateway auth-service product-service cart-service inventory-service order-service payment-service delivery-service notification-service operations-service frontend'.split()
OWNERS='AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS'.split()
PROJECT='orderflow-phase10-local'
UIDS={'postgres':(70,70),'redis':(999,1000),'kafka':(1000,1000),'prometheus':(65534,65534),'grafana':(472,472)}


def prepare(platform='linux/amd64'):
    if platform not in ('linux/amd64', 'linux/arm64'):
        raise ValueError('Local application platform must be linux/amd64 or linux/arm64')
    architecture = platform.split('/')[1]
    if not shutil.which('openssl'):
        raise RuntimeError('openssl is required for fresh local signing keys')
    base=Path(tempfile.mkdtemp(prefix='orderflow-phase10-local-',dir='/tmp'))
    base.chmod(0o700)
    config=base/'config';data=base/'data'
    config.mkdir(mode=0o700);data.mkdir(mode=0o755)
    for name in ['postgres','redis','kafka','caddy/data','caddy/config','caddy/logs','prometheus','grafana','node-exporter']:
        path=data/name;path.mkdir(parents=True,mode=0o755,exist_ok=True)
    run=uuid.uuid4().hex[:12]
    private=config/'jwt-private.pem';public=config/'jwt-public.pem'
    subprocess.run(['openssl','genpkey','-algorithm','RSA','-pkeyopt','rsa_keygen_bits:2048','-out',str(private)],capture_output=True,check=True,timeout=15)
    subprocess.run(['openssl','pkey','-in',str(private),'-pubout','-out',str(public)],capture_output=True,check=True,timeout=10)
    for key in [private,public]:key.chmod(0o600)
    (config/'backup.key').write_text(secrets.token_hex(32)+'\n');(config/'backup.key').chmod(0o600)
    account_file=config/'cloud-accounts.json'
    account_file.write_text(json.dumps({role:{'email':'phase10-'+role.lower().replace('_','-')+'-'+run+'@example.test','password':secrets.token_urlsafe(32)} for role in ('VENDOR','DELIVERY_AGENT','ADMIN')},indent=2)+'\n')
    account_file.chmod(0o600)
    env={key:secrets.token_hex(24) for key in re.findall(r'\$\{([A-Z_]+):\?',(ROOT/'docker-compose.cloud.yml').read_text())}
    # Synthetic immutable references satisfy cloud substitution during fixture validation.
    # The isolated local overlay/application builds remain a separate unapproved test scope.
    for name in ('CADDY', 'KAFKA', 'NODE_EXPORTER', 'GRAFANA'):
        env['INFRA_IMAGE_'+name] = '123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/frontend@sha256:'+'a'*64
    env.update(ORDERFLOW_ENVIRONMENT='cloud-demo',AWS_ACCOUNT_ID='123456789012',AWS_REGION='us-east-1',RELEASE_VERSION='v10.0.0-local',ECR_REGISTRY='123456789012.dkr.ecr.us-east-1.amazonaws.com',DOMAIN_NAME='localhost:8443',ACME_EMAIL='local-operator@example.test',DATA_ROOT=str(data),CONFIG_ROOT=str(config),BACKUP_KEY_FILE=str(config/'backup.key'),POSTGRES_USER='local_admin',GRAFANA_ADMIN_USER='local_admin',APP_JWT_KEY_ID='phase10-local-'+run,KAFKA_CLUSTER_ID=base64.urlsafe_b64encode(secrets.token_bytes(16)).decode().rstrip('='))
    for owner in OWNERS:
        for suffix in ('DB','USER'):env[owner+'_POSTGRES_'+suffix]='local_'+owner.lower()
    # Required cloud interpolation remains valid; test override replaces all12
    # image URIs with local tags. Nothing is published or pushed to this registry.
    for name in APPS:
        env['IMAGE_'+name.upper().replace('-','_')]='123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/'+name+'@sha256:'+'a'*64
    runtime=config/'runtime.env';runtime.write_text('\n'.join(k+'='+v for k,v in sorted(env.items()))+'\n');runtime.chmod(0o600)
    caddy=(ROOT/'infrastructure/caddy/Caddyfile').read_text().replace('{$DOMAIN_NAME} {','{$DOMAIN_NAME} {\n\ttls internal',1)
    caddy_path=config/'Caddyfile.local';caddy_path.write_text(caddy);caddy_path.chmod(0o644)
    lines=['name: '+PROJECT,'','# TEST ONLY: lower memory, local image tags, local CA and loopback port overrides.','services:']
    for name in APPS:
        lines.extend(['  '+name+':','    image: orderflow/'+name+':phase10-local-'+architecture,'    platform: '+platform,'    mem_limit: '+('224m' if name=='service-registry' else '256m' if name=='frontend' else '512m' if name in ('order-service','inventory-service') else '384m')])
        if name!='frontend':
            lines.extend(['    environment:','      JAVA_TOOL_OPTIONS: "-Xms32m -Xmx128m -XX:+UseSerialGC -XX:ActiveProcessorCount=2 -XX:+ExitOnOutOfMemoryError"'])
        else:lines.extend(['    environment:','      NODE_OPTIONS: --max-old-space-size=160'])
    lines.extend(['  postgres:','    volumes: !override '+json.dumps([str(data/'postgres')+':/var/lib/postgresql/data']),'    mem_limit: 256m','  redis:','    volumes: !override '+json.dumps([str(data/'redis')+':/data']),'    mem_limit: 128m','    command: !override ["/bin/sh", "-ec", "exec redis-server --appendonly yes --appendfsync everysec --requirepass \\\"$$REDIS_PASSWORD\\\" --maxmemory 96mb --maxmemory-policy noeviction"]','  kafka:','    volumes: !override '+json.dumps([str(data/'kafka')+':/var/lib/kafka/data',str(ROOT/'infrastructure/cloud/tests/kafka-native-health.sh')+':/opt/orderflow/kafka-native-health.sh:ro']),'    # NativeARM infrastructure is intentional; application platform is selected explicitly.','    healthcheck: !override {test: [CMD, /bin/sh, /opt/orderflow/kafka-native-health.sh], interval: 10s, timeout: 5s, retries: 20, start_period: 20s}','    image: apache/kafka-native:3.9.1','    # Test-only native image uses its own launcher, not the guarded JVM command.','    command: !reset null','    platform: linux/arm64','    mem_limit: 384m','    environment:','      KAFKA_HEAP_OPTS: -Xms128m -Xmx192m','  caddy:','    ports: !override ["127.0.0.1:8443:8443", "127.0.0.1:18080:80"]','    mem_limit: 96m','    volumes: !override '+json.dumps([str(caddy_path)+':/etc/caddy/Caddyfile:ro',str(data/'caddy/data')+':/data',str(data/'caddy/config')+':/config',str(data/'caddy/logs')+':/var/log/caddy']),'  prometheus:','    volumes: !override '+json.dumps([str(ROOT/'infrastructure/cloud/monitoring/prometheus.yml')+':/etc/prometheus/prometheus.yml:ro',str(ROOT/'monitoring/prometheus/rules')+':/etc/prometheus/rules:ro',str(ROOT/'infrastructure/cloud/monitoring/cloud-alerts.yml')+':/etc/prometheus/cloud-alerts.yml:ro',str(data/'prometheus')+':/prometheus']),'    ports: !override ["127.0.0.1:19090:9090"]','    mem_limit: 160m','  grafana:','    volumes: !override '+json.dumps([str(ROOT/'monitoring/grafana/provisioning')+':/etc/grafana/provisioning:ro',str(ROOT/'monitoring/grafana/dashboards')+':/var/lib/grafana/dashboards:ro',str(data/'grafana')+':/var/lib/grafana']),'    ports: !override ["127.0.0.1:19001:3000"]','    mem_limit: 192m','    environment:','      GF_SERVER_ROOT_URL: http://localhost:19001','  node-exporter:','    # DockerDesktop root is not an rslave/shared mount. Test-only override.','    volumes: !override '+json.dumps(['/:/host:ro',str(data/'node-exporter')+':/textfile:ro']),'','# Direct test bindmounts use DockerDesktop host-path translation; cloud EBS',' # local-driver devices refer to LinuxVM paths and must not be used on macOS.'.strip(),'volumes: !override {}'])
    overlay=base/'local-compose.yml';overlay.write_text('\n'.join(lines)+'\n');overlay.chmod(0o600)
    # Keep container chmod scope limited to generated artifacts: no recursive
    # ownership change and no source repository/user paths are mounted here.
    commands=['set -eu']
    for name,(uid,gid) in UIDS.items():commands.append('chown '+str(uid)+':'+str(gid)+' /stage/data/'+name)
    commands.extend(['chown 10001:10001 /stage/config/jwt-private.pem /stage/config/jwt-public.pem','chmod 0400 /stage/config/jwt-private.pem /stage/config/jwt-public.pem'])
    permission_script=base/'prepare-permissions.sh';permission_script.write_text('#!/bin/sh\n'+'\n'.join(commands)+'\n');permission_script.chmod(0o600)
    cmd=['docker','compose','--project-name',PROJECT,'--env-file',str(runtime),'-f',str(ROOT/'docker-compose.yml'),'-f',str(ROOT/'docker-compose.cloud.yml'),'-f',str(overlay),'--profile','observability']
    permission_command=['docker','run','--rm','--memory','64m','--network','none','--entrypoint','/bin/sh','--mount','type=bind,src='+str(base)+',dst=/stage','postgres:16-alpine','/stage/prepare-permissions.sh']
    metadata={'projectName':PROJECT,'applicationPlatform':platform,'directory':str(base),'runtimeEnv':str(runtime),'accountsFile':str(account_file),'overlay':str(overlay),'localCaFile':str(config/'local-ca.pem'),'composeCommand':cmd,'permissionCommand':permission_command}
    metadata_file=base/'fixture.json';metadata_file.write_text(json.dumps(metadata,indent=2)+'\n');metadata_file.chmod(0o600)
    return metadata


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--application-platform', choices=('linux/amd64', 'linux/arm64'), default='linux/amd64', help='Match explicitly built local images; native ARM64 avoids emulation on a small Apple Silicon Docker VM')
    parser.add_argument('--prepare-permissions',action='store_true',help='Run only the bounded generated-directory chown container; no app startup')
    args=parser.parse_args()
    metadata=prepare(args.application_platform)
    if args.prepare_permissions:
        subprocess.run(metadata['permissionCommand'],capture_output=True,check=True,timeout=30)
    cmd=shlex.join(metadata['composeCommand'])
    print('Prepared fresh local fixture: '+metadata['directory'])
    print('Metadata: '+str(Path(metadata['directory'])/'fixture.json'))
    if not args.prepare_permissions:print('Prepare ownership only: '+shlex.join(metadata['permissionCommand']))
    print('Validate only: '+cmd+' config --quiet')
    print('When rootimagebuildscomplete, explicitly start: '+cmd+' up -d --wait --wait-timeout 300')
    print('Afterstartup extract localCA: '+cmd+' cp caddy:/data/caddy/pki/authorities/local/root.crt '+metadata['localCaFile'])
    print('Shared gates (no secrets): ORDERFLOW_CLOUD_E2E=true ORDERFLOW_TARGET_ENVIRONMENT=cloud-demo AWS_ACCOUNT_ID=123456789012 ORDERFLOW_LOCAL_TLS_TEST=true ORDERFLOW_PUBLIC_URL=https://localhost:8443 ORDERFLOW_CA_FILE='+metadata['localCaFile']+' ORDERFLOW_CLOUD_ACCOUNTS_FILE='+metadata['accountsFile']+' ORDERFLOW_PROMETHEUS_URL=http://127.0.0.1:19090 ORDERFLOW_RUNTIME_ENV_FILE='+metadata['runtimeEnv']+' ORDERFLOW_COMPOSE_PROJECT_NAME='+PROJECT)
    print('Seed adds: ORDERFLOW_DEMO_SEED=true ORDERFLOW_TRUSTED_HOST_ROLE_GRANT=true; scripts/cloud/seed-demo.sh --dry-run then scripts/cloud/seed-demo.sh')
    print('Journey: scripts/cloud/verify-journey.sh; performance adds ORDERFLOW_PERFORMANCE_SMOKE=true then scripts/cloud/performance-smoke.sh')
    print('No application started/stopped. No existing path, volume or userdata deleted. LocalCA is not publicly trusted TLS.')

if __name__=='__main__':main()

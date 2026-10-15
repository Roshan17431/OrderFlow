#!/usr/bin/env python3
"""Root-only controller installed only on the Terraform-tagged OrderFlow demo host."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timezone
import fcntl
import importlib.util
import json
import math
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile
import time
import urllib.request
from on_demand import Aws, SafetyError, guard, require, validate_config

CONFIG=Path('/etc/orderflow-demo.json')
STATE=Path('/run/orderflow-demo')
RELEASES=Path('/opt/orderflow/releases')

def run(command,timeout=60,**kwargs):
    try:
        result=subprocess.run(command,capture_output=True,text=True,timeout=timeout,**kwargs)
        require(result.returncode==0,'Host command failed; inspect private service journal')
        return result.stdout.strip()
    except (OSError,subprocess.TimeoutExpired):
        raise SafetyError('Host command unavailable or timed out') from None

def hours(value):
    value=float(value)
    require(math.isfinite(value) and .25<=value<=24,'Runtime must be0.25..24 hours')
    return value

def session(existing,boot,uptime,runtime):
    if existing and existing.get('bootId')==boot:
        require(isinstance(existing.get('deadlineUptime'),(int,float)) and math.isfinite(existing['deadlineUptime']), 'Invalid session deadline')
        return existing
    return {'bootId':boot,'deadlineUptime':hours(runtime)*3600}

def extend_session(existing,uptime,runtime):
    require(not existing.get('shutdownRequested'),'Shutdown already committed; extension refused')
    deadline=uptime+hours(runtime)*3600
    require(deadline>existing['deadlineUptime'],'Extension must move the deadline later; current session unchanged')
    return {**existing,'deadlineUptime':deadline}

def write_session(value):
    fd,name=tempfile.mkstemp(prefix='.session-',dir=STATE)
    try:
        with os.fdopen(fd,'w') as stream:
            json.dump(value,stream);stream.flush();os.fsync(stream.fileno())
        os.replace(name,STATE/'session.json')
    finally:
        if Path(name).exists():Path(name).unlink()

def audit_event(reason):
    event={'timestamp':datetime.now(timezone.utc).isoformat(),'reason':reason}
    print(json.dumps(event),file=sys.stderr)
    if os.path.ismount('/opt/orderflow'):
        directory=RELEASES/'state';directory.mkdir(mode=0o700,exist_ok=True)
        fd=os.open(directory/'on-demand-events.jsonl',os.O_WRONLY|os.O_CREAT|os.O_APPEND|os.O_NOFOLLOW,0o600)
        with os.fdopen(fd,'a') as stream:
            stream.write(json.dumps(event)+'\n');stream.flush();os.fsync(stream.fileno())


def imds_identity():
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
    try:
        request=urllib.request.Request('http://169.254.169.254/latest/api/token',method='PUT',headers={'X-aws-ec2-metadata-token-ttl-seconds':'60'})
        with opener.open(request,timeout=3) as response:token=response.read(4096).decode()
        request=urllib.request.Request('http://169.254.169.254/latest/dynamic/instance-identity/document',headers={'X-aws-ec2-metadata-token':token})
        with opener.open(request,timeout=3) as response:return json.loads(response.read(16384))
    except Exception:
        raise SafetyError('IMDSv2 identity unavailable; refusing host operation') from None

def verified_host(c):
    require(os.geteuid()==0,'Root through approved SSM required')
    identity=imds_identity()
    require(identity.get('accountId')==c['account'] and identity.get('region')==c['region'], 'Host account/region differs from dedicated configuration')
    target={**c,'instance':identity.get('instanceId','')}
    validate_config(target)
    guard(Aws(c['region']),target)
    return target

def release_path(raw):
    p=Path(raw)
    require(p.parent==RELEASES and bool(re.fullmatch(r'v[0-9]+\.[0-9]+\.[0-9]+(?:-[0-9A-Za-z.-]+)?-[0-9a-f]{40}',p.name)), 'Resume source must be an immutable release directory')
    require(p.resolve()==p and p.is_dir(),'Release directory missing or contains symlinks')
    return p

def release_context(c):
    require(run(['mountpoint','-q','/opt/orderflow'])=='','Retained EBS must be mounted')
    metadata=RELEASES/'state'
    pointer=json.loads((metadata/'runtime-release.json').read_text())
    repo=release_path(pointer['repo'])
    spec=importlib.util.spec_from_file_location('orderflow_release',repo/'scripts/deploy/cloud.py')
    release=importlib.util.module_from_spec(spec);spec.loader.exec_module(release)
    data=release.validate_manifest(release.read_json(metadata/'healthy.json'))
    require(release.read_json(repo/'deploy/release-manifest.json')==data and pointer['gitSha']==data['gitSha'], 'Healthy resume pointer/manifest mismatch')
    schema=release.validate_manifest(release.read_json(metadata/'schema-release.json'))
    if schema!=data:release.check_rollback(data,schema)
    require(all(data[key]==c[attr] for key,attr in [('awsAccountId','account'),('awsRegion','region'),('environment','environment')]),'Release target mismatch')
    domain=pointer.get('domain','');release.validate_domain(domain)
    args=argparse.Namespace(**{key:c[key] for key in ('account','region','environment')},domain=domain,
                            release=data['releaseVersion'],repo=str(repo),runtime_env='/opt/orderflow/config/runtime.env',
                            state_dir=str(metadata),timeout=600,minimum_free_gb=5,verification_script=str(repo/'scripts/cloud/verify-journey.sh'))
    return release,args,data

@contextmanager
def deployment_lock():
    run(['mountpoint','-q','/opt/orderflow'])
    state=RELEASES/'state';state.mkdir(parents=True,mode=0o700,exist_ok=True)
    with (state/'.deployment.lock').open('a') as stream:
        try:fcntl.flock(stream,fcntl.LOCK_EX|fcntl.LOCK_NB)
        except BlockingIOError:raise SafetyError('Deployment or host operation already holds the release lock') from None
        try:yield
        finally:fcntl.flock(stream,fcntl.LOCK_UN)


def resume(c):
    verified_host(c)
    with deployment_lock():
        verified_release_pointer()
        release,args,data=release_context(c)
        release.fetch_secrets(args) # preserves signing/backup keys; mismatch fails
        cmd,env=release.compose(args,data)
        release.pull(args,data) # immutable prebuilt images only
        release.run(cmd+['up','-d','--no-build','--wait','--wait-timeout','600'],env=env,cwd=args.repo,timeout=630)
        release.health(args,data)
    print('OrderFlow resumed verified release '+data['releaseVersion'],file=sys.stderr)


def stop_containers(c):
    with deployment_lock():
        # No pointer means first installation, with no known application to stop.
        if not (RELEASES/'state/runtime-release.json').exists():return
        release,args,data=release_context(c)
        cmd,env=release.compose(args,data)
        release.run(cmd+['stop','--timeout','45'],env=env,cwd=args.repo,timeout=180)


def quiesce(c):
    run(['systemctl','stop','orderflow-demo.service'],timeout=120)
    # Also covers manually deployed containers when the boot service was inactive.
    stop_containers(c)

def poweroff():
    run(['systemctl','--no-block','poweroff'],timeout=15)

def claim_shutdown(current,uptime):
    # Re-read under the same lock used by extension; never act on the pre-API snapshot.
    with (STATE/'session.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        latest=json.loads((STATE/'session.json').read_text())
        require(latest.get('bootId')==current.get('bootId'),'Session boot identity changed')
        if uptime<latest['deadlineUptime']:return False
        latest['shutdownRequested']=True
        write_session(latest)
        return True


def verified_release_pointer():
    metadata=RELEASES/'state'
    pointer=json.loads((metadata/'runtime-release.json').read_text())
    ready=metadata/'boot-ready.json'
    require(ready.is_file() and json.loads(ready.read_text())==pointer,
            'Last deployment was not verified; refusing container replay or readiness')
    return pointer


def boot_guard(c):
    verified_host(c)
    with deployment_lock():
        metadata=RELEASES/'state'
        pointer=metadata/'runtime-release.json'
        if not pointer.exists():
            require(not any((metadata/name).exists() for name in ('healthy.json','schema-release.json','schema-source.json','boot-ready.json')),
                    'Unverified deployment state; Docker startup requires explicit maintenance recovery')
            return # first installation with no application deployment history
        verified_release_pointer()
        release_context(c) # validates immutable source, healthy manifest and schema compatibility before Docker

def tick(current,uptime,c):
    if uptime<current['deadlineUptime']:return
    verified_host(c) # explicitly STOP, correct account/host/tags before shutdown
    if not claim_shutdown(current,uptime):return
    try:audit_event('max-runtime')
    except OSError:print('Persistent shutdown audit unavailable; proceeding with safe stop',file=sys.stderr)
    try:quiesce(c)
    except Exception:print('Graceful container stop failed; requesting normal OS shutdown',file=sys.stderr)
    poweroff()

def status(c,current,uptime,target):
    remaining=max(0,int(current['deadlineUptime']-uptime))
    value={'instance':target['instance'],'remainingSeconds':remaining,
           'stopDeadlineUtc':datetime.fromtimestamp(time.time()+remaining,timezone.utc).isoformat(),
           'timerActive':run(['systemctl','is-active','orderflow-demo-stop.timer'])=='active','ready':False}
    try:
        with deployment_lock():
            verified_release_pointer()
            release,args,data=release_context(c)
            args.timeout=15
            release.health(args,data)
            # Strict public certificate/hostname validation through local Caddy.
            for path in ('/','/api/products?page=0&size=1'):
                run(['curl','--fail','--silent','--show-error','--connect-timeout','5','--max-time','10',
                     '--resolve',args.domain+':443:127.0.0.1','https://'+args.domain+path],timeout=20)
            value.update(ready=True,release=data['releaseVersion'],httpsUrl='https://'+args.domain)
    except Exception:
        pass # status is useful even when the application is down; readiness remains false
    return value

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=('boot-guard','resume','stop-containers','tick','status','quiesce','extend'))
    p.add_argument('--hours',type=float,default=8)
    args=p.parse_args()
    require(os.geteuid()==0,'Root through approved SSM required')
    require(CONFIG.is_file() and CONFIG.stat().st_uid==0 and CONFIG.stat().st_mode&0o022==0,'Root-owned dedicated host configuration required')
    c=json.loads(CONFIG.read_text());hours(c['maxRuntimeHours'])
    STATE.mkdir(mode=0o700,exist_ok=True)
    uptime=float(Path('/proc/uptime').read_text().split()[0]);boot=Path('/proc/sys/kernel/random/boot_id').read_text().strip()
    extension_target=verified_host(c) if args.action=='extend' else None
    # Short lock only for deadline changes: the timer never waits behind slow startup/health polling.
    with (STATE/'session.lock').open('a') as lock:
        fcntl.flock(lock,fcntl.LOCK_EX)
        existing=json.loads((STATE/'session.json').read_text()) if (STATE/'session.json').exists() else None
        current=session(existing,boot,uptime,os.environ.get('ORDERFLOW_DEMO_MAX_RUNTIME_HOURS',c['maxRuntimeHours']))
        if args.action=='extend':
            current=extend_session(current,uptime,args.hours)
            audit_event('intentional-session-extension')
        write_session(current)
    if args.action=='boot-guard':boot_guard(c)
    elif args.action=='tick':tick(current,uptime,c)
    elif args.action=='resume':resume(c)
    elif args.action=='stop-containers':
        audit_event('service-stop');stop_containers(c)
    elif args.action=='quiesce':
        target=verified_host(c);audit_event('manual-stop-requested');quiesce(c);print(json.dumps({'instance':target['instance'],'quiesced':True}))
    else:
        target=extension_target or verified_host(c);print(json.dumps(status(c,current,uptime,target)))

if __name__=='__main__':
    try:main()
    except Exception:
        print('OrderFlow host operation failed; inspect configuration, approved identity, mounted EBS and service journal privately',file=sys.stderr)
        sys.exit(1)

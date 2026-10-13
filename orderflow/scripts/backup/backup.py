#!/usr/bin/env python3
"""Encrypted eight-owner backups and isolated PostgreSQL restore verification."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import sys
import tempfile
import time
import zlib
sys.path.insert(0,str(Path(__file__).resolve().parents[1]/'deploy'))
from cloud import OWNERS, REPO, SafetyError, atomic_json, lock, parse_env, private_file, require, run

def validate_restore_name(name):
    require(bool(re.fullmatch(r'orderflow-restore-[0-9a-f]{12}',name)), 'restore target must be a uniquely isolated container; live database targets forbidden')

def verify_backup(directory):
    directory=Path(directory)
    metadata=json.loads((directory/'backup.json').read_text())
    require(metadata.get('schemaVersion')==1 and set(metadata.get('databases',{}))==set(OWNERS), 'backup must contain exactly eight owner databases')
    for owner,record in metadata['databases'].items():
        require(record.get('file')==owner.lower()+'.dump.enc','invalid encrypted backup filename')
        path=directory/record['file']
        require(path.is_file() and not path.is_symlink(),'encrypted dump missing')
        require(hashlib.sha256(path.read_bytes()).hexdigest()==record.get('sha256'),'backup checksum mismatch')
    return metadata

def flyway_checksum(content):
    crc=0
    for line in content.decode('utf-8-sig').splitlines(): crc=zlib.crc32(line.encode('utf-8'),crc)
    return crc if crc < 2**31 else crc-2**32

def quote_identifier(name):
    return '"'+name.replace('"','""')+'"'

def snapshot(sql, allow_uninitialized=False):
    tables=sql("SELECT schemaname || '.' || tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;").strip().splitlines()
    if not tables and allow_uninitialized: return {'tables':{},'flyway':[]}
    require(tables,'database has no application tables; refusing empty readiness backup')
    require('public.flyway_schema_history' in tables,'nonempty database without Flyway history: initialization backup forbidden')
    records={}
    for table in tables:
        schema,name=table.split('.',1); qualified=quote_identifier(schema)+'.'+quote_identifier(name)
        records[table]={'count':int(sql('SELECT count(*) FROM '+qualified+';').strip()),
                        'representativeHash':sql('SELECT md5(row_to_json(t)::text) FROM '+qualified+' t ORDER BY row_to_json(t)::text LIMIT 1;').strip()}
    require('public.flyway_schema_history' in records,'Flyway migration history missing')
    history=sql("SELECT script || '|' || checksum || '|' || success FROM public.flyway_schema_history WHERE type='SQL' ORDER BY installed_rank;").strip().splitlines()
    return {'tables':records,'flyway':history}

def validate_flyway(owner,history,repo):
    directory=Path(repo)/(owner.lower()+'-service')/'src/main/resources/db/migration'
    for line in history:
        pieces=line.split('|'); require(len(pieces)==3,'invalid Flyway history')
        script,checksum,success=pieces
        require(success in ('t','true'),'Flyway history contains unsuccessful migration')
        require(Path(script).name==script,'invalid Flyway migration name')
        path=directory/script
        require(path.is_file() and str(flyway_checksum(path.read_bytes()))==checksum,'Flyway migration validation failed against release SQL')

def compose(args):
    env=os.environ.copy(); env.update(parse_env(args.runtime_env))
    cmd=['docker','compose','--project-name',args.project_name,'--env-file',args.runtime_env,
         '-f',str(Path(args.repo)/'docker-compose.yml'),'-f',str(Path(args.repo)/'docker-compose.cloud.yml')]
    run(cmd+['config','--quiet'],env=env,cwd=args.repo)
    return cmd,env

def metric(path,name):
    if not path: return
    path=Path(path); path.parent.mkdir(parents=True,exist_ok=True)
    existing={}
    if path.exists():
        for line in path.read_text().splitlines():
            if ' ' in line: key,value=line.split(' ',1); existing[key]=value
    existing[name]=str(int(time.time()))
    fd,temp=tempfile.mkstemp(prefix='.metric-',dir=path.parent)
    with os.fdopen(fd,'w') as stream: stream.write('\n'.join(key+' '+value for key,value in sorted(existing.items()))+'\n')
    os.chmod(temp,0o644); os.replace(temp,path)

def backup(args):
    key=private_file(args.key_file)
    require(key.stat().st_size>=32,'backup encryption key must contain at least 32 random bytes')
    root=Path(args.output); root.mkdir(parents=True,exist_ok=True,mode=0o700)
    with lock(root):
        directory=root/(datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S.%fZ')+'-'+secrets.token_hex(4))
        directory.mkdir(mode=0o700)
        cmd,env=compose(args)
        metadata={'schemaVersion':1,'createdAt':datetime.now(timezone.utc).isoformat(),'encryption':'AES-256-CBC/PBKDF2-SHA256-200000',
                  'databases':{},'retentionPolicy':'retain until explicit reviewed policy; no automatic local pruning'}
        try:
            for owner in OWNERS:
                user=env.get(owner+'_POSTGRES_USER')
                require(bool(re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',user or '')) and user!='postgres','explicit separate database owner username required')
                db=env.get(owner+'_POSTGRES_DB'); require(bool(re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',db or '')),'explicit database owner name required')
                # The container's private admin credentials are expanded inside it; never in host argv.
                shell='PGPASSWORD="$POSTGRES_PASSWORD" exec "$@" -U "$POSTGRES_USER"'
                base=cmd+['exec','-T','postgres','sh','-c',shell,'--']
                sql=lambda statement:run(base+['psql','--dbname',db,'-v','ON_ERROR_STOP=1','-A','-t','-c',statement],env=env,cwd=args.repo)
                before=snapshot(sql,args.allow_uninitialized); validate_flyway(owner,before['flyway'],args.migration_repo or args.repo)
                dump=run(base+['pg_dump','--dbname',db,'--format=custom','--no-owner','--no-acl'],binary=True,env=env,cwd=args.repo,timeout=args.timeout)
                require(dump.startswith(b'PGDMP'),'invalid PostgreSQL custom dump')
                file=directory/(owner.lower()+'.dump.enc')
                run(['openssl','enc','-aes-256-cbc','-pbkdf2','-iter','200000','-md','sha256','-salt','-pass','file:'+str(key),'-out',str(file)],input=dump,binary=True,timeout=args.timeout)
                file.chmod(0o600)
                # Data validation metadata describes the backup snapshot. Concurrent writes could
                # change count/sample between commands, so a mismatch stops rather than claiming proof.
                require(snapshot(sql,args.allow_uninitialized)==before,'database changed during backup capture; use an approved write quiescence window and retry')
                metadata['databases'][owner]={'database':db,'ownerUser':user,'file':file.name,'sha256':hashlib.sha256(file.read_bytes()).hexdigest(),
                                            'plaintextSha256':hashlib.sha256(dump).hexdigest(),'validation':before}
            atomic_json(directory/'backup.json',metadata)
            verify_backup(directory)
            if args.s3_uri:
                require(bool(re.fullmatch(r's3://[a-z0-9][a-z0-9.-]+/backups/?',args.s3_uri)),'S3 backup URI must use private bucket backups/ prefix')
                require(args.region,'explicit AWS region required for S3 backup')
                run(['aws','s3','cp',str(directory),args.s3_uri.rstrip('/')+'/'+directory.name+'/', '--recursive','--sse','AES256','--region',args.region,'--only-show-errors'],timeout=args.timeout)
            metric(args.metrics_file,'orderflow_backup_last_success_timestamp_seconds')
            print(json.dumps({'backup':str(directory),'databases':8,'checksumVerified':True,'restoreVerified':False}))
        except Exception:
            # Preserve partial evidence for diagnosis; never overwrite an existing backup.
            atomic_json(directory/'failed.json',{'status':'FAILED','createdAt':metadata['createdAt']})
            raise

def restore(args):
    require(args.isolated_test,'restore requires --isolated-test; no live target option exists')
    directory=Path(args.backup); metadata=verify_backup(directory); key=private_file(args.key_file)
    name='orderflow-restore-'+secrets.token_hex(6); validate_restore_name(name)
    require(bool(re.fullmatch(r'postgres:16(?:\.[0-9]+)?-alpine(?:@sha256:[0-9a-f]{64})?',args.postgres_image)), 'restore uses explicitly versioned PostgreSQL 16 image')
    password=secrets.token_urlsafe(48)
    with tempfile.TemporaryDirectory(prefix='orderflow-restore-') as temporary:
        envfile=Path(temporary)/'postgres.env'; envfile.write_text('POSTGRES_PASSWORD='+password+'\n'); envfile.chmod(0o600)
        # No ports, no live network attachment, no existing volumes, container-scoped tmpfs only.
        run(['docker','run','-d','--name',name,'--label','orderflow.isolated-restore=true','--network','none',
             '--memory','384m','--cpus','1','--tmpfs','/var/lib/postgresql/data:rw,size=1g','--env-file',str(envfile),args.postgres_image],timeout=args.timeout)
        completed=False
        try:
            deadline=time.monotonic()+min(args.timeout,120)
            while True:
                try: run(['docker','exec',name,'pg_isready','-U','postgres'],timeout=10); break
                except SafetyError:
                    require(time.monotonic()<deadline,'isolated restore database readiness timeout'); time.sleep(1)
            base=['docker','exec','-i',name,'sh','-c','PGPASSWORD="$POSTGRES_PASSWORD" exec "$@" -U postgres','--']
            for owner,record in metadata['databases'].items():
                db=record['database']; require(bool(re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',db)),'invalid backup database name')
                user=record.get('ownerUser','')
                require(bool(re.fullmatch('[A-Za-z_][A-Za-z0-9_]*',user)) and user!='postgres','backup owner username missing or unsafe')
                owner_password=secrets.token_urlsafe(40)
                setup='CREATE ROLE '+quote_identifier(user)+" LOGIN PASSWORD '"+owner_password+"'; CREATE DATABASE "+quote_identifier(db)+' OWNER '+quote_identifier(user)+'; REVOKE CONNECT ON DATABASE '+quote_identifier(db)+' FROM PUBLIC;'
                run(base+['psql','--dbname','postgres','-v','ON_ERROR_STOP=1'],input=setup)
                dump=run(['openssl','enc','-d','-aes-256-cbc','-pbkdf2','-iter','200000','-md','sha256','-pass','file:'+str(key),'-in',str(directory/record['file'])],binary=True,timeout=args.timeout)
                require(hashlib.sha256(dump).hexdigest()==record['plaintextSha256'],'decrypted dump checksum mismatch')
                run(base+['pg_restore','--exit-on-error','--no-owner','--no-acl','--role',user,'--dbname',db],input=dump,binary=True,timeout=args.timeout)
                owner_base=['docker','exec','-i',name,'sh','-c','read -r PGPASSWORD; export PGPASSWORD; exec "$@"','--','psql','--host','127.0.0.1','--username',user,'--dbname',db,'-v','ON_ERROR_STOP=1','-A','-t']
                sql=lambda statement:run(owner_base+['-c',statement],input=owner_password+'\n')
                require(sql('SELECT current_user;').strip()==user,'restored owner SQL authentication failed')
                actual=snapshot(sql,not record['validation']['tables'])
                require(actual==record['validation'],'restored schema/history/count/representative record mismatch')
                validate_flyway(owner,actual['flyway'],args.repo)
            atomic_json(directory/'restore-verification.json',{'status':'PASS','verifiedAt':datetime.now(timezone.utc).isoformat(),
                        'databases':8,'container':name,'checks':['pg_restore schema/data','owner-role authenticated SQL connectivity','Flyway SQL checksum/history','table counts','representative record hashes'],
                        'applicationStartup':'NOT RUN','liveRecovery':'NOT RUN'})
            metric(args.metrics_file,'orderflow_backup_restore_verified_timestamp_seconds'); completed=True
        finally:
            # Only the uniquely generated, labeled test container may be removed; no -v or volume deletion.
            label=run(['docker','inspect','--format','{{ index .Config.Labels "orderflow.isolated-restore" }}',name]).strip()
            require(label=='true','restore cleanup refused: isolation label mismatch')
            run(['docker','rm','-f',name])
        require(completed,'restore verification failed')
    print('isolated restore verification PASS: eight owner databases; application startup/live recovery NOT RUN')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['backup','verify','restore'])
    parser.add_argument('--migration-repo',default='',help='newest attempted schema artifact when backing up during image rollback')
    parser.add_argument('--project-name',default='orderflow-cloud')
    parser.add_argument('--allow-uninitialized',action='store_true',help='first deployment only: permits completely empty schema, never nonempty database')
    parser.add_argument('--runtime-env',default='/opt/orderflow/config/runtime.env')
    parser.add_argument('--repo',default=str(REPO))
    parser.add_argument('--output',default='/opt/orderflow/backups')
    parser.add_argument('--backup',default='')
    parser.add_argument('--key-file',default='/opt/orderflow/config/backup.key')
    parser.add_argument('--s3-uri',default='')
    parser.add_argument('--region',default='')
    parser.add_argument('--metrics-file',default='/opt/orderflow/data/node-exporter/backup.prom')
    parser.add_argument('--timeout',type=int,default=600)
    parser.add_argument('--isolated-test',action='store_true')
    parser.add_argument('--postgres-image',default='postgres:16-alpine')
    args=parser.parse_args(); require(1<=args.timeout<=3600,'timeout must be 1..3600 seconds')
    if args.action=='backup': backup(args)
    elif args.action=='verify': verify_backup(args.backup); print('encrypted backup checksums verified; restore NOT RUN')
    else: restore(args)
if __name__=='__main__':
    try: main()
    except (SafetyError,OSError,ValueError,KeyError) as error:
        print('ERROR: '+str(error) if isinstance(error,SafetyError) else 'ERROR: backup metadata or file invalid',file=sys.stderr); sys.exit(1)

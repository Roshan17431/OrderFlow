#!/usr/bin/env python3
"""Opt-in real Docker backup exercise. Uses unique tmpfs containers, never developer volumes."""
from argparse import Namespace
import importlib.util
import os
from pathlib import Path
import secrets
import sys
import tempfile
import time
spec=importlib.util.spec_from_file_location('backup',Path(__file__).with_name('backup.py'))
b=importlib.util.module_from_spec(spec); spec.loader.exec_module(b)

def main():
    project='orderflow-backup-test-'+secrets.token_hex(6)
    password=secrets.token_urlsafe(40)
    with tempfile.TemporaryDirectory(prefix='orderflow-backup-fixture-') as temporary:
        root=Path(temporary)
        configuration={'services':{'postgres':{'image':'postgres:16-alpine','environment':{'POSTGRES_USER':'postgres','POSTGRES_PASSWORD':password,'POSTGRES_DB':'postgres'},
            'network_mode':'none','mem_limit':'384m','cpus':1,'tmpfs':['/var/lib/postgresql/data:rw,size=1g'],
            'labels':{'orderflow.isolated-backup-fixture':'true'}}}}
        b.atomic_json(root/'docker-compose.yml',configuration); b.atomic_json(root/'docker-compose.cloud.yml',{'services':{}})
        values={owner+'_'+suffix:'orderflow_'+owner.lower() for owner in b.OWNERS for suffix in ['POSTGRES_DB','POSTGRES_USER']}
        (root/'runtime.env').write_text('\n'.join(key+'='+value for key,value in values.items())+'\n'); (root/'runtime.env').chmod(0o600)
        (root/'backup.key').write_text(secrets.token_urlsafe(64)); (root/'backup.key').chmod(0o600)
        args=Namespace(migration_repo=str(b.REPO),repo=str(root),runtime_env=str(root/'runtime.env'),project_name=project,output=str(root/'backups'),key_file=str(root/'backup.key'),timeout=180,s3_uri='',region='',metrics_file='',allow_uninitialized=False)
        cmd,env=b.compose(args)
        b.run(cmd+['up','-d'],env=env,cwd=root)
        try:
            deadline=time.monotonic()+45
            while True:
                # The entrypoint's temporary initialization server accepts Unix
                # sockets; TCP opens only on the final server used by this test.
                try: b.run(cmd+['exec','-T','postgres','pg_isready','-h','127.0.0.1','-U','postgres'],env=env,cwd=root); break
                except b.SafetyError:
                    b.require(time.monotonic()<deadline,'fixture postgres did not start'); time.sleep(1)
            for owner in b.OWNERS:
                db=values[owner+'_POSTGRES_DB']
                b.run(cmd+['exec','-T','postgres','createdb','-U','postgres',db],env=env,cwd=root)
                files=sorted((b.REPO/(owner.lower()+'-service')/'src/main/resources/db/migration').glob('*.sql'))
                # Real release SQL schema plus deterministic representative record in a test-only table.
                statements=['CREATE TABLE flyway_schema_history (installed_rank integer primary key, script text, checksum integer, success boolean, type text);']
                for index,path in enumerate(files,1):
                    statements.append(path.read_text())
                    statements.append(f"INSERT INTO flyway_schema_history VALUES ({index},'{path.name}',{b.flyway_checksum(path.read_bytes())},true,'SQL');")
                statements.append("CREATE TABLE backup_fixture_record(id integer primary key, payload text); INSERT INTO backup_fixture_record VALUES(1,'synthetic-restore-proof');")
                b.run(cmd+['exec','-T','postgres','psql','-U','postgres','-d',db,'-v','ON_ERROR_STOP=1'],input='\n'.join(statements),env=env,cwd=root)
            # Backup needs actual migration files for validation, while compose fixture is elsewhere.
            original_validate=b.validate_flyway
            b.validate_flyway=lambda owner,history,repo:original_validate(owner,history,b.REPO)
            b.backup(args)
            directory=next(path for path in (root/'backups').iterdir() if path.is_dir())
            b.restore(Namespace(isolated_test=True,backup=str(directory),key_file=str(root/'backup.key'),postgres_image='postgres:16-alpine',timeout=180,repo=str(b.REPO),metrics_file=''))
            report=b.read_json(directory/'restore-verification.json') if hasattr(b,'read_json') else __import__('json').loads((directory/'restore-verification.json').read_text())
            b.require('owner-role authenticated SQL connectivity' in report['checks'],'restore did not prove each isolated application owner login')
            b.atomic_json(b.REPO/'deploy/local-restore-evidence.json',report)
        finally:
            # Project is random and this fixture owns its tmpfs container only; no -v flag.
            b.run(cmd+['down'],env=env,cwd=root)
if __name__=='__main__':
    try: main()
    except b.SafetyError as error: print('ERROR: '+str(error),file=sys.stderr); sys.exit(1)

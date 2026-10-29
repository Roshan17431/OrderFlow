"""Cloud safety contracts. External AWS/Docker effects are mocked, never a deployed result."""
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'cloud.py'

class CloudSafety(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        if MODULE.exists():
            spec = importlib.util.spec_from_file_location('cloud', MODULE)
            cls.cloud = importlib.util.module_from_spec(spec)
            spec.loader.exec_module(cls.cloud)
        else:
            cls.cloud = None

    def setUp(self):
        self.assertIsNotNone(self.cloud, 'deployment safety implementation is missing')
        self.tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)

    def manifest(self):
        return {'schemaVersion': 1, 'releaseVersion': 'v1.0.0', 'gitSha': 'a'*40,
                'builtAt': '2026-10-12T00:00:00Z', 'platform': 'linux/amd64',
                'awsAccountId': '123456789012', 'awsRegion': 'us-east-1',
                'environment': 'cloud-demo', 'images': {s: {'digest': 'sha256:'+'a'*64,
                'uri': f'123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/{s}@sha256:'+ 'a'*64}
                for s in self.cloud.SERVICES},
                'migrationCompatibility': {'backwardCompatible': True, 'files': {'auth-service/V1.sql': 'a'*64}}}

    def infrastructure_manifest(self):
        return {'schemaVersion': 1, 'releaseVersion': 'v1.0.0', 'gitSha': 'a'*40,
                'platform': 'linux/amd64', 'awsAccountId': '123456789012',
                'awsRegion': 'us-east-1', 'environment': 'cloud-demo', 'images': {
                    name: {'digest': 'sha256:'+digit*64,
                           'uri': '123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/'+repository+'@sha256:'+digit*64}
                    for name, repository, digit in [('caddy', 'frontend', 'b'),
                        ('kafka', 'service-registry', 'c'), ('node-exporter', 'operations-service', 'd'),
                        ('grafana', 'notification-service', 'e')]}}

    def test_infrastructure_contract_accepts_exactly_four_reviewed_records(self):
        data=self.infrastructure_manifest()
        data['images']['grafana']={'digest':'sha256:'+'e'*64,
            'uri':'123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/notification-service@sha256:'+'e'*64}
        try: result=self.cloud.validate_infrastructure_manifest(data,self.manifest())
        except self.cloud.SafetyError as error: self.fail('four reviewed infrastructure records rejected: '+str(error))
        self.assertEqual(set(result['images']),{'caddy','kafka','node-exporter','grafana'})

    def test_three_record_infrastructure_manifest_requires_grafana(self):
        data=self.infrastructure_manifest(); data['images'].pop('grafana',None)
        with self.assertRaises(self.cloud.SafetyError):
            self.cloud.validate_infrastructure_manifest(data,self.manifest())

    def compose_fixture(self, *, legacy=False):
        from argparse import Namespace
        self.root.joinpath('docker-compose.cloud.yml').write_text(
            'services:\n  caddy:\n    image: caddy:2.11.7-alpine@sha256:'+'a'*64+'\n' if legacy else
            'services:\n'+''.join('  '+name+':\n    image: "${'+variable+':?approved release pin required}"\n'
                for name, variable in [('caddy', 'INFRA_IMAGE_CADDY'), ('kafka', 'INFRA_IMAGE_KAFKA'),
                                       ('node-exporter', 'INFRA_IMAGE_NODE_EXPORTER'), ('grafana', 'INFRA_IMAGE_GRAFANA')]))
        config=self.root/'config'; config.mkdir(exist_ok=True)
        values={'ORDERFLOW_ENVIRONMENT':'cloud-demo','DOMAIN_NAME':'demo.example.com'}
        values.update({key:'fixture' for key in ['POSTGRES_PASSWORD','REDIS_PASSWORD',
            'APP_PAYMENT_SIMULATOR_WEBHOOK_SECRET','GRAFANA_ADMIN_PASSWORD']})
        values.update({owner+'_POSTGRES_'+suffix:'fixture' for owner in self.cloud.OWNERS
                       for suffix in ('DB','USER','PASSWORD')})
        for variable in ('INFRA_IMAGE_CADDY','INFRA_IMAGE_KAFKA','INFRA_IMAGE_NODE_EXPORTER','INFRA_IMAGE_GRAFANA'):
            values[variable]='stale-runtime:latest'
        runtime=config/'runtime.env'
        runtime.write_text(''.join(key+'='+value+'\n' for key,value in values.items())); runtime.chmod(0o600)
        for name in ('jwt-private.pem','jwt-public.pem','backup.key','cloud-accounts.json'):
            path=config/name; path.write_text('fixture'); path.chmod(0o400 if name.startswith('jwt-') else 0o600)
        return Namespace(repo=str(self.root),runtime_env=str(runtime),account='123456789012',
                         region='us-east-1',environment='cloud-demo',domain='demo.example.com',timeout=10)

    def compose_with_fixture_keys(self, args):
        import os
        original_stat=Path.stat
        def fixture_stat(path, *arguments, **options):
            value=original_stat(path,*arguments,**options)
            if path.name in ('jwt-private.pem','jwt-public.pem'):
                fields=list(value); fields[4]=10001; fields[5]=10001
                return os.stat_result(fields)
            return value
        # Key ownership and OpenSSL are separate contracts; use real metadata/env parsing here.
        with patch.object(Path,'stat',fixture_stat),patch.object(self.cloud,'run',return_value=b'fixture-public-key'):
            return self.cloud.compose(args,self.manifest())

    def write_infrastructure_manifest(self, data=None):
        path=self.root/'deploy/infrastructure-images.json'; path.parent.mkdir(exist_ok=True)
        path.write_text(json.dumps(self.infrastructure_manifest() if data is None else data))
        return path

    def test_new_compose_rejects_missing_empty_or_symlink_infrastructure_manifest(self):
        args=self.compose_fixture()
        with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)
        path=self.write_infrastructure_manifest(); path.write_text('')
        with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)
        path.unlink(); target=self.root/'outside.json'; target.write_text(json.dumps(self.infrastructure_manifest())); path.symlink_to(target)
        with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)

    def test_infrastructure_manifest_rejects_target_git_platform_digest_and_unknown_fields(self):
        args=self.compose_fixture()
        mutations=[('gitSha','b'*40),('awsAccountId','999999999999'),('awsRegion','ap-south-1'),
                   ('platform','linux/arm64'),('environment','other'),('releaseVersion','v2.0.0'),
                   ('schemaVersion',True),('unexpected','forbidden')]
        for key,value in mutations:
            with self.subTest(key=key):
                data=self.infrastructure_manifest(); data[key]=value; self.write_infrastructure_manifest(data)
                with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)
        invalid_images=[{'uri':'repo:latest','digest':'sha256:'+'b'*64},
            {'uri':'123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/frontend@sha256:'+'b'*64,'digest':'sha256:'+'e'*64},
            {'uri':'123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/new-infrastructure@sha256:'+'b'*64,'digest':'sha256:'+'b'*64},
            {'uri':'999999999999.dkr.ecr.us-east-1.amazonaws.com/orderflow/frontend@sha256:'+'b'*64,'digest':'sha256:'+'b'*64},
            {'uri':'123456789012.dkr.ecr.ap-south-1.amazonaws.com/orderflow/frontend@sha256:'+'b'*64,'digest':'sha256:'+'b'*64},
            {'uri':'sha256:'+'b'*64,'digest':'sha256:'+'b'*64},
            {'uri':'repo:latest','digest':None}]
        for image in invalid_images:
            with self.subTest(image=image):
                data=self.infrastructure_manifest(); data['images']['caddy']=image; self.write_infrastructure_manifest(data)
                with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)
        for extra in ('image-field','extra-image','missing-image','missing-top-field'):
            with self.subTest(extra=extra):
                data=self.infrastructure_manifest()
                if extra=='image-field': data['images']['caddy']['configId']='sha256:'+'b'*64
                elif extra=='extra-image': data['images']['postgres']=data['images']['caddy']
                elif extra=='missing-image': del data['images']['kafka']
                else: del data['gitSha']
                self.write_infrastructure_manifest(data)
                with self.assertRaises(self.cloud.SafetyError): self.compose_with_fixture_keys(args)

    def test_release_pins_override_both_ambient_and_runtime_environment(self):
        args=self.compose_fixture(); self.write_infrastructure_manifest()
        variables={'INFRA_IMAGE_CADDY':'stale-ambient:latest','INFRA_IMAGE_KAFKA':'stale-ambient:latest',
                   'INFRA_IMAGE_NODE_EXPORTER':'stale-ambient:latest','INFRA_IMAGE_GRAFANA':'stale-ambient:latest'}
        with patch.dict('os.environ',variables): command,env=self.compose_with_fixture_keys(args)
        expected=self.infrastructure_manifest()['images']
        self.assertEqual(env['INFRA_IMAGE_CADDY'],expected['caddy']['uri'])
        self.assertEqual(env['INFRA_IMAGE_KAFKA'],expected['kafka']['uri'])
        self.assertEqual(env['INFRA_IMAGE_NODE_EXPORTER'],expected['node-exporter']['uri'])
        self.assertEqual(env['INFRA_IMAGE_GRAFANA'],expected['grafana']['uri'])
        self.assertIn(str(self.root/'docker-compose.cloud.yml'),command)
        self.assertEqual(len(self.manifest()['images']),12)

    def test_legacy_fixed_vendor_compose_does_not_require_infrastructure_manifest(self):
        args=self.compose_fixture(legacy=True)
        self.compose_with_fixture_keys(args)

    def infrastructure_archive(self, infrastructure=None, *, legacy=False, directory=False):
        import io, tarfile, hashlib
        data=self.manifest(); sql=b'SELECT 1;'; sql_path='auth-service/src/main/resources/db/migration/V1.sql'
        data['migrationCompatibility']['files']={sql_path:hashlib.sha256(sql).hexdigest()}
        files={'deploy/release-manifest.json':json.dumps(data).encode(),sql_path:sql}
        for name in ['scripts/deploy/remote-entrypoint.sh','scripts/deploy/cloud.py',
                     'scripts/cloud/verify-journey.sh','scripts/cloud/verify_journey.py','docker-compose.yml']:
            files[name]=b'fixture'
        files['docker-compose.cloud.yml']=b'image: caddy:2.11.7-alpine@sha256:'+b'a'*64 if legacy else (
            b'image: ${INFRA_IMAGE_CADDY:?approved pin required}\n'
            b'image: ${INFRA_IMAGE_KAFKA:?approved pin required}\n'
            b'image: ${INFRA_IMAGE_NODE_EXPORTER:?approved pin required}\n'
            b'image: ${INFRA_IMAGE_GRAFANA:?approved pin required}\n')
        if infrastructure is not None: files['deploy/infrastructure-images.json']=infrastructure
        archive=self.root/'infrastructure.tar.gz'
        with tarfile.open(archive,'w:gz') as output:
            for name,body in files.items():
                info=tarfile.TarInfo('./'+name)
                if directory and name=='deploy/infrastructure-images.json':
                    info.type=tarfile.DIRTYPE; output.addfile(info)
                else:
                    info.size=len(body); output.addfile(info,io.BytesIO(body))
        return archive,data

    def test_new_archive_requires_regular_nonempty_valid_bound_infrastructure_manifest(self):
        stale=self.infrastructure_manifest(); stale['gitSha']='b'*40
        missing_grafana=self.infrastructure_manifest(); del missing_grafana['images']['grafana']
        for value,directory in [(None,False),(b'',False),(b'{}',False),
                                (json.dumps(stale).encode(),False),
                                (json.dumps(missing_grafana).encode(),False),
                                (json.dumps(self.infrastructure_manifest()).encode(),True)]:
            with self.subTest(value=value,directory=directory):
                archive,data=self.infrastructure_archive(value,directory=directory)
                with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_artifact(archive,data)
        archive,data=self.infrastructure_archive(json.dumps(self.infrastructure_manifest()).encode())
        self.cloud.validate_artifact(archive,data)

    def test_fixed_vendor_rollback_archive_remains_valid_without_new_manifest(self):
        archive,data=self.infrastructure_archive(legacy=True)
        self.cloud.validate_artifact(archive,data)

    def test_artifact_validation_command_is_offline_and_fails_closed(self):
        import subprocess, sys
        for infrastructure in (json.dumps(self.infrastructure_manifest()).encode(),None):
            with self.subTest(infrastructure_present=infrastructure is not None):
                archive,data=self.infrastructure_archive(infrastructure)
                manifest=self.root/'external-manifest.json'; manifest.write_text(json.dumps(data))
                result=subprocess.run([sys.executable,str(MODULE),'validate-artifact','--manifest',str(manifest),
                                       '--artifact',str(archive)],text=True,capture_output=True)
                if infrastructure is None:
                    self.assertEqual(result.returncode,1)
                    self.assertIn('infrastructure-images.json',result.stderr)
                else:
                    self.assertEqual(result.returncode,0,result.stderr)
                    self.assertIn('no deployment performed',result.stdout)

    def run_publication_validation(self, approved_json):
        import os, shutil, subprocess, textwrap
        workflow=MODULE.parents[3]/'.github/workflows/build-images.yml'
        source=workflow.read_text().split('      - name: Validate explicit publication target\n',1)[1]
        source=source.split('      - uses:',1)[0]
        script=textwrap.dedent(source.split('        run: |\n',1)[1])
        checkout=self.root/'checkout'; (checkout/'scripts/deploy').mkdir(parents=True,exist_ok=True)
        shutil.copyfile(MODULE,checkout/'scripts/deploy/cloud.py')
        pins=checkout/'deploy/infrastructure-images.json'; pins.parent.mkdir(exist_ok=True)
        pins.write_text(json.dumps({'staleCheckoutFileMustNotAuthorizePublication':True}))
        env=os.environ.copy()
        env.update({'AWS_ACCOUNT_ID':'123456789012','AWS_REGION':'us-east-1','RELEASE_VERSION':'v1.0.0',
                    'TARGET_PLATFORM':'linux/amd64','GITHUB_SHA':'a'*40,'DOMAIN_NAME':'demo.example.com',
                    'ROLE_ARN':'arn:aws:iam::123456789012:role/reviewed-publish','NEXT_PUBLIC_OAUTH_PROVIDERS':'',
                    'APPROVED_INFRASTRUCTURE_IMAGES_JSON':approved_json})
        result=subprocess.run(['bash','-e','-o','pipefail','-c',script],cwd=checkout,env=env,
                              text=True,capture_output=True)
        return result,pins

    def test_publication_generates_pins_from_reviewed_json_without_a_self_referencing_commit(self):
        expected=self.infrastructure_manifest()
        result,pins=self.run_publication_validation(json.dumps(expected))
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(json.loads(pins.read_text()),expected)

    def test_publication_requires_reviewed_grafana_record_before_credentials(self):
        pins=self.infrastructure_manifest(); pins['images'].pop('grafana',None)
        result,_=self.run_publication_validation(json.dumps(pins))
        self.assertNotEqual(result.returncode,0)
        pins['images']['grafana']={'digest':'sha256:'+'e'*64,
            'uri':'123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/notification-service@sha256:'+'e'*64}
        result,path=self.run_publication_validation(json.dumps(pins))
        self.assertEqual(result.returncode,0,result.stderr)
        self.assertEqual(json.loads(path.read_text()),pins)

    def test_publication_rejects_missing_malformed_or_mismatched_reviewed_json_before_credentials(self):
        values=['','{malformed-json']
        for key,value in [('gitSha','b'*40),('awsAccountId','999999999999'),('awsRegion','ap-south-1'),
                          ('platform','linux/arm64'),('releaseVersion','v2.0.0')]:
            pins=self.infrastructure_manifest(); pins[key]=value; values.append(json.dumps(pins))
        pins=self.infrastructure_manifest(); pins['images']['caddy']['digest']='sha256:'+'e'*64
        values.append(json.dumps(pins))
        pins=self.infrastructure_manifest(); pins['unexpected']='$(touch must-not-execute)'
        values.append(json.dumps(pins))
        for approved_json in values:
            with self.subTest(approved_json=approved_json):
                result,path=self.run_publication_validation(approved_json)
                self.assertNotEqual(result.returncode,0)
                self.assertEqual(json.loads(path.read_text()),{'staleCheckoutFileMustNotAuthorizePublication':True})
                self.assertFalse((path.parent.parent/'must-not-execute').exists())

    def test_pull_checks_infrastructure_registry_manifest_and_platform(self):
        args=self.compose_fixture(); self.write_infrastructure_manifest()
        command,env=self.compose_with_fixture_keys(args)
        for failure in ('repo-digest','architecture','grafana-repo-digest','grafana-architecture',None):
            with self.subTest(failure=failure):
                def external(command,**options):
                    if command[:3]==['docker','image','inspect']:
                        uri=command[-1]
                        if command[4]=='{{json .RepoDigests}}':
                            mismatch=failure=='repo-digest' or (failure=='grafana-repo-digest' and uri==env['INFRA_IMAGE_GRAFANA'])
                            return json.dumps([] if mismatch else [uri])
                        if uri==env['INFRA_IMAGE_CADDY'] and failure=='architecture': return 'linux/arm64'
                        if uri==env['INFRA_IMAGE_GRAFANA'] and failure=='grafana-architecture': return 'linux/arm64'
                        return 'linux/amd64'
                    if command[:2]==['docker','run']: return '10001'
                    return ''
                with patch.object(self.cloud,'compose',return_value=(command,env)),patch.object(self.cloud,'run',side_effect=external):
                    if failure:
                        with self.assertRaises(self.cloud.SafetyError): self.cloud.pull(args,self.manifest())
                    else: self.cloud.pull(args,self.manifest())

    def test_missing_target_fields_fail_closed(self):
        for field in ['awsAccountId', 'awsRegion', 'environment', 'releaseVersion']:
            with self.subTest(field=field):
                data = self.manifest(); data[field] = ''
                with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_manifest(data)

    def test_requires_all_twelve_images(self):
        data = self.manifest(); del data['images']['frontend']
        with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_manifest(data)

    def test_rejects_floating_tag_and_wrong_repository(self):
        for uri in ['repo:latest', '123456789012.dkr.ecr.us-east-1.amazonaws.com/other/auth-service@sha256:'+'a'*64]:
            data = self.manifest(); data['images']['auth-service']['uri'] = uri
            with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_manifest(data)

    def test_rejects_invalid_release_and_region(self):
        for field,value in [('releaseVersion','latest'), ('releaseVersion','../bad'), ('awsRegion',''), ('awsAccountId','unknown')]:
            data=self.manifest(); data[field]=value
            with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_manifest(data)

    def test_domain_required_and_not_url(self):
        for domain in ['', 'http://demo.example.com', 'localhost', 'demo.example.com;id']:
            with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_domain(domain)

    def test_unhealthy_and_missing_services_fail(self):
        for records in [[], [{'Service': 'api-gateway', 'State': 'running', 'Health': 'unhealthy'}]]:
            with self.assertRaises(self.cloud.SafetyError): self.cloud.check_records(records, ['api-gateway'])

    def test_health_needs_completed_bootstrap_and_actual_health(self):
        with self.assertRaises(self.cloud.SafetyError): self.cloud.check_records([{'Service':'postgres','State':'running','Health':''}], ['postgres'])
        self.cloud.check_records([{'Service':'postgres','State':'running','Health':'healthy'}, {'Service':'database-bootstrap','State':'exited','ExitCode':0}], ['postgres','database-bootstrap'])

    def test_schema_rollback_requires_unchanged_existing_hashes_and_explicit_compatibility(self):
        old=self.manifest(); new=self.manifest()
        new['migrationCompatibility']['files']['order-service/V2.sql']='b'*64
        self.cloud.check_rollback(old,new)
        for unsafe in [False, 'true', None]:
            new['migrationCompatibility']['backwardCompatible']=unsafe
            with self.assertRaises(self.cloud.SafetyError): self.cloud.check_rollback(old,new)
        new['migrationCompatibility']['backwardCompatible']=True
        new['migrationCompatibility']['files']['auth-service/V1.sql']='b'*64
        with self.assertRaises(self.cloud.SafetyError): self.cloud.check_rollback(old,new)

    def test_rollback_target_missing(self):
        with self.assertRaises(self.cloud.SafetyError): self.cloud.read_json(self.root/'missing.json')

    def test_subprocess_timeout_is_failure_and_redacts_output(self):
        import subprocess
        with patch.object(self.cloud.subprocess,'run',side_effect=subprocess.TimeoutExpired(['docker'],1)):
            with self.assertRaisesRegex(self.cloud.SafetyError, 'timed out'): self.cloud.run(['docker'], timeout=1)
        with patch.object(self.cloud.subprocess,'run',return_value=subprocess.CompletedProcess(['docker'],1,'password-secret','password-secret')):
            with self.assertRaises(self.cloud.SafetyError) as error: self.cloud.run(['docker'])
            self.assertNotIn('password-secret',str(error.exception))

    def test_filesystem_lock_blocks_concurrent_deployments(self):
        with self.cloud.lock(self.root):
            with self.assertRaises(self.cloud.SafetyError):
                with self.cloud.lock(self.root): pass

    def test_failed_deploy_preserves_metadata(self):
        data=self.manifest(); state=self.root/'state'; state.mkdir()
        self.cloud.atomic_json(state/'healthy.json',data)
        before=(state/'healthy.json').read_bytes()
        with self.assertRaises(self.cloud.SafetyError):
            self.cloud.record_healthy(state,data,lambda: (_ for _ in ()).throw(self.cloud.SafetyError('unhealthy')))
        self.assertEqual((state/'healthy.json').read_bytes(),before)
        self.assertFalse((state/'previous.json').exists())

    def test_resume_pointer_is_written_only_after_verified_release(self):
        state=self.root/'state';data=self.manifest()
        self.cloud.record_healthy(state,data,lambda:None,str(self.root/'release'),'demo.example.com')
        pointer=self.cloud.read_json(state/'runtime-release.json')
        self.assertEqual(pointer,{'repo':str((self.root/'release').resolve()),'gitSha':data['gitSha'],'domain':'demo.example.com'})
        self.assertEqual(self.cloud.read_json(state/'boot-ready.json'),pointer)
        before=(state/'runtime-release.json').read_bytes()
        with self.assertRaises(self.cloud.SafetyError):
            self.cloud.record_healthy(state,data,lambda:(_ for _ in ()).throw(self.cloud.SafetyError('failed journey')),str(self.root/'unverified'),'demo.example.com')
        self.assertEqual((state/'runtime-release.json').read_bytes(),before)

    def test_missing_secret_or_permissive_file_is_rejected(self):
        with self.assertRaises(self.cloud.SafetyError): self.cloud.private_file(self.root/'runtime.env')
        path=self.root/'runtime.env'; path.write_text('secret'); path.chmod(0o644)
        with self.assertRaises(self.cloud.SafetyError): self.cloud.private_file(path)

    def test_compose_failure_is_not_hidden(self):
        import subprocess
        with patch.object(self.cloud.subprocess,'run',return_value=subprocess.CompletedProcess(['docker'],1,'','invalid compose')):
            with self.assertRaises(self.cloud.SafetyError): self.cloud.run(['docker','compose','config','--quiet'])

    def test_remote_timeout_and_failed_exit_are_truthful(self):
        for status,code in [('TimedOut',1), ('Failed',1), ('Success',1)]:
            with self.assertRaises(self.cloud.SafetyError): self.cloud.check_invocation({'Status':status,'ResponseCode':code})
        self.assertTrue(self.cloud.check_invocation({'Status':'Success','ResponseCode':0}))

    def test_tar_rejects_escape_links_and_mismatched_manifest(self):
        import io,tarfile
        for name,kind in [('../escape',tarfile.REGTYPE), ('scripts/link',tarfile.SYMTYPE), ('/etc/escape',tarfile.REGTYPE)]:
            archive=self.root/'release.tar.gz'
            with tarfile.open(archive,'w:gz') as output:
                info=tarfile.TarInfo(name); info.type=kind; info.size=1 if kind==tarfile.REGTYPE else 0; info.linkname='/etc'
                output.addfile(info,io.BytesIO(b'x') if info.size else None)
            with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_artifact(archive,self.manifest())

    def test_valid_archive_is_bound_to_release_manifest(self):
        import io,tarfile,hashlib
        data=self.manifest(); sql=b'SELECT 1;'
        data['migrationCompatibility']['files']={'auth-service/src/main/resources/db/migration/V1.sql':hashlib.sha256(sql).hexdigest()}
        files={'deploy/release-manifest.json':json.dumps(data).encode(),'auth-service/src/main/resources/db/migration/V1.sql':sql}
        for name in ['scripts/deploy/remote-entrypoint.sh','scripts/deploy/cloud.py','scripts/cloud/verify-journey.sh','scripts/cloud/verify_journey.py','docker-compose.yml','docker-compose.cloud.yml']:
            files[name]=b'fixture'
        archive=self.root/'valid.tar.gz'
        with tarfile.open(archive,'w:gz') as output:
            for name,body in files.items():
                info=tarfile.TarInfo('./'+name); info.size=len(body); output.addfile(info,io.BytesIO(body))
        self.cloud.validate_artifact(archive,data)
        data['gitSha']='b'*40
        with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_artifact(archive,data)

    def test_guarded_kafka_archive_requires_script_but_legacy_archive_still_valid(self):
        import io, tarfile, hashlib
        for guarded, include_script in [(False, False), (True, False), (True, True)]:
            with self.subTest(guarded=guarded, include_script=include_script):
                data=self.manifest(); sql=b'SELECT 1;'
                sql_path='auth-service/src/main/resources/db/migration/V1.sql'
                data['migrationCompatibility']['files']={sql_path:hashlib.sha256(sql).hexdigest()}
                files={'deploy/release-manifest.json':json.dumps(data).encode(),sql_path:sql}
                for name in ['scripts/deploy/remote-entrypoint.sh','scripts/deploy/cloud.py','scripts/cloud/verify-journey.sh','scripts/cloud/verify_journey.py','docker-compose.yml','docker-compose.cloud.yml']:
                    files[name]=b'fixture'
                if guarded:
                    files['docker-compose.cloud.yml']=b'command: [/bin/bash, /opt/orderflow/kafka-safe-bootstrap.sh]'
                if include_script:
                    files['infrastructure/kafka/safe-bootstrap.sh']=b'#!/bin/bash\nexit 0\n'
                archive=self.root/'guarded.tar.gz'
                with tarfile.open(archive,'w:gz') as output:
                    for name,body in files.items():
                        info=tarfile.TarInfo('./'+name); info.size=len(body); output.addfile(info,io.BytesIO(body))
                if guarded and not include_script:
                    with self.assertRaisesRegex(self.cloud.SafetyError,'Kafka storage guard'):
                        self.cloud.validate_artifact(archive,data)
                else:
                    self.cloud.validate_artifact(archive,data)

    def test_secret_fetch_requires_journey_credentials_before_writing(self):
        from argparse import Namespace
        args=Namespace(account='123456789012',region='us-east-1',environment='cloud-demo',domain='demo.example.com',release='v1.0.0',runtime_env=str(self.root/'config/runtime.env'))
        params={'Parameters':[{'Name':'/orderflow/cloud-demo/runtime/'+key,'Value':'fixture','Type':'SecureString'} for key in ['runtime.env','jwt-private.pem','jwt-public.pem','backup.key']]}
        with patch.object(self.cloud,'target'),patch.object(self.cloud,'run',return_value=json.dumps(params)):
            with self.assertRaises(self.cloud.SafetyError): self.cloud.fetch_secrets(args)
        self.assertFalse(Path(args.runtime_env).exists())

    def test_wrong_authenticated_account_is_rejected(self):
        from argparse import Namespace
        args=Namespace(account='123456789012',region='us-east-1',environment='cloud-demo',domain='demo.example.com',release='v1.0.0')
        with patch.object(self.cloud,'run',return_value=json.dumps({'Account':'999999999999'})):
            with self.assertRaises(self.cloud.SafetyError): self.cloud.target(args,self.manifest())

    def test_private_prometheus_uses_actual_private_network_not_edge_subnet(self):
        networks={'orderflow-cloud_private':{'IPAddress':'172.19.0.5'}}
        self.assertEqual(self.cloud.private_prometheus_address(networks,'orderflow-cloud_private'),'172.19.0.5')
        for invalid in [{'orderflow-cloud_edge':{'IPAddress':'172.31.250.5'}},{'orderflow-cloud_private':{'IPAddress':'8.8.8.8'}}]:
            with self.assertRaises(self.cloud.SafetyError): self.cloud.private_prometheus_address(invalid,'orderflow-cloud_private')

    def test_manifest_rejects_unknown_fields_that_could_contain_secrets(self):
        data=self.manifest();data['password']='private-value'
        with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_manifest(data)

    def test_cloud_accounts_value_matches_journey_synthetic_policy(self):
        accounts={role:{'email':role.lower()+'@example.test','password':'Fixture-password-strong-123'} for role in ['VENDOR','DELIVERY_AGENT','ADMIN']}
        self.cloud.validate_accounts_value(json.dumps(accounts))
        for role,key,value in [('ADMIN','email','real@gmail.com'),('VENDOR','password','short'),('DELIVERY_AGENT','email',accounts['VENDOR']['email'])]:
            invalid=json.loads(json.dumps(accounts));invalid[role][key]=value
            with self.assertRaises(self.cloud.SafetyError): self.cloud.validate_accounts_value(json.dumps(invalid))

    def test_no_database_restore_in_rollback(self):
        import inspect
        self.assertNotIn('restore(',inspect.getsource(self.cloud.rollback))

if __name__ == '__main__': unittest.main()

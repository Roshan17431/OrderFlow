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

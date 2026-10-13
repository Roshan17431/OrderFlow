"""First deployment seed authorization and healthy-state regression contracts."""
from argparse import Namespace
import importlib.util
import json
import inspect
import sys
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

spec=importlib.util.spec_from_file_location('initial_cloud',Path(__file__).resolve().parents[1]/'cloud.py')
cloud=importlib.util.module_from_spec(spec);spec.loader.exec_module(cloud)

class InitialSeedToolSafety(unittest.TestCase):
    def test_initial_only_conflict_never_grants_existing_role(self):
        sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'cloud'))
        import seed_demo
        self.assertIn('initial_only',inspect.signature(seed_demo.fixture_client).parameters,'initial-only seed contract missing')
        client=unittest.mock.Mock()
        def registration(*args,**kwargs):
            if 409 not in kwargs['expected']:
                raise seed_demo.VerificationError('registration conflict')
            return {}
        client.request.side_effect=registration
        with patch.object(seed_demo,'Client',return_value=client),patch.object(seed_demo,'grant_role') as grant:
            with self.assertRaises(seed_demo.VerificationError):
                seed_demo.fixture_client({}, {'email':'vendor@example.test','password':'Synthetic-fixture-password-123'},'VENDOR',['docker'],'authdb',initial_only=True)
            grant.assert_not_called();client.login.assert_not_called()

class InitialSeedSafety(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup);self.root=Path(self.tmp.name)
        config=self.root/'config';config.mkdir(mode=0o700)
        accounts={role:{'email':role.lower()+'@example.test','password':'Synthetic-fixture-password-123'} for role in ['VENDOR','DELIVERY_AGENT','ADMIN']}
        (config/'cloud-accounts.json').write_text(json.dumps(accounts));(config/'cloud-accounts.json').chmod(0o600)
        script=self.root/'scripts/cloud/seed-demo.sh';script.parent.mkdir(parents=True);script.write_text('#!/bin/sh\nexit 0\n');script.chmod(0o755)
        self.args=Namespace(state_dir=str(self.root/'state'),runtime_env=str(config/'runtime.env'),initial_demo_seed=False,environment='cloud-demo',domain='demo.example.test',repo=str(self.root),timeout=60)
        self.data={'releaseVersion':'v1.0.0','gitSha':'a'*40,'migrationCompatibility':{'files':{}}}

    def test_default_deployment_does_not_seed(self):
        with patch.object(cloud,'preflight',return_value=(['docker','compose'],{})),patch.object(cloud,'pull'),patch.object(cloud,'backup_predeploy'),patch.object(cloud,'health'),patch.object(cloud,'verify') as verify,patch.object(cloud,'run') as run:
            cloud.deploy(self.args,self.data)
            verify.assert_called_once()
            self.assertFalse(any('seed-demo.sh' in str(call.args[0]) for call in run.call_args_list))

    def test_explicit_first_seed_runs_after_health_before_journey(self):
        self.args.initial_demo_seed=True;sequence=[];seed_env={}
        def command(command,**kwargs):
            if any('seed-demo.sh' in str(part) for part in command):
                sequence.append('seed');seed_env.update(kwargs['env'])
        with patch.object(cloud,'preflight',return_value=(['docker','compose'],{'IMAGE_AUTH_SERVICE':'registry@sha256:fixture'})),patch.object(cloud,'pull'),patch.object(cloud,'backup_predeploy'),patch.object(cloud,'health',side_effect=lambda *args:sequence.append('health')),patch.object(cloud,'verify',side_effect=lambda *args:sequence.append('verify')),patch.object(cloud,'run',side_effect=command):
            cloud.deploy(self.args,self.data)
        self.assertEqual(sequence,['health','seed','verify'])
        self.assertEqual(seed_env['ORDERFLOW_DEMO_SEED'],'true');self.assertEqual(seed_env['ORDERFLOW_TRUSTED_HOST_ROLE_GRANT'],'true')
        self.assertEqual(seed_env['IMAGE_AUTH_SERVICE'],'registry@sha256:fixture')
        self.assertTrue((Path(self.args.state_dir)/'initial-seed.json').is_file())

    def test_existing_healthy_or_wrong_environment_refuses_initial_seed(self):
        self.args.initial_demo_seed=True
        state=Path(self.args.state_dir);state.mkdir();cloud.atomic_json(state/'healthy.json',{'sentinel':'last healthy'})
        with patch.object(cloud,'preflight') as preflight:
            with self.assertRaises(cloud.SafetyError):cloud.deploy(self.args,self.data)
            preflight.assert_not_called()
        self.assertEqual(cloud.read_json(state/'healthy.json'),{'sentinel':'last healthy'})
        (state/'healthy.json').unlink();self.args.environment='production'
        with self.assertRaises(cloud.SafetyError):cloud.initial_seed_guard(self.args)

    def test_seed_command_failure_does_not_advance_healthy_metadata(self):
        self.args.initial_demo_seed=True;state=Path(self.args.state_dir);state.mkdir();cloud.atomic_json(state/'previous.json',{'sentinel':'preserved previous'})
        def command(command,**kwargs):
            if any('seed-demo.sh' in str(part) for part in command):raise cloud.SafetyError('seed failed')
        with patch.object(cloud,'preflight',return_value=(['docker','compose'],{})),patch.object(cloud,'pull'),patch.object(cloud,'backup_predeploy'),patch.object(cloud,'health'),patch.object(cloud,'verify') as verify,patch.object(cloud,'run',side_effect=command):
            with self.assertRaises(cloud.SafetyError):cloud.deploy(self.args,self.data)
            verify.assert_not_called()
        self.assertFalse((state/'healthy.json').exists());self.assertFalse((state/'initial-seed.json').exists())
        self.assertEqual(cloud.read_json(state/'previous.json'),{'sentinel':'preserved previous'})

if __name__=='__main__':unittest.main()

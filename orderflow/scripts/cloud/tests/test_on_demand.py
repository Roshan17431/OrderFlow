"""Operational safety tests: AWS and poweroff boundaries are always replaced."""
import importlib.util
import json
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))

class OnDemandTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec = importlib.util.spec_from_file_location('on_demand', ROOT/'on_demand.py')
        cls.ops = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(cls.ops)

    def config(self):
        return {'account':'123456789012','region':'us-east-1','instance':'i-0123456789abcdef0','project':'orderflow','environment':'cloud-demo'}

    def instance(self):
        return {'InstanceId':self.config()['instance'],'RootDeviceType':'ebs','State':{'Name':'stopped'},'Tags':[{'Key':k,'Value':v} for k,v in {'Project':'orderflow','Environment':'cloud-demo','ManagedBy':'Terraform','Purpose':'PortfolioDemo','OrderFlowOnDemand':'true'}.items()]}

    def test_reject_unknown_account_before_any_mutation(self):
        aws=Mock(); aws.call.return_value={'Account':'999999999999'}
        with self.assertRaises(self.ops.SafetyError): self.ops.guard(aws,self.config())
        self.assertEqual(aws.call.call_count,1)

    def test_reject_unrelated_instance_and_wrong_shutdown_behavior(self):
        for field,value in [('Tags',[]),('InstanceId','i-11111111111111111'),('RootDeviceType','instance-store')]:
            item=self.instance(); item[field]=value
            with self.subTest(field=field), self.assertRaises(self.ops.SafetyError): self.ops.validate_instance(item,self.config())
        aws=Mock(); aws.call.side_effect=[{'Account':self.config()['account']},{'Reservations':[{'OwnerId':self.config()['account'],'Instances':[self.instance()]}]},{'InstanceInitiatedShutdownBehavior':{'Value':'terminate'}}]
        with self.assertRaises(self.ops.SafetyError): self.ops.guard(aws,self.config())

    def test_explicit_target_required(self):
        for key in ('account','region','instance','project','environment'):
            c=self.config(); c[key]=''
            with self.subTest(key=key), self.assertRaises(self.ops.SafetyError): self.ops.validate_config(c)

    def test_confirmation_is_exact_and_not_assumed(self):
        self.ops.confirm('start',self.config(),'start '+self.config()['instance'])
        with self.assertRaises(self.ops.SafetyError): self.ops.confirm('start',self.config(),'yes')
        with patch('builtins.input',return_value='no'), self.assertRaises(self.ops.SafetyError): self.ops.confirm('stop',self.config(),None)

    def test_running_start_does_not_restart_or_extend_session(self):
        aws=Mock(); item=self.instance(); item['State']['Name']='running'
        with patch.object(self.ops,'guard',return_value=item),patch.object(self.ops,'wait_ready',return_value={'remainingSeconds':100}):
            self.ops.start(aws,self.config(),None,60)
        self.assertFalse(aws.call.called)

    def test_declined_start_never_mutates(self):
        aws=Mock()
        with patch.object(self.ops,'guard',return_value=self.instance()), self.assertRaises(self.ops.SafetyError):
            self.ops.start(aws,self.config(),'yes',60)
        self.assertFalse(aws.call.called)

    def test_wait_has_bounded_deadline(self):
        clock=iter([0,0,0,2])
        with patch.object(self.ops.time,'monotonic',side_effect=lambda:next(clock)),patch.object(self.ops.time,'sleep'), self.assertRaises(self.ops.SafetyError):
            self.ops.wait_for(lambda:False,1,'test')

    def test_ssm_failure_is_not_readiness_success(self):
        aws=Mock(); aws.call.side_effect=[{'Command':{'CommandId':'test'}},{'Status':'Failed','ResponseCode':1,'StandardOutputContent':'secret'}]
        with self.assertRaises(self.ops.SafetyError) as error: self.ops.remote(aws,self.config(),'status',10)
        self.assertNotIn('secret',str(error.exception))

    def test_ssm_nonzero_response_code_is_failure(self):
        aws=Mock(); aws.call.side_effect=[{'Command':{'CommandId':'test'}},{'Status':'Success','ResponseCode':7,'StandardOutputContent':'{}'}]
        with self.assertRaises(self.ops.SafetyError): self.ops.remote(aws,self.config(),'status',10)

class HostTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        spec=importlib.util.spec_from_file_location('on_demand_host',ROOT/'on_demand_host.py')
        cls.host=importlib.util.module_from_spec(spec);spec.loader.exec_module(cls.host)

    def test_default_eight_hours_and_repeated_checks_keep_deadline(self):
        s=self.host.session(None,'boot-1',100,8)
        self.assertEqual(s['deadlineUptime'],28800)
        self.assertEqual(self.host.session(s,'boot-1',200,8),s)
        self.assertEqual(self.host.session(s,'boot-2',10,8)['bootId'],'boot-2')

    def test_session_extension_is_explicit_bounded_and_not_additive(self):
        self.assertEqual(self.host.extend_session({'bootId':'b','deadlineUptime':100},20,1)['deadlineUptime'],3620)
        for v in (0,-1,25,float('nan'),float('inf')):
            with self.subTest(v=v), self.assertRaises(self.host.SafetyError): self.host.hours(v)

    def test_expired_session_requests_shutdown_only_after_guard(self):
        with patch.object(self.host,'verified_host',side_effect=self.host.SafetyError('wrong host')),patch.object(self.host,'poweroff') as power:
            with self.assertRaises(self.host.SafetyError): self.host.tick({'deadlineUptime':1},2,{})
            power.assert_not_called()

    def test_graceful_stop_failure_still_requests_poweroff_after_guard(self):
        with patch.object(self.host,'verified_host'),patch.object(self.host,'claim_shutdown',return_value=True),patch.object(self.host,'quiesce',side_effect=self.host.SafetyError('unhealthy')),patch.object(self.host,'poweroff') as power:
            self.host.tick({'deadlineUptime':1},2,{})
            power.assert_called_once()

    def test_unexpired_session_never_stops_application(self):
        with patch.object(self.host,'quiesce') as stop,patch.object(self.host,'poweroff') as power:
            self.host.tick({'deadlineUptime':10},2,{})
            stop.assert_not_called();power.assert_not_called()

    def test_short_disposable_countdown_calls_harmless_recorder(self):
        import time
        deadline=time.monotonic()+.1
        current={'deadlineUptime':deadline}
        recorded=[]
        with patch.object(self.host,'verified_host'),patch.object(self.host,'claim_shutdown',return_value=True),patch.object(self.host,'quiesce'),patch.object(self.host,'poweroff',side_effect=lambda:recorded.append('stopped')):
            self.host.tick(current,time.monotonic(),{})
            self.assertEqual(recorded,[])
            time.sleep(.15)
            self.host.tick(current,time.monotonic(),{})
        self.assertEqual(recorded,['stopped'])

    def test_extension_must_not_shorten_existing_session(self):
        with self.assertRaises(self.host.SafetyError):
            self.host.extend_session({'bootId':'b','deadlineUptime':7200},20,1)

    def test_extension_during_host_validation_prevents_stale_tick_shutdown(self):
        with tempfile.TemporaryDirectory() as directory:
            store=Path(directory);old={'bootId':'b','deadlineUptime':1}
            (store/'session.json').write_text(json.dumps(old))
            def extend_while_checking(_):
                (store/'session.json').write_text(json.dumps({'bootId':'b','deadlineUptime':10000}))
            with patch.object(self.host,'STATE',store),patch.object(self.host,'verified_host',side_effect=extend_while_checking),patch.object(self.host,'quiesce') as stop,patch.object(self.host,'poweroff') as power:
                self.host.tick(old,2,{})
                power.assert_not_called();stop.assert_not_called()

    def test_extension_after_shutdown_claim_is_rejected(self):
        with self.assertRaises(self.host.SafetyError):
            self.host.extend_session({'bootId':'b','deadlineUptime':1,'shutdownRequested':True},20,8)

    def test_boot_guard_rejects_failed_initial_or_later_release_before_docker(self):
        with tempfile.TemporaryDirectory() as directory:
            releases=Path(directory);state=releases/'state';state.mkdir()
            with patch.object(self.host,'RELEASES',releases),patch.object(self.host,'run',return_value=''),patch.object(self.host,'verified_host'),patch.object(self.host,'release_context') as context:
                self.host.boot_guard({}) # empty first installation
                (state/'schema-release.json').write_text('{}')
                with self.assertRaises(self.host.SafetyError):self.host.boot_guard({})
                pointer={'repo':'release-a','gitSha':'a'*40}
                (state/'runtime-release.json').write_text(json.dumps(pointer))
                with self.assertRaises(self.host.SafetyError):self.host.boot_guard({})
                (state/'boot-ready.json').write_text(json.dumps({'gitSha':'b'*40}))
                with self.assertRaises(self.host.SafetyError):self.host.boot_guard({})
                (state/'boot-ready.json').write_text(json.dumps(pointer))
                self.host.boot_guard({});context.assert_called_once()

    def test_status_does_not_claim_failed_candidate_is_verified(self):
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            release=Mock();args=SimpleNamespace(domain='demo.example.com');data={'releaseVersion':'v1.0.0'}
            with patch.object(self.host,'RELEASES',Path(directory)),patch.object(self.host,'release_context',return_value=(release,args,data)),patch.object(self.host,'run',return_value='active'):
                value=self.host.status({}, {'deadlineUptime':100}, 10, {'instance':'i-0123456789abcdef0'})
                self.assertFalse(value['ready'])
                release.health.assert_not_called()

    def test_resume_uses_release_current_when_deployment_lock_is_acquired(self):
        from contextlib import contextmanager
        from types import SimpleNamespace
        active=['A'];applied=[];release=Mock()
        @contextmanager
        def concurrent_deployment_finishes():
            active[0]='B'
            yield
        release.lock.side_effect=lambda _:concurrent_deployment_finishes()
        args=SimpleNamespace(state_dir='state',repo='repo')
        release.compose.side_effect=lambda a,d:(applied.append(d['releaseVersion']) or ['compose'],{})
        with patch.object(self.host,'boot_guard'),patch.object(self.host,'verified_host'),patch.object(self.host,'verified_release_pointer'),patch.object(self.host,'deployment_lock',side_effect=concurrent_deployment_finishes,create=True),patch.object(self.host,'release_context',side_effect=lambda c:(release,args,{'releaseVersion':active[0]})):
            self.host.resume({})
        self.assertEqual(applied,['B'])

    def test_readiness_is_false_while_a_deployment_holds_the_state_lock(self):
        import fcntl
        from types import SimpleNamespace
        with tempfile.TemporaryDirectory() as directory:
            root=Path(directory);state=root/'state';state.mkdir()
            release=Mock();args=SimpleNamespace(domain='demo.example.com')
            with (state/'.deployment.lock').open('a') as lock:
                fcntl.flock(lock,fcntl.LOCK_EX)
                with patch.object(self.host,'RELEASES',root),patch.object(self.host,'verified_release_pointer'),patch.object(self.host,'release_context',return_value=(release,args,{'releaseVersion':'A'})),patch.object(self.host,'run',return_value='active'):
                    result=self.host.status({}, {'deadlineUptime':100},10,{'instance':'i-0123456789abcdef0'})
                    self.assertFalse(result['ready'])
                    release.health.assert_not_called()

    def test_resume_pointer_cannot_escape_release_directory(self):
        for p in ('/tmp/release','/opt/orderflow/releases/../../tmp','/opt/orderflow/releases/state'):
            with self.subTest(p=p), self.assertRaises(self.host.SafetyError): self.host.release_path(p)

if __name__=='__main__':unittest.main()

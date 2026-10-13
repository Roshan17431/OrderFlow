import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('cloud_common',ROOT/'cloud_common.py')
common=importlib.util.module_from_spec(spec)
spec.loader.exec_module(common)

def metric_fixture():
    return {'starts':{s:1 for s in common.SERVICES}, 'backlog':{s:0 for s in common.OUTBOX_SERVICES}, 'failed':{s:0 for s in common.OUTBOX_SERVICES}, 'dlt':{}}

class CloudSafetyTests(unittest.TestCase):
    def env(self,**changes):
        values={'ORDERFLOW_PUBLIC_URL':'https://orderflow.example.test','ORDERFLOW_CLOUD_E2E':'true','ORDERFLOW_TARGET_ENVIRONMENT':'cloud-demo','AWS_ACCOUNT_ID':'123456789012'}
        values.update(changes);return values
    def test_requires_explicit_opt_in_before_network(self):
        for v in ['', 'false', '1']:
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_CLOUD_E2E=v))
    def test_production_is_rejected(self):
        for v in ['production','prod','', 'dev']:
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_TARGET_ENVIRONMENT=v))
    def test_invalid_account_is_rejected(self):
        for v in ['', 'abc','123','12345678901x']:
            with self.assertRaises(common.SafetyError):common.target(self.env(AWS_ACCOUNT_ID=v))
    def test_https_origin_required(self):
        for v in ['http://example.test','https://name:secret@example.test','https://example.test/api','https://example.test?token=x','https://example.test#x','file:///tmp/a']:
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_PUBLIC_URL=v))
    def test_public_tls_cannot_be_disabled_or_use_local_override(self):
        with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_LOCAL_TLS_TEST='true'))
        with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_CA_FILE='/tmp/ca.pem'))
    def test_loopback_requires_separate_local_tls_opt_in_and_ca(self):
        for host in ['localhost','127.0.0.1']:
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_PUBLIC_URL='https://'+host+':8443'))
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_PUBLIC_URL='https://'+host+':8443',ORDERFLOW_LOCAL_TLS_TEST='true'))
    def test_local_tls_still_verifies_a_real_ca(self):
        with tempfile.NamedTemporaryFile() as f:
            f.write(b'not a real certificate');f.flush()
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_PUBLIC_URL='https://127.0.0.1:8443',ORDERFLOW_LOCAL_TLS_TEST='true',ORDERFLOW_CA_FILE=f.name))
    def test_private_metrics_endpoint_has_no_public_network_or_userinfo(self):
        for url in ['http://public.example.test:9090','http://user:pass@localhost:9090','http://localhost:9090/path','https://localhost:9090?token=x']:
            with self.assertRaises(common.SafetyError):common.private_metrics_url(url)
        self.assertEqual(common.private_metrics_url('http://127.0.0.1:9090'),'http://127.0.0.1:9090')
    def test_alternate_loopback_forms_are_rejected(self):
        for host in ['[::1]','127.0.0.2','2130706433','localhost.']:
            with self.assertRaises(common.SafetyError):common.target(self.env(ORDERFLOW_PUBLIC_URL='https://'+host))
    def test_private_metrics_accepts_only_literal_rfc1918_and_loopback(self):
        for host in ['10.1.2.3','172.31.250.3','192.168.1.3']:
            self.assertEqual(common.private_metrics_url('http://'+host+':9090'),'http://'+host+':9090')
        for host in ['169.254.169.254','172.15.0.1','8.8.8.8','[::1]']:
            with self.assertRaises(common.SafetyError):common.private_metrics_url('http://'+host+':9090')
    def test_fifo_secret_path_does_not_block(self):
        with tempfile.TemporaryDirectory() as d:
            p=str(Path(d)/'fifo');os.mkfifo(p)
            with self.assertRaises(common.SafetyError):common.accounts(p)
    def test_redirects_are_never_followed_with_credentials(self):
        with self.assertRaises(common.VerificationError):common.NoRedirect().redirect_request(None,None,302,'redirect',{},'https://evil.example.test')
    def test_origin_escape_fails_before_network(self):
        client=common.Client(common.target(self.env()))
        with patch.object(client.opener,'open') as open_request:
            for path in ['//evil.example.test','https://evil.example.test','/api\\evil','/api\nheader']:
                with self.assertRaises(common.SafetyError):client.request(path)
            open_request.assert_not_called()
    def account_file(self, mode=0o600, email='vendor@example.test'):
        temp=tempfile.NamedTemporaryFile(mode='w',delete=False)
        json.dump({role:{'email': email if role=='VENDOR' else role.lower()+'@example.test','password':'Fixture-password-strong-123'} for role in ['VENDOR','DELIVERY_AGENT','ADMIN']},temp);temp.close();os.chmod(temp.name,mode)
        self.addCleanup(lambda:os.unlink(temp.name));return temp.name
    def test_protected_accounts_are_loaded(self):
        self.assertEqual(set(common.accounts(self.account_file())),{'VENDOR','DELIVERY_AGENT','ADMIN'})
    def test_world_or_group_readable_account_file_rejected(self):
        for mode in [0o644,0o640,0o666]:
            with self.assertRaises(common.SafetyError):common.accounts(self.account_file(mode))
    def test_real_customer_email_rejected(self):
        for email in ['vendor@gmail.com','vendor@example.com','vendor@example.test.evil.com']:
            with self.assertRaises(common.SafetyError):common.accounts(self.account_file(email=email))
    def test_symlink_secret_file_rejected(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'link';p.symlink_to(self.account_file())
            with self.assertRaises(common.SafetyError):common.accounts(str(p))
    def test_duplicate_accounts_rejected(self):
        p=self.account_file();data=json.loads(Path(p).read_text());data['ADMIN']['email']=data['VENDOR']['email'];Path(p).write_text(json.dumps(data))
        with self.assertRaises(common.SafetyError):common.accounts(p)
    def test_uuid_validation(self):
        self.assertEqual(common.identity('10000000-0000-4000-8000-000000000001'),'10000000-0000-4000-8000-000000000001')
        for v in ['bad',"';drop table users;",None]:
            with self.assertRaises(common.VerificationError):common.identity(v)
    def test_performance_limits_cannot_escalate(self):
        for requests,workers in [('31','1'),('10','3'),('0','1'),('10','0'),('invalid','1')]:
            with self.assertRaises(common.SafetyError):common.performance_limits({'ORDERFLOW_PERFORMANCE_REQUESTS':requests,'ORDERFLOW_PERFORMANCE_CONCURRENCY':workers})
        self.assertEqual(common.performance_limits({}),(12,2))
    def test_metrics_fail_on_missing_required_service_gauges(self):
        with self.assertRaises(common.VerificationError):common.validate_metric_snapshot({'starts':{},'backlog':{},'failed':{},'dlt':{}})
    def test_metrics_fail_on_nonzero_failed_records(self):
        s=metric_fixture();s['failed']['order-service']=1
        with self.assertRaises(common.VerificationError):common.validate_metric_snapshot(s)
    def test_metrics_fail_on_restart_or_new_dlt(self):
        old=metric_fixture();new=metric_fixture();new['starts']['auth-service']+=1
        with self.assertRaises(common.VerificationError):common.compare_metrics(old,new)
        new=metric_fixture();new['dlt']['order-service']=1
        with self.assertRaises(common.VerificationError):common.compare_metrics(old,new)
    def test_no_unbounded_polling(self):
        with patch.object(common.time,'monotonic',side_effect=[0,0,1,2,3]),patch.object(common.time,'sleep'):
            with self.assertRaises(common.VerificationError):common.poll('fixture',lambda:False,timeout=2,interval=.1)

if __name__=='__main__':unittest.main()

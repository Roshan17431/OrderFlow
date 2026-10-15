import importlib
import io
import json
import os
from pathlib import Path
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
common=importlib.import_module('cloud_common')
seed=importlib.import_module('seed_demo')
journey=importlib.import_module('verify_journey')
performance=importlib.import_module('performance_smoke')

class ToolSafetyTests(unittest.TestCase):
    def test_role_grant_rejects_bad_uuid_before_external_tool(self):
        with patch.object(seed.subprocess,'run') as run:
            with self.assertRaises(common.VerificationError):seed.grant_role(['docker'],'authdb',"bad';SQL",'fake@example.test','ADMIN')
            run.assert_not_called()
    def test_role_grant_rejects_real_email_and_unsupported_role(self):
        with patch.object(seed.subprocess,'run') as run:
            for email,role in [('fake@gmail.com','ADMIN'),('fake@example.test','SUPERUSER')]:
                with self.assertRaises(common.SafetyError):seed.grant_role(['docker'],'authdb','10000000-0000-4000-8000-000000000001',email,role)
            run.assert_not_called()
    def test_role_grant_failure_never_reports_success_or_server_output(self):
        result=Mock(returncode=1,stdout='PRIVATE_DATA',stderr='PRIVATE_SECRET')
        with patch.object(seed.subprocess,'run',return_value=result):
            with self.assertRaises(common.VerificationError) as error:seed.grant_role(['docker'],'authdb','10000000-0000-4000-8000-000000000001','fake@example.test','VENDOR')
        self.assertNotIn('PRIVATE',str(error.exception))
    def test_seed_host_runtime_account_mismatch_stops_before_sql(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'runtime.env';p.write_text('AWS_ACCOUNT_ID=999999999999\nORDERFLOW_ENVIRONMENT=cloud-demo\nDOMAIN_NAME=orderflow.example.test\nAUTH_POSTGRES_DB=orderflow_auth\n');p.chmod(0o600)
            with self.assertRaises(common.SafetyError):seed.compose_configuration({'ORDERFLOW_RUNTIME_ENV_FILE':str(p)},{'account':'123456789012','environment':'cloud-demo','url':'https://orderflow.example.test'})
    def test_local_tls_seed_uses_exact_hostname_and_port(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'runtime.env';p.write_text('AWS_ACCOUNT_ID=123456789012\nORDERFLOW_ENVIRONMENT=cloud-demo\nDOMAIN_NAME=localhost:8443\nAUTH_POSTGRES_DB=local_auth\n');p.chmod(0o600)
            _,database,_=seed.compose_configuration({'ORDERFLOW_RUNTIME_ENV_FILE':str(p)}, {'account':'123456789012','environment':'cloud-demo','url':'https://localhost:8443','local':True})
            self.assertEqual(database,'local_auth')
            with self.assertRaises(common.SafetyError):seed.compose_configuration({'ORDERFLOW_RUNTIME_ENV_FILE':str(p)}, {'account':'123456789012','environment':'cloud-demo','url':'https://localhost:9443','local':True})
    def test_seed_host_runtime_domain_mismatch_stops_before_sql(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'runtime.env';p.write_text('AWS_ACCOUNT_ID=123456789012\nORDERFLOW_ENVIRONMENT=cloud-demo\nDOMAIN_NAME=wrong.example.test\nAUTH_POSTGRES_DB=orderflow_auth\n');p.chmod(0o600)
            with self.assertRaises(common.SafetyError):seed.compose_configuration({'ORDERFLOW_RUNTIME_ENV_FILE':str(p)},{'account':'123456789012','environment':'cloud-demo','url':'https://orderflow.example.test'})
    def test_journey_critical_monitoring_failure_precedes_api_mutations(self):
        metrics=Mock();metrics.snapshot.side_effect=common.VerificationError('Missing actual metrics')
        with patch.object(journey,'Client') as client:
            with self.assertRaises(common.VerificationError):journey.journey({}, {r:{} for r in common.ROLES}, metrics)
            client.return_value.login.assert_not_called()
            client.return_value.request.assert_not_called()
    def test_connected_event_alone_does_not_prove_notification(self):
        stream=journey.NotificationStream.__new__(journey.NotificationStream)
        stream.response=io.BytesIO(b'event: connected\ndata: {"connected":true}\n\n')
        stream.notification=None;stream.failure=None
        stream.read()
        self.assertIsNone(stream.notification)
        self.assertIsNotNone(stream.failure)
    def test_sse_event_with_invalid_identity_is_not_accepted(self):
        stream=journey.NotificationStream.__new__(journey.NotificationStream)
        stream.response=io.BytesIO(b'event: notification\ndata: {"id":"not-a-uuid"}\n\n')
        stream.notification=None;stream.failure=None;stream.read()
        self.assertIsNotNone(stream.failure)
    def test_public_performance_cap_applies_before_requests(self):
        with patch.object(performance,'Client') as client:
            with self.assertRaises(common.SafetyError):performance.run({},31,2)
            with self.assertRaises(common.SafetyError):performance.run({},10,3)
            client.assert_not_called()
    def test_stock_verification_rejects_correct_total_with_wrong_reservations(self):
        client=Mock()
        client.request.return_value={'onHandQuantity':5,'reservedQuantity':0,'availableQuantity':5}
        self.assertFalse(journey.stock_state(client,'product',5,2,3))
        client.request.return_value={'onHandQuantity':5,'reservedQuantity':2,'availableQuantity':3}
        self.assertEqual(journey.stock_state(client,'product',5,2,3),client.request.return_value)

    def test_percentiles_use_actual_bounded_samples(self):
        self.assertEqual(performance.percentile([4,1,2,3],.5),2)
        self.assertEqual(performance.percentile([4,1,2,3],.95),4)
        self.assertEqual(performance.percentile([4,1,2,3],.99),4)
        with self.assertRaises(common.VerificationError):performance.percentile([],.95)
    def test_report_never_overwrites_existing_file(self):
        with tempfile.TemporaryDirectory() as d:
            p=Path(d)/'report.json';p.write_text('existing work')
            with self.assertRaises(FileExistsError):common.write_report(p,{'result':'PASS'})
            self.assertEqual(p.read_text(),'existing work')
    def test_metric_nan_and_unknown_labels_are_rejected(self):
        with self.assertRaises(common.VerificationError):common.metric_map([{'metric':{'service':'order-service'},'value':[1,'NaN']}], 'service')
        with self.assertRaises(common.VerificationError):common.metric_map([{'metric':{'service':'unknown'},'value':[1,'0']}], 'service')

if __name__=='__main__':unittest.main()

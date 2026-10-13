"""Real local HTTPS transport checks with a temporary CA; no application journey mock."""
import http.server
import importlib
import json
from pathlib import Path
import shutil
import ssl
import subprocess
import sys
import tempfile
import threading
import unittest
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
common=importlib.import_module('cloud_common')

class Handler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path=='/redirect':
            self.send_response(302);self.send_header('Location','https://elsewhere.example.test/');self.end_headers();return
        self.send_response(200)
        if self.path in ['/api/auth/csrf','/unsafe']:
            secure='; Secure' if self.path!='/unsafe' else ''
            self.send_header('Set-Cookie','ORDERFLOW_REFRESH=transport-fixture; Path=/api/auth; HttpOnly; SameSite=Strict'+secure)
            self.send_header('Set-Cookie','XSRF-TOKEN=transport-csrf; Path=/'+secure)
        data=json.dumps({'headerName':'X-XSRF-TOKEN','parameterName':'_csrf','token':'transport-csrf'}).encode()
        self.send_header('Content-Type','application/json');self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def log_message(self,*args):pass

@unittest.skipUnless(shutil.which('openssl'),'openssl required for real temporary local TLS fixture')
class HttpsTransportTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.tmp=tempfile.TemporaryDirectory(prefix='orderflow-cloud-tls-');directory=Path(cls.tmp.name)
        cls.ca=directory/'ca.pem';key=directory/'key.pem'
        subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost,IP:127.0.0.1','-addext','basicConstraints=critical,CA:TRUE','-keyout',str(key),'-out',str(cls.ca)],check=True,capture_output=True,timeout=15)
        cls.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),Handler)
        context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(cls.ca,key)
        cls.server.socket=context.wrap_socket(cls.server.socket,server_side=True)
        cls.thread=threading.Thread(target=cls.server.serve_forever,daemon=True);cls.thread.start()
        cls.env={'ORDERFLOW_CLOUD_E2E':'true','ORDERFLOW_TARGET_ENVIRONMENT':'cloud-demo','AWS_ACCOUNT_ID':'123456789012','ORDERFLOW_PUBLIC_URL':'https://localhost:'+str(cls.server.server_port),'ORDERFLOW_LOCAL_TLS_TEST':'true','ORDERFLOW_CA_FILE':str(cls.ca)}
    @classmethod
    def tearDownClass(cls):
        cls.server.shutdown();cls.server.server_close();cls.thread.join(timeout=2);cls.tmp.cleanup()
    def test_explicit_ca_keeps_certificate_verification_enabled(self):
        configuration=common.target(self.env)
        self.assertEqual(configuration['context'].verify_mode,ssl.CERT_REQUIRED)
        self.assertTrue(configuration['context'].check_hostname)
        client=common.Client(configuration);self.assertEqual(client.csrf_token(),'transport-csrf');client.verify_refresh_cookie()
    def test_insecure_cookie_is_rejected_from_actual_https_response(self):
        client=common.Client(common.target(self.env));client.request('/unsafe')
        with self.assertRaises(common.VerificationError):client.verify_refresh_cookie()
    def test_untrusted_certificate_cannot_be_silently_accepted(self):
        configuration=common.target(self.env);configuration['context']=ssl.create_default_context()
        with self.assertRaises(common.VerificationError):common.Client(configuration).request('/api/auth/csrf')
    def test_actual_https_redirect_is_refused(self):
        with self.assertRaises(common.VerificationError):common.Client(common.target(self.env)).request('/redirect')

if __name__=='__main__':unittest.main()

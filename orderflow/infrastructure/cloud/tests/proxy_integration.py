#!/usr/bin/env python3
"""Adapt and test real Caddy HTTP handlers against an isolated host echo server.
Uses one named Caddy container and loopback ephemeral port; never accesses app data.
TLS/ACME disabled only in the in-memory test config, not the deployed Caddyfile.
"""
import http.server
import json
from pathlib import Path
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request
import uuid
ROOT=Path(__file__).resolve().parents[3]
IMAGE='caddy:2.11.7-alpine'

class Echo(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        if self.path.startswith('/api/notifications/stream'):
            self.send_response(200);self.send_header('Content-Type','text/event-stream');self.end_headers()
            self.wfile.write(b'data: first\n\n');self.wfile.flush();time.sleep(1)
            self.wfile.write(b'data: second\n\n');self.wfile.flush();return
        data=json.dumps({'path':self.path,'headers':dict(self.headers)}).encode()
        self.send_response(200);self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data)
    def do_POST(self):
        try:
            self.rfile.read(int(self.headers.get('Content-Length',0)))
        except Exception:pass
        self.do_GET()
    def log_message(self,*a):pass

server=http.server.ThreadingHTTPServer(('0.0.0.0',0),Echo)
threading.Thread(target=server.serve_forever,daemon=True).start()
name='orderflow-phase10-proxy-'+uuid.uuid4().hex[:8]
with tempfile.TemporaryDirectory(prefix='orderflow-proxy-') as tmp:
    tmp=Path(tmp)
    r=subprocess.run(['docker','run','--rm','--name',name+'-adapt','-e','DOMAIN_NAME=orderflow.example.invalid','-e','ACME_EMAIL=operator@example.invalid','-v',str(ROOT/'infrastructure/caddy/Caddyfile')+':/etc/caddy/Caddyfile:ro',IMAGE,'caddy','adapt','--config','/etc/caddy/Caddyfile'],capture_output=True,text=True,check=True)
    config=json.loads(r.stdout)
    config['logging']['logs']['log0']['writer']['filename']='/logs/access.json'
    # Replace only listener/TLS/upstream addresses. Preserve route handlers,
    # redaction, limits, forwarding, stream flush and transport timeouts.
    app=config['apps']['http']; public=next(s for s in app['servers'].values() if ':443' in s['listen'])
    public['listen']=[':8085'];public.pop('tls_connection_policies',None)
    public['automatic_https']={'disable':True}
    app['servers']={'test':public};config['apps'].pop('tls',None)
    def replace(value):
        if isinstance(value,dict):
            for k,v in value.items():
                if k=='dial' and v in ['api-gateway-edge:8080','frontend:3000']:value[k]='host.docker.internal:'+str(server.server_port)
                else:replace(v)
        elif isinstance(value,list):
            for v in value:replace(v)
    replace(config)
    (tmp/'config.json').write_text(json.dumps(config));(tmp/'logs').mkdir()
    try:
        subprocess.run(['docker','run','-d','--name',name,'--memory','128m','-p','127.0.0.1::8085','-v',str(tmp/'config.json')+':/etc/caddy/test.json:ro','-v',str(tmp/'logs')+':/logs',IMAGE,'caddy','run','--config','/etc/caddy/test.json'],check=True,capture_output=True)
        port=json.loads(subprocess.check_output(['docker','inspect',name]))[0]['NetworkSettings']['Ports']['8085/tcp'][0]['HostPort']
        def request(path, headers=None, data=None):
            h={'Host':'orderflow.example.invalid'};h.update(headers or {})
            return urllib.request.urlopen(urllib.request.Request('http://127.0.0.1:'+port+path,headers=h,data=data),timeout=5)
        for _ in range(50):
            try:request('/').close();break
            except Exception:time.sleep(.1)
        else:raise AssertionError('Caddy test startup failed')
        for path in ['/products','/api/products','/oauth2/authorization/google','/login/oauth2/code/google','/oauth2/success','/oauth2/failure']:
            with request(path) as r:assert json.load(r)['path']==path,path
        for path in ['/actuator/health','/api/auth/actuator/prometheus','/internal/test','/api/payments/internal/test','/eureka/']:
            try:request(path);raise AssertionError('Internal path exposed '+path)
            except urllib.error.HTTPError as e:assert e.code==404
        with request('/api/products?code=QUERY_SECRET',{'Forwarded':'for=203.0.113.5;proto=http','X-Forwarded-For':'203.0.113.5, 1.1.1.1','X-Forwarded-Proto':'http','X-Forwarded-Host':'evil.invalid','X-Real-IP':'203.0.113.5','X-Correlation-ID':'fixture-correlation','Authorization':'Bearer HEADER_SECRET','Cookie':'secret=COOKIE_SECRET'}) as r:
            h={k.lower():v for k,v in json.load(r)['headers'].items()}
        assert 'forwarded' not in h and 'x-real-ip' not in h
        assert h['x-forwarded-for']!='203.0.113.5, 1.1.1.1' and ',' not in h['x-forwarded-for']
        assert h['x-forwarded-proto']=='https' and h['x-forwarded-host']=='orderflow.example.invalid'
        assert h['x-correlation-id']=='fixture-correlation'
        start=time.monotonic()
        with request('/api/notifications/stream') as r:
            assert r.readline()==b'data: first\n' and time.monotonic()-start<.8,'SSE was buffered'
        try:
            request('/api/products',data=b'x'*(2*1024*1024+1));raise AssertionError('Oversized body accepted')
        except urllib.error.HTTPError as e:assert e.code==413
        time.sleep(.2)
        logs=(tmp/'logs/access.json').read_text()
        for secret in ['QUERY_SECRET','HEADER_SECRET','COOKIE_SECRET']:assert secret not in logs,'Secret in access log'
        assert 'fixture-correlation' not in logs # complete header exclusion
        print('PASS Caddy adaptation, six routing paths, five internal blocks, forwarding spoof protection, correlation propagation, immediate SSE, 413 body bound, access-log secret redaction')
    finally:
        subprocess.run(['docker','rm','-f',name],capture_output=True)
        server.shutdown()

"""Offline/effective Compose policies; no Docker daemon or real secret required.
Run python3 -m unittest discover -s infrastructure/cloud/tests -v.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
APPS = 'service-registry api-gateway auth-service product-service cart-service inventory-service order-service payment-service delivery-service notification-service operations-service frontend'.split()
OWNERS = 'AUTH PRODUCT INVENTORY ORDER PAYMENT DELIVERY NOTIFICATION OPERATIONS'.split()

def fixture_env():
    text = (ROOT / 'docker-compose.cloud.yml').read_text()
    env = {key: 'fixture_'+key.lower() for key in re.findall(r'\$\{([A-Z_]+):\?', text)}
    for service in APPS:
        key = 'IMAGE_'+service.upper().replace('-', '_')
        env[key] = '123456789012.dkr.ecr.us-east-1.amazonaws.com/orderflow/'+service+'@sha256:'+'a'*64
    env.update(DOMAIN_NAME='orderflow.example.invalid', ACME_EMAIL='operator@example.invalid', POSTGRES_USER='fixture_admin', KAFKA_CLUSTER_ID='MkU3OEVBNTcwNTJENDM2Qk')
    return env

def effective(extra=None):
    with tempfile.NamedTemporaryFile(mode='w') as f:
        for key, value in fixture_env().items():
            f.write(key+'='+value+'\n')
        f.flush()
        result = subprocess.run(['docker', 'compose', '--env-file', f.name, '-f', 'docker-compose.yml', '-f', 'docker-compose.cloud.yml', '--profile', 'observability', 'config', '--format', 'json'], cwd=ROOT, capture_output=True, text=True, env=extra)
    if result.returncode:
        raise AssertionError('Compose config failed: '+result.stderr)
    return json.loads(result.stdout)

class RuntimePolicyTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.config = effective()
        cls.services = cls.config['services']

    def test_all_twelve_images_have_no_build_or_local_ports(self):
        for name in APPS:
            s = self.services[name]
            self.assertNotIn('build', s, name)
            self.assertFalse(s.get('ports'), name)
            self.assertRegex(s['image'], r'^123456789012\.dkr\.ecr\.us-east-1\.amazonaws\.com/.+@sha256:[a-f0-9]{64}$')
            self.assertNotIn('profiles', s, name)
            self.assertEqual(s['restart'], 'unless-stopped')
            self.assertEqual(s['stop_grace_period'], '45s')

    def test_only_caddy_has_public_ports(self):
        for name, s in self.services.items():
            for p in s.get('ports', []):
                if name == 'caddy':
                    self.assertIn(str(p['published']), ['80', '443'])
                else:
                    self.assertIn(name, ['grafana', 'prometheus'])
                    self.assertEqual(p['host_ip'], '127.0.0.1')

    def test_infrastructure_images_are_frozen_by_registry_digest(self):
        for name, service in self.services.items():
            if name not in APPS:
                self.assertRegex(service['image'], r'^.+@sha256:[a-f0-9]{64}$', name)

    def test_no_local_passwords_or_localhost_public_origin(self):
        for name, s in self.services.items():
            for key, value in s.get('environment', {}).items():
                self.assertNotIn('local_password', str(value), (name, key))
                self.assertNotIn('local-simulator-secret', str(value), (name, key))
                if key in ['APP_JWT_ISSUER', 'APP_CORS_ALLOWED_ORIGINS']:
                    self.assertEqual(value, 'https://orderflow.example.invalid')

    def test_key_mounts_use_filesystem_resource_locations(self):
        env = self.services["auth-service"]["environment"]
        for key in ["APP_JWT_PRIVATE_KEY_LOCATION", "APP_JWT_PUBLIC_KEY_LOCATION"]:
            self.assertTrue(env[key].startswith("file:/run/secrets/"), "Spring ResourceLoader needs explicit filesystem URI for mounted keys")

    def test_database_owners_are_separate_and_auth_waits_for_bootstrap(self):
        db = self.services['database-bootstrap']['environment']
        owners = [db[p+'_POSTGRES_USER'] for p in OWNERS]
        self.assertEqual(len(set(owners)), 8)
        self.assertNotIn(db['POSTGRES_USER'], owners)
        self.assertEqual(self.services['auth-service']['environment']['POSTGRES_USER'], db['AUTH_POSTGRES_USER'])
        self.assertEqual(self.services['auth-service']['depends_on']['database-bootstrap']['condition'], 'service_completed_successfully')
        script = (ROOT/'infrastructure/bootstrap/bootstrap-databases.sh').read_text()
        for privilege in ['NOSUPERUSER', 'NOCREATEDB', 'NOCREATEROLE', 'NOBYPASSRLS']:
            self.assertIn(privilege, script)
        self.assertIn('\\getenv pass ORDERFLOW_DATABASE_PASSWORD', script)
        self.assertNotIn('-v pass=', script)

    def test_persistent_keys_are_read_only_and_fail_if_missing(self):
        auth = self.services['auth-service']
        self.assertEqual(auth['environment']['APP_JWT_ALLOW_EPHEMERAL_KEYS'], 'false')
        self.assertEqual(auth['environment']['APP_AUTH_REFRESH_COOKIE_SECURE'], 'true')
        for v in auth['volumes']:
            self.assertTrue(v['read_only'])
            self.assertFalse(v['bind']['create_host_path'])
        self.assertEqual(len(auth['volumes']), 2)

    def test_kafka_and_redis_are_persistent_and_private(self):
        k = self.services['kafka']
        self.assertNotIn('EXTERNAL', k['environment']['KAFKA_LISTENERS'])
        self.assertNotIn('localhost', k['environment']['KAFKA_ADVERTISED_LISTENERS'])
        self.assertTrue(k['volumes'])
        self.assertIn('--requirepass', self.services['redis']['command'][-1])
        self.assertIn('--appendonly yes', self.services['redis']['command'][-1])
        for key in ['postgres-data', 'redis-data', 'kafka-data', 'caddy-data', 'grafana-data']:
            self.assertTrue(self.config['volumes'][key]['driver_opts']['device'].startswith('/opt/orderflow/data/'))

    def test_trusted_proxy_has_fixed_address(self):
        c = self.services['caddy']['networks']['edge']['ipv4_address']
        g = self.services['api-gateway']['environment']
        self.assertEqual(c, g['APP_PROXY_TRUSTED_ADDRESS'])
        self.assertEqual(self.services['api-gateway']['networks']['edge']['aliases'], ['api-gateway-edge'])
        self.assertIn('reverse_proxy api-gateway-edge:8080', (ROOT/'infrastructure/caddy/Caddyfile').read_text())
        self.assertEqual(g['SERVER_FORWARD_HEADERS_STRATEGY'], 'none')
        self.assertTrue(re.fullmatch(g['SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_TRUSTED_PROXIES'], c))
        self.assertEqual(self.services['auth-service']['environment']['SERVER_FORWARD_HEADERS_STRATEGY'], 'framework')

    def test_gateway_cannot_take_reserved_caddy_address_before_proxy_start(self):
        gateway = self.services['api-gateway']['networks']['edge']
        self.assertEqual(gateway.get('ipv4_address'), '172.31.250.3')
        self.assertNotEqual(gateway['ipv4_address'], self.services['caddy']['networks']['edge']['ipv4_address'])

    def test_cloud_profile_is_explicit_and_monitoring_bounded(self):
        p = self.services['payment-service']['environment']
        self.assertEqual(p['SPRING_PROFILES_ACTIVE'], 'docker,cloud-demo')
        self.assertEqual(p['APP_PAYMENT_DEMO_COMPLETION_ENABLED'], 'true')
        for s in self.services.values():
            self.assertEqual(s['logging']['options']['max-size'], '10m')
        self.assertIn('--storage.tsdb.retention.time=15d', self.services['prometheus']['command'])
        self.assertTrue(self.services['grafana']['environment']['GF_SECURITY_ADMIN_PASSWORD'])

    def test_memory_budgets_leave_jvm_native_and_host_reserve(self):
        # Local heap128/RSS375MiB observations show that heap is only part of
        # the process budget. Kafka-facing services need extra native reserve.
        heavy = {'inventory-service', 'order-service', 'payment-service',
                 'delivery-service', 'notification-service'}
        total = sum(int(s.get('mem_limit', 0)) for s in self.services.values())
        self.assertLessEqual(total, 14 * 1024**3, 'Reserve at least2GiB of the proposed16GiB host')
        for name in APPS:
            if name == 'frontend':
                continue
            service = self.services[name]
            options = service['environment']['JAVA_TOOL_OPTIONS']
            match = re.search(r'-XX:MaxRAMPercentage=([0-9.]+)', options)
            self.assertIsNotNone(match, name)
            heap_fraction = float(match.group(1)) / 100
            self.assertGreater(heap_fraction, 0, name)
            self.assertLessEqual(heap_fraction, .5, name)
            native_budget = int(service['mem_limit']) * (1 - heap_fraction)
            required = 128 if name == 'service-registry' else 384 if name in heavy else 256
            self.assertGreaterEqual(native_budget, required * 1024**2, name)

    def test_missing_secret_fails_closed(self):
        env = os.environ.copy()
        env.update(fixture_env())
        env.pop('REDIS_PASSWORD')
        # No env-file: secret absence must fail before any container is started.
        r = subprocess.run(['docker','compose','-f','docker-compose.yml','-f','docker-compose.cloud.yml','config'], cwd=ROOT, env=env, capture_output=True, text=True)
        self.assertNotEqual(r.returncode, 0)
        self.assertIn('REDIS_PASSWORD', r.stderr)

if __name__ == '__main__':
    unittest.main()

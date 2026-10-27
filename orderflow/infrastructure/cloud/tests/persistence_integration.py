#!/usr/bin/env python3
"""Disposable restart probe, not full E2E. Uses four services and fresh volumes.

Requires locally built migrated auth image; publishes only a random loopback
auth port. No AWS, existing Compose project, user volume or business data is used.
Auth uses direct HTTP only inside this isolated probe; cloud secure cookies are
not changed. Outputs no passwords, keys, bearer tokens or response bodies.
"""
import argparse
import hashlib
import http.cookiejar
import json
from pathlib import Path
import secrets
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[3]


def command(args, *, data=None, timeout=90):
    result = subprocess.run(args, input=data, text=True, capture_output=True, timeout=timeout)
    if result.returncode:
        raise RuntimeError('Isolated probe command failed: ' + Path(args[0]).name +
                           ' (private output withheld)')
    return result.stdout.strip()


def wait(probe, seconds=120):
    deadline = time.monotonic() + seconds
    while time.monotonic() < deadline:
        try:
            value = probe()
            if value:
                return value
        except (RuntimeError, OSError, ValueError):
            pass
        time.sleep(1)
    raise RuntimeError('Isolated probe readiness deadline exceeded')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--auth-image', default='orderflow-boot4/auth-service:verification')
    parser.add_argument('--report', required=True, help='New nonsecret JSON result file')
    args = parser.parse_args()
    output = Path(args.report)
    if output.exists():
        raise RuntimeError('Refusing to overwrite an existing result')
    # Fail before creating any fixture if the migration image is missing.
    image = json.loads(command(['docker', 'image', 'inspect', args.auth_image]))[0]
    namespace = 'orderflow-persistence-' + uuid.uuid4().hex[:10]
    containers, volumes = [], []
    network_created = False
    db_password, redis_password = secrets.token_hex(24), secrets.token_hex(24)
    try:
        command(['docker', 'network', 'create', namespace]); network_created = True
        for suffix in ('postgres', 'redis', 'kafka'):
            name = namespace + '-' + suffix
            command(['docker', 'volume', 'create', name]); volumes.append(name)
        with tempfile.TemporaryDirectory(prefix=namespace + '-') as tmp:
            directory = Path(tmp)
            private, public = directory / 'private', directory / 'public'
            command(['openssl', 'genpkey', '-algorithm', 'RSA', '-pkeyopt', 'rsa_keygen_bits:2048', '-out', str(private)])
            command(['openssl', 'pkey', '-in', str(private), '-pubout', '-out', str(public)])
            key_hashes = [hashlib.sha256(p.read_bytes()).hexdigest() for p in (private, public)]
            command(['docker', 'run', '--rm', '--network', 'none', '--memory', '64m',
                     '-v', str(directory) + ':/keys', 'postgres:16-alpine', 'sh', '-ec',
                     'chown 10001:10001 /keys/private /keys/public; chmod 0400 /keys/private /keys/public'])
            definitions = {
                'postgres': ['--memory', '384m', '-e', 'POSTGRES_PASSWORD=' + db_password,
                    '-e', 'POSTGRES_USER=fixture_admin', '-e', 'POSTGRES_DB=postgres',
                    '-v', volumes[0] + ':/var/lib/postgresql/data', 'postgres:16-alpine'],
                'redis': ['--memory', '384m', '-e', 'REDISCLI_AUTH=' + redis_password,
                    '-v', volumes[1] + ':/data', 'redis:7-alpine', 'redis-server',
                    '--appendonly', 'yes', '--appendfsync', 'everysec', '--requirepass', redis_password,
                    '--maxmemory', '256mb', '--maxmemory-policy', 'noeviction'],
                'kafka': ['--memory', '1g', '-e', 'CLUSTER_ID=MkU3OEVBNTcwNTJENDM2Qk',
                    '-e', 'KAFKA_NODE_ID=1', '-e', 'KAFKA_PROCESS_ROLES=broker,controller',
                    '-e', 'KAFKA_CONTROLLER_QUORUM_VOTERS=1@kafka:29093',
                    '-e', 'KAFKA_LISTENERS=CONTROLLER://:29093,INTERNAL://:29092',
                    '-e', 'KAFKA_ADVERTISED_LISTENERS=INTERNAL://kafka:29092',
                    '-e', 'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP=CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT',
                    '-e', 'KAFKA_CONTROLLER_LISTENER_NAMES=CONTROLLER',
                    '-e', 'KAFKA_INTER_BROKER_LISTENER_NAME=INTERNAL',
                    '-e', 'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR=1',
                    '-e', 'KAFKA_LOG_DIRS=/var/lib/kafka/data', '-e', 'KAFKA_HEAP_OPTS=-Xms256m -Xmx512m',
                    '-v', volumes[2] + ':/var/lib/kafka/data', 'apache/kafka:3.9.1'],
                'auth': ['--platform', image['Os'] + '/' + image['Architecture'],
                    '--memory', '640m', '--read-only', '--tmpfs', '/tmp:size=64m,mode=1777',
                    '-p', '127.0.0.1::8081', '-v', str(private) + ':/run/secrets/private:ro',
                    '-v', str(public) + ':/run/secrets/public:ro'],
            }
            auth_env = dict(SPRING_PROFILES_ACTIVE='docker,cloud-demo',
                JAVA_TOOL_OPTIONS='-XX:InitialRAMPercentage=15.0 -XX:MaxRAMPercentage=50.0 -XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError',
                POSTGRES_HOST='postgres', POSTGRES_DB='fixture_auth', POSTGRES_USER='fixture_auth',
                POSTGRES_PASSWORD=db_password, REDIS_HOST='redis', REDIS_PASSWORD=redis_password,
                KAFKA_BOOTSTRAP_SERVERS='kafka:29092', EUREKA_CLIENT_ENABLED='false',
                APP_JWT_ALLOW_EPHEMERAL_KEYS='false', APP_JWT_KEY_ID='persistence-fixture',
                APP_JWT_ISSUER='https://acceptance.example.invalid',
                APP_JWT_PRIVATE_KEY_LOCATION='file:/run/secrets/private',
                APP_JWT_PUBLIC_KEY_LOCATION='file:/run/secrets/public',
                APP_AUTH_REFRESH_COOKIE_SECURE='false', APP_BOOTSTRAP_ADMIN_ENABLED='false')
            for key, value in auth_env.items():
                definitions['auth'] += ['-e', key + '=' + value]
            definitions['auth'].append(args.auth_image)

            def start(service):
                name = namespace + '-' + service
                if name not in containers: containers.append(name)
                command(['docker', 'run', '-d', '--name', name, '--network', namespace,
                         '--network-alias', service, *definitions[service]])

            def execute(service, *parts, data=None):
                return command(['docker', 'exec', '-i', namespace + '-' + service, *parts], data=data)

            def sql(query):
                return execute('postgres', 'psql', '-X', '-At', '-U', 'fixture_admin', '-d', 'fixture_auth', '-c', query)

            for service in ('postgres', 'redis', 'kafka'): start(service)
            wait(lambda: execute('postgres', 'pg_isready', '-U', 'fixture_admin', '-d', 'postgres'))
            bootstrap_env = ['POSTGRES_HOST=localhost', 'POSTGRES_USER=fixture_admin',
                             'POSTGRES_DB=postgres', 'POSTGRES_PASSWORD=' + db_password]
            for owner in ('AUTH', 'PRODUCT', 'INVENTORY', 'ORDER', 'PAYMENT', 'DELIVERY', 'NOTIFICATION', 'OPERATIONS'):
                bootstrap_env += [owner + '_POSTGRES_DB=fixture_' + owner.lower(),
                                  owner + '_POSTGRES_USER=fixture_' + owner.lower(),
                                  owner + '_POSTGRES_PASSWORD=' + db_password]
            env_args = [part for item in bootstrap_env for part in ('-e', item)]
            command(['docker', 'exec', '-i', *env_args, namespace + '-postgres', 'sh'],
                    data=(ROOT / 'infrastructure/bootstrap/bootstrap-databases.sh').read_text())
            wait(lambda: execute('redis', 'redis-cli', 'PING') == 'PONG')
            kafka = '/opt/kafka/bin/'
            wait(lambda: execute('kafka', kafka + 'kafka-topics.sh', '--bootstrap-server', 'kafka:29092', '--list') is not None)
            execute('kafka', kafka + 'kafka-topics.sh', '--bootstrap-server', 'kafka:29092', '--create',
                    '--topic', 'restart-fixture', '--partitions', '1', '--replication-factor', '1')
            execute('kafka', kafka + 'kafka-console-producer.sh', '--bootstrap-server', 'kafka:29092',
                    '--topic', 'restart-fixture', data='synthetic-retained-event\n')
            execute('redis', 'redis-cli', 'SET', 'restart-fixture', 'synthetic-retained-cart', 'EX', '3600')
            start('auth')

            def client():
                port = json.loads(command(['docker', 'inspect', namespace + '-auth']))[0]['NetworkSettings']['Ports']['8081/tcp'][0]['HostPort']
                origin = 'http://127.0.0.1:' + port
                opener = urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
                def request(path, data=None, token=None):
                    headers = {'Content-Type': 'application/json'}
                    if token: headers['Authorization'] = 'Bearer ' + token
                    payload = None
                    if data is not None:
                        csrf = json.load(opener.open(origin + '/api/auth/csrf', timeout=10))['token']
                        headers['X-XSRF-TOKEN'] = csrf
                        payload = json.dumps(data).encode()
                    return json.load(opener.open(urllib.request.Request(origin + path, data=payload, headers=headers), timeout=10))
                wait(lambda: request('/actuator/health/readiness').get('status') == 'UP')
                return request

            request = client()
            registered = request('/api/auth/register', {'email': namespace + '@example.test',
                'password': secrets.token_urlsafe(24), 'displayName': 'Synthetic restart fixture'})
            bearer, user_id = registered['accessToken'], registered['user']['id']
            assert request('/api/auth/me', token=bearer)['id'] == user_id
            jwks = request('/api/auth/.well-known/jwks.json')
            history = sql('SELECT version, checksum, success FROM flyway_schema_history ORDER BY installed_rank')
            assert history and all(row.endswith('|t') for row in history.splitlines())
            time.sleep(2)  # Allow Redis every-second AOF fsync before graceful stop.
            # Remove/recreate containers with the same volumes; never delete data.
            for service in ('auth', 'kafka', 'redis', 'postgres'):
                command(['docker', 'stop', '--time', '45', namespace + '-' + service])
                command(['docker', 'rm', namespace + '-' + service])
            for service in ('postgres', 'redis', 'kafka'): start(service)
            wait(lambda: execute('postgres', 'pg_isready', '-U', 'fixture_admin', '-d', 'postgres'))
            wait(lambda: execute('redis', 'redis-cli', 'PING') == 'PONG')
            start('auth'); request = client()
            assert request('/api/auth/me', token=bearer)['id'] == user_id
            assert request('/api/auth/.well-known/jwks.json') == jwks
            assert sql('SELECT version, checksum, success FROM flyway_schema_history ORDER BY installed_rank') == history
            assert sql('SELECT count(*) FROM users') == '1'
            assert execute('redis', 'redis-cli', 'GET', 'restart-fixture') == 'synthetic-retained-cart'
            retained = execute('kafka', kafka + 'kafka-console-consumer.sh', '--bootstrap-server', 'kafka:29092',
                '--topic', 'restart-fixture', '--from-beginning', '--max-messages', '1', '--timeout-ms', '30000')
            assert retained == 'synthetic-retained-event'
            mounted_hashes = execute('auth', 'sha256sum', '/run/secrets/private', '/run/secrets/public')
            assert [row.split()[0] for row in mounted_hashes.splitlines()] == key_hashes
            result = {'result': 'PASS', 'scope': 'isolated-four-service-container-recreation',
                'authImageId': image['Id'], 'authArchitecture': image['Architecture'],
                'postgresUserRetained': True, 'flywayHistoryUnchanged': True,
                'redisAofRecordRetained': True, 'kafkaRecordRetained': True,
                'jwtIssuedBeforeRestartAcceptedAfterRestart': True, 'jwksUnchanged': True,
                'signingKeyBytesUnchanged': True, 'fullCommerceE2E': 'NOT RUN',
                'hostEbsReboot': 'NOT RUN', 'secureCookieThroughCaddy': 'NOT RUN'}
            output.parent.mkdir(parents=True, exist_ok=True)
            output.write_text(json.dumps(result, indent=2) + '\n')
            print('PASS isolated PostgreSQL/Flyway, Redis AOF, Kafka record, persisted JWT and signing keys after container recreation')
    finally:
        for name in reversed(containers):
            subprocess.run(['docker', 'rm', '-f', name], capture_output=True, timeout=60)
        for name in volumes:
            subprocess.run(['docker', 'volume', 'rm', name], capture_output=True, timeout=30)
        if network_created:
            subprocess.run(['docker', 'network', 'rm', namespace], capture_output=True, timeout=30)


if __name__ == '__main__':
    main()

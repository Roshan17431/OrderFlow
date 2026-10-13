"""Shared guarded HTTPS clients for opt-in OrderFlow demo/staging checks."""
import http.cookiejar
import ipaddress
import json
import math
import os
from pathlib import Path
import re
import ssl
import stat
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

class SafetyError(Exception):
    pass
class VerificationError(Exception):
    pass

ROLES = ('VENDOR', 'DELIVERY_AGENT', 'ADMIN')
SERVICES = ('service-registry', 'api-gateway', 'auth-service', 'product-service', 'cart-service', 'inventory-service', 'order-service', 'payment-service', 'delivery-service', 'notification-service', 'operations-service')
OUTBOX_SERVICES = {'inventory-service', 'order-service', 'payment-service', 'delivery-service', 'notification-service'}


def target(env=None):
    env = os.environ if env is None else env
    if env.get('ORDERFLOW_CLOUD_E2E') != 'true':
        raise SafetyError('ORDERFLOW_CLOUD_E2E=true is required before network access')
    if env.get('ORDERFLOW_TARGET_ENVIRONMENT') not in ('cloud-demo', 'staging'):
        raise SafetyError('Only explicitly selected cloud-demo or staging targets are permitted')
    account = env.get('AWS_ACCOUNT_ID', '')
    if not re.fullmatch(r'[0-9]{12}', account):
        raise SafetyError('AWS_ACCOUNT_ID must contain twelve digits')
    raw = env.get('ORDERFLOW_PUBLIC_URL', '')
    try:
        parsed = urllib.parse.urlsplit(raw)
        port = parsed.port
    except ValueError:
        raise SafetyError('Invalid HTTPS origin') from None
    if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.password or parsed.query or parsed.fragment or parsed.path not in ('', '/'):
        raise SafetyError('ORDERFLOW_PUBLIC_URL must be a plain HTTPS origin without credentials, path, query or fragment')
    local = parsed.hostname in ('localhost', '127.0.0.1')
    local_flag = env.get('ORDERFLOW_LOCAL_TLS_TEST') == 'true'
    ca = env.get('ORDERFLOW_CA_FILE', '')
    if local != local_flag or (local and not ca) or (not local and ca):
        raise SafetyError('Loopback TLS requires separate local-test opt-in and an explicit CA file; public TLS cannot use that override')
    if not local and not re.fullmatch(r'(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z][a-z0-9-]{1,62}', parsed.hostname):
        raise SafetyError('Public TLS requires a DNS hostname; alternate numeric/IPv6 loopback forms are rejected')
    if parsed.hostname.startswith('127.') and parsed.hostname != '127.0.0.1':
        raise SafetyError('Only exact localhost/127.0.0.1 may be local TLS targets')
    if not local and port not in (None, 443):
        raise SafetyError('Public HTTPS checks require the normal 443 endpoint')
    try:
        context = ssl.create_default_context(cafile=ca or None)
    except (OSError, ssl.SSLError):
        raise SafetyError('The explicitly selected local CA certificate is invalid or unavailable') from None
    return {'url': raw.rstrip('/'), 'context': context, 'local': local, 'account': account, 'environment': env['ORDERFLOW_TARGET_ENVIRONMENT']}


def protected_json(path):
    if not path:
        raise SafetyError('A protected account file is required')
    try:
        fd = os.open(path, os.O_RDONLY | os.O_NONBLOCK | getattr(os, 'O_NOFOLLOW', 0))
        with os.fdopen(fd, 'r', encoding='utf-8') as handle:
            info = os.fstat(handle.fileno())
            if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.geteuid() or info.st_size > 65536:
                raise SafetyError('Account file must be a regular, current-user owned file with mode0600 and bounded size')
            return json.load(handle)
    except (OSError, ValueError):
        raise SafetyError('Protected account file cannot be loaded') from None


def accounts(path):
    value = protected_json(path)
    if not isinstance(value, dict) or set(value) != set(ROLES):
        raise SafetyError('Account file must contain exactly VENDOR, DELIVERY_AGENT and ADMIN')
    emails = set()
    for role in ROLES:
        item = value[role]
        if not isinstance(item, dict) or set(item) != {'email', 'password'}:
            raise SafetyError('Each role must contain only email and password')
        email, password = item['email'], item['password']
        if not isinstance(email, str) or not re.fullmatch(r'[a-z0-9][a-z0-9._+-]{0,120}@example\.test', email) or email in emails:
            raise SafetyError('Only distinct fake example.test accounts are permitted')
        if not isinstance(password, str) or not 16 <= len(password) <= 128:
            raise SafetyError('Fixture passwords require16–128 characters')
        emails.add(email)
    return value


def identity(value):
    try:
        result = str(uuid.UUID(str(value)))
    except (ValueError, AttributeError):
        raise VerificationError('Invalid server UUID') from None
    if result != value:
        raise VerificationError('Server identity must be a canonical UUID')
    return result


def private_metrics_url(raw):
    try:
        p = urllib.parse.urlsplit(raw)
        _ = p.port
    except ValueError:
        raise SafetyError('Invalid private Prometheus URL') from None
    private = p.hostname in ('localhost', '127.0.0.1')
    try:
        address = ipaddress.ip_address(p.hostname or '')
        private = private or any(address in ipaddress.ip_network(cidr) for cidr in ('10.0.0.0/8', '172.16.0.0/12', '192.168.0.0/16'))
    except ValueError:
        pass
    if p.scheme not in ('http', 'https') or not private or p.username or p.password or p.path not in ('', '/') or p.query or p.fragment:
        raise SafetyError('Prometheus must use an operator loopback or literal RFC1918 origin through private host access')
    return raw.rstrip('/')


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise VerificationError('Unexpected redirect; refusing to forward fixture credentials')


class Client:
    def __init__(self, configuration):
        self.url = configuration['url']
        self.jar = http.cookiejar.CookieJar()
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPSHandler(context=configuration['context']), urllib.request.HTTPCookieProcessor(self.jar), NoRedirect())
        self.token = None
        self.user = None
        self.csrf = None
        self.csrf_header = 'X-XSRF-TOKEN'
        self.last_headers = None

    def request(self, path, method='GET', body=None, expected=(200,), csrf=True, auth=True, headers=None, raw=False, timeout=15):
        if not path.startswith('/') or path.startswith('//') or '\\' in path or '\r' in path or '\n' in path:
            raise SafetyError('API paths must remain on the approved origin')
        outgoing = {'Accept': 'application/json', 'User-Agent': 'OrderFlow-Cloud-Verification/1', 'Origin': self.url}
        if auth and self.token:
            outgoing['Authorization'] = 'Bearer ' + self.token
        if method not in ('GET', 'HEAD') and csrf and self.csrf:
            outgoing[self.csrf_header] = self.csrf
        outgoing.update(headers or {})
        data = None if body is None else json.dumps(body).encode()
        if data is not None:
            outgoing['Content-Type'] = 'application/json'
        request = urllib.request.Request(self.url + path, data=data, method=method, headers=outgoing)
        try:
            response = self.opener.open(request, timeout=timeout)
        except urllib.error.HTTPError as error:
            response = error
        except (urllib.error.URLError, TimeoutError, OSError):
            raise VerificationError('HTTPS request failed; check reachability/TLS privately') from None
        self.last_headers = response.headers
        if response.status not in expected:
            response.close()
            raise VerificationError('Unexpected HTTP status '+str(response.status)+' during '+method+' '+path.split('?')[0])
        if raw:
            return response
        with response:
            data = response.read(1048577)
        if len(data) > 1048576:
            raise VerificationError('API response exceeded oneMiB bound')
        if not data:
            return None
        try:
            return json.loads(data)
        except ValueError:
            raise VerificationError('Expected JSON response') from None

    def csrf_token(self):
        body = self.request('/api/auth/csrf', auth=False)
        if not isinstance(body, dict) or not isinstance(body.get('token'), str) or body.get('headerName') != 'X-XSRF-TOKEN':
            raise VerificationError('Invalid CSRF contract')
        self.csrf, self.csrf_header = body['token'], body['headerName']
        return self.csrf

    def authenticated(self, body, expected_role=None):
        if not isinstance(body, dict) or body.get('tokenType') != 'Bearer' or not isinstance(body.get('accessToken'), str):
            raise VerificationError('Invalid authentication response')
        self.user, self.token = body['user'], body['accessToken']
        identity(self.user['id'])
        if expected_role and expected_role not in self.user.get('roles', []):
            raise VerificationError('Operator role has not been provisioned')
        self.verify_refresh_cookie()
        self.csrf_token()
        return self.user

    def verify_refresh_cookie(self):
        refresh = [c for c in self.jar if c.name == 'ORDERFLOW_REFRESH']
        if len(refresh) != 1 or not refresh[0].secure:
            raise VerificationError('Secure refresh cookie missing')
        rest = {k.lower(): v for k, v in refresh[0]._rest.items()}
        if 'httponly' not in rest or str(rest.get('samesite', '')).lower() != 'strict':
            raise VerificationError('Refresh cookie must remain HttpOnly and SameSiteStrict')
        xsrf = [c for c in self.jar if c.name == 'XSRF-TOKEN']
        if not xsrf or any(not c.secure for c in xsrf):
            raise VerificationError('Secure CSRF cookie missing')

    def login(self, fixture, role):
        self.csrf_token()
        body = self.request('/api/auth/login', 'POST', fixture, auth=False)
        self.authenticated(body, role)
        if self.user.get('email') != fixture['email']:
            raise VerificationError('Authenticated fixture email does not match the operator account')
        return self.user

    def register(self, email, password, display_name):
        self.csrf_token()
        body = self.request('/api/auth/register', 'POST', {'email': email, 'password': password, 'displayName': display_name}, expected=(201,), auth=False)
        return self.authenticated(body, 'CUSTOMER')


def poll(label, operation, timeout=90, interval=2):
    if not 0 < timeout <= 120 or not .1 <= interval <= 5:
        raise SafetyError('Polling bounds invalid')
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        result = operation()
        if result:
            return result
        time.sleep(min(interval, max(0, deadline-time.monotonic())))
    raise VerificationError(label+' did not reach its expected state before the bounded deadline')


def performance_limits(env=None):
    env = os.environ if env is None else env
    try:
        count, workers = int(env.get('ORDERFLOW_PERFORMANCE_REQUESTS', '12')), int(env.get('ORDERFLOW_PERFORMANCE_CONCURRENCY', '2'))
    except ValueError:
        raise SafetyError('Performance bounds must be integer values') from None
    if not 1 <= count <= 30 or not 1 <= workers <= 2:
        raise SafetyError('Performance smoke permits at most30 requests and concurrency2')
    return count, workers


def metric_map(rows, label='instance'):
    result = {}
    for row in rows:
        name = row.get('metric', {}).get(label, '').split(':')[0]
        if name not in SERVICES or name in result:
            raise VerificationError('Unexpected or duplicate private metric service')
        try:
            value = float(row['value'][1])
        except (KeyError, ValueError, TypeError):
            raise VerificationError('Invalid metric response') from None
        if not math.isfinite(value) or value < 0:
            raise VerificationError('Nonfinite or negative reliability metric')
        result[name] = value
    return result


class Metrics:
    def __init__(self, url):
        self.url = private_metrics_url(url)
        self.opener = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())
    def query(self, expression):
        try:
            with self.opener.open(self.url+'/api/v1/query?'+urllib.parse.urlencode({'query': expression}), timeout=10) as response:
                data = response.read(1048577)
            if len(data)>1048576:
                raise VerificationError('Private metrics response exceeded bound')
            body = json.loads(data)
            if body.get('status') != 'success' or body['data']['resultType'] != 'vector':
                raise VerificationError('Private metrics query failed')
            return body['data']['result']
        except (urllib.error.URLError, OSError, ValueError, KeyError):
            raise VerificationError('Required private Prometheus query unavailable') from None
    def snapshot(self):
        up = metric_map(self.query('up{job="orderflow"}'))
        if set(up) != set(SERVICES) or any(v != 1 for v in up.values()):
            raise VerificationError('All eleven application scrapes must be up')
        scrapes = metric_map(self.query('timestamp(up{job="orderflow"})'))
        if set(scrapes) != set(SERVICES) or any(not -5 <= time.time()-stamp <= 45 for stamp in scrapes.values()):
            raise VerificationError('Private application scrape evidence is missing or stale')
        value = {'scrapes': scrapes, 'starts': metric_map(self.query('process_start_time_seconds{job="orderflow"}')),
                 'backlog': metric_map(self.query('orderflow_outbox_backlog{job="orderflow"}'), 'service'),
                 'failed': metric_map(self.query('orderflow_outbox_failed{job="orderflow"}'), 'service'),
                 'dlt': metric_map(self.query('sum by(service) (orderflow_kafka_dlt_publications_total{job="orderflow"})'), 'service')}
        validate_metric_snapshot(value)
        return value


def validate_metric_snapshot(value):
    if set(value['starts']) != set(SERVICES) or set(value['backlog']) != OUTBOX_SERVICES or set(value['failed']) != OUTBOX_SERVICES:
        raise VerificationError('Required process/outbox metrics are missing')
    if any(x != 0 for x in value['failed'].values()) or any(x != 0 for x in value['dlt'].values()):
        raise VerificationError('Failed outbox records or DLT publications exist')
    return value


def compare_metrics(before, after):
    validate_metric_snapshot(before); validate_metric_snapshot(after)
    if before['starts'] != after['starts']:
        raise VerificationError('A service restarted during verification; counter evidence is invalid')
    for service in set(before['dlt']) | set(after['dlt']):
        if before['dlt'].get(service, 0) != after['dlt'].get(service, 0):
            raise VerificationError('DLT publications changed during verification')


def write_report(path, value):
    # Reports contain only synthetic fixture identifiers, aggregate results and
    # timing; never authentication bodies, accounts, cookies or secret values.
    destination = Path(path)
    destination.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, 'w') as handle:
        json.dump(value, handle, indent=2)

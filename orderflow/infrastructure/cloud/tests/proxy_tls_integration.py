#!/usr/bin/env python3
"""Exercise production Caddy handlers over real HTTPS, HTTP/2 and local ACME.

Requires Docker Desktop (host.docker.internal), OpenSSL and HTTP/2-enabled curl.
Use --output NEW_EMPTY_DIRECTORY outside Git: fixture keys, configurations, raw
logs, image/container inspection and result.json remain there even on failure.
Only uniquely named, labelled fixture containers/network are removed. All Caddy
/data and /config paths are owned bind mounts; no Docker volume is removed.
This is isolated synthetic acceptance, not full-stack or AWS acceptance.
"""

import argparse
import copy
import hashlib
import http.server
import json
import os
from pathlib import Path
import re
import selectors
import shutil
import subprocess
import threading
import time
import traceback
import uuid


ROOT = Path(__file__).resolve().parents[3]
DOMAIN = "orderflow.example.invalid"
ACME_DIRECTORY = "https://pebble:14000/dir"
LABEL = "com.orderflow.proxy-tls-fixture"


def fixture_config(source, upstream_port, trust_acme):
    """Adapt only fixture addresses, output paths, and the local ACME issuer.

    Leave all listeners, HTTP protocols, routes, limits, forwarding, streaming,
    access-log filtering and the production FATAL default logger intact. The
    diagnostic logger is limited to certificate/ACME namespaces, not HTTP errors.
    Negative and positive configurations differ only in the ACME trust anchor.
    """
    config = copy.deepcopy(source)

    def upstreams(value):
        if isinstance(value, dict):
            for key, child in value.items():
                if key == "dial" and child in ("api-gateway-edge:8080", "frontend:3000"):
                    value[key] = f"host.docker.internal:{upstream_port}"
                else:
                    upstreams(child)
        elif isinstance(value, list):
            for child in value:
                upstreams(child)

    upstreams(config["apps"]["http"])
    logs = config["logging"]["logs"]
    for logger in logs.values():
        writer = logger.get("writer", {})
        if writer.get("filename") == "/var/log/caddy/access.json":
            writer["filename"] = "/logs/access.json"
    logs["fixture_acme_diagnostic"] = {
        "level": "DEBUG",
        "include": ["tls", "http.acme_client", "http.auto_https"],
        "writer": {"output": "file", "filename": "/logs/acme-diagnostic.json"},
        "encoder": {"format": "json"},
    }
    issuer = {
        "module": "acme",
        "ca": ACME_DIRECTORY,
        # Keep certmagic retries inside the fixture as well.
        "test_ca": ACME_DIRECTORY,
        "email": "operator@example.invalid",
        "challenges": {"http": {"disabled": False}, "tls-alpn": {"disabled": True}},
    }
    if trust_acme:
        issuer["trusted_roots_pem_files"] = ["/fixture/testca.crt"]
    tls = config["apps"].setdefault("tls", {})
    tls.setdefault("automation", {})["policies"] = [
        {"subjects": [DOMAIN], "issuers": [issuer]}
    ]
    return config


class Echo(http.server.BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"

    def do_GET(self):
        if self.path.startswith("/api/notifications/stream"):
            self.send_response(200)
            self.send_header("Content-Type", "text/event-stream")
            self.send_header("Connection", "close")
            self.end_headers()
            self.close_connection = True
            try:
                self.wfile.write(b"data: first\n\n")
                self.wfile.flush()
                time.sleep(2)
                self.wfile.write(b"data: second\n\n")
                self.wfile.flush()
            except (BrokenPipeError, ConnectionResetError):
                pass
            return
        body = json.dumps({"path": self.path, "headers": dict(self.headers)}).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(body)))
        if self.path.startswith("/api/test-cookie"):
            self.send_header("Set-Cookie", "session=RESPONSE_COOKIE_SECRET; Path=/; HttpOnly; Secure; SameSite=Lax")
        self.end_headers()
        try:
            self.wfile.write(body)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_POST(self):
        self.connection.settimeout(10)
        try:
            self.rfile.read(int(self.headers.get("Content-Length", "0")))
            self.do_GET()
        except (OSError, ValueError):
            self.close_connection = True

    def log_message(self, *_args):
        pass


class Harness:
    def __init__(self, args, output):
        self.args = args
        self.output = output
        self.commands = output / "commands"
        self.commands.mkdir(mode=0o700)
        self.fixture = output / "fixture"
        self.fixture.mkdir(mode=0o700)
        self.token = uuid.uuid4().hex[:12]
        self.prefix = "orderflow-proxy-tls-" + self.token
        self.network = self.prefix + "-net"
        self.network_created = False
        self.containers = []
        self.server = None
        self.sequence = 0
        self.result = {
            "status": "RUNNING", "image": args.image, "pebble_image": args.pebble_image,
            "fixture_id": self.token, "checks": {}, "cleanup_errors": [],
            "scope": "isolated synthetic local HTTPS/HTTP2/ACME; full-stack/AWS NOT RUN",
            "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime()),
        }
        self.write_result()

    def write_result(self):
        temporary = self.output / "result.json.tmp"
        temporary.write_text(json.dumps(self.result, indent=2) + "\n")
        temporary.replace(self.output / "result.json")

    def check(self, name, condition, details=None):
        self.result["checks"][name] = {"passed": bool(condition), "details": details}
        self.write_result()
        if not condition:
            raise AssertionError(name + (": " + str(details) if details else ""))

    def run(self, name, argv, *, timeout=45, check=True, input=None):
        self.sequence += 1
        stem = self.commands / f"{self.sequence:03d}-{name}"
        started = time.monotonic()
        entry = {"name": name, "argv": [str(arg) for arg in argv]}
        try:
            result = subprocess.run(entry["argv"], input=input, capture_output=True,
                                    text=True, timeout=timeout)
            stem.with_suffix(".stdout").write_text(result.stdout)
            stem.with_suffix(".stderr").write_text(result.stderr)
            entry["returncode"] = result.returncode
            entry["elapsed_seconds"] = round(time.monotonic() - started, 3)
            stem.with_suffix(".json").write_text(json.dumps(entry, indent=2) + "\n")
        except Exception as error:
            entry["error"] = repr(error)
            stem.with_suffix(".json").write_text(json.dumps(entry, indent=2) + "\n")
            raise
        if check and result.returncode:
            raise RuntimeError(f"{name} exited {result.returncode}; see {stem}.stderr")
        return result

    def json_file(self, path, value):
        path.write_text(json.dumps(value, indent=2) + "\n")

    def mounted_state(self, directory):
        for child in ("data", "config", "logs"):
            (directory / child).mkdir(mode=0o700, parents=True)
        return ["-v", f"{directory / 'data'}:/data", "-v", f"{directory / 'config'}:/config"]

    def container_inspect(self, name):
        result = self.run("inspect-" + name, ["docker", "inspect", name], check=False)
        if result.returncode:
            return None
        data = json.loads(result.stdout)[0]
        self.json_file(self.output / (name + ".inspect.json"), data)
        return data

    def remove_container(self, name):
        info = self.container_inspect(name)
        if info is None:
            return
        if info.get("Config", {}).get("Labels", {}).get(LABEL) != self.token:
            raise RuntimeError("Refusing cleanup of a container without our ownership label: " + name)
        logs = self.run("logs-" + name, ["docker", "logs", "--timestamps", name], check=False)
        (self.output / (name + ".docker.log")).write_text(logs.stdout + logs.stderr)
        self.run("remove-" + name, ["docker", "rm", "-f", name])
        self.containers.remove(name)

    def start_caddy(self, name, directory):
        self.containers.append(name)
        self.run("start-" + name, [
            "docker", "run", "-d", "--name", name, "--label", LABEL + "=" + self.token,
            "--network", self.network, "--network-alias", DOMAIN, "--memory", "192m",
            "--cap-drop", "ALL", "--security-opt", "no-new-privileges:true",
            "--add-host", "host.docker.internal:host-gateway", "-p", "127.0.0.1::443",
            *self.mounted_state(directory), "-v", f"{self.fixture}:/fixture:ro",
            "-v", f"{directory / 'logs'}:/logs", "-v", f"{directory / 'caddy.json'}:/etc/caddy/test.json:ro",
            self.args.image, "caddy", "run", "--config", "/etc/caddy/test.json",
        ])
        info = self.container_inspect(name)
        mounts = {mount["Destination"]: mount for mount in info["Mounts"]}
        self.check(name + "_owned_state_binds", all(
            mounts.get(destination, {}).get("Type") == "bind" and
            Path(mounts[destination]["Source"]).resolve() == (directory / destination[1:]).resolve()
            for destination in ("/data", "/config")
        ))
        return int(info["NetworkSettings"]["Ports"]["443/tcp"][0]["HostPort"])

    def curl(self, name, port, ca, path="/", *, hostname=DOMAIN, extra=(), check=True):
        stem = self.output / name
        argv = [
            "curl", "--silent", "--show-error", "--noproxy", "*", "--http2",
            "--connect-timeout", "3", "--max-time", "15",
            "--resolve", f"{hostname}:{port}:127.0.0.1", "--cacert", str(ca),
            "--dump-header", str(stem.with_suffix(".headers")),
            "--output", str(stem.with_suffix(".body")), "--write-out",
            '{"http_code":"%{http_code}","http_version":"%{http_version}","ssl_verify_result":%{ssl_verify_result},"time_starttransfer":%{time_starttransfer}}\n',
            *extra, f"https://{hostname}:{port}{path}",
        ]
        result = self.run(name, argv, timeout=20, check=check)
        metadata = json.loads(result.stdout) if result.stdout.strip() else {}
        if "http_code" in metadata:
            metadata["http_code"] = int(metadata["http_code"])
        metadata["curl_exit"] = result.returncode
        self.json_file(stem.with_suffix(".json"), metadata)
        return metadata, stem.with_suffix(".body"), stem.with_suffix(".headers")

    def generate_fixture(self):
        ca = self.fixture / "testca.crt"
        key = self.fixture / "testca.key"
        self.run("generate-acme-endpoint-ca", [
            "openssl", "req", "-x509", "-newkey", "rsa:2048", "-nodes", "-sha256", "-days", "2",
            "-keyout", str(key), "-out", str(ca), "-subj", "/CN=OrderFlow Local ACME Endpoint Fixture",
            "-addext", "basicConstraints=critical,CA:TRUE", "-addext", "keyUsage=critical,keyCertSign,cRLSign",
        ])
        self.run("generate-acme-endpoint-csr", [
            "openssl", "req", "-new", "-newkey", "rsa:2048", "-nodes", "-sha256",
            "-keyout", str(self.fixture / "pebble.key"), "-out", str(self.fixture / "pebble.csr"),
            "-subj", "/CN=pebble",
        ])
        extensions = self.fixture / "pebble.ext"
        extensions.write_text("basicConstraints=critical,CA:FALSE\nkeyUsage=critical,digitalSignature,keyEncipherment\nextendedKeyUsage=serverAuth\nsubjectAltName=DNS:pebble\n")
        self.run("sign-acme-endpoint", [
            "openssl", "x509", "-req", "-in", str(self.fixture / "pebble.csr"),
            "-CA", str(ca), "-CAkey", str(key), "-CAcreateserial", "-days", "2", "-sha256",
            "-extfile", str(extensions), "-out", str(self.fixture / "pebble.crt"),
        ])
        for private_key in self.fixture.glob("*.key"):
            private_key.chmod(0o600)
        self.json_file(self.fixture / "pebble-config.json", {"pebble": {
            "listenAddress": "0.0.0.0:14000", "managementListenAddress": "0.0.0.0:15000",
            "certificate": "/fixture/pebble.crt", "privateKey": "/fixture/pebble.key",
            "httpPort": 80, "tlsPort": 443,
        }})
        return ca

    def test_sse(self, port, root):
        argv = ["curl", "--silent", "--show-error", "--no-buffer", "--noproxy", "*", "--http2",
                "--connect-timeout", "3", "--max-time", "12", "--cacert", str(root),
                "--resolve", f"{DOMAIN}:{port}:127.0.0.1", "--dump-header", str(self.output / "sse.headers"),
                f"https://{DOMAIN}:{port}/api/notifications/stream"]
        started = time.monotonic()
        received = bytearray()
        first = second = None
        with (self.output / "sse.stderr").open("wb") as errors:
            process = subprocess.Popen(argv, stdout=subprocess.PIPE, stderr=errors)
            try:
                with selectors.DefaultSelector() as selector:
                    selector.register(process.stdout, selectors.EVENT_READ)
                    while time.monotonic() - started < 13:
                        if not selector.select(timeout=.1):
                            if process.poll() is not None:
                                break
                            continue
                        chunk = os.read(process.stdout.fileno(), 4096)
                        if not chunk:
                            break
                        received.extend(chunk)
                        elapsed = time.monotonic() - started
                        if b"data: first\n\n" in received and first is None:
                            first = elapsed
                        if b"data: second\n\n" in received and second is None:
                            second = elapsed
                code = process.wait(timeout=3)
            finally:
                if process.poll() is None:
                    process.kill()
                    process.wait(timeout=3)
                process.stdout.close()
                (self.output / "sse.body").write_bytes(received)
        details = {"argv": argv, "curl_exit": code, "first_seconds": first, "second_seconds": second}
        self.json_file(self.output / "sse.json", details)
        self.check("https_sse_streaming", code == 0 and first is not None and second is not None
                   and first < 1.5 and second - first >= 1.5, details)

    def execute(self):
        curl_version = self.run("curl-version", ["curl", "--version"]).stdout
        self.check("client_http2_support", "HTTP2" in curl_version)
        self.run("openssl-version", ["openssl", "version"])
        self.run("docker-version", ["docker", "version"])
        for name, image in (("caddy", self.args.image), ("pebble", self.args.pebble_image)):
            details = self.run(name + "-image-inspect", ["docker", "image", "inspect", image])
            self.json_file(self.output / (name + "-image.inspect.json"), json.loads(details.stdout))
        endpoint_ca = self.generate_fixture()
        caddyfile = ROOT / "infrastructure/caddy/Caddyfile"
        shutil.copyfile(caddyfile, self.output / "production.Caddyfile")
        self.result["production_caddyfile_sha256"] = hashlib.sha256(caddyfile.read_bytes()).hexdigest()
        adapt = self.output / "adapt"
        adapt.mkdir(mode=0o700)
        result = self.run("adapt-production", [
            "docker", "run", "--rm", "--name", self.prefix + "-adapt",
            "--label", LABEL + "=" + self.token, "--network", "none", *self.mounted_state(adapt),
            "-e", "DOMAIN_NAME=" + DOMAIN, "-e", "ACME_EMAIL=operator@example.invalid",
            "-v", f"{caddyfile}:/etc/caddy/Caddyfile:ro", self.args.image,
            "caddy", "adapt", "--config", "/etc/caddy/Caddyfile",
        ])
        source = json.loads(result.stdout)
        self.json_file(self.output / "production.adapted.json", source)
        handlers = set()

        def collect_handlers(value):
            if isinstance(value, dict):
                if "handler" in value:
                    handlers.add(value["handler"])
                for child in value.values():
                    collect_handlers(child)
            elif isinstance(value, list):
                for child in value:
                    collect_handlers(child)

        collect_handlers(source)
        # The unfixed transitive go-jose/v3 parser is linked through Smallstep.
        # Its inbound ACME/JWK entry points must remain absent from this proxy.
        self.check("production_untrusted_jwk_entrypoints_absent",
                   source.get("admin", {}).get("disabled") is True
                   and "pki" not in source.get("apps", {})
                   and handlers <= {"subroute", "headers", "reverse_proxy", "static_response", "request_body"},
                   {"active_handlers": sorted(handlers), "admin_disabled": source.get("admin", {}).get("disabled"),
                    "configured_apps": sorted(source.get("apps", {})),
                    "limit": "Exact reviewed proxy configuration only; enabling PKI/ACME-server/JWT modules requires a new assessment"})
        public = next(server for server in source["apps"]["http"]["servers"].values()
                      if ":443" in server.get("listen", []))
        self.check("production_h1_h2", public.get("protocols") == ["h1", "h2"])
        self.server = http.server.ThreadingHTTPServer(("0.0.0.0", 0), Echo)
        self.server.daemon_threads = True
        threading.Thread(target=self.server.serve_forever, daemon=True).start()
        self.run("create-private-network", ["docker", "network", "create", "--driver", "bridge",
                 "--label", LABEL + "=" + self.token, self.network])
        self.network_created = True
        pebble = self.prefix + "-pebble"
        self.containers.append(pebble)
        self.run("start-pebble", [
            "docker", "run", "-d", "--name", pebble, "--label", LABEL + "=" + self.token,
            "--network", self.network, "--network-alias", "pebble", "--memory", "192m", "--user", "0:0",
            "-e", "PEBBLE_VA_NOSLEEP=1", "-p", "127.0.0.1::15000",
            "-v", f"{self.fixture}:/fixture:ro", self.args.pebble_image,
            "-config", "/fixture/pebble-config.json",
        ])
        info = self.container_inspect(pebble)
        self.check("pebble_validation_not_bypassed", not any(
            entry.startswith(("PEBBLE_VA_ALWAYS_VALID=", "PEBBLE_VA_SKIP="))
            and entry.split("=", 1)[1] not in ("", "0", "false")
            for entry in info["Config"]["Env"]
        ))
        management_port = int(info["NetworkSettings"]["Ports"]["15000/tcp"][0]["HostPort"])
        issuance_root = self.output / "issuance-root.pem"
        for attempt in range(40):
            metadata, body, _headers = self.curl(f"pebble-root-{attempt:02d}", management_port,
                                                endpoint_ca, "/roots/0", hostname="pebble", check=False)
            if metadata.get("curl_exit") == 0 and metadata.get("http_code") == 200:
                issuance_root.write_bytes(body.read_bytes())
                break
            time.sleep(.25)
        else:
            raise AssertionError("Pebble management endpoint did not become healthy with verified TLS")
        self.run("validate-issuance-root", ["openssl", "x509", "-in", str(issuance_root), "-noout", "-text"])
        negative = self.output / "negative"
        positive = self.output / "positive"
        for directory, trusted in ((negative, False), (positive, True)):
            directory.mkdir(mode=0o700)
            self.json_file(directory / "caddy.json", fixture_config(source, self.server.server_port, trusted))
        negative_name = self.prefix + "-untrusted-acme"
        negative_port = self.start_caddy(negative_name, negative)
        diagnostics = negative / "logs/acme-diagnostic.json"
        for _attempt in range(100):
            text = diagnostics.read_text() if diagnostics.exists() else ""
            if "certificate signed by unknown authority" in text:
                break
            time.sleep(.2)
        else:
            raise AssertionError("Untrusted ACME endpoint did not emit an actual unknown-authority failure")
        self.check("acme_endpoint_unknown_authority_rejected", True,
                   "Actual Caddy ACME error retained in negative/logs/acme-diagnostic.json")
        rejected, _, _ = self.curl("untrusted-acme-frontend", negative_port, endpoint_ca, check=False)
        certificate_paths = [str(path.relative_to(negative)) for path in (negative / "data").rglob("*.crt")]
        self.check("untrusted_acme_no_certificate_issued", not certificate_paths and rejected["curl_exit"] != 0,
                   {"certificate_paths": certificate_paths, "frontend": rejected})
        self.remove_container(negative_name)
        positive_name = self.prefix + "-trusted-acme"
        port = self.start_caddy(positive_name, positive)
        for attempt in range(100):
            metadata, _body, _headers = self.curl(f"https-ready-{attempt:02d}", port, issuance_root, check=False)
            if metadata.get("curl_exit") == 0 and metadata.get("http_code") == 200:
                break
            time.sleep(.3)
        else:
            raise AssertionError("Caddy did not obtain and serve a trusted local ACME certificate")
        self.check("https_certificate_and_http2", metadata["http_version"] == "2"
                   and metadata["ssl_verify_result"] == 0, metadata)
        diagnostics = positive / "logs/acme-diagnostic.json"
        diagnostic_text = diagnostics.read_text()
        self.check("actual_acme_http01_issuance", bool(re.search(r'"challenge_type"\s*:\s*"http-01"', diagnostic_text))
                   and "certificate obtained successfully" in diagnostic_text,
                   "HTTP-01 is enabled, TLS-ALPN disabled; raw ACME diagnostic retained")
        wrong_root, _, _ = self.curl("wrong-root-rejected", port, endpoint_ca, check=False)
        self.check("frontend_wrong_root_rejected", wrong_root["curl_exit"] == 60, wrong_root)
        session = self.run("verify-live-chain-alpn", [
            "openssl", "s_client", "-connect", f"127.0.0.1:{port}", "-servername", DOMAIN,
            "-alpn", "h2", "-showcerts", "-verify_return_error", "-verify_hostname", DOMAIN,
            "-CAfile", str(issuance_root),
        ], input="", timeout=15)
        self.check("openssl_live_hostname_chain_and_h2", "ALPN protocol: h2" in session.stdout
                   and "Verify return code: 0 (ok)" in session.stdout)
        chain = "\n".join(re.findall(r"-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----", session.stdout, re.S)) + "\n"
        (self.output / "served-chain.pem").write_text(chain)
        for name, path in (("endpoint-ca", endpoint_ca), ("endpoint-leaf", self.fixture / "pebble.crt"),
                           ("issuance-root", issuance_root), ("served-leaf", self.output / "served-chain.pem")):
            self.run("fingerprint-" + name, ["openssl", "x509", "-in", str(path), "-noout", "-sha256",
                     "-fingerprint", "-subject", "-issuer", "-serial", "-dates", "-ext", "subjectAltName"])
        for index, path in enumerate(("/products", "/api/products", "/oauth2/authorization/google",
                                      "/login/oauth2/code/google", "/oauth2/success", "/oauth2/failure")):
            response, body, headers = self.curl(f"route-{index}", port, issuance_root, path)
            self.check("https_route_" + path, response["http_code"] == 200 and response["http_version"] == "2"
                       and json.loads(body.read_text())["path"] == path)
            if index == 0:
                header_text = headers.read_text().lower()
                self.check("https_security_headers", "strict-transport-security: max-age=31536000" in header_text
                           and "x-content-type-options: nosniff" in header_text
                           and "referrer-policy: strict-origin-when-cross-origin" in header_text
                           and not re.search(r"(?m)^server:", header_text))
        for index, path in enumerate(("/actuator/health", "/api/auth/actuator/prometheus", "/internal/test",
                                      "/api/payments/internal/test", "/eureka/")):
            response, _body, _headers = self.curl(f"internal-{index}", port, issuance_root, path)
            self.check("https_internal_block_" + path, response["http_code"] == 404)
        spoofed = ["Forwarded: for=203.0.113.5;proto=http", "X-Forwarded-For: 203.0.113.5, 1.1.1.1",
                   "X-Forwarded-Proto: http", "X-Forwarded-Host: evil.invalid", "X-Forwarded-Evil: injected",
                   "X-Real-IP: 203.0.113.5", "True-Client-IP: 203.0.113.5", "CF-Connecting-IP: 203.0.113.5",
                   "X-Correlation-ID: fixture-correlation", "Authorization: Bearer HEADER_SECRET", "Cookie: secret=COOKIE_SECRET"]
        extra = [item for header in spoofed for item in ("--header", header)]
        response, body, headers = self.curl("auth-forwarding-cookie", port, issuance_root,
                                           "/api/test-cookie?code=QUERY_SECRET", extra=extra)
        upstream = {key.lower(): value for key, value in json.loads(body.read_text())["headers"].items()}
        self.check("https_auth_cookie_correlation", response["http_code"] == 200
                   and upstream.get("authorization") == "Bearer HEADER_SECRET"
                   and upstream.get("cookie") == "secret=COOKIE_SECRET"
                   and upstream.get("x-correlation-id") == "fixture-correlation"
                   and "session=RESPONSE_COOKIE_SECRET; Path=/; HttpOnly; Secure; SameSite=Lax" in headers.read_text())
        self.check("https_forwarded_header_spoof_rejected", all(key not in upstream for key in
                   ("forwarded", "x-real-ip", "true-client-ip", "cf-connecting-ip", "x-forwarded-evil"))
                   and bool(upstream.get("x-forwarded-for")) and "," not in upstream["x-forwarded-for"]
                   and upstream["x-forwarded-for"] != "203.0.113.5"
                   and upstream.get("x-forwarded-proto") == "https"
                   and upstream.get("x-forwarded-host") == DOMAIN and upstream.get("host") == DOMAIN)
        self.test_sse(port, issuance_root)
        oversized = self.output / "oversized-body.bin"
        oversized.write_bytes(b"x" * (2 * 1024 * 1024 + 1))
        response, _body, _headers = self.curl("body-limit", port, issuance_root, "/api/products",
                                             extra=["--header", "Expect:", "--data-binary", "@" + str(oversized)])
        self.check("https_body_limit_413", response["http_code"] == 413, response)
        time.sleep(.3)
        access_paths = sorted((positive / "logs").glob("access*.json"))
        access_text = "".join(path.read_text() for path in access_paths)
        records = [json.loads(line) for line in access_text.splitlines() if line.strip()]
        forbidden = ("QUERY_SECRET", "HEADER_SECRET", "COOKIE_SECRET", "RESPONSE_COOKIE_SECRET", "fixture-correlation")
        self.check("https_access_log_redaction", bool(records) and all(secret not in access_text for secret in forbidden)
                   and all("uri" not in record.get("request", {}) and "headers" not in record.get("request", {})
                           and "resp_headers" not in record for record in records)
                   and any(record.get("status") == 413 for record in records),
                   {"records": len(records), "paths": [str(path.relative_to(self.output)) for path in access_paths]})
        self.result["status"] = "PASS"

    def cleanup(self):
        for name in list(reversed(self.containers)):
            try:
                self.remove_container(name)
            except Exception as error:
                self.result["cleanup_errors"].append(str(error))
        if self.network_created:
            try:
                inspection = self.run("inspect-network", ["docker", "network", "inspect", self.network])
                info = json.loads(inspection.stdout)[0]
                self.json_file(self.output / "network.inspect.json", info)
                if info.get("Labels", {}).get(LABEL) != self.token:
                    raise RuntimeError("Refusing cleanup of a network without our ownership label")
                self.run("remove-network", ["docker", "network", "rm", self.network])
            except Exception as error:
                self.result["cleanup_errors"].append(str(error))
        if self.server:
            self.server.shutdown()
            self.server.server_close()
        for private_key in self.output.rglob("*.key"):
            private_key.chmod(0o600)
        if self.result["cleanup_errors"]:
            self.result["status"] = "FAIL"
        self.result["finished_utc"] = time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())
        self.write_result()


def output_directory(value):
    output = Path(value).expanduser().resolve()
    ancestor = output
    while not ancestor.exists():
        ancestor = ancestor.parent
    repository = subprocess.run(["git", "-C", str(ancestor), "rev-parse", "--show-toplevel"],
                                capture_output=True, text=True)
    if repository.returncode == 0:
        raise ValueError("--output must be outside Git, because retained fixture evidence includes private keys")
    if output.exists() and (not output.is_dir() or any(output.iterdir())):
        raise ValueError("--output must name a new or empty directory")
    output.mkdir(mode=0o700, parents=True, exist_ok=True)
    output.chmod(0o700)
    return output


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--image", required=True, help="Exact local Caddy image ID or digest")
    parser.add_argument("--pebble-image", required=True, help="Exact official Pebble image ID or digest")
    parser.add_argument("--output", required=True, help="New empty evidence directory outside Git")
    args = parser.parse_args()
    previous_umask = os.umask(0o077)
    harness = None
    try:
        harness = Harness(args, output_directory(args.output))
        try:
            harness.execute()
        except BaseException as error:
            harness.result["status"] = "FAIL"
            harness.result["error"] = str(error)
            (harness.output / "failure.txt").write_text(traceback.format_exc())
        finally:
            harness.cleanup()
        print(json.dumps(harness.result, indent=2))
        return 0 if harness.result["status"] == "PASS" else 1
    finally:
        os.umask(previous_umask)


if __name__ == "__main__":
    raise SystemExit(main())

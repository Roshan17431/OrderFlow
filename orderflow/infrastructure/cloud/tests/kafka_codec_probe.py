#!/usr/bin/env python3
"""Measure the real application Kafka clients on an ALREADY isolated test broker.

No broker/image builds, pulls, volume lifecycle, application configuration changes,
or real-topic access. Each app probe uses the image's original user, the cloud
640 MiB auth-service limit, read-only root, no-new-privileges and unchanged noexec
/tmp tmpfs. A separate broker CLI seeds reference records for failed native paths;
that seed is NOT counted as an application producer success.

Example (run sequentially after preparing an isolated broker/network):
  python3 kafka_codec_probe.py --image arm64=sha256:... --network test-network \
    --broker-container test-kafka --bootstrap kafka:29092 \
    --output /absolute/evidence/path --confirm-isolated-synthetic

Exit 2 preserves measured codec failures; COMPLETE in the JSON means measurement
finished, not that all codecs or infrastructure security passed. --native-only
needs no broker. --self-test exercises the persisted batch header/CRC verifier
without Docker. The caller owns the broker, network and retained synthetic data.
"""
from __future__ import annotations

import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import sys
import uuid


CODECS = {"none": 0, "gzip": 1, "snappy": 2, "lz4": 3, "zstd": 4}
MARKER = "ORDERFLOW_CODEC_RESULT="
HERE = Path(__file__).resolve().parent
JAVA = HERE / "KafkaCodecProbe.java"
REPO = HERE.parents[2]
JAVA_OPTIONS = ("-XX:InitialRAMPercentage=15.0 -XX:MaxRAMPercentage=50.0 "
                "-XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError")


def sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def crc32c(data: bytes) -> int:
    value = 0xFFFFFFFF
    for byte in data:
        value ^= byte
        for _ in range(8):
            value = (value >> 1) ^ (0x82F63B78 if value & 1 else 0)
    return value ^ 0xFFFFFFFF


def batch_headers(path: Path) -> list[dict]:
    """Independently verify Kafka magic-2 persisted bytes without loading JNI.

    Attributes select compression even when decompression cannot run. CRC32C
    verifies the entire stored attributes/payload region. Payload equality is
    independently checked by the actual application-image KafkaConsumer.
    """
    data = path.read_bytes()
    cursor, batches = 0, []
    while cursor < len(data):
        if len(data) - cursor < 61:
            raise ValueError(f"Truncated Kafka batch header at {cursor}")
        base_offset, length = struct.unpack_from(">qi", data, cursor)
        end = cursor + 12 + length
        if length < 49 or end > len(data):
            raise ValueError(f"Invalid/truncated Kafka batch length at {cursor}: {length}")
        if data[cursor + 16] != 2:
            raise ValueError(f"Expected magic 2 at {cursor}")
        expected_crc = struct.unpack_from(">I", data, cursor + 17)[0]
        actual_crc = crc32c(data[cursor + 21:end])
        if actual_crc != expected_crc:
            raise ValueError(f"Kafka batch CRC32C mismatch at {cursor}")
        attributes, last_delta = struct.unpack_from(">hi", data, cursor + 21)
        count = struct.unpack_from(">i", data, cursor + 57)[0]
        codec_id = attributes & 7
        codec = next((name for name, number in CODECS.items() if number == codec_id), None)
        if codec is None or count <= 0 or last_delta + 1 != count or attributes & 0x30:
            raise ValueError(f"Unexpected synthetic batch attributes/count at {cursor}")
        batches.append({"file": path.name, "filePosition": cursor, "batchBytes": 12 + length,
                        "baseOffset": base_offset, "lastOffset": base_offset + last_delta,
                        "recordCount": count, "attributes": attributes,
                        "codecId": codec_id, "codec": codec,
                        "crc32c": f"{actual_crc:08x}", "crc32cVerified": True})
        cursor = end
    return batches


def self_test() -> None:
    import tempfile
    assert crc32c(b"123456789") == 0xE3069283
    with tempfile.TemporaryDirectory(prefix="orderflow-codec-parser-") as temp:
        path = Path(temp) / "synthetic.log"
        for codec, number in CODECS.items():
            # Structural parser fixture, not a compressed-record compatibility test.
            payload = b"parser-fixture"
            tail = struct.pack(">hiqqqhii", number, 0, 0, 0, -1, -1, -1, 1) + payload
            data = struct.pack(">qiiBI", 0, 9 + len(tail), 0, 2, crc32c(tail)) + tail
            path.write_bytes(data)
            rows = batch_headers(path)
            assert len(rows) == 1 and rows[0]["codec"] == codec and rows[0]["recordCount"] == 1
            for bad in (data[:-1], data[:17] + bytes([data[17] ^ 1]) + data[18:]):
                path.write_bytes(bad)
                try:
                    batch_headers(path)
                except ValueError:
                    pass
                else:
                    raise AssertionError("Corrupt/truncated Kafka bytes were accepted")
    print("PASS: codec attribute parsing, CRC32C known vector, corruption and truncation rejection")


def config_snapshot() -> dict:
    services = ("order", "inventory", "payment", "delivery", "notification", "operations")
    files = {}
    overrides = []
    for service in services:
        root = REPO / f"{service}-service"
        paths = sorted(root.glob("src/main/resources/application*.yml"))
        paths += sorted(root.glob("src/main/java/**/config/*Kafka*.java"))
        for path in paths:
            text = path.read_text()
            files[str(path.relative_to(REPO))] = sha256(path)
            for number, line in enumerate(text.splitlines(), 1):
                if re.search(r"compression[.-]type|compression-type|COMPRESSION_TYPE", line):
                    overrides.append({"file": str(path.relative_to(REPO)), "line": number, "text": line})
    compose = REPO / "docker-compose.cloud.yml"
    files[str(compose.relative_to(REPO))] = sha256(compose)
    return {
        "sourceSha256": files,
        "compressionOverridesFoundInReviewedProducerConfig": overrides,
        "reviewedServices": [f"{service}-service" for service in services],
        "repositoryProducerCodec": "none (Kafka client default; verified again in measured runtime)",
        "serializers": "StringSerializer/StringDeserializer",
        "consumerPolicy": "auto.offset.reset=earliest; enable.auto.commit=false; no codec allowlist",
        "reviewedBrokerCompressionOverride": "none in cloud Compose; test topics explicitly preserve producer codec",
        "initialStagingScope": {
            "status": "SECURITY_DECISION_REQUIRED",
            "candidateConstraint": "New empty staging only; OrderFlow default none producers; no imported records or external producers using unsupported codecs",
            "enforcement": "No policy approval or application/broker codec restrictions are introduced by this probe",
            "existingRecords": "No actual staging/production records inspected; codec history remains unverified",
            "compatibilityLimit": "No arbitrary legacy compressed-record compatibility claim",
        },
    }


class Probe:
    def __init__(self, args):
        self.args = args
        self.output = args.output.resolve()
        self.output.mkdir(parents=True, exist_ok=False)
        self.prefix = "orderflow-codec-probe-" + datetime.now(timezone.utc).strftime("%Y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:8]
        self.report = {"schemaVersion": 1, "measurementStatus": "RUNNING", "startedAt": datetime.now(timezone.utc).isoformat(),
                       "syntheticPrefix": self.prefix, "javaSourceSha256": sha256(JAVA),
                       "driverSha256": sha256(Path(__file__)), "configurationReview": config_snapshot(),
                       "commands": [], "images": {}, "measurements": [], "persistedBatches": [],
                       "referenceSeeds": []}

    def save(self):
        (self.output / "results.json").write_text(json.dumps(self.report, indent=2) + "\n")

    def command(self, name, command, data=None, timeout=90):
        try:
            process = subprocess.run(command, input=data, text=True, capture_output=True, timeout=timeout)
            row = {"name": name, "command": command, "exitCode": process.returncode}
        except subprocess.TimeoutExpired as error:
            stdout = error.stdout or b""
            stderr = error.stderr or b""
            if isinstance(stdout, bytes): stdout = stdout.decode(errors="replace")
            if isinstance(stderr, bytes): stderr = stderr.decode(errors="replace")
            process = subprocess.CompletedProcess(command, 124, stdout, stderr + "\nPROBE TIMED OUT\n")
            row = {"name": name, "command": command, "exitCode": 124, "timeout": True}
        (self.output / f"{name}.stdout.log").write_text(process.stdout)
        (self.output / f"{name}.stderr.log").write_text(process.stderr)
        self.report["commands"].append(row)
        self.save()
        return process

    def checked(self, name, command, data=None, timeout=90):
        result = self.command(name, command, data, timeout)
        if result.returncode:
            raise RuntimeError(f"{name} failed with exit {result.returncode}; raw logs retained")
        return result.stdout

    def image(self, label, ref):
        info = json.loads(self.checked(f"{label}-image-inspect", ["docker", "image", "inspect", ref]))[0]
        if info["Architecture"] != label or info["Os"] != "linux":
            raise ValueError(f"Expected linux/{label} image, got {info['Os']}/{info['Architecture']}")
        self.report["images"][label] = {"requested": ref, "imageId": info["Id"],
            "repoDigests": info.get("RepoDigests", []), "platform": f"linux/{label}",
            "defaultUser": info["Config"].get("User"), "labels": info["Config"].get("Labels")}
        return info["Id"]

    def app(self, label, image, operation, topic=None, codec=None):
        name = f"{label}-{operation}" + (f"-{codec}-{topic.rsplit('.', 1)[-1]}" if topic else "")
        container = self.prefix + "-" + name
        arguments = [operation] + ([self.args.bootstrap, topic, codec, str(self.args.count)] if topic else [])
        # Extract exactly the measured image's five direct client/codec/logging JARs.
        shell = r'''set -eu
cd /tmp
cat > KafkaCodecProbe.java
libs="$(jar -tf /app/app.jar | awk '$0 ~ "^BOOT-INF/lib/(kafka-clients|lz4-java|snappy-java|zstd-jni|slf4j-api)-[^/]+[.]jar$" {print}')"
set -- $libs
[ "$#" -eq 5 ] || { echo "Expected exactly five image client/codec JARs" >&2; exit 78; }
jar -xf /app/app.jar "$@"
sha256sum BOOT-INF/lib/*.jar
classpath="$(printf '%s:' BOOT-INF/lib/*.jar)"
classpath="${classpath%:}"
exec java --class-path "$classpath" KafkaCodecProbe.java ''' + shlex.join(arguments)
        command = ["docker", "run", "--rm", "-i", "--pull", "never", "--name", container,
                   "--platform", f"linux/{label}", "--network", self.args.network or "none",
                   "--init", "--memory", "640m", "--read-only", "--tmpfs", "/tmp:size=64m,mode=1777",
                   "--security-opt", "no-new-privileges:true", "-e", "JAVA_TOOL_OPTIONS=" + JAVA_OPTIONS,
                   "--entrypoint", "sh", image, "-ec", shell]
        process = self.command(name, command, JAVA.read_text(), timeout=180)
        if process.returncode == 124:
            self.command(name + "-owned-timeout-cleanup", ["docker", "rm", "-f", container], timeout=20)
        results = [json.loads(line[len(MARKER):]) for line in process.stdout.splitlines() if line.startswith(MARKER)]
        if len(results) != 1:
            row = {"operation": operation, "topic": topic, "codec": codec, "status": "HARNESS_FAILURE"}
        else:
            row = results[0]
        row.update({"platform": f"linux/{label}", "imageId": image, "exitCode": process.returncode, "logPrefix": name})
        self.report["measurements"].append(row)
        self.save()
        print(f"{label} {operation} {codec or 'native diagnostics'}: {row['status']}", flush=True)
        return row

    def reference_seed(self, label, topic, codec):
        broker = self.args.broker_container
        base = ["docker", "exec", "-i", broker]
        created = self.command(f"{label}-reference-topic-{codec}", base + ["/opt/kafka/bin/kafka-topics.sh",
            "--bootstrap-server", self.args.bootstrap, "--create", "--topic", topic,
            "--partitions", "1", "--replication-factor", "1", "--config", "compression.type=producer"])
        payload = "".join(f"codec-key-{i}\torderflow-synthetic-codec-probe|{codec}|{i}|" +
                          "payload-0123456789abcdef" * 256 + "\n" for i in range(self.args.count))
        seeded = None
        if created.returncode == 0:
            seeded = self.command(f"{label}-reference-seed-{codec}", base + ["/opt/kafka/bin/kafka-console-producer.sh",
                "--bootstrap-server", self.args.bootstrap, "--topic", topic, "--compression-codec", codec,
                "--property", "parse.key=true", "--property", "key.separator=\t",
                "--producer-property", "acks=all", "--producer-property", "batch.size=262144"], payload)
        passed = seeded is not None and seeded.returncode == 0
        self.report["referenceSeeds"].append({"platform": f"linux/{label}", "topic": topic,
            "codec": codec, "origin": "reference broker CLI, not application producer",
            "status": "PASS" if passed else "FAIL"})
        self.save()
        return passed

    def persisted(self, label, topic, codec, origin):
        directory = self.args.broker_log_dir.rstrip("/") + "/" + topic + "-0"
        paths = self.checked(f"{label}-segments-{codec}-{origin}", ["docker", "exec", self.args.broker_container,
            "find", directory, "-maxdepth", "1", "-type", "f", "-name", "*.log"]).splitlines()
        batches = []
        for index, path in enumerate(sorted(paths)):
            target = self.output / f"{label}-{codec}-{origin}-{index:02d}.log"
            self.checked(f"{label}-copy-{codec}-{origin}-{index:02d}", ["docker", "cp",
                self.args.broker_container + ":" + path, str(target)])
            batches.extend(batch_headers(target))
        passed = bool(batches) and all(row["codec"] == codec for row in batches)
        passed = passed and sum(row["recordCount"] for row in batches) == self.args.count
        passed = passed and batches[0]["baseOffset"] == 0 and batches[-1]["lastOffset"] == self.args.count - 1
        passed = passed and all(right["baseOffset"] == left["lastOffset"] + 1
                                for left, right in zip(batches, batches[1:]))
        row = {"platform": f"linux/{label}", "topic": topic, "expectedCodec": codec,
               "origin": origin, "status": "PASS" if passed else "FAIL", "batches": batches}
        self.report["persistedBatches"].append(row)
        self.save()

    def run(self):
        if not self.args.native_only:
            self.report["network"] = json.loads(self.checked("network-inspect", ["docker", "network", "inspect", self.args.network]))
            inspect_format = ('{"Id":{{json .Id}},"Image":{{json .Image}},'
                '"ConfiguredImage":{{json .Config.Image}},"User":{{json .Config.User}},'
                '"Mounts":{{json .Mounts}},"Networks":{{json .NetworkSettings.Networks}}}')
            self.report["broker"] = json.loads(self.checked("broker-inspect", ["docker", "inspect",
                "--format", inspect_format, self.args.broker_container]))
            self.checked("broker-reference-version", ["docker", "exec", self.args.broker_container,
                "/opt/kafka/bin/kafka-topics.sh", "--version"])
        for specification in self.args.image:
            label, ref = specification.split("=", 1)
            image = self.image(label, ref)
            self.app(label, image, "native")
            if self.args.native_only:
                continue
            for codec in ("none", "gzip", "lz4", "snappy", "zstd"):
                topic = f"{self.prefix}.{label}.{codec}.hardened"
                produced = self.app(label, image, "produce", topic, codec)
                if produced["status"] == "PASS":
                    self.app(label, image, "consume", topic, codec)
                    self.persisted(label, topic, codec, "hardened")
                else:
                    reference = f"{self.prefix}.{label}.{codec}.reference"
                    if self.reference_seed(label, reference, codec):
                        self.persisted(label, reference, codec, "reference-broker-cli")
                        self.app(label, image, "consume", reference, codec)
                    else:
                        self.report["measurements"].append({"platform": f"linux/{label}",
                            "operation": "consume", "topic": reference, "codec": codec,
                            "status": "NOT_RUN_REFERENCE_SEED_FAILED"})
                        self.save()
        failures = [row for row in self.report["measurements"] if row["status"] != "PASS"]
        native_failures = []
        for row in self.report["measurements"]:
            if row["operation"] == "native":
                native_failures += [{"platform": row["platform"], "probe": key, "observation": row[key]}
                    for key in ("lz4JavaSafe", "lz4Fastest", "lz4Native", "snappyNative", "zstdNative")
                    if key in row and row[key]["status"] != "PASS"]
        self.report.update(measurementStatus="COMPLETE", completedAt=datetime.now(timezone.utc).isoformat(),
            compatibilityStatus="FAIL" if failures or native_failures or any(row["status"] != "PASS" for row in self.report["persistedBatches"]) else "PASS",
            nativeFailures=native_failures,
            scope="Synthetic exact current application-client paths only. Reference broker seeds do not establish application producer support. No formal security approval or arbitrary legacy-record compatibility.")
        self.save()
        return 2 if self.report["compatibilityStatus"] == "FAIL" else 0


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--image", action="append", help="arm64=local-image-id or amd64=local-image-id; repeat sequentially")
    parser.add_argument("--network")
    parser.add_argument("--broker-container")
    parser.add_argument("--bootstrap", default="kafka:29092")
    parser.add_argument("--broker-log-dir", default="/var/lib/kafka/data")
    parser.add_argument("--output", type=Path)
    parser.add_argument("--count", type=int, default=12)
    parser.add_argument("--native-only", action="store_true")
    parser.add_argument("--confirm-isolated-synthetic", action="store_true")
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if not args.image or not args.output or not args.confirm_isolated_synthetic:
        parser.error("--image, a new --output directory and --confirm-isolated-synthetic are required")
    if not args.native_only and (not args.network or not args.broker_container):
        parser.error("An existing isolated --network and --broker-container are required")
    if not 1 <= args.count <= 100 or any(not re.fullmatch(r"(?:arm64|amd64)=[^\s]+", image) for image in args.image):
        parser.error("Invalid image specification or count")
    if any(character.isspace() for character in args.broker_log_dir) or not args.broker_log_dir.startswith("/"):
        parser.error("Invalid broker log directory")
    probe = Probe(args)
    try:
        return probe.run()
    except Exception as error:
        probe.report.update(measurementStatus="INCOMPLETE", harnessError=f"{type(error).__name__}: {error}")
        probe.save()
        print(str(error), file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

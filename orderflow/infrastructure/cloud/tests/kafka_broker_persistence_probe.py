#!/usr/bin/env python3
"""Synthetic Kafka 4.3.2 vendor -> candidate -> vendor persistence proof.

Run only when explicitly scheduled with --run. No Docker operation
occurs for --help or --self-test. The caller schedules Docker use. All named
volumes created by this driver are retained, including failed/refusal fixtures.
There is deliberately no volume-removal path. The exact application's packaged
Kafka/client codec libraries run in its unchanged hardened container settings.

Example:
 python3 kafka_broker_persistence_probe.py --run \
   --candidate-image sha256:LOCAL_CANDIDATE_CONFIG_ID \
   --client-image sha256:LOCAL_AMD64_APPLICATION_CONFIG_ID \
   --output /absolute/new/cache/evidence-directory

The required Java/helper files are produced by the coordinated codec probe.
This driver performs no publication, pull, build, feature finalization, arbitrary
broker access, existing-volume access, or historical 3.9 compatibility claim.
"""
from __future__ import annotations

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import importlib.util
import json
from pathlib import Path
import re
import shlex
import shutil
import subprocess
import sys
import time
import uuid


REPO = Path(__file__).resolve().parents[3]
BASELINE = 'apache/kafka@sha256:8f325fc00143fed78eabe00b033a86747078bb06edc21345d06651f77ae44f30'
DATA = '/var/lib/kafka/data'
MARKER = 'ORDERFLOW_CODEC_RESULT='
OWNER_LABEL = 'io.orderflow.synthetic.kafka-persistence'
JAVA_OPTIONS = ('-XX:InitialRAMPercentage=15.0 -XX:MaxRAMPercentage=50.0 '
                '-XX:+UseSerialGC -XX:+ExitOnOutOfMemoryError')
VENDOR_VOLUMES = ('/etc/kafka/secrets', '/mnt/shared/config', DATA)


def now():
    return datetime.now(timezone.utc).isoformat()


def sha(value):
    return hashlib.sha256(value.encode() if isinstance(value, str) else value).hexdigest()


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def parse_marker(stdout):
    rows = [json.loads(line[len(MARKER):]) for line in stdout.splitlines() if line.startswith(MARKER)]
    require(len(rows) == 1, f'Expected one helper result marker, found {len(rows)}')
    return rows[0]


def feature_levels(raw):
    rows = re.findall(r'Feature:\s*(\S+).*?FinalizedVersionLevel:\s*(\S+)', raw)
    require(bool(rows), 'No finalized feature levels found in broker CLI output')
    result = dict(rows)
    require(result.get('metadata.version') in ('3.9-IV0', '21'), f'Unexpected metadata.version: {result}')
    result['metadata.version'] = '3.9-IV0'
    require(result.get('kraft.version', '0') == '0', f'Unexpected kraft.version: {result}')
    require(all(value == '0' for key, value in result.items() if key != 'metadata.version'),
            f'Other feature was finalized: {result}')
    return result


def compare_features(before, after):
    for key in before.keys() | after.keys():
        require(before.get(key, '0') == after.get(key, '0'), f'Finalized feature changed: {key}')


def comparable_topic(snapshot):
    # Leader/ISR are included because this is the same single-node synthetic
    # topology. Group member IDs, transient state and feature epochs are not
    # persisted-offset identity and are recorded without equality assertions.
    keys = ('partition', 'leader', 'replicas', 'isr', 'logStart', 'logEnd', 'committedOffset')
    require('allTopicConfigs' in snapshot, 'Coordinated Java snapshot lacks allTopicConfigs')
    return {
        'clusterId': snapshot['clusterId'], 'topicId': snapshot['topicId'],
        'partitionCount': snapshot['partitionCount'], 'allTopicConfigs': snapshot['allTopicConfigs'],
        'finalizedFeatures': snapshot['finalizedFeatures'],
        'partitions': [{key: row[key] for key in keys}
                       for row in sorted(snapshot['partitions'], key=lambda row: row['partition'])],
    }


def static_topic(snapshot):
    result = comparable_topic(snapshot)
    result.pop('partitions')
    return result


class Probe:
    def __init__(self, args):
        self.args = args
        self.out = args.output.resolve()
        self.out.mkdir(mode=0o700, parents=True, exist_ok=False)
        self.run_id = 'orderflow-codec-probe-persist-' + uuid.uuid4().hex[:12]
        self.topic = self.run_id + '.lz4'
        self.group = self.run_id + '.classic'
        self.cluster_id = '-' + base64.urlsafe_b64encode(uuid.uuid4().bytes).decode().rstrip('=')[1:]
        self.network = self.run_id + '-net'
        self.containers = set()
        self.volumes = {}
        self.images = {}
        self.network_created = False
        self.counter = 0
        self.guard = self.out / 'safe-bootstrap.source.sh'
        self.java = self.out / 'KafkaCodecProbe.java'
        self.codec_driver = self.out / 'codec_parser.source.py'
        shutil.copyfile(args.guard, self.guard)
        shutil.copyfile(args.java_source, self.java)
        shutil.copyfile(args.codec_driver, self.codec_driver)
        spec = importlib.util.spec_from_file_location('orderflow_persistence_codec_parser', self.codec_driver)
        self.parser = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(self.parser)
        self.report = {
            'schemaVersion': 1, 'status': 'RUNNING', 'startedAt': now(),
            'runId': self.run_id, 'platform': 'linux/amd64', 'clusterId': self.cluster_id,
            'topic': self.topic, 'group': self.group,
            'sourceSHA256': {'driver': sha(Path(__file__).read_bytes()),
                             'guard': sha(self.guard.read_bytes()), 'javaHelper': sha(self.java.read_bytes()),
                             'codecParser': sha(self.codec_driver.read_bytes())},
            'scope': 'Owned synthetic vendor4.3.2 -> candidate4.3.2 -> vendor4.3.2 only; partition0 payloads on a three-partition topic; classic subscribed group with real committed offsets.',
            'historical39Claim': False, 'allVolumesRetained': True,
            'commands': [], 'images': {}, 'volumes': self.volumes,
            'negativeCases': [], 'stages': [], 'clientMeasurements': [], 'persistedBatches': [],
        }
        self.save()

    def save(self):
        temporary = self.out / 'report.json.new'
        temporary.write_text(json.dumps(self.report, indent=2) + '\n')
        temporary.replace(self.out / 'report.json')

    def command(self, label, argv, *, data=None, timeout=90, check=True):
        self.counter += 1
        stem = f'{self.counter:04d}-{label}'
        started = now()
        try:
            result = subprocess.run(argv, input=data, text=True, capture_output=True, timeout=timeout)
            timed_out = False
        except subprocess.TimeoutExpired as error:
            def decode(value):
                return value.decode(errors='replace') if isinstance(value, bytes) else (value or '')
            result = subprocess.CompletedProcess(argv, 124, decode(error.stdout), decode(error.stderr))
            timed_out = True
        stdout, stderr = result.stdout, result.stderr
        (self.out / (stem + '.stdout')).write_text(stdout)
        (self.out / (stem + '.stderr')).write_text(stderr)
        row = {'label': label, 'argv': argv, 'startedAt': started, 'finishedAt': now(),
               'exitCode': result.returncode, 'timeoutSeconds': timeout, 'timedOut': timed_out,
               'stdoutSHA256': sha(stdout), 'stderrSHA256': sha(stderr), 'evidencePrefix': stem}
        (self.out / (stem + '.json')).write_text(json.dumps(row, indent=2) + '\n')
        self.report['commands'].append(row)
        self.save()
        if check and result.returncode:
            raise RuntimeError(f'{label}: exit {result.returncode}; retained {stem}.stdout/.stderr')
        return result

    def docker(self, label, *args, **kwargs):
        return self.command(label, ['docker', *args], **kwargs)

    def inspect_image(self, kind, reference):
        info = json.loads(self.docker('inspect-image-' + kind, 'image', 'inspect', reference).stdout)[0]
        require(info['Os'] == 'linux' and info['Architecture'] == 'amd64', f'{kind} must be linux/amd64')
        if kind == 'client':
            require(not info['Config'].get('Volumes'), 'Client image declares volumes; refuse implicit anonymous lifecycle')
            require(info['Config'].get('User') not in ('', None, 'root', '0', '0:0'),
                    'Expected the actual application nonroot image user')
        else:
            require(set(info['Config'].get('Volumes', {})) == set(VENDOR_VOLUMES),
                    f'{kind} vendor volume declarations changed')
            require(info['Config'].get('Entrypoint') == ['/__cacert_entrypoint.sh'],
                    f'{kind} CA entrypoint changed')
            require(info['Config'].get('User') == 'appuser', f'{kind} vendor user changed')
        row = {'requested': reference, 'imageId': info['Id'], 'repoDigests': info.get('RepoDigests', []),
               'architecture': info['Architecture'], 'config': info['Config']}
        self.report['images'][kind] = row
        self.images[kind] = info['Id']
        self.save()

    def assert_owned(self, resource, name):
        rows = json.loads(self.docker('ownership-' + resource, resource, 'inspect', name).stdout)
        labels = rows[0].get('Labels') or rows[0].get('Config', {}).get('Labels') or {}
        require(labels.get(OWNER_LABEL) == self.run_id, f'Refuse resource not owned by this run: {name}')

    def volume(self, suffix):
        name = self.run_id + '-' + suffix
        exists = self.docker('volume-must-not-exist-' + suffix, 'volume', 'inspect', name, check=False)
        require(exists.returncode != 0, f'Generated volume already exists; refusing reuse: {name}')
        require(not exists.returncode == 124, 'Volume inspection timed out; ownership is unknown')
        self.docker('create-volume-' + suffix, 'volume', 'create', '--label', OWNER_LABEL + '=' + self.run_id, name)
        self.volumes[suffix] = name
        self.save()
        self.assert_owned('volume', name)
        return name

    def helper(self, label, volume, script, *, writable=False):
        require(volume in self.volumes.values(), 'Only newly owned volume mounts are allowed')
        self.assert_owned('volume', volume)
        name = self.run_id + '-fs-' + uuid.uuid4().hex[:6]
        self.containers.add(name)
        mount = 'type=volume,source=' + volume + ',target=/probe,volume-nocopy'
        if not writable:
            mount += ',readonly'
        args = ['run', '--rm', '--pull', 'never', '--name', name, '--platform', 'linux/amd64',
                '--label', OWNER_LABEL + '=' + self.run_id, '--network', 'none', '--read-only',
                '--memory', '192m', '--user', '0:0', '--mount', mount,
                '--tmpfs', '/tmp:rw,nosuid,nodev,size=32m']
        # Override all parent VOLUME declarations so --rm creates/deletes no
        # anonymous storage. The explicit named /probe mount is always retained.
        for path in VENDOR_VOLUMES:
            args += ['--tmpfs', path + ':rw,nosuid,nodev,size=4m']
        args += ['--entrypoint', '/bin/bash', self.images['baseline'], '-ec', script]
        result = self.docker(label, *args, timeout=60).stdout
        self.containers.discard(name)
        return result

    def manifest(self, volume, label):
        raw = self.helper(label, volume,
            "cd /probe; find . -type d -print | LC_ALL=C sort; "
            "find . -type f -exec sha256sum '{}' + | LC_ALL=C sort; "
            "find . -type l -exec ls -ld '{}' + | LC_ALL=C sort")
        return {'sha256': sha(raw), 'manifest': raw}

    def environment(self, *, initialize=False, cluster=None):
        return {
            'CLUSTER_ID': cluster or self.cluster_id, 'KAFKA_NODE_ID': '1',
            'KAFKA_PROCESS_ROLES': 'broker,controller', 'KAFKA_LOG_DIRS': DATA,
            'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@kafka:29093',
            'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER',
            'KAFKA_LISTENERS': 'CONTROLLER://:29093,INTERNAL://:29092',
            'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT',
            'KAFKA_INTER_BROKER_LISTENER_NAME': 'INTERNAL',
            'KAFKA_ADVERTISED_LISTENERS': 'INTERNAL://kafka:29092',
            'KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR': '1',
            'KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR': '1',
            'KAFKA_TRANSACTION_STATE_LOG_MIN_ISR': '1', 'KAFKA_NUM_PARTITIONS': '3',
            'KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS': '0',
            'KAFKA_HEAP_OPTS': '-Xms256m -Xmx512m',
            'KAFKA_LOG_RETENTION_HOURS': '168', 'KAFKA_LOG_RETENTION_BYTES': '1073741824',
            'ORDERFLOW_KAFKA_INITIALIZE_EMPTY': 'true' if initialize else 'false',
        }

    def start(self, label, kind, volume, *, initialize=False, cluster=None):
        for named in (volume, self.volumes['secrets'], self.volumes['shared-config']):
            require(named in self.volumes.values(), 'Refuse existing/unowned volume')
            self.assert_owned('volume', named)
        self.assert_owned('network', self.network)
        name = self.run_id + '-' + label
        self.containers.add(name)
        args = ['run', '-d', '--pull', 'never', '--name', name, '--platform', 'linux/amd64',
                '--label', OWNER_LABEL + '=' + self.run_id, '--hostname', 'kafka',
                '--network', self.network, '--network-alias', 'kafka', '--memory', '1g', '--cpus', '2',
                '--mount', 'type=volume,source=' + volume + ',target=' + DATA + ',volume-nocopy',
                '--mount', 'type=volume,source=' + self.volumes['secrets'] + ',target=/etc/kafka/secrets,volume-nocopy',
                '--mount', 'type=volume,source=' + self.volumes['shared-config'] + ',target=/mnt/shared/config,volume-nocopy',
                '--mount', 'type=bind,source=' + str(self.guard) + ',target=/opt/orderflow/kafka-safe-bootstrap.sh,readonly']
        for key, value in self.environment(initialize=initialize, cluster=cluster).items():
            args += ['-e', key + '=' + value]
        args += [self.images[kind], '/bin/bash', '/opt/orderflow/kafka-safe-bootstrap.sh']
        self.docker('start-' + label, *args)
        return name

    def cli(self, name, label, tool, *args, timeout=65, check=True):
        return self.docker(label, 'exec', '-e', 'KAFKA_HEAP_OPTS=-Xms32m -Xmx96m', name,
                           '/opt/kafka/bin/' + tool, *args, timeout=timeout, check=check)

    def ready(self, name):
        deadline = time.monotonic() + 240
        while time.monotonic() < deadline:
            state = self.docker('readiness-state', 'inspect', '--format', '{{.State.Status}}', name).stdout.strip()
            require(state == 'running', 'Broker exited before protocol readiness')
            result = self.cli(name, 'readiness-kafka-admin', 'kafka-topics.sh', '--bootstrap-server',
                              'localhost:29092', '--list', timeout=min(35, max(1, deadline - time.monotonic())), check=False)
            if result.returncode == 0:
                return
            time.sleep(min(2, max(0, deadline - time.monotonic())))
        raise TimeoutError('Broker did not become protocol-ready within 240 seconds')

    def stop(self, name, label):
        state = json.loads(self.docker(label + '-inspect-before-stop', 'inspect', name).stdout)[0]
        self.report.setdefault('containerStates', []).append({'container': name, 'state': state['State'],
            'mounts': state.get('Mounts', [])})
        self.save()
        require(not state['State'].get('OOMKilled'), 'Broker was OOMKilled')
        self.docker(label + '-stop', 'stop', '--time', '30', name, timeout=50)
        self.docker(label + '-logs', 'logs', '--timestamps', name, check=False)
        self.assert_owned('container', name)
        self.docker(label + '-remove-container', 'rm', name)
        self.containers.discard(name)

    def app(self, label, operation, *operation_args):
        name = self.run_id + '-client-' + uuid.uuid4().hex[:6]
        self.containers.add(name)
        arguments = [operation, 'kafka:29092', self.topic, *map(str, operation_args)]
        shell = r'''set -eu
cd /tmp
libs="$(jar -tf /app/app.jar | awk '$0 ~ "^BOOT-INF/lib/(kafka-clients|lz4-java|snappy-java|zstd-jni|slf4j-api)-[^/]+[.]jar$" {print}')"
set -- $libs
[ "$#" -eq 5 ] || { echo 'Expected exactly five actual app client/codec JARs' >&2; exit 78; }
jar -xf /app/app.jar "$@"
sha256sum BOOT-INF/lib/*.jar
classpath="$(printf '%s:' BOOT-INF/lib/*.jar)"
classpath="${classpath%:}"
exec java --class-path "$classpath" /opt/orderflow-probe/KafkaCodecProbe.java ''' + shlex.join(arguments)
        result = self.docker(label, 'run', '--rm', '--pull', 'never', '--name', name,
            '--platform', 'linux/amd64', '--label', OWNER_LABEL + '=' + self.run_id,
            '--network', self.network, '--init', '--memory', '640m', '--read-only',
            '--tmpfs', '/tmp:size=64m,mode=1777', '--security-opt', 'no-new-privileges:true',
            '--mount', 'type=bind,source=' + str(self.java) + ',target=/opt/orderflow-probe/KafkaCodecProbe.java,readonly',
            '-e', 'JAVA_TOOL_OPTIONS=' + JAVA_OPTIONS, '--entrypoint', 'sh', self.images['client'],
            '-ec', shell, timeout=180, check=False)
        row = parse_marker(result.stdout)
        row.update(label=label, exitCode=result.returncode, imageId=self.images['client'])
        self.report['clientMeasurements'].append(row)
        self.save()
        require(result.returncode == 0 and row.get('status') == 'PASS', f'{label}: actual app helper failed')
        require(row.get('kafkaVersion') == self.args.expected_client_version,
                f'{label}: actual application Kafka client is {row.get("kafkaVersion")}, expected {self.args.expected_client_version}')
        require(bool(row.get('jarSha256')), label + ': actual application JAR hashes missing')
        require(any(re.search(r'\bnoexec\b', entry) for entry in row.get('tmpMount', [])),
                label + ': actual application /tmp lost its deployed noexec flag')
        require(any(re.match(r'NoNewPrivs:\s+1$', entry) for entry in row.get('processSecurity', [])),
                label + ': actual application lost no-new-privileges')
        first_jars = self.report['clientMeasurements'][0].get('jarSha256')
        require(row['jarSha256'] == first_jars, label + ': actual application JAR classpath changed')
        self.containers.discard(name)
        return row

    def identity(self, name, label):
        raw = self.docker(label + '-meta', 'exec', name, 'cat', DATA + '/meta.properties').stdout
        identity = dict(line.split('=', 1) for line in raw.splitlines() if line and not line.startswith('#'))
        require(identity.get('cluster.id') == self.cluster_id and identity.get('node.id') == '1'
                and identity.get('version') == '1' and re.fullmatch(r'[A-Za-z0-9_-]{22}', identity.get('directory.id', '')),
                f'{label}: unexpected persisted identity')
        features = feature_levels(self.cli(name, label + '-features', 'kafka-features.sh',
            '--bootstrap-server', 'localhost:29092', 'describe').stdout)
        quorum = self.cli(name, label + '-quorum', 'kafka-metadata-quorum.sh', '--bootstrap-server',
                         'localhost:29092', 'describe', '--status').stdout
        require(re.search(r'ClusterId:\s*' + re.escape(self.cluster_id) + r'(?:\s|$)', quorum),
                f'{label}: quorum cluster ID mismatch')
        self.cli(name, label + '-topic-description', 'kafka-topics.sh', '--bootstrap-server',
                 'localhost:29092', '--describe', '--topic', self.topic)
        self.cli(name, label + '-all-topic-config-cli', 'kafka-configs.sh', '--bootstrap-server',
                 'localhost:29092', '--entity-type', 'topics', '--entity-name', self.topic, '--describe', '--all')
        self.cli(name, label + '-group-offset-cli', 'kafka-consumer-groups.sh', '--bootstrap-server',
                 'localhost:29092', '--describe', '--group', self.group)
        return {'identity': identity, 'finalizedFeatures': features, 'quorumRawSHA256': sha(quorum)}

    def snapshot(self, name, label, expected_end, expected_commit):
        result = self.app(label + '-snapshot', 'snapshot', self.group)
        require(result['clusterId'] == self.cluster_id, label + ': Admin cluster ID mismatch')
        require(result['partitionCount'] == 3, label + ': partition count changed')
        require(sorted(row['partition'] for row in result['partitions']) == [0, 1, 2],
                label + ': partition identities changed')
        require(result['topicConfigs']['compression.type'] == 'producer', label + ': codec topic policy changed')
        require(result.get('groupMemberCount') == 0, label + ': helper left a live consumer member')
        require(str(result.get('groupType', '')).upper() == 'CLASSIC', label + ': persisted group is not classic')
        require(str(result.get('groupState', '')).upper() == 'EMPTY', label + ': persisted group is not empty')
        for part in result['partitions']:
            expected = expected_end if part['partition'] == 0 else 0
            committed = expected_commit if part['partition'] == 0 else None
            require(part['logStart'] == 0 and part['logEnd'] == expected
                    and part['committedOffset'] == committed, label + ': log/group offsets changed')
        state = self.identity(name, label)
        state['admin'] = result
        comparable_topic(result)
        return state

    def consume(self, label, total, target, before, *, audit=False):
        group = self.run_id + '.' + label + '.audit' if audit else self.group
        row = self.app(label, 'consume-commit', 'lz4', total, group, target)
        require(row.get('groupProtocol') == 'classic', label + ': actual group protocol is not classic')
        require(row.get('committedOffsetBefore') == before, label + ': unexpected initial committed offset')
        require(row.get('committedOffsetAfter') == target and row.get('committedOffsetAfterRestart') == target,
                label + ': commit/restart offset mismatch')
        require(row.get('resumedInitialPosition') == target and row.get('restartNoCommittedReplay') is True,
                label + ': restarted subscription did not preserve the commit')
        require(row.get('consumedRecords') == target - (before or 0), label + ': consumed wrong record range')
        if target == total:
            require(row.get('restartNoReplay') is True, label + ': unexpected data after final committed record')
        return row

    def append(self, label, count, start):
        row = self.app(label, 'append', 'lz4', count, start)
        require(row['startOffset'] == start and row['acknowledgedOffsets'] == list(range(start, start + count)),
                label + ': acknowledged record offsets differ')
        require(row['resolvedProducerCompression'] == 'lz4', label + ': actual app did not produce LZ4')

    def persisted(self, name, label, expected_records):
        paths = self.docker(label + '-list-all-partition-segments', 'exec', name, 'find', DATA,
                            '-type', 'f', '-name', '*.log').stdout.splitlines()
        selected = [path for path in paths if re.fullmatch(re.escape(DATA + '/' + self.topic)
                   + r'-[0-2]/[0-9]+\.log', path)]
        require(bool(selected), label + ': no synthetic topic segment found')
        by_partition = {0: [], 1: [], 2: []}
        for number, remote in enumerate(sorted(selected)):
            part = int(remote.rsplit('/', 2)[1].rsplit('-', 1)[1])
            local = self.out / f'{label}-p{part}-{number:03d}-{Path(remote).name}'
            self.docker(label + '-copy-segment-' + str(number), 'cp', name + ':' + remote, str(local))
            rows = self.parser.batch_headers(local)
            for row in rows:
                row.update(partition=part, sourceSHA256=sha(local.read_bytes()), sourcePath=remote)
            by_partition[part].extend(rows)
        for part, rows in by_partition.items():
            rows.sort(key=lambda row: row['baseOffset'])
            expected = expected_records if part == 0 else 0
            require(sum(row['recordCount'] for row in rows) == expected,
                    label + f': persisted partition{part} record count differs')
            require(all(row['codec'] == 'lz4' and row['crc32cVerified'] for row in rows),
                    label + ': actual on-disk codec/CRC mismatch')
            if expected:
                require(rows[0]['baseOffset'] == 0 and rows[-1]['lastOffset'] == expected - 1,
                        label + ': persisted offset range changed')
                for left, right in zip(rows, rows[1:]):
                    require(left['lastOffset'] + 1 == right['baseOffset'], label + ': missing/duplicate batch offsets')
        self.report['persistedBatches'].append({'stage': label, 'status': 'PASS',
            'expectedRecords': expected_records, 'partitions': by_partition,
            'recordPayloadProof': 'separate actual application helper audit group consumed every absolute sentinel index'})
        self.save()

    def refused(self, label, volume, *, kind='candidate', initialize=False, cluster=None):
        before = self.manifest(volume, label + '-before')
        name = self.start(label, kind, volume, initialize=initialize, cluster=cluster)
        result = self.docker(label + '-wait', 'wait', name, timeout=60).stdout.strip()
        logs = self.docker(label + '-logs', 'logs', name)
        require(result == '78' and 'Kafka storage guard:' in logs.stdout + logs.stderr,
                label + ': expected storage guard refusal exit78')
        after = self.manifest(volume, label + '-after')
        require(before == after, label + ': refusal altered volume bytes/entries')
        self.report['negativeCases'].append({'label': label, 'exitCode': int(result),
            'before': before, 'after': after, 'status': 'PASS'})
        self.save()
        self.stop(name, label)

    def run(self):
        print('Evidence directory:', self.out, flush=True)
        self.inspect_image('baseline', BASELINE)
        self.inspect_image('candidate', self.args.candidate_image)
        self.inspect_image('client', self.args.client_image)
        require(self.images['candidate'] != self.images['baseline'], 'Candidate resolves to unchanged baseline image')
        self.docker('create-private-network', 'network', 'create', '--internal', '--label',
                    OWNER_LABEL + '=' + self.run_id, self.network)
        self.network_created = True
        self.report['network'] = self.network
        self.save()
        for suffix in ('data', 'secrets', 'shared-config', 'missing-meta'):
            volume = self.volume(suffix)
            self.helper('prepare-owned-' + suffix, volume,
                        'test -z "$(find /probe -mindepth 1 -maxdepth 1 -print -quit)"; '
                        'chown 1000:0 /probe; chmod 0775 /probe', writable=True)
        data = self.volumes['data']
        # Failure evidence is retained before any explicitly approved first format.
        self.refused('fresh-default-denied', data, kind='baseline')
        print('Running baseline4.3.2 explicit fresh initialization', flush=True)
        name = self.start('baseline-seed', 'baseline', data, initialize=True)
        self.ready(name)
        self.cli(name, 'create-three-partition-synthetic-topic', 'kafka-topics.sh', '--bootstrap-server',
                 'localhost:29092', '--create', '--topic', self.topic, '--partitions', '3',
                 '--replication-factor', '1', '--config', 'compression.type=producer',
                 '--config', 'cleanup.policy=delete', '--config', 'retention.ms=604800000',
                 '--config', 'retention.bytes=1073741824', '--config', 'segment.ms=1',
                 '--config', 'segment.bytes=1048576', '--config', 'min.insync.replicas=1')
        self.append('baseline-produce0-through5', 6, 0)
        self.consume('baseline-partial-group-commit', 6, 3, None)
        self.consume('baseline-all-records-audit', 6, 6, None, audit=True)
        seeded = self.snapshot(name, 'baseline-seeded', 6, 3)
        self.persisted(name, 'baseline-seeded', 6)
        self.report['stages'].append({'stage': 'baseline-seeded', 'imageId': self.images['baseline'],
                                      'state': seeded, 'status': 'PASS'})
        self.save()
        self.stop(name, 'baseline-seeded')

        print('Running candidate against the exact baseline volume and pending group records', flush=True)
        name = self.start('candidate', 'candidate', data)
        self.ready(name)
        candidate_before = self.snapshot(name, 'candidate-before-mutation', 6, 3)
        require(seeded['identity'] == candidate_before['identity'], 'Candidate changed storage identity')
        compare_features(seeded['finalizedFeatures'], candidate_before['finalizedFeatures'])
        require(comparable_topic(seeded['admin']) == comparable_topic(candidate_before['admin']),
                'Candidate changed topic/config/features/persisted group offsets before mutation')
        self.consume('candidate-resume-pending3-through5', 6, 6, 3)
        self.append('candidate-produce6-through8', 3, 6)
        self.consume('candidate-consume6-through8', 9, 9, 6)
        self.consume('candidate-all-records-audit', 9, 9, None, audit=True)
        candidate_after = self.snapshot(name, 'candidate-after', 9, 9)
        require(seeded['identity'] == candidate_after['identity'], 'Candidate changed identity while serving')
        require(static_topic(seeded['admin']) == static_topic(candidate_after['admin']),
                'Candidate changed topic ID/config/partition count/Admin features while serving')
        compare_features(seeded['finalizedFeatures'], candidate_after['finalizedFeatures'])
        self.persisted(name, 'candidate-after', 9)
        self.report['stages'].append({'stage': 'candidate', 'imageId': self.images['candidate'],
            'before': candidate_before, 'after': candidate_after, 'status': 'PASS'})
        self.save()
        self.stop(name, 'candidate')

        print('Returning the same volume to the exact vendor4.3.2 image', flush=True)
        name = self.start('vendor-return', 'baseline', data)
        self.ready(name)
        returned_before = self.snapshot(name, 'vendor-return-before-mutation', 9, 9)
        require(candidate_after['identity'] == returned_before['identity'], 'Return changed storage identity')
        compare_features(candidate_after['finalizedFeatures'], returned_before['finalizedFeatures'])
        require(comparable_topic(candidate_after['admin']) == comparable_topic(returned_before['admin']),
                'Vendor return changed topic/config/features/group offsets before mutation')
        self.append('vendor-return-produce9-through11', 3, 9)
        self.consume('vendor-return-consume9-through11', 12, 12, 9)
        self.consume('vendor-return-all-records-audit', 12, 12, None, audit=True)
        returned_after = self.snapshot(name, 'vendor-return-after', 12, 12)
        require(seeded['identity'] == returned_after['identity'], 'Return changed identity while serving')
        require(static_topic(seeded['admin']) == static_topic(returned_after['admin']),
                'Vendor return changed topic ID/config/partition count/Admin features while serving')
        compare_features(seeded['finalizedFeatures'], returned_after['finalizedFeatures'])
        self.persisted(name, 'vendor-return-after', 12)
        self.report['stages'].append({'stage': 'vendor-return', 'imageId': self.images['baseline'],
            'before': returned_before, 'after': returned_after, 'status': 'PASS'})
        self.save()
        self.stop(name, 'vendor-return')

        self.refused('candidate-wrong-cluster', data, cluster='B' + self.cluster_id[1:])
        missing = self.volumes['missing-meta']
        self.helper('seed-owned-missing-meta-sentinel', missing,
                    "printf 'synthetic-retained-sentinel\\000\\377' > /probe/sentinel.bin", writable=True)
        self.refused('candidate-nonempty-missing-meta', missing, initialize=True)
        self.report['status'] = 'PASS'
        self.report['passedAssertions'] = [
            'Default empty refusal precedes explicit fresh initialization at metadata3.9-IV0/kraft0',
            'Exact vendor4.3.2 -> candidate4.3.2 -> exact vendor4.3.2 on one owned volume',
            'Cluster/node/directory IDs, every finalized feature and all topic configs preserved',
            'Three partition identities/log starts/log ends and classic committed group offsets preserved',
            'Pending records3..5 resume on candidate; new records6..8 and9..11 resume without reset/replay',
            'Every sentinel payload/key/absolute offset consumed by an independent audit group',
            'All synthetic topic segments/partitions parsed with CRC32C and actual LZ4 batch attributes',
            'Wrong cluster/nonempty missing metadata refuse without volume byte changes',
        ]
        self.save()

    def cleanup(self):
        errors = []
        for name in sorted(self.containers):
            try:
                exists = self.docker('cleanup-inspect-container', 'container', 'inspect', name, check=False)
                if exists.returncode != 0:
                    continue
                self.assert_owned('container', name)
                self.docker('cleanup-container-logs', 'logs', '--timestamps', name, check=False)
                self.docker('cleanup-stop-container', 'stop', '--time', '20', name, timeout=35, check=False)
                # No -v: even unexpected anonymous volumes must not be removed.
                self.docker('cleanup-remove-container', 'rm', '-f', name, timeout=30)
            except Exception as error:
                errors.append(f'{name}: {error}')
        if self.network_created:
            try:
                self.assert_owned('network', self.network)
                self.docker('cleanup-remove-private-network', 'network', 'rm', self.network, timeout=30)
            except Exception as error:
                errors.append(f'{self.network}: {error}')
        retained = {}
        for suffix, name in self.volumes.items():
            try:
                self.assert_owned('volume', name)
                retained[suffix] = json.loads(self.docker('retained-volume-inspect', 'volume', 'inspect', name).stdout)[0]
            except Exception as error:
                errors.append(f'{name}: retained-volume inspection failed: {error}')
        self.report['retainedVolumeEvidence'] = retained
        self.report['cleanupErrors'] = errors
        self.report['finishedAt'] = now()
        if errors and self.report.get('status') == 'PASS':
            self.report['status'] = 'CLEANUP_INCOMPLETE'
        self.save()


def self_test():
    # Pure parsing/contract checks only. Never invoke a subprocess or Docker.
    raw = ('Feature: metadata.version SupportedMinVersion: 3.3-IV3 SupportedMaxVersion: 4.3-IV0 FinalizedVersionLevel: 3.9-IV0 Epoch: 2\n'
           'Feature: kraft.version SupportedMinVersion: 0 SupportedMaxVersion: 1 FinalizedVersionLevel: 0 Epoch: 2')
    levels = feature_levels(raw)
    compare_features(levels, dict(levels, **{'group.version': '0'}))
    require(parse_marker('noise\n' + MARKER + '{"status":"PASS"}\n')['status'] == 'PASS', 'Marker parser')
    for bad in ('', MARKER + '{}\n' + MARKER + '{}'):
        try:
            parse_marker(bad)
        except AssertionError:
            pass
        else:
            raise AssertionError('Invalid helper marker count accepted')
    try:
        compare_features(levels, dict(levels, **{'group.version': '1'}))
    except AssertionError:
        pass
    else:
        raise AssertionError('Changed finalized feature was accepted')
    print('PASS: helper marker cardinality and finalized-feature preservation; no Docker/subprocess execution')


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('--run', action='store_true', help='Explicitly execute the scheduled isolated Docker trial')
    parser.add_argument('--self-test', action='store_true', help='Pure parser checks; no Docker/subprocess execution')
    parser.add_argument('--candidate-image', help='Already built local amd64 candidate; resolved once to immutable config ID')
    parser.add_argument('--client-image', help='Already built actual amd64 app image containing /app/app.jar and full JDK')
    parser.add_argument('--expected-client-version', default='4.2.2', help='Assert actual image Kafka client version; default4.2.2')
    parser.add_argument('--output', type=Path, help='New evidence directory; existing directories are refused')
    parser.add_argument('--guard', type=Path, default=REPO / 'infrastructure/kafka/safe-bootstrap.sh')
    parser.add_argument('--java-source', type=Path, default=REPO / 'infrastructure/cloud/tests/KafkaCodecProbe.java')
    parser.add_argument('--codec-driver', type=Path, default=REPO / 'infrastructure/cloud/tests/kafka_codec_probe.py')
    args = parser.parse_args()
    if args.self_test:
        self_test()
        return 0
    if not args.run:
        parser.error('Review-only by default. Supply --run only after parent schedules Docker execution.')
    if not args.candidate_image or not args.client_image or not args.output:
        parser.error('--candidate-image, --client-image and a new --output directory are required')
    if args.output.exists():
        parser.error('--output already exists; each initial failure/run needs its own retained evidence directory')
    for label, path in (('guard', args.guard), ('Java helper', args.java_source), ('codec parser', args.codec_driver)):
        if not path.is_file():
            parser.error(label + ' file is missing: ' + str(path))
    for token in ('consume-commit', 'append', 'snapshot', 'allTopicConfigs'):
        if token not in args.java_source.read_text():
            parser.error('Java helper is missing the coordinated interface: ' + token)
    probe = Probe(args)
    try:
        probe.run()
    except BaseException as error:
        probe.report.update(status='FAIL', failure={'type': type(error).__name__, 'message': str(error), 'time': now()})
        probe.save()
        print(f'FAIL: {type(error).__name__}: {error}', file=sys.stderr, flush=True)
    finally:
        probe.cleanup()
    print(json.dumps({'status': probe.report['status'], 'report': str(probe.out / 'report.json'),
                      'retainedVolumes': probe.volumes}), flush=True)
    return 0 if probe.report['status'] == 'PASS' else 1


if __name__ == '__main__':
    raise SystemExit(main())

"""Execute the storage guard against private files and vendor-process boundaries.

These tests do not run Docker or Kafka. Only absolute container paths are
relocated into a disposable fixture; the actual guard's shell logic executes.
The separate persisted-message integration probe verifies real Kafka behavior.
"""
import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[3]
GUARD = ROOT / 'infrastructure/kafka/safe-bootstrap.sh'
CLUSTER_ID = 'MkU3OEVBNTcwNTJENDM2Qk'
DIRECTORY_ID = 'abcdefghijklmnopqrstuv'


class KafkaStorageGuardTests(unittest.TestCase):
    def setUp(self):
        self.assertTrue(GUARD.is_file(), 'Kafka storage guard implementation is missing')
        self.fixture = tempfile.TemporaryDirectory(prefix='orderflow-kafka-guard-')
        self.addCleanup(self.fixture.cleanup)
        self.root = Path(self.fixture.name)
        self.data = self.root / 'data'
        self.vendor = self.root / 'vendor'
        self.mounted = self.root / 'mounted'
        for path in [self.data, self.vendor, self.mounted]:
            path.mkdir()
        self.meta = self.data / 'meta.properties'
        self.calls = self.root / 'vendor-calls'
        self.format_args = self.root / 'format-args'
        self.format_config = self.root / 'format-config'
        self.script = self.root / 'safe-bootstrap.sh'
        replacements = {
            '/var/lib/kafka/data': str(self.data),
            '/etc/kafka/docker': str(self.vendor),
            '/mnt/shared/config': str(self.mounted),
            '/opt/kafka/bin/kafka-storage.sh': str(self.root / 'storage-tool'),
            '/tmp/orderflow-kafka-format.': str(self.root / 'format.'),
        }
        source = GUARD.read_text()
        for original, replacement in replacements.items():
            source = source.replace(original, replacement)
        self.script.write_text(source)
        self.env = {
            'PATH': os.environ.get('PATH', '/usr/bin:/bin'),
            'CLUSTER_ID': CLUSTER_ID,
            'KAFKA_NODE_ID': '1',
            'KAFKA_PROCESS_ROLES': 'broker,controller',
            'KAFKA_LOG_DIRS': str(self.data),
            'KAFKA_CONTROLLER_QUORUM_VOTERS': '1@kafka:29093',
            'KAFKA_CONTROLLER_LISTENER_NAMES': 'CONTROLLER',
            'KAFKA_LISTENERS': 'CONTROLLER://:29093,INTERNAL://:29092',
            'KAFKA_LISTENER_SECURITY_PROTOCOL_MAP': 'CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT',
            'KAFKA_INTER_BROKER_LISTENER_NAME': 'INTERNAL',
            'KAFKA_ADVERTISED_LISTENERS': 'INTERNAL://kafka:29092',
            'PROBE_CALLS': str(self.calls),
            'PROBE_ARGS': str(self.format_args),
            'PROBE_CONFIG': str(self.format_config),
        }
        # The real vendor scripts validate environment/config and then start a
        # JVM. Replace only those external process boundaries for this unit test.
        (self.vendor / 'configureDefaults').write_text('printf "defaults\\n" >> "$PROBE_CALLS"\n')
        (self.vendor / 'configure').write_text('printf "configure\\n" >> "$PROBE_CALLS"\n')
        self.executable(self.vendor / 'launch', 'printf "launch\\n" >> "$PROBE_CALLS"\n')
        self.executable(self.root / 'storage-tool', '''
printf 'format\n' >> "$PROBE_CALLS"
printf '%s\n' "$@" > "$PROBE_ARGS"
# Match the real argparse4j boundary: a leading '-' value is interpreted as an
# option unless --cluster-id=<value> binds it into the same argument.
if [ "${2:-}" = --cluster-id ] && [[ ${3:-} == -* ]]; then
    printf 'argument --cluster-id: expected one argument\\n' >&2
    exit 2
fi
while [ "$#" -gt 0 ]; do
    if [ "$1" = --config ]; then cp "$2" "$PROBE_CONFIG"; break; fi
    shift
done
if [ "${PROBE_FORMAT_FAIL:-false}" = true ]; then exit 42; fi
cat > "$KAFKA_LOG_DIRS/meta.properties" <<EOF
version=1
cluster.id=$CLUSTER_ID
node.id=$KAFKA_NODE_ID
directory.id=abcdefghijklmnopqrstuv
EOF
''')

    @staticmethod
    def executable(path, body):
        path.write_text('#!/usr/bin/env bash\nset -euo pipefail\n' + body)
        path.chmod(0o755)

    def existing_storage(self, text=None):
        self.meta.write_text(text if text is not None else (
            'version=1\ncluster.id=MkU3OEVBNTcwNTJENDM2Qk\n'
            'node.id=1\ndirectory.id=abcdefghijklmnopqrstuv\n'
        ))
        (self.data / 'persisted-message').write_bytes(b'preserve-existing-message\x00\xff')

    def data_snapshot(self):
        result = {}
        for path in sorted(self.data.rglob('*')):
            relative = str(path.relative_to(self.data))
            if path.is_symlink():
                result[relative] = ('symlink', os.readlink(path))
            elif path.is_file():
                result[relative] = ('file', path.read_bytes())
            else:
                result[relative] = ('directory',)
        return result

    def run_guard(self):
        self.calls.unlink(missing_ok=True)
        return subprocess.run(['/bin/bash', str(self.script)], env=self.env,
                              text=True, capture_output=True, timeout=10)

    def recorded_calls(self):
        return self.calls.read_text().splitlines() if self.calls.exists() else []

    def assert_refused_without_data_changes(self):
        before = self.data_snapshot()
        result = self.run_guard()
        self.assertEqual(result.returncode, 78, result.stdout + result.stderr)
        self.assertNotIn('format', self.recorded_calls())
        self.assertNotIn('launch', self.recorded_calls())
        self.assertEqual(self.data_snapshot(), before)

    def test_existing_identity_starts_without_formatting_or_changing_bytes(self):
        self.existing_storage()
        before = self.data_snapshot()
        result = self.run_guard()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.recorded_calls(), ['defaults', 'configure', 'launch'])
        self.assertEqual(self.data_snapshot(), before)

    def test_explicit_empty_initialization_pins_all_formatter_identity_arguments(self):
        self.env['ORDERFLOW_KAFKA_INITIALIZE_EMPTY'] = 'true'
        result = self.run_guard()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertEqual(self.recorded_calls(), ['defaults', 'configure', 'format', 'launch'])
        args = self.format_args.read_text().splitlines()
        formatter = argparse.ArgumentParser()
        for option in ['--cluster-id', '--config', '--release-version', '--feature']:
            formatter.add_argument(option, required=True)
        parsed = formatter.parse_args(args[1:])
        self.assertEqual(args[0], 'format')
        self.assertEqual(parsed.cluster_id, CLUSTER_ID)
        self.assertEqual(parsed.release_version, '3.9-IV0')
        self.assertEqual(parsed.feature, 'kraft.version=0')
        self.assertFalse(Path(parsed.config).exists(), 'temporary formatter config must be removed')
        config = dict(line.split('=', 1) for line in self.format_config.read_text().splitlines())
        self.assertEqual(config, {
            'node.id': '1', 'process.roles': 'broker,controller',
            'listeners': 'CONTROLLER://:29093,INTERNAL://:29092',
            'advertised.listeners': 'INTERNAL://kafka:29092',
            'listener.security.protocol.map': 'CONTROLLER:PLAINTEXT,INTERNAL:PLAINTEXT',
            'inter.broker.listener.name': 'INTERNAL', 'controller.listener.names': 'CONTROLLER',
            'controller.quorum.voters': '1@kafka:29093', 'log.dirs': str(self.data),
        })
        self.assertEqual(self.meta.read_text(), 'version=1\ncluster.id=' + CLUSTER_ID +
                         '\nnode.id=1\ndirectory.id=' + DIRECTORY_ID + '\n')

    def test_fresh_valid_cluster_id_starting_with_hyphen_is_not_parsed_as_an_option(self):
        cluster_id = '-hESEXqNQ3yANUPgZI5bVQ'
        self.env.update(CLUSTER_ID=cluster_id, ORDERFLOW_KAFKA_INITIALIZE_EMPTY='true')
        result = self.run_guard()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        self.assertIn('cluster.id=' + cluster_id + '\n', self.meta.read_text())
        self.assertEqual(self.recorded_calls(), ['defaults', 'configure', 'format', 'launch'])

    def test_empty_volume_is_denied_by_default(self):
        self.assert_refused_without_data_changes()

    def test_nonempty_directory_without_identity_is_never_formatted(self):
        self.env['ORDERFLOW_KAFKA_INITIALIZE_EMPTY'] = 'true'
        (self.data / '.retained-log').write_bytes(b'previous Kafka bytes')
        self.assert_refused_without_data_changes()

    def test_mismatched_identity_fields_are_rejected(self):
        self.existing_storage()
        original = self.meta.read_text()
        for before, after in [('version=1', 'version=0'), ('node.id=1', 'node.id=2'),
                              ('cluster.id=' + CLUSTER_ID, 'cluster.id=' + DIRECTORY_ID)]:
            with self.subTest(field=before):
                self.meta.write_text(original.replace(before, after))
                self.assert_refused_without_data_changes()

    def test_duplicate_or_missing_identity_fields_are_rejected(self):
        self.existing_storage()
        original = self.meta.read_text()
        for field in ['version', 'cluster.id', 'node.id', 'directory.id']:
            line = next(line for line in original.splitlines() if line.startswith(field + '='))
            for changed in [original + line + '\n', original.replace(line + '\n', '')]:
                with self.subTest(field=field, duplicate=changed.startswith(original)):
                    self.meta.write_text(changed)
                    self.assert_refused_without_data_changes()

    def test_symlinked_identity_file_is_rejected(self):
        self.existing_storage()
        target = self.root / 'identity-outside-volume'
        target.write_bytes(self.meta.read_bytes())
        self.meta.unlink()
        self.meta.symlink_to(target)
        original = target.read_bytes()
        self.assert_refused_without_data_changes()
        self.assertEqual(target.read_bytes(), original)

    def test_noncanonical_properties_cannot_hide_duplicate_identity_fields(self):
        self.existing_storage()
        original = self.meta.read_text()
        for hidden in [' node.id=2\n', 'node.id:2\n', 'node\\.id=2\n',
                       'node.id=1\\\n2\n']:
            with self.subTest(hidden=hidden):
                self.meta.write_text(original + hidden)
                self.assert_refused_without_data_changes()

    def test_symlinked_data_directory_is_rejected(self):
        self.existing_storage()
        real_data = self.root / 'real-data'
        self.data.rename(real_data)
        self.data.symlink_to(real_data, target_is_directory=True)
        self.assert_refused_without_data_changes()

    def test_extra_storage_and_feature_settings_are_rejected(self):
        self.existing_storage()
        for key, value in [('KAFKA_METADATA_LOG_DIR', '/unexpected'),
                           ('KAFKA_METADATA_LOG_DIR', ''), ('KAFKA_LOG_DIR', '/unexpected'),
                           ('KAFKA_METADATA_VERSION', '3.9-IV0'),
                           ('KAFKA_UNSTABLE_FEATURE_VERSIONS_ENABLE', 'true'),
                           ('KAFKA_LOG_DIRS', str(self.data) + ',/unexpected')]:
            with self.subTest(setting=key, value=value):
                before = self.env.copy()
                self.env[key] = value
                self.assert_refused_without_data_changes()
                self.env = before

    def test_mounted_configuration_cannot_add_unchecked_storage_paths(self):
        self.existing_storage()
        (self.mounted / 'server.properties').write_text('metadata.log.dir=/unexpected\n')
        self.assert_refused_without_data_changes()

    def test_formatter_failure_prevents_vendor_launch(self):
        self.env.update(ORDERFLOW_KAFKA_INITIALIZE_EMPTY='true', PROBE_FORMAT_FAIL='true')
        result = self.run_guard()
        self.assertEqual(result.returncode, 42, result.stdout + result.stderr)
        self.assertEqual(self.recorded_calls(), ['defaults', 'configure', 'format'])
        self.assertEqual(self.data_snapshot(), {})
        self.assertEqual(list(self.root.glob('format.*')), [])


if __name__ == '__main__':
    unittest.main()

import importlib.util
from pathlib import Path
import tempfile
import unittest

MODULE=Path(__file__).resolve().parents[2]/'backup/backup.py'
class BackupSafety(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.backup=None
        if MODULE.exists():
            spec=importlib.util.spec_from_file_location('backup',MODULE)
            cls.backup=importlib.util.module_from_spec(spec); spec.loader.exec_module(cls.backup)
    def setUp(self):
        self.assertIsNotNone(self.backup,'encrypted backup tooling is missing')
        self.tmp=tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup); self.root=Path(self.tmp.name)
    def test_restore_refuses_live_or_unisolated_container_name(self):
        for name in ['postgres','orderflow-cloud-postgres-1','anything','restore;rm']:
            with self.assertRaises(self.backup.SafetyError): self.backup.validate_restore_name(name)
        self.backup.validate_restore_name('orderflow-restore-0123456789ab')
    def test_verify_rejects_missing_owner_dump(self):
        self.backup.atomic_json(self.root/'backup.json', {'schemaVersion':1,'databases':{}})
        with self.assertRaises(self.backup.SafetyError): self.backup.verify_backup(self.root)
    def test_verification_detects_ciphertext_corruption(self):
        import hashlib
        records={}
        for owner in self.backup.OWNERS:
            path=self.root/(owner.lower()+'.dump.enc'); path.write_bytes(b'encrypted')
            records[owner]={'file':path.name,'sha256':hashlib.sha256(b'encrypted').hexdigest()}
        self.backup.atomic_json(self.root/'backup.json', {'schemaVersion':1,'databases':records})
        self.backup.verify_backup(self.root)
        (self.root/'auth.dump.enc').write_bytes(b'broken')
        with self.assertRaises(self.backup.SafetyError): self.backup.verify_backup(self.root)
    def test_backup_path_traversal_rejected(self):
        records={o:{'file':'../secret','sha256':'a'*64} for o in self.backup.OWNERS}
        self.backup.atomic_json(self.root/'backup.json', {'schemaVersion':1,'databases':records})
        with self.assertRaises(self.backup.SafetyError): self.backup.verify_backup(self.root)
    def test_uninitialized_backup_is_only_allowed_for_empty_schema(self):
        self.assertEqual(self.backup.snapshot(lambda sql: '',allow_uninitialized=True),{'tables':{},'flyway':[]})
        with self.assertRaises(self.backup.SafetyError): self.backup.snapshot(lambda sql:'')
        with self.assertRaises(self.backup.SafetyError): self.backup.snapshot(lambda sql:'public.orders\n',allow_uninitialized=True)

    def test_flyway_text_concatenation_true_is_accepted(self):
        folder=self.root/'auth-service/src/main/resources/db/migration'; folder.mkdir(parents=True)
        (folder/'V1.sql').write_bytes(b'SELECT 1;')
        checksum=self.backup.flyway_checksum(b'SELECT 1;')
        self.backup.validate_flyway('AUTH',[f'V1.sql|{checksum}|true'],self.root)

    def test_flyway_checksum_normalizes_line_endings(self):
        self.assertEqual(self.backup.flyway_checksum(b'SELECT 1;\r\nSELECT 2;'),self.backup.flyway_checksum(b'SELECT 1;\nSELECT 2;'))
if __name__=='__main__': unittest.main()

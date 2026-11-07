import unittest
from manage import Instance

class RestoreConsentTests(unittest.TestCase):
    def test_live_database_cannot_be_reset(self):
        instance=object.__new__(Instance)
        instance.sql=lambda *args: self.fail('Live SQL must not execute')
        with self.assertRaises(RuntimeError):instance.suspend_restored_training('skincare_dev')

    def test_restored_snapshot_disables_old_training_grants_without_deleting_images(self):
        instance=object.__new__(Instance);calls=[]
        def sql(statement,*args):
            calls.append(statement)
            if statement.startswith('SELECT'):return 'users\ttraining_granted\nanalysis_sessions\ttraining_eligible\ndiagnoses\ttraining_use'
            return ''
        instance.sql=sql;instance.suspend_restored_training('skincare_restore_synthetic')
        self.assertIn('UPDATE users SET training_granted=false',calls[1])
        self.assertIn("training_review_status='RECONSENT_REQUIRED'",calls[1])
        self.assertNotIn('DELETE',calls[1]);self.assertNotIn('SET image',calls[1])

    def test_pre_consent_schema_remains_restorable(self):
        instance=object.__new__(Instance);calls=[]
        instance.sql=lambda statement,*args:(calls.append(statement) or '')
        instance.suspend_restored_training('skincare_restore_legacy')
        self.assertEqual(calls[1],'START TRANSACTION;\nCOMMIT;')

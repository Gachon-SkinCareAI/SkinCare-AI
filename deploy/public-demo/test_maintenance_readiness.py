import sys
from pathlib import Path
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / 'mysql'))
import maintenance
import retention


class MaintenanceReadinessTests(unittest.TestCase):
    def tearDown(self):
        maintenance.set_ready(False)

    def test_lock_timeout_retries_without_restarting_or_opening_gate(self):
        db = Mock()
        states = []
        def cycle(_):
            states.append(maintenance.state['ok'])
            if len(states) < 2:raise RuntimeError('MySQL command failed (error 1205)')
            return {'expiredRows': 0}
        db.maintain.side_effect = cycle
        with patch.object(maintenance, 'daily_backup') as backup:
            result = maintenance.run_maintenance_cycle(db, lambda _: None)
        self.assertEqual(states, [False, False])
        self.assertEqual(result, {'expiredRows': 0})
        self.assertTrue(maintenance.ready_event.is_set())
        backup.assert_called_once_with(db)

    def test_repeated_lock_failure_stays_closed_without_crashing_listener(self):
        db = Mock()
        db.maintain.side_effect = RuntimeError('MySQL command failed (error 1213)')
        self.assertIsNone(maintenance.run_maintenance_cycle(db, lambda _: None))
        self.assertEqual(db.maintain.call_count, 3)
        self.assertFalse(maintenance.state['ok'])
        self.assertFalse(maintenance.ready_event.is_set())

    def test_unexpected_errors_are_not_treated_as_ready(self):
        db = Mock()
        db.maintain.side_effect = RuntimeError('identity mismatch')
        with self.assertRaises(RuntimeError):maintenance.run_maintenance_cycle(db)
        self.assertFalse(maintenance.state['ok'])

    def test_retention_no_longer_locks_every_live_account(self):
        sql = []
        db = Mock()
        def execute(query, name):
            sql.append(query)
            if query == 'SHOW TABLES;':return 'users\nanalysis_sessions\nanalysis_answers'
            return '0'
        db.sql.side_effect = execute
        retention.expire(db, 'skincare_dev')
        transaction = sql[-1]
        self.assertIn('READ COMMITTED', transaction)
        self.assertNotIn('SELECT user_id FROM users ORDER BY', transaction)
        self.assertIn('INSERT INTO retention_users SELECT user_id FROM users WHERE DATE_ADD(created_at, INTERVAL 5 YEAR)', transaction)
        self.assertIn('FROM retention_users r STRAIGHT_JOIN users u ON u.user_id=r.id ORDER BY u.user_id FOR UPDATE', transaction)
        self.assertNotIn('FROM users WHERE DATE_ADD(created_at, INTERVAL 5 YEAR) <= CURRENT_TIMESTAMP(6) ORDER BY user_id FOR UPDATE', transaction)
        self.assertIn('COMMIT;', transaction)


if __name__ == '__main__':unittest.main()

import unittest
from retention import unexpired_receipts
class ReceiptDates(unittest.TestCase):
    def test_leap_day_expires_on_calendar_anniversary(self):
        values={'a':'2024-02-29 12:00:00'}
        self.assertEqual(values,unexpired_receipts(values,'2029-02-28 11:59:59.999999'))
        self.assertEqual({},unexpired_receipts(values,'2029-02-28 12:00:00'))
    def test_each_receipt_keeps_its_own_collection_time(self):
        values={'old':'2021-11-01 09:00:00','recent':'2026-11-01 09:00:00'}
        self.assertEqual({'recent':values['recent']},unexpired_receipts(values,'2026-11-01 09:00:00'))
        with self.assertRaises(ValueError):unexpired_receipts({'bad':'not-a-date'},'2026-11-01 09:00:00')
if __name__=='__main__':unittest.main()

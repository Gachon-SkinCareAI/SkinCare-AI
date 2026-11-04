"""Regression for Windows-prepared secrets consumed by the official MySQL image."""
from pathlib import Path
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]/'mysql'))
from db_runtime import root_password, option_password
from prepare_bundle import write_secret

class SecretFileTests(unittest.TestCase):
    def test_new_secret_uses_lf_bytes_on_every_host(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'secret'
            write_secret(path, 'a'*64)
            self.assertEqual(path.read_bytes(), b'a'*64+b'\n')
            self.assertEqual(root_password(path), 'a'*64)

    def test_existing_crlf_matches_mysql_file_env_without_password_rotation(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'secret'
            path.write_bytes(b'b'*64+b'\r\n')
            self.assertEqual(root_password(path), 'b'*64+'\r')
            self.assertEqual(option_password(root_password(path)), 'b'*64+'\\r')
            self.assertEqual(path.read_bytes(), b'b'*64+b'\r\n')

    def test_unexpected_secret_format_is_rejected(self):
        with tempfile.TemporaryDirectory() as folder:
            path=Path(folder)/'secret'
            for value in (b'', b'a'*63+b'\n', b'a'*64+b'\nextra', b'a'*64+b'\r\r\n'):
                path.write_bytes(value)
                with self.assertRaises(RuntimeError):
                    root_password(path)

if __name__=='__main__':
    unittest.main()

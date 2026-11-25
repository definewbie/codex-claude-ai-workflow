"""Exercise the real frozen-input consumer without changing installed bytes."""
import unittest
from pathlib import Path
from unittest.mock import patch

import gate_support as gate


class ObservedSdkIdentityTest(unittest.TestCase):
    def test_observed_binary_and_package_are_consumed_with_current_bytes(self):
        sdk = gate.read(gate.OUT / 'observed-sdk-identity.json')
        identity = gate.read(gate.OUT / 'launch-identity.json')
        for item in [sdk['observed_sdk_executable'], sdk['package']]:
            self.assertEqual(identity['config_and_installed_sources'][item['path']], item['sha256'])
        gate.frozen_check()

    def test_either_sdk_input_drift_is_rejected_by_the_actual_frozen_check(self):
        sdk = gate.read(gate.OUT / 'observed-sdk-identity.json')
        real_sha = gate.sha
        for item in [sdk['observed_sdk_executable'], sdk['package']]:
            with self.subTest(path=item['path']):
                def changed_sha(path):
                    return '0' * 64 if Path(path) == Path(item['path']) else real_sha(path)
                with patch.object(gate, 'sha', side_effect=changed_sha):
                    with self.assertRaisesRegex(AssertionError, 'Selected client/settings drift'):
                        gate.frozen_check()


if __name__ == '__main__':
    unittest.main()

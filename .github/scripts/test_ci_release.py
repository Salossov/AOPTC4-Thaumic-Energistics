import unittest
from unittest.mock import patch
import ci

class PublicationBoundaryTests(unittest.TestCase):
    def test_local_act_cannot_publish(self):
        with patch.dict(ci.os.environ, {'ACT':'true'}), self.assertRaisesRegex(ValueError,'dry-run'):
            ci.publish(False)
    def test_pull_request_cannot_publish(self):
        with patch.dict(ci.os.environ, {'ACT':'false'}), patch.object(ci,'load',return_value={'mode':'build'}), self.assertRaisesRegex(ValueError,'Pull requests'):
            ci.publish(True)
    def test_unverified_artifacts_cannot_publish(self):
        with patch.dict(ci.os.environ, {'ACT':'false'}), patch.object(ci,'load',return_value={'mode':'release'}), patch.object(ci,'verified_dist',return_value=False), self.assertRaisesRegex(ValueError,'unverified'):
            ci.publish(True)

"""Exercise complete-pack readiness without downloading or publishing pack contents."""
import hashlib
import importlib.util
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).with_name('pack_source.py')


class PackSourceTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        self.payload = b'exact complete pack fixture'
        self.fixture = {
            'sourcePackSha256': hashlib.sha256(self.payload).hexdigest(),
            'overrides': [{'path': 'resourcepacks/private.zip', 'downloads': []}],
        }

    def command(self, *arguments, environment=None):
        fixture = self.root / 'fixture.json'
        fixture.write_text(json.dumps(self.fixture))
        output = self.root / 'output'
        summary = self.root / 'summary'
        env = dict(os.environ)
        env.pop('SLABBED_FULL_PACK', None)
        env.pop('SLABBED_FULL_PACK_URL', None)
        env.update(GITHUB_OUTPUT=str(output), GITHUB_STEP_SUMMARY=str(summary))
        env.update(environment or {})
        result = subprocess.run([sys.executable, str(SCRIPT), '--fixture', str(fixture), *arguments],
                                env=env, capture_output=True, text=True)
        return result, output.read_text() if output.exists() else '', summary.read_text() if summary.exists() else ''

    def module(self):
        spec = importlib.util.spec_from_file_location('pack_source', SCRIPT)
        module = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(module)
        return module

    def test_unavailable_pack_skips_only_compatibility_and_reports_missing_input(self):
        result, output, summary = self.command()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('ready=false', output)
        self.assertIn('not run', summary)
        self.assertIn('resourcepacks/private.zip', summary)
        self.assertNotIn('passed', summary)

    def test_public_fixture_is_ready_without_private_source(self):
        self.fixture['overrides'][0]['downloads'] = ['https://example.invalid/public.zip']
        result, output, _ = self.command()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('ready=true', output)

    def test_exact_local_pack_is_ready_and_reused(self):
        source = self.root / 'source.mrpack'
        source.write_bytes(self.payload)
        result, output, _ = self.command(environment={'SLABBED_FULL_PACK': str(source)})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('ready=true', output)
        resolved = self.module().resolve_source(self.fixture, self.root / 'unused', source_pack=source)
        self.assertEqual(resolved, source)
        self.assertFalse((self.root / 'unused').exists())

    def test_wrong_local_pack_fails_instead_of_skipping(self):
        source = self.root / 'wrong.mrpack'
        source.write_bytes(b'older pack')
        result, output, _ = self.command('--source-pack', str(source))
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('ready=true', output)
        self.assertIn('hash mismatch', result.stdout + result.stderr)

    def test_configured_https_source_is_ready_without_disclosing_url(self):
        url = 'https://example.invalid/private.mrpack?signature=do-not-print'
        result, output, summary = self.command(environment={'SLABBED_FULL_PACK_URL': url})
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn('ready=true', output)
        self.assertNotIn(url, result.stdout + result.stderr + summary)
        self.assertNotIn('do-not-print', result.stdout + result.stderr + summary)

    def test_invalid_source_url_fails_without_disclosing_it(self):
        result, output, summary = self.command('--source-url', 'http://example.invalid/?secret=do-not-print')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('ready=true', output)
        self.assertNotIn('do-not-print', result.stdout + result.stderr + summary)

    def test_malformed_ipv6_url_does_not_disclose_host_fragment(self):
        result, output, summary = self.command('--source-url', 'https://[secret=do-not-print]/')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('ready=true', output)
        self.assertNotIn('do-not-print', result.stdout + result.stderr + summary)

    def test_invalid_normalized_url_does_not_disclose_host_fragment(self):
        result, output, summary = self.command('--source-url', 'https://secret=do-not-print\uff0fexample.invalid/')
        self.assertNotEqual(result.returncode, 0)
        self.assertNotIn('ready=true', output)
        self.assertNotIn('do-not-print', result.stdout + result.stderr + summary)

    def test_download_is_hash_checked_before_installing(self):
        module = self.module()
        destination = self.root / 'download.mrpack'
        with patch.object(module.urllib.request, 'urlopen', return_value=io.BytesIO(self.payload)):
            actual = module.resolve_source(self.fixture, destination, source_url='https://example.invalid/pack')
        self.assertEqual(actual, destination)
        self.assertEqual(destination.read_bytes(), self.payload)

    def test_wrong_download_preserves_existing_file_and_removes_partial_copy(self):
        module = self.module()
        destination = self.root / 'download.mrpack'
        destination.write_bytes(b'preserve existing file')
        with patch.object(module.urllib.request, 'urlopen', return_value=io.BytesIO(b'wrong archive')):
            with self.assertRaisesRegex(ValueError, 'hash mismatch'):
                module.resolve_source(self.fixture, destination, source_url='https://example.invalid/pack')
        self.assertEqual(destination.read_bytes(), b'preserve existing file')
        self.assertFalse(destination.with_suffix('.partial').exists())

    def test_download_error_does_not_disclose_signed_url(self):
        module = self.module()
        with patch.object(module.urllib.request, 'urlopen', side_effect=OSError('secret=do-not-print')):
            with self.assertRaises(RuntimeError) as failure:
                module.resolve_source(self.fixture, self.root / 'download', source_url='https://example.invalid/pack')
        self.assertNotIn('do-not-print', str(failure.exception))


if __name__ == '__main__':
    unittest.main()

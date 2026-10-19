"""Check complete-pack inputs separately from build and gameplay verification."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import urllib.parse
import urllib.request


def digest(path):
    result = hashlib.sha256()
    with Path(path).open('rb') as stream:
        while chunk := stream.read(1024 * 1024):
            result.update(chunk)
    return result.hexdigest()


def inspect_source(fixture, source_pack=None, source_url=None):
    if source_pack:
        source = Path(source_pack)
        if not source.is_file():
            raise ValueError('Configured complete pack file is unavailable')
        if digest(source) != fixture['sourcePackSha256']:
            raise ValueError('Complete pack source hash mismatch')
        return True, []
    if source_url:
        try:
            url = urllib.parse.urlsplit(source_url)
        except ValueError:
            raise ValueError('Configured complete pack source URL is invalid') from None
        if url.scheme != 'https' or not url.netloc:
            raise ValueError('Configured complete pack source must use HTTPS')
        return True, []
    missing = [entry['path'] for entry in fixture.get('overrides', []) if not entry.get('downloads')]
    return not missing, missing


def resolve_source(fixture, destination, source_pack=None, source_url=None):
    ready, missing = inspect_source(fixture, source_pack, source_url)
    if not ready:
        raise ValueError('Exact complete pack files unavailable; provide --source-pack: ' + ', '.join(missing))
    if source_pack:
        return Path(source_pack)
    if not source_url:
        return None
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    partial = destination.with_suffix('.partial')
    try:
        try:
            with urllib.request.urlopen(source_url, timeout=120) as response, partial.open('wb') as output:
                while chunk := response.read(1024 * 1024):
                    output.write(chunk)
        except Exception:
            # Download exceptions can contain signed source URLs. Keep them out of public logs.
            raise RuntimeError('Configured complete pack source could not be downloaded') from None
        if digest(partial) != fixture['sourcePackSha256']:
            raise ValueError('Complete pack source hash mismatch')
        partial.replace(destination)
        return destination
    finally:
        partial.unlink(missing_ok=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--fixture', type=Path, default=Path('tools/full-pack-fixture.json'))
    parser.add_argument('--source-pack', type=Path, default=os.environ.get('SLABBED_FULL_PACK'))
    parser.add_argument('--source-url', default=os.environ.get('SLABBED_FULL_PACK_URL'))
    args = parser.parse_args()
    fixture = json.loads(args.fixture.read_text())
    try:
        ready, missing = inspect_source(fixture, args.source_pack, args.source_url)
    except (OSError, ValueError) as error:
        print('::error::' + str(error))
        return 1
    if output := os.environ.get('GITHUB_OUTPUT'):
        with Path(output).open('a') as stream:
            stream.write('ready=' + str(ready).lower() + '\n')
    if ready:
        message = 'Full-pack source inputs configured; compatibility staging and gameplay checks must still pass.'
    else:
        message = ('Full-pack gameplay checks not run: exact private source inputs are unavailable. '
                   'Server, Indigo and Sodium validation run independently. Missing files:\n\n'
                   + '\n'.join('- `' + path + '`' for path in missing)
                   + '\n\nSupply the exact pack using SLABBED_FULL_PACK locally or the SLABBED_FULL_PACK_URL repository secret in Actions.')
        print('::notice::Full-pack gameplay checks not run: exact private source inputs are unavailable.')
    print(message)
    if summary := os.environ.get('GITHUB_STEP_SUMMARY'):
        with Path(summary).open('a') as stream:
            stream.write('## Complete modpack compatibility\n\n' + message + '\n')
    return 0


if __name__ == '__main__':
    raise SystemExit(main())

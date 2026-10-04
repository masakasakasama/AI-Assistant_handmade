"""Fail the release if large payloads stop being compressed or the download regresses."""
import sys
import zipfile
from pathlib import Path

apk = Path(sys.argv[1])
with zipfile.ZipFile(apk) as archive:
    payloads = [entry for entry in archive.infolist()
                if entry.filename.endswith('.dex') or entry.filename.startswith('lib/') and entry.filename.endswith('.so')]
    assert payloads, 'No APK executable payloads found'
    assert all(entry.compress_type == zipfile.ZIP_DEFLATED for entry in payloads), 'APK executables must be compressed'
    architectures = {entry.filename.split('/')[1] for entry in payloads if entry.filename.startswith('lib/')}
    assert architectures == {'arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64'}, 'Supported architectures changed'
assert apk.stat().st_size < 90_000_000, 'APK download exceeds the 90 MB release budget'
print(f'Compressed APK: {apk.stat().st_size / 1_000_000:.2f} MB; all four architectures retained')

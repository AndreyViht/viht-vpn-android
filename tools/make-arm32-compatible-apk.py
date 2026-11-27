"""Package the verified app's unchanged ARM32 payload; sign only in GitHub Actions."""
import hashlib
import json
import re
import struct
import sys
import zipfile


def signature(name):
    return re.fullmatch(r'META-INF/(?:MANIFEST\.MF|[^/]+\.(?:SF|RSA|DSA|EC))', name, re.I)


def retain(name):
    return not signature(name) and (not name.startswith('lib/') or name.startswith('lib/armeabi-v7a/'))


def verify(source, result):
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(result) as output:
        expected = {n for n in original.namelist() if not n.endswith('/') and retain(n)}
        actual = {n for n in output.namelist() if not n.endswith('/') and not signature(n)}
        assert expected == actual, 'APK payload entries changed'
        for name in expected:
            assert original.read(name) == output.read(name), 'APK payload changed: ' + name
        libs = [n for n in actual if n.startswith('lib/') and n.endswith('.so')]
        assert libs and all(n.startswith('lib/armeabi-v7a/') for n in libs)
        assert {'libbox.so', 'libhev-socks5-tunnel.so'} <= {n.rsplit('/', 1)[1] for n in libs}
        for name in libs:
            elf = output.read(name)
            assert elf[:5] == b'\x7fELF\x01' and struct.unpack_from('<H', elf, 18)[0] == 40
        assert output.testzip() is None, 'Corrupt APK entry'
        return {'abis': ['armeabi-v7a'], 'unchanged_payload_entries': len(actual), 'native_libraries': sorted(libs)}


if sys.argv[1] == '--verify':
    print(json.dumps(verify(sys.argv[2], sys.argv[3])))
else:
    source, target = sys.argv[1:]
    with zipfile.ZipFile(source) as original, zipfile.ZipFile(target, 'w') as output:
        for entry in original.infolist():
            if retain(entry.filename):
                output.writestr(entry, original.read(entry.filename), compress_type=entry.compress_type, compresslevel=9)
    print(json.dumps(verify(source, target)))

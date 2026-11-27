"""Check universal compressed native packaging without changing any ELF binary."""
import sys, struct, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    libraries=[x for x in archive.infolist() if x.filename.startswith('lib/') and x.filename.endswith('.so')]
    expected={'armeabi-v7a':(1,40), 'arm64-v8a':(2,183), 'x86':(1,3), 'x86_64':(2,62)}
    assert {x.filename.split('/')[1] for x in libraries}==set(expected), 'Missing universal ABI'
    for abi in expected:
        names={x.filename.rsplit('/',1)[1] for x in libraries if x.filename.split('/')[1]==abi}
        assert {'libbox.so','libhev-socks5-tunnel.so'}<=names, 'Missing VPN runtime for '+abi
        if abi in {'arm64-v8a','x86_64'}:
            assert 'libvihtxray.so' in names, 'Missing existing Xray runtime for '+abi
    for entry in libraries:
        assert entry.compress_type==zipfile.ZIP_DEFLATED, 'Native library is not compressed: '+entry.filename
        abi=entry.filename.split('/')[1]
        elf=archive.read(entry)
        bits,machine=expected[abi]
        assert elf[:5]==b'\x7fELF'+bytes([bits]) and struct.unpack_from('<H',elf,18)[0]==machine, 'Wrong ELF architecture: '+entry.filename
        start=struct.unpack_from('<Q' if bits==2 else '<I',elf,32 if bits==2 else 28)[0]
        size,count=struct.unpack_from('<HH',elf,54 if bits==2 else 42)
        for index in range(count):
            offset=start+size*index
            if struct.unpack_from('<I',elf,offset)[0]==1:
                alignment=struct.unpack_from('<Q' if bits==2 else '<I',elf,offset+(48 if bits==2 else 28))[0]
                assert alignment>=(16384 if bits==2 else 4096), 'Invalid ELF LOAD alignment: '+entry.filename
    print('PASS: ARM32/ARM64/x86/x86_64 runtimes, compressed native libraries, valid ELF alignment')

"""Check universal compressed native packaging without changing any ELF binary."""
import sys, struct, zipfile
with zipfile.ZipFile(sys.argv[1]) as archive:
    libraries=[x for x in archive.infolist() if x.filename.startswith('lib/') and x.filename.endswith('.so')]
    assert {x.filename.split('/')[1] for x in libraries}=={'arm64-v8a','armeabi-v7a','x86','x86_64'}, 'Missing universal ABI'
    for entry in libraries:
        assert entry.compress_type==zipfile.ZIP_DEFLATED, 'Native library is not compressed: '+entry.filename
        if entry.filename.split('/')[1] not in {'arm64-v8a','x86_64'}: continue
        elf=archive.read(entry)
        assert elf[:5]==b'\x7fELF\x02', 'Expected ELF64'
        start=struct.unpack_from('<Q',elf,32)[0]
        size,count=struct.unpack_from('<HH',elf,54)
        for index in range(count):
            offset=start+size*index
            if struct.unpack_from('<I',elf,offset)[0]==1:
                assert struct.unpack_from('<Q',elf,offset+48)[0]>=16384, 'ELF LOAD alignment below 16 KB: '+entry.filename
    print('PASS: all four ABIs, compressed native libraries, unchanged 16 KB ELF LOAD alignment')

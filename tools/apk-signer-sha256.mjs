import fs from 'node:fs';
import { createHash } from 'node:crypto';
const data = fs.readFileSync(process.argv[2]);
const eocd = data.lastIndexOf(Buffer.from([0x50,0x4b,0x05,0x06]));
if (eocd < 0) throw Error('Missing APK ZIP directory');
const central = data.readUInt32LE(eocd + 16);
if (data.subarray(central-16, central).toString() !== 'APK Sig Block 42') throw Error('Missing APK signing block');
const start = central - Number(data.readBigUInt64LE(central-24)) - 8;
function lp(bytes, offset=0) { const end=offset+4+bytes.readUInt32LE(offset); if(end>bytes.length)throw Error('Truncated signing data'); return [bytes.subarray(offset+4,end),end]; }
let digest;
for (let offset=start+8; offset<central-24;) {
  const length=Number(data.readBigUInt64LE(offset)), id=data.readUInt32LE(offset+8);
  if(id===0x7109871a) {
    const [signers]=lp(data.subarray(offset+12,offset+8+length)), [signer]=lp(signers), [signed]=lp(signer);
    const [,next]=lp(signed), [certificates]=lp(signed,next), [certificate]=lp(certificates);
    digest=createHash('sha256').update(certificate).digest('hex'); break;
  }
  offset+=8+length;
}
if(!digest)throw Error('Missing v2 certificate');
console.log(digest);

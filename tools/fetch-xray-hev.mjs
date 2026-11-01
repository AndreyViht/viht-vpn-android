import fs from 'node:fs';
import path from 'node:path';
import crypto from 'node:crypto';
import {execFileSync} from 'node:child_process';
const root=path.resolve(import.meta.dirname,'..');
const artifacts=[
 ['Xray-android-arm64-v8a.zip','https://github.com/XTLS/Xray-core/releases/download/v26.10.31/Xray-android-arm64-v8a.zip','ee7878bb47ce28ff5b068e2d173ba1a9fab20ca1aec94191644919ec08ba5f1d','arm64-v8a'],
 ['Xray-android-amd64.zip','https://github.com/XTLS/Xray-core/releases/download/v26.10.31/Xray-android-amd64.zip','077f2f768de2356cea4a32598d333837fe0fa33ab540c4d508d9f17e2eb9e5b4','x86_64'],
 ['hev-socks5-tunnel.aar','https://github.com/heiher/hev-socks5-tunnel/releases/download/2.19.3/hev-socks5-tunnel.aar','ab6c581e6157680cfe5687ee7bbdcf95405c006c2db3701879ec44b9e5f83836',null]
];
fs.mkdirSync(path.join(root,'.native-cache'),{recursive:true});
for(const [name,url,sha,abi] of artifacts){
 const file=path.join(root,'.native-cache',name);
 const valid=()=>fs.existsSync(file)&&crypto.createHash('sha256').update(fs.readFileSync(file)).digest('hex')===sha;
 if(!valid()){
  let bytes;
  for(let attempt=0;attempt<3;attempt++){try{const response=await fetch(url);if(!response.ok)throw Error('HTTP '+response.status);bytes=Buffer.from(await response.arrayBuffer());break;}catch(e){if(attempt===2)throw e;}}
  if(crypto.createHash('sha256').update(bytes).digest('hex')!==sha)throw Error('Native dependency checksum mismatch: '+name);
  fs.writeFileSync(file,bytes);
 }
 if(abi){
  const destination=path.join(root,'app/src/main/jniLibs',abi,'libvihtxray.so');
  execFileSync(process.env.VIHT_PYTHON||'python3',['-c',
   'import zipfile,sys,pathlib; z=zipfile.ZipFile(sys.argv[1]); names=[n for n in z.namelist() if pathlib.PurePosixPath(n).name=="xray"]; assert len(names)==1; p=pathlib.Path(sys.argv[2]); p.parent.mkdir(parents=True,exist_ok=True); p.write_bytes(z.read(names[0]))',file,destination]);
 }else fs.copyFileSync(file,path.join(root,'app/libs/hev-socks5-tunnel.aar'));
 console.log('Verified '+name);
}

import fs from 'node:fs';import path from 'node:path';import crypto from 'node:crypto';
const root=path.resolve(import.meta.dirname,'..');fs.mkdirSync(path.join(root,'app/libs'),{recursive:true});
for(const [name,url,hash]of[
['libv2ray.aar','https://github.com/2dust/AndroidLibXrayLite/releases/download/v26.10.31/libv2ray.aar','2efdf731b1ed624eb1cb0d5a4d0aa95c0672e89f9702226d1ba2dc90308630b6'],
['hev-socks5-tunnel.aar','https://github.com/heiher/hev-socks5-tunnel/releases/download/2.19.3/hev-socks5-tunnel.aar','ab6c581e6157680cfe5687ee7bbdcf95405c006c2db3701879ec44b9e5f83836']]){
const p=path.join(root,'app/libs',name);const digest=b=>crypto.createHash('sha256').update(b).digest('hex');
if(!fs.existsSync(p)||digest(fs.readFileSync(p))!==hash){const r=await fetch(url);if(!r.ok)throw Error('Native dependency HTTP '+r.status);const b=Buffer.from(await r.arrayBuffer());if(digest(b)!==hash)throw Error('Native digest mismatch: '+name);fs.writeFileSync(p,b)}console.log('Verified '+name);
}

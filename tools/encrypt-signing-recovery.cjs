// One-time recovery artifact is encrypted to the operator's public key.
// No unencrypted signing material is printed or uploaded.
const fs=require('fs'),crypto=require('crypto');
const key=crypto.randomBytes(32),iv=crypto.randomBytes(12);
const cipher=crypto.createCipheriv('aes-256-gcm',key,iv);
const content=Buffer.concat([cipher.update(fs.readFileSync(process.env.VIHT_TEST_KEYSTORE)),cipher.final()]);
const encryptedKey=crypto.publicEncrypt({key:fs.readFileSync('.github/signing-recovery-public.pem'),oaepHash:'sha256'},key);
fs.writeFileSync('signing-recovery.json',JSON.stringify({key:encryptedKey.toString('base64'),iv:iv.toString('base64'),tag:cipher.getAuthTag().toString('base64'),content:content.toString('base64')}));

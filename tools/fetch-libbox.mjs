import { createHash } from 'node:crypto';
import { createReadStream, createWriteStream } from 'node:fs';
import { stat, rename, rm } from 'node:fs/promises';
import { Readable } from 'node:stream';
import { pipeline } from 'node:stream/promises';
const path = 'app/libs/libbox.aar';
const oid = '3a706062e1b3e804ca7f7abbdef523a05c4ad7c125ab1496e2b0a52a62c360a5';
const size = 92997417;
async function valid(file) {
  if ((await stat(file).catch(() => null))?.size !== size) return false;
  const hash = createHash('sha256');
  for await (const chunk of createReadStream(file)) hash.update(chunk);
  return hash.digest('hex') === oid;
}
if (!await valid(path)) {
  const response = await fetch('https://github.com/scripchenko/pingwin.git/info/lfs/objects/batch', {
    method: 'POST', headers: { Accept: 'application/vnd.git-lfs+json', 'Content-Type': 'application/vnd.git-lfs+json' },
    body: JSON.stringify({ operation: 'download', transfers: ['basic'], objects: [{ oid, size }] }), signal: AbortSignal.timeout(30000)
  });
  if (!response.ok) throw new Error('LFS metadata: HTTP ' + response.status);
  const action = (await response.json()).objects?.[0]?.actions?.download;
  if (!action?.href?.startsWith('https://')) throw new Error('Missing secure LFS download');
  const temporary = path + '.download';
  try {
    const download = await fetch(action.href, { headers: action.header, signal: AbortSignal.timeout(300000) });
    if (!download.ok) throw new Error('LFS download: HTTP ' + download.status);
    await pipeline(Readable.fromWeb(download.body), createWriteStream(temporary));
    if (!await valid(temporary)) throw new Error('libbox size or SHA-256 mismatch');
    await rename(temporary, path);
  } finally { await rm(temporary, { force: true }); }
}
console.log('libbox: pinned size and SHA-256 verified');

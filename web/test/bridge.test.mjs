import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
const source = fs.readFileSync(new URL('../public/android-bridge.js', import.meta.url), 'utf8');
function fixture() {
  const requests = [], timers = new Map(); let next = 0;
  const window = { VihtNative: { request: (...args) => requests.push(args) }, addEventListener() {} };
  vm.runInNewContext(source, { window, localStorage: { getItem: () => null }, setTimeout: fn => { timers.set(++next, fn); return next; }, clearTimeout: id => timers.delete(id), queueMicrotask });
  return { window, requests, timers };
}
test('RPC matches concurrent responses and releases timers', async () => {
  const {window:w,requests:r,timers} = fixture();
  const first = w.electronAPI.getVpnStatus(), second = w.electronAPI.getAppVersion();
  assert.notEqual(r[0][0], r[1][0]);
  w.__vihtNativeCallback(r[1][0], '1.4.0'); w.__vihtNativeCallback(r[0][0], {state:'connected'});
  assert.equal(await second, '1.4.0'); assert.equal((await first).state, 'connected'); assert.equal(timers.size,0);
});
test('late RPC response cannot resolve a timed-out request', async () => {
  const {window:w,requests:r,timers} = fixture();
  const pending = w.electronAPI.pingServers([]); const rejected = assert.rejects(pending, /Android/);
  [...timers.values()][0](); w.__vihtNativeCallback(r[0][0], {fake:1}); await rejected;
});
test('OAuth event arriving before React subscribes is delivered once', async () => {
  const {window:w} = fixture(); let count=0;
  w.__vihtNativeEvent('auth-success',{token:'test'});
  const unsubscribe=w.electronAPI.onAuthSuccess(data => {assert.equal(data.token,'test');count++;});
  await Promise.resolve(); unsubscribe(); w.__vihtNativeEvent('auth-success',{token:'test'}); assert.equal(count,1);
});

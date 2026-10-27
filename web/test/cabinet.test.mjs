import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source = fs.readFileSync(new URL('../src/services/api.js', import.meta.url), 'utf8')
  .replace(/^import .*;\r?\n/gm, '').replace(/^export \{.*;\r?\n/gm, '').replace(/export async function/g, 'async function');
function fixture(reply) {
  const calls=[], events=[];
  const window={electronAPI:{cabinetRequest:async options=>{calls.push(options);return reply;}},dispatchEvent:e=>events.push(e.type)};
  vm.runInNewContext(source+'\nwindow.testApi={fetchCabinetProfile,fetchActiveDevices,deviceAction};',{
    window,fetch:()=>{throw Error('Android must not use WebView fetch');},CustomEvent:class{constructor(type){this.type=type;}}
  });
  return {api:window.testApi,calls,events};
}
test('profile and devices use native Android HTTP without browser fetch',async()=>{
  const {api,calls}=fixture({ok:true,data:{profile:{id:'owner'},devices:[]}});
  assert.equal((await api.fetchCabinetProfile('unused','phone',{includeKeys:false})).data.profile.id,'owner');
  await api.fetchActiveDevices('unused','phone');
  assert.deepEqual(calls.map(x=>x.action),['profile','active-devices']);
});
test('device mutations are forwarded once with their body',async()=>{
  const {api,calls}=fixture({ok:true,data:{ok:true}});
  await api.deviceAction('unused',{action:'revoke',device_id:'sample'});
  assert.equal(calls.length,1); assert.equal(calls[0].method,'POST');assert.equal(calls[0].body.device_id,'sample');
});
test('expired native session returns user to sign-in',async()=>{
  const {api,events}=fixture({ok:false,unauthorized:true,error:'Session expired'});
  assert.equal((await api.fetchActiveDevices('unused')).unauthorized,true);
  assert.deepEqual(events,['viht-auth-required']);
});

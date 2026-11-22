import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import vm from 'node:vm';
const source=fs.readFileSync(new URL('../src/services/api.js',import.meta.url),'utf8').replace(/^import .*;\r?\n/gm,'').replace(/^export \{.*;\r?\n/gm,'').replace(/export async function/g,'async function');
test('slow native subscription result is accepted beyond the old 12-second cutoff',async()=>{
 let complete,delay,cleared=false;
 const window={electronAPI:{fetchSubscription:()=>new Promise(r=>complete=r)}};
 vm.runInNewContext(source+'\nwindow.fetchKeys=fetchSubscriptionClients;',{
  window,localStorage:{getItem:()=> 'owner'},setTimeout:(fn,ms)=>{delay=ms;return 1},clearTimeout:()=>{cleared=true},parseSubscriptionBody:body=>[body],
 });
 const pending=window.fetchKeys('subscription');assert(delay>12000&&delay<80000);
 complete({ok:true,body:'three valid keys'});const r=await pending;assert.equal(r.ok,true);assert.equal(r.clients[0],'three valid keys');assert(cleared);
});

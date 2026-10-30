import test from 'node:test';
import assert from 'node:assert/strict';
import {restoreStoredSession} from '../src/services/sessionRestore.js';

test('cold owner restore is local and accepts expired access token for subsequent native refresh',async()=>{
 const token='header.expired.signature';let reads=0;
 const bridge={getStoredOwnerSession:async()=>{reads++;return{ok:true,hasSession:true,token,provider:'vk'}},getOwnerSession:()=>{throw Error('network refresh must not gate restoring UI')}};
 assert.deepEqual(await restoreStoredSession(bridge),{token,provider:'vk'});assert.equal(reads,1);
});
test('guest restore does not need account login or browser storage',async()=>{
 const token='vg_'+'a'.repeat(64);assert.deepEqual(await restoreStoredSession({getStoredOwnerSession:async()=>({ok:true,hasSession:true,token,provider:'guest'})}),{token,provider:'guest'});
});
test('missing native session and unavailable encrypted session are distinct',async()=>{
 assert.equal(await restoreStoredSession({getStoredOwnerSession:async()=>({ok:true,hasSession:false})}),null);
 await assert.rejects(restoreStoredSession({getStoredOwnerSession:async()=>({ok:false,error:'KeyStore temporarily unavailable'})}),/KeyStore/);
 await assert.rejects(restoreStoredSession({getStoredOwnerSession:async()=>({ok:true,hasSession:true})}),/сохранённый вход/);
});

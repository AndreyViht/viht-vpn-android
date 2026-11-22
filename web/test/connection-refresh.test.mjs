import test from 'node:test';
import assert from 'node:assert/strict';
import {freshConnectionTarget} from '../src/services/connectionRefresh.mjs';
test('ordinary connect obtains new transport for the selected location without manual sync',async()=>{
 const old={name:'Нидерланды',vless_uri:'cached-ws'},fresh={name:'Нидерланды',vless_uri:'new-reality'};let called=0;
 const result=await freshConnectionTarget(old,async()=>{called++;return [{name:'Германия',vless_uri:'de'},fresh]},()=>true);
 assert.equal(called,1);assert.equal(result.server,fresh);
});
test('temporary sync failure keeps working cached key; confirmed empty subscription does not',async()=>{
 const cached={name:'Германия',vless_uri:'cached'};
 assert.equal((await freshConnectionTarget(cached,async()=>null,()=>true)).server,cached);
 assert.equal((await freshConnectionTarget(cached,async()=>[],()=>true)).server,undefined);
});
test('logout or changed connection intent cancels a delayed refresh',async()=>{
 const result=await freshConnectionTarget({vless_uri:'old'},async()=>[{vless_uri:'new'}],()=>false);
 assert.equal(result.cancelled,true);assert.equal(result.server,undefined);
});

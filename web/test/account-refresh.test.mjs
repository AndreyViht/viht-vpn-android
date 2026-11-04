import test from 'node:test';
import assert from 'node:assert/strict';
import {observeAccount,accountRevision} from '../src/services/accountRefresh.mjs';
test('account updates after Android resume and visible polling, without concurrent requests',async()=>{
 const win=new EventTarget(),doc=new EventTarget();doc.hidden=false;let clock=0,calls=0,complete,resume,tick;
 const stop=observeAccount({token:'owner',window:win,document:doc,now:()=>clock,setTimer:fn=>{tick=fn;return 1},clearTimer:()=>{},bridge:{onAppResumed:fn=>{resume=fn;return()=>{}}},refresh:()=>{calls++;return new Promise(resolve=>{complete=resolve})}});
 const first=resume();win.dispatchEvent(new Event('focus'));assert.equal(calls,1);complete();await first;clock=2000;
 const second=tick();assert.equal(calls,2);complete();await second;doc.hidden=true;clock=4000;await tick();assert.equal(calls,2);stop();
});
test('subscription renewal and slot purchase change the account revision',()=>{
 const before={id:'owner',expires_at:'2026-11-01',limits:{total_slots:4}};
 assert.notEqual(accountRevision(before),accountRevision({...before,expires_at:'2027-11-01'}));
 assert.notEqual(accountRevision(before),accountRevision({...before,limits:{total_slots:6}}));
});

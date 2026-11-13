import test from 'node:test';
import assert from 'node:assert/strict';
import {slotUsage, deviceAppLabel, deviceState, devicePresenceText} from '../src/services/devices.js';
test('slot usage is independent of the visible record count',()=>{
 const rows=[...Array.from({length:15},()=>({})),{slot_exempt:true},{slot_exempt:true}];
 assert.equal(slotUsage(rows,{active_count:15,total_slots:16}).used,15);
 assert.equal(slotUsage(rows,{}).used,15);
 assert.equal(slotUsage(rows,{active_count:0,total_slots:0}).used,0);
 assert.equal(slotUsage(rows,{total_slots:0}).total,0);
 assert.equal(slotUsage(rows,{}).total,null);
});
test('unknown presence is not presented as offline',()=>{
 assert.equal(deviceState({online:false,online_known:false}),'unknown');
 assert.equal(deviceState({online:false,online_known:true}),'offline');
 assert.equal(deviceState({online:true,disabled:true}),'offline');
});
test('Windows and Android Viht clients keep their actual platform',()=>{
 assert.match(deviceAppLabel({app_name:'Viht Desktop',platform:'windows',app_version:'1.3.2'}),/Windows v1.3.2/);
 assert.match(deviceAppLabel({app_name:'Viht Android',platform:'android'}),/Android/);
});

test('device history explains stale status without inventing a current connection', () => {
  const now = Date.parse('2026-11-13T10:00:00Z');
  const last = '2026-11-13T09:55:00Z';
  assert.equal(devicePresenceText({online:false,online_known:false,last_vpn_activity_at:last},'ru',now), 'VPN работал 5 мин. назад');
  assert.equal(devicePresenceText({online:true,last_vpn_activity_at:last},'ru',now), 'VPN подключён');
  assert.equal(devicePresenceText({disabled:true,online:true},'ru',now), 'Доступ отключён');
  assert.equal(devicePresenceText({online:false,online_known:false,last_seen:last},'ru',now), 'Приложение открывали 5 мин. назад');
  assert.equal(devicePresenceText({online:false,online_known:false},'ru',now), 'Ожидаем связи с приложением');
});


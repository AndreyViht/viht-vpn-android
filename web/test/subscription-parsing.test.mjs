import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
const source = fs.readFileSync(new URL('../src/services/servers.js', import.meta.url), 'utf8');
const {parseSubscriptionBody, parseApiClients} = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

test('subscription representations preserve the same name, endpoint and original VLESS URI', () => {
  const uri = 'vless://b281630d-8173-4dfa-81d1-de082e5bf313@[2001:db8::1]:8443?type=grpc&serviceName=vpn&security=reality&sni=edge.example&pbk=public&sid=ab#' + encodeURIComponent('🇸🇪 Стокгольм');
  const feeds = [uri, Buffer.from(uri).toString('base64'), JSON.stringify([{vless_uri:uri}]), JSON.stringify([{uri,name:'🇸🇪 Стокгольм'}])];
  const results = feeds.map(feed => parseApiClients(parseSubscriptionBody(feed))[0]);
  for (const server of results) {
    assert.equal(server.name, '🇸🇪 Стокгольм');
    assert.equal(server.host, '2001:db8::1');
    assert.equal(server.port, 8443);
    assert.equal(server.vless_uri, uri);
    assert.equal(server.id, results[0].id);
  }
});

test('Xray subscription conversion retains transport and Reality settings', () => {
  const config = {
    remarks:'Germany',
    outbounds:[{
      protocol:'vless',
      settings:{vnext:[{address:'edge.example',port:8443,users:[{id:'b281630d-8173-4dfa-81d1-de082e5bf313',encryption:'none',flow:'xtls-rprx-vision'}]}]},
      streamSettings:{network:'grpc',security:'reality',grpcSettings:{serviceName:'vpn'},realitySettings:{serverName:'sni.example',fingerprint:'chrome',publicKey:'public',shortId:'ab',spiderX:'/path'}}
    }]
  };
  const [server] = parseApiClients(parseSubscriptionBody(JSON.stringify([config])));
  const uri = new URL(server.vless_uri);
  assert.equal(server.name, 'Germany');
  for (const [key,value] of Object.entries({type:'grpc',security:'reality',serviceName:'vpn',sni:'sni.example',fp:'chrome',pbk:'public',sid:'ab',spx:'/path',flow:'xtls-rprx-vision'})) {
    assert.equal(uri.searchParams.get(key), value);
  }
});

test('a fresh subscription removes obsolete keys and filters notices and disabled entries', () => {
  const key = 'vless://id@edge.example:443?security=tls#Location';
  const first = parseApiClients([{vless_uri:key}]);
  const changed = key.replace(':443', ':8443');
  const next = parseApiClients([{vless_uri:changed},{vless_uri:changed},{vless_uri:key,enabled:false},{vless_uri:'vless://notice@127.0.0.1:443#Notice'}]);
  assert.equal(next.length, 1);
  assert.notEqual(next[0].id, first[0].id);
  assert.equal(next[0].port, 8443);
});

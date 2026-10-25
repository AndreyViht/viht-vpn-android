import test from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
test('Android validates and refreshes sessions through the Viht domain', () => {
  const config = JSON.parse(fs.readFileSync(new URL('../../app/src/main/assets/auth-config.json', import.meta.url), 'utf8'));
  assert.equal(config.authUrl, 'https://anviht.ru/auth');
  const source = fs.readFileSync(new URL('../../app/src/main/java/com/pingwin/vpn/AndroidSessionStore.kt', import.meta.url), 'utf8');
  assert.ok(source.includes('authRequest(configuration.getString("authUrl") + "/user"'));
  assert.ok(source.includes('authRequest(configuration.getString("authUrl") + "/token?grant_type=refresh_token"'));
  assert.ok(!source.includes('supabase.co'));
});

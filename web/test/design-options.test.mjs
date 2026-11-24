import {strict as assert} from 'node:assert';
import {readFileSync} from 'node:fs';
import test from 'node:test';
import groups,{designAttributes} from '../src/components/designOptions.js';

test('all design controls map to scoped styles and keep the original appearance at default', () => {
  const options = groups.flatMap(([, , , items]) => items);
  assert.ok(options.length > 20);
  assert.equal(new Set(options.map(([key]) => key)).size, options.length);
  const css = readFileSync(new URL('../src/components/DesignStudio.css', import.meta.url), 'utf8');
  for (const [key, , , ...variants] of options) {
    for (let i = 1; i <= variants.length; i++) {
      assert.deepEqual(designAttributes({[key]:i}), {['data-design-' + key]:i});
      assert.ok(css.includes(`[data-design-${key}="${i}"]`), key);
    }
    assert.deepEqual(designAttributes({[key]:0}), {});
    assert.deepEqual(designAttributes({[key]:variants.length + 1}), {});
  }
  assert.deepEqual(designAttributes(null), {});
  assert.deepEqual(designAttributes({unknown:1,'server-shape':'2'}), {});
});

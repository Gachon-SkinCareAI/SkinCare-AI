import { readFile } from 'node:fs/promises';
import { createHash } from 'node:crypto';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const storage = new Map(); let writes = 0, failWrite = -1;
globalThis.__draftTest = {
  Platform: { OS: 'android' },
  SecureStore: {
    WHEN_UNLOCKED_THIS_DEVICE_ONLY: 'device-only',
    getItemAsync: async key => storage.get(key) || null,
    deleteItemAsync: async key => { storage.delete(key); },
    setItemAsync: async (key, value, options) => {
      assert.equal(options.keychainAccessible, 'device-only');assert.ok(Buffer.byteLength(value, 'utf8') < 2048);
      if (++writes === failWrite) throw new Error('synthetic storage interruption');storage.set(key, value);
    },
  },
  Crypto: { CryptoDigestAlgorithm: { SHA256: 'sha256' }, digestStringAsync: async (_, value) => createHash('sha256').update(value).digest('hex') },
};
let source = await readFile(new URL('../src/stores/inquiryDraft.js', import.meta.url), 'utf8');
source = source.replace(/^import .*\r?\n/gm, '');
const mod = await import(`data:text/javascript;base64,${Buffer.from('const {Platform,SecureStore,Crypto}=globalThis.__draftTest;\n'+source).toString('base64')}`);
test('native drafts preserve Korean/emoji and remain separated across accounts and erasure', async () => {
  const draft = { title: '문의 초안', content: '가나다😀'.repeat(900), type: 'OTHER' };
  await mod.saveInquiryDraft('member-one', draft); assert.deepEqual(await mod.readInquiryDraft('member-one'), draft);
  assert.equal(await mod.readInquiryDraft('member-two'), null);
  await mod.saveInquiryDraft('member-two', { title: '다른 계정' });
  await mod.clearInquiryDraft('member-one'); assert.equal(await mod.readInquiryDraft('member-one'), null);
  assert.deepEqual(await mod.readInquiryDraft('member-two'), { title: '다른 계정' });
});
test('interrupted native draft write retains the last committed draft', async () => {
  const old = { title: '기존', content: '원래 내용' };
  await mod.saveInquiryDraft('interruption-account', old);failWrite=writes+3;
  await assert.rejects(mod.saveInquiryDraft('interruption-account', { content: '나'.repeat(4000) }), /storage interruption/);
  failWrite=-1;assert.deepEqual(await mod.readInquiryDraft('interruption-account'), old);
});

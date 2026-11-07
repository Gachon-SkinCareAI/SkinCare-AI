import { readFile } from 'node:fs/promises';
import { test } from 'node:test';
import assert from 'node:assert/strict';
const moduleFrom = async file => import(`data:text/javascript;base64,${Buffer.from(await readFile(new URL(file, import.meta.url),'utf8')).toString('base64')}`);
const { recordDate, sortRecords, symptomLabel } = await moduleFrom('../src/services/progress.js');
const { searchArticles } = await moduleFrom('../src/data/encyclopedia.js');
test('missing capture date and unanswered symptoms do not become observations', () => {
  assert.equal(recordDate({createdAt:'2026-11-06T12:00:00'}),'촬영일 미기록 · 등록 2026-11-06');
  assert.equal(symptomLabel(null),'미기록');assert.equal(symptomLabel('UNKNOWN'),'잘 모르겠음');assert.equal(symptomLabel('NONE'),'없음');
  const rows=[{id:'b',createdAt:'2026-11-06',capturedOn:'2026-11-01'},{id:'a',createdAt:'2026-11-05',capturedOn:'2026-11-02'}];
  assert.deepEqual(sortRecords(rows).map(r=>r.id),['b','a']);assert.equal(rows[0].id,'b');
});
test('encyclopedia combines query and category without inventing results', () => {
  assert.equal(searchArticles('멍','상처')[0].id,'bruise');assert.equal(searchArticles('화상','피부 질환').length,0);
  assert.equal(searchArticles('does-not-exist').length,0);assert.equal(searchArticles('','피부 질환').length,3);
});

import {test} from 'node:test';
import assert from 'node:assert/strict';
import {validateEntry,appendEntry,searchDiseases,searchFacilities} from '../src/utils/serviceState.js';
import {diseases,facilities} from '../src/data/servicesDemo.js';
const entry={photo:{uri:'file://photo',mimeType:'image/png'},date:'2026-09-28',symptoms:{통증:'줄었어요',붓기:'비슷해요',진물:'없음'}};
test('symptoms are required for followup but not for new wound',()=>{assert.equal(validateEntry({...entry,symptoms:{}},null),'');assert.match(validateEntry({...entry,symptoms:{}},{date:'2026-09-20'}),/모두 선택/);assert.equal(validateEntry(entry,{date:'2026-09-20'}),'');assert.match(validateEntry({...entry,date:'2026-09-01'},{startedAt:'2026-09-10'}),/최초 등록일/)});
test('followup retains symptoms and memo only on selected wound',()=>{const rs=[{id:'a',count:1},{id:'b',count:2}],data={date:entry.date,image:{uri:'file://photo'},symptoms:entry.symptoms,memo:'메모'};const out=appendEntry(rs,'b',data,'entry-1');assert.equal(out[0],rs[0]);assert.equal(out[1].updates[0].memo,'메모');assert.equal(out[1].updates[0].symptoms.통증,'줄었어요');assert.equal(out[1].count,3);assert.equal(rs[1].updates,undefined);const fresh=appendEntry(rs,null,data,'new');assert.equal(fresh[0].status,'분석 대기');assert.equal(fresh[0].demo,false)});
test('search combines category and multilingual title/tag matching',()=>{assert.equal(searchDiseases(diseases,'  BURN ','외상').length,2);assert.equal(searchDiseases(diseases,'#가려움','만성').length,1);assert.equal(searchDiseases(diseases,'없는질환','전체').length,0)});
test('facility tab data and open filter never mix',()=>{assert.equal(searchFacilities(facilities.pharmacy,'약국',true).length,3);assert.equal(searchFacilities(facilities.hospital,'약국',false).length,0)});

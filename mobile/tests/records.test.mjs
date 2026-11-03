import {test} from 'node:test';
import assert from 'node:assert/strict';
import {filterRecords,filterTimeline,areaChange} from '../src/utils/records.js';
test('completed and pending records use current state',()=>{const rs=[{id:1,complete:true},{id:2,status:'분석 대기'}];assert.equal(filterRecords(rs,'전체').length,2);assert.equal(filterRecords(rs,'진행 중')[0].id,2);assert.equal(filterRecords(rs,'회복 완료')[0].id,1)});
test('ranges cross year boundaries and exclude older entries',()=>{const entries=[{date:'2025-12-15',area:8},{date:'2026-02-20',area:6},{date:'2026-03-10',area:4}];assert.equal(filterTimeline(entries,'1개월').length,2);assert.equal(filterTimeline(entries,'3개월').length,3);assert.equal(areaChange(entries),50)});
test('missing or zero measurements never create fabricated improvement',()=>{assert.equal(areaChange([{area:0},{area:1}]),null);assert.equal(areaChange([{area:8},{date:'2026-03-10'}]),null);assert.equal(areaChange([{area:8},{area:0}]),100);assert.equal(areaChange([{area:4},{area:8}]),-100)});

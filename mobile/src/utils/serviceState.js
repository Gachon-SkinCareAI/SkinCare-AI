import {validateDate,validatePhoto} from './draft.js';
export const symptomNames=['통증','붓기','진물'];
export const symptomChoices=['없음','줄었어요','비슷해요','늘었어요'];
export function validateEntry({photo,date,symptoms},record){
 const message=validatePhoto(photo)||validateDate(date);if(message)return message;
 if(record){if(date<(record.startedAt||record.date))return '최초 등록일 이후의 날짜를 입력해주세요.';
 if(symptomNames.some(key=>!symptomChoices.includes(symptoms[key])))return '통증, 붓기, 진물의 변화를 모두 선택해주세요.';}
 return '';
}
export function searchDiseases(items,query,category){const q=query.trim().toLocaleLowerCase().replace(/^#/,'');return items.filter(d=>(category==='전체'||d.category===category)&&[d.name,d.english,d.description,...d.tags].join(' ').toLocaleLowerCase().includes(q));}
export function searchFacilities(items,query,onlyOpen){const q=query.trim().toLocaleLowerCase();return items.filter(p=>(!onlyOpen||p.open)&&[p.name,p.region,p.tag].join(' ').toLocaleLowerCase().includes(q));}
export function appendEntry(records,target,entry,id){
 if(target)return records.map(r=>r.id===target?{...r,count:(r.count||1)+1,updates:[{...entry,id},...(r.updates||[])]}:r);
 return [{id,title:'신규 상처',summary:'사진 등록 · 분석 대기',status:'분석 대기',date:entry.date,startedAt:entry.date,image:entry.image,demo:false,count:1,complete:false,...entry},...records];
}

// Figma list fixtures for UI development, not a reviewed medical knowledge base.
export const diseases=[
 ['contact','접촉성 피부염','Contact Dermatitis','염증','외부 물질 접촉으로 유발되는 피부 염증 반응',['알레르기','염증'],'진료 권고','🔴'],
 ['atopic','아토피 피부염','Atopic Dermatitis','만성','만성 재발성 염증성 피부 질환으로 가려움이 특징',['만성','가려움'],'진료 권고','🟠'],
 ['burn1','1도 화상','1st Degree Burn','외상','표피층이 손상된 화상',['화상','외상'],'자가처치 가능','🟡'],
 ['burn2','2도 화상','2nd Degree Burn','외상','진피층까지 손상되며 수포가 나타날 수 있는 화상',['화상','응급'],'즉시 내원','🔴'],
 ['urticaria','두드러기','Urticaria','알레르기','피부에 팽진과 가려움이 발생하는 반응',['알레르기','급성'],'진료 권고','🟠'],
 ['psoriasis','건선','Psoriasis','만성','피부에 인설이 나타나는 만성 염증성 질환',['만성','인설'],'진료 권고','🟤'],
 ['abrasion','찰과상','Abrasion','외상','피부 표면이 마찰로 벗겨진 상처',['외상','마찰'],'자가처치 가능','🩹'],
 ['zoster','대상포진','Herpes Zoster','감염','띠 모양 발진과 통증이 나타날 수 있는 질환',['감염','신경통'],'즉시 내원','🔴'],
 ['acne','여드름','Acne Vulgaris','염증','모낭과 피지선에 발생하는 피부 질환',['만성','피지'],'자가처치 가능','🟡'],
 ['cellulitis','봉와직염','Cellulitis','감염','피부와 피하 조직에 발생하는 세균 감염',['감염','응급'],'즉시 내원','🔴'],
].map(([id,name,english,category,description,tags,label,icon])=>({id,name,english,category,description,tags,label,icon}));
export const categories=['전체','염증','만성','외상','알레르기','감염'];
// All facilities and distances/statuses below are design fixtures, not live places.
const names={hospital:['서울사랑피부과의원','행복한 피부과','우리동 종합병원','강남피부과클리닉'],pharmacy:['서울사랑약국','행복한 약국','우리약국','강남24시약국']};
export const facilities=Object.fromEntries(Object.entries(names).map(([type,items])=>[type,items.map((name,i)=>({id:`${type}-${i}`,name,region:'서울 강남 · 예시 지역',distance:[.8,1.2,2.1,2.8][i],rating:[4.8,4.5,4.7,4.6][i],open:i!==2,tag:(type==='hospital'?['야간진료','주말진료','응급실','비대면진료']:['야간운영','주말운영','일반약국','24시간'])[i],x:[32,58,45,74][i],y:[33,52,75,28][i]}))]));

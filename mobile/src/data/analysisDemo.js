// Figma prototype fixtures. Never use these values for an uploaded photo.
export const analysisDemo = {
  leg: {type:'2도 화상 의심',part:'다리 (하퇴부)',confidence:87,risk:'경고'},
  arm: {type:'찰과상 의심',part:'팔 (전완부)',confidence:87,risk:'관찰'},
  hand: {type:'베임 의심',part:'손등 (우측)',confidence:87,risk:'관찰'},
};
export const riskLevels = [
  {name:'응급',icon:'🚨',caption:'즉시 진료 필요',color:'#EF4444',bg:'#FEF2F2'},
  {name:'경고',icon:'⚠️',caption:'빠른 진료 권고',color:'#F59E0B',bg:'#FFFBEB'},
  {name:'관찰',icon:'👁️',caption:'경과 관찰',color:'#10B981',bg:'#ECFDF5'},
];
// Presentation copy only; real care instructions must come from reviewed API data.
export const demoCare = ['해당 부위를 깨끗하게 유지해주세요.','해당 부위에 자극을 주지 않도록 주의해주세요.','상태가 악화되면 의료진과 상담해주세요.','사진을 추가하여 상태 변화를 기록해주세요.'];

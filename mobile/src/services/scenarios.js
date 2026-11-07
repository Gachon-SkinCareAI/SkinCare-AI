export const SCENARIOS = {
  abrasion: {
    title: '찰과상',
    description: '긁히거나 쓸린 부위와 오염 여부를 차례로 확인해요.',
    topics: ['다친 부위', '흙·이물질', '발생 시점'],
    result: '찰과상 관련 응답과 위험 신호 확인 결과를 함께 살펴보세요.'
  },
  bruise: {
    title: '타박상',
    description: '멍이 든 부위와 붉은색·멍이 번지는지 확인해요.',
    topics: ['멍이 든 부위', '번짐 여부', '발생 시점'],
    result: '타박상 관련 응답을 바탕으로 안내합니다. 사진과 날짜별 변화를 함께 기록해 보세요.'
  },
  burn: {
    title: '화상',
    description: '화상의 원인과 부위, 물집 등 현재 상태를 확인해요.',
    topics: ['화상 원인', '부위·범위', '물집·붙은 옷'],
    result: '화상 원인과 상태에 대한 응답이 반영된 결과예요. 아래의 안내와 출처를 확인해 주세요.'
  },
  open_wound: {
    title: '벌어진 상처',
    description: '출혈 여부를 먼저 확인하고, 벌어짐과 이물질 등을 살펴봐요.',
    topics: ['출혈 여부', '벌어짐·이물질', '오염 여부'],
    result: '출혈과 상처 상태에 대한 응답을 함께 확인한 결과예요. 표시된 위험도 안내를 먼저 확인해 주세요.'
  },
  normal: {
    title: '정상 피부로 분류된 사진',
    description: '사진 분류와 별개로, 불편한 증상과 발생 상황을 문진으로 확인해요.',
    topics: ['현재 증상', '발생 상황', '발생 시점'],
    result: '사진 분류만으로 이상이 없다고 확정하지 않습니다. 문진 응답을 반영한 위험도와 안내를 확인해 주세요.'
  },
  unknown: {
    title: '상처 유형 확인 중',
    description: '사진만으로 유형을 정하기 어려울 수 있어요. 가장 가까운 상황부터 알려 주세요.',
    topics: ['현재 증상', '발생 상황', '발생 시점'],
    result: '사진의 상처 유형을 특정하지 못했어요. 위험도는 문진 응답을 함께 반영한 별도 결과예요.'
  }
};
export function scenarioKey(session) {
  const scenario = session.slots.scenario;
  if (scenario === 'unsure') return 'unknown';
  if (typeof scenario === 'string' && ['abrasion', 'bruise', 'burn', 'open_wound'].includes(scenario)) return scenario;
  const raw = session.vision.classification?.label?.toLowerCase();
  const label = raw === 'normal_skin' ? 'normal' : raw;
  return label && Object.hasOwn(SCENARIOS, label) ? label : 'unknown';
}

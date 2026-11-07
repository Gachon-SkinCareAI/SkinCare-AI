// Consent persistence and OS permission are separate steps. No coordinates are read here.
export async function completeSignup({ api, policy, choices, requestLocationPermission }) {
  if (!policy?.requiredAvailable || !choices.terms || !choices.privacy || !choices.health) throw new Error('필수 동의 내용을 확인해 주세요.');
  const locationAvailable = !!policy.location?.url && !!policy.location?.version;
  const trainingAvailable = !!policy.trainingAvailable && !!policy.training?.url && !!policy.training?.version;
  if ((choices.location && !locationAvailable) || (choices.training && !trainingAvailable)) throw new Error('선택 동의 문서를 다시 확인해 주세요.');
  await api.post('/api/wc/users/me/consents', {
    agreedTerms: choices.terms, agreedPrivacy: choices.privacy, agreedHealthData: choices.health,
    termsVersion: policy.terms.version, privacyVersion: policy.privacy.version, healthDataVersion: policy.healthData.version,
    ...(locationAvailable ? { agreedLocation: choices.location, locationVersion: policy.location.version } : {}),
  }, 'PUT');
  if (trainingAvailable) {
    try { await api.post('/api/wc/users/me/consents/training', { granted: choices.training, version: policy.training.version }, 'PUT'); }
    catch { throw new Error('필수 가입 정보는 저장됐지만 학습 이용 선택을 저장하지 못했어요. 선택 내용을 확인하고 가입 완료를 다시 눌러 주세요.'); }
  }
  if (!choices.location) return '가입을 완료했어요. 위치 동의는 개인정보 설정에서 변경할 수 있어요.';
  try {
    const permission = await requestLocationPermission();
    if (permission.granted) return '가입을 완료했어요. 병원 탭에서 현재 위치로 주변 기관을 검색할 수 있어요.';
    if (permission.web) return '가입과 위치 동의를 저장했어요. 휴대전화 앱에서 위치 권한을 허용하면 주변 검색을 이용할 수 있어요.';
    return '가입을 완료했어요. 위치 권한은 허용되지 않았어요. 지역명으로 검색하거나 병원 탭에서 다시 요청할 수 있어요.';
  } catch {
    return '가입과 위치 동의를 저장했어요. 기기 위치 권한을 확인하지 못했어요. 병원 탭에서 다시 요청할 수 있어요.';
  }
}

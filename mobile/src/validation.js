export function validateAuth(mode, values) {
  const errors = {};
  if (mode === 'signup' && !values.name.trim()) errors.name = '이름을 입력해 주세요.';
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(values.email.trim())) errors.email = '올바른 이메일 주소를 입력해 주세요.';
  if (!values.password) errors.password = '비밀번호를 입력해 주세요.';
  // 가입 정책은 백엔드와 합의한 규칙으로 변경하세요. 로그인에는 가입 규칙을 강제하지 않습니다.
  else if (mode === 'signup' && (values.password.length < 8 || !/[A-Za-z]/.test(values.password) || !/[0-9]/.test(values.password))) errors.password = '영문과 숫자를 포함해 8자 이상 입력해 주세요.';
  if (mode === 'signup' && values.password !== values.confirmPassword) errors.confirmPassword = '비밀번호가 일치하지 않습니다.';
  return errors;
}

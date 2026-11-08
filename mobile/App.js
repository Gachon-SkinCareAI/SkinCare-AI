import AppShell from './src/AppShell';
import React, { useEffect, useState } from 'react';
import { ActivityIndicator, Image, BackHandler, Keyboard, KeyboardAvoidingView, Modal, Platform, Pressable, ScrollView, StatusBar, StyleSheet, Text, TextInput, View } from 'react-native';
import { SafeAreaProvider, SafeAreaView } from 'react-native-safe-area-context';
import { useAssets } from 'expo-asset';
import { SvgUri } from 'react-native-svg';
import { useFonts, NotoSansKR_400Regular, NotoSansKR_500Medium, NotoSansKR_700Bold, NotoSansKR_900Black } from '@expo-google-fonts/noto-sans-kr';

const BLUE = '#2563EB', NAVY = '#0D2B6B', MUTED = '#6B7A99';
const assetModules = [require('./assets/back.svg'), require('./assets/eye-off.svg'), require('./assets/shield.svg'), require('./assets/check.svg'), require('./assets/chevron.svg'), require('./assets/google.svg'), require('./assets/mail.svg'), require('./assets/skincare-ai-symbol.svg')];
const indices = { back: 0, eye: 1, shield: 2, check: 3, chevron: 4, google: 5, mail: 6, brand: 7 };
const sizes = { back: 20, eye: 18, shield: 20, check: 12, chevron: 14, google: 20, mail: 16 };
const AssetContext = React.createContext([]);
function Icon({ name }) {
  const assets = React.useContext(AssetContext);
  const a = assets[indices[name]], size = sizes[name];
  return <View style={{ width: size, height: size }}><SvgUri uri={a.localUri || a.uri} /></View>;
}
function T({ style, children, ...props }) { return <Text {...props} style={[s.text, style]}>{children}</Text>; }
function Symbol({ large = false }) {
  const assets = React.useContext(AssetContext);
  const brand = assets[indices.brand];
  const size = large ? 80 : 38;
  return <View style={{ width: size, height: size }}><SvgUri uri={brand.localUri || brand.uri} /></View>;
}
const featureImages = {'🔍':require('./assets/feature-search.png'), '⚠️':require('./assets/feature-warning.png'), '🏥':require('./assets/feature-hospital.png')};

function Button({ children, onPress, secondary, icon }) { return <Pressable accessibilityRole="button" onPress={onPress} style={({ pressed }) => [s.button, secondary && s.secondary, pressed && { opacity: 0.75 }]}>{icon && <Icon name={icon} />}<T style={[s.buttonText, secondary && { color: icon === 'google' ? '#374151' : MUTED }]}>{children}</T></Pressable>; }
function Field({ label, value, onChangeText, password, hint, placeholder }) {
  const [visible, setVisible] = useState(false);
  return <View style={s.field}><T style={s.label}>{label}</T><View style={s.inputBox}>
    <TextInput accessibilityLabel={label} value={value} onChangeText={onChangeText} style={s.input} placeholder={placeholder} placeholderTextColor={MUTED} secureTextEntry={!!password && !visible} autoCapitalize="none" autoCorrect={false} autoComplete="off" keyboardType={password ? 'default' : 'email-address'} />
    {password && <Pressable accessibilityRole="button" accessibilityLabel={visible ? `${label} 숨기기` : `${label} 표시`} onPress={() => setVisible(!visible)} hitSlop={12}>{visible ? <T style={{ color: MUTED, fontSize: 10 }}>숨김</T> : <Icon name="eye" />}</Pressable>}
  </View>{hint && <T style={s.hint}>{hint}</T>}</View>;
}
function Check({ checked, mixed, large }) { return <View style={[s.check, large && { width: 20, height: 20 }, checked && s.checked, mixed && s.mixed]}>{checked ? <Icon name="check" /> : mixed ? <View style={{ width: 10, height: 2, backgroundColor: BLUE }} /> : null}</View>; }
const AGREEMENTS = ['만 14세 이상입니다', '서비스 이용약관 동의', '개인정보 수집 및 이용 동의', '혜택 및 소식 이메일 수신 동의'];

// UI 시연 전용: 인증 API, Google OAuth, 계정 생성, 토큰 발급을 실행하지 않습니다.
// 실제 인증 연동 시 이 콜백을 서버 인증 성공 후에만 호출하도록 바꾸세요.
export function AuthScreen({ onContinue }) {
  const [screen, setScreen] = useState('intro');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [agreed, setAgreed] = useState([false, false, false, false]);
  const [dialog, setDialog] = useState(null);
  function go(next) { Keyboard.dismiss(); setPassword(''); setConfirmation(''); setScreen(next); }
  function proceed(provider = 'email') { Keyboard.dismiss(); setPassword(''); setConfirmation(''); onContinue({ demo: true, provider, email: email.trim() }); }
  useEffect(() => {
    const handler = BackHandler.addEventListener('hardwareBackPress', () => {
      if (dialog) { setDialog(null); return true; }
      if (screen !== 'intro') { go(screen === 'signup' ? 'login' : 'intro'); return true; }
      return false;
    });
    return () => handler.remove();
  }, [screen, dialog]);
  const all = agreed.every(Boolean), some = agreed.some(Boolean);
  return <SafeAreaView style={s.safe}>
    {screen !== 'intro' && <View style={s.nav}><Pressable accessibilityRole="button" accessibilityLabel="뒤로" onPress={() => go(screen === 'signup' ? 'login' : 'intro')} hitSlop={12}><Icon name="back" /></Pressable><T style={s.navText}>{screen === 'signup' ? '이메일로 회원가입' : '이메일로 로그인'}</T></View>}
    <KeyboardAvoidingView style={s.flex} behavior={Platform.OS === 'ios' ? 'padding' : 'height'}>
      <ScrollView contentContainerStyle={[s.scroll, screen === 'intro' && s.introScroll]} keyboardShouldPersistTaps="handled">
        {screen === 'intro' ? <>
          <View style={s.introTop}><Symbol large /><T style={s.introTitle}>SkinCare AI</T><T style={s.introDescription}>{'AI가 피부·상처를 분석하고\n위험도와 케어 가이드를 제공합니다'}</T>
            <View style={s.features}>{[['🔍', 'AI 피부 이미지 분석', '사진 한 장으로 병변 유형 탐지'], ['⚠️', '3단계 위험도 평가', '관찰 · 경고 · 응급 즉시 판별'], ['🏥', '의료기관 즉시 연결', '야간·주말 진료 가능 병원 안내']].map(([emoji, title, desc]) => <View style={s.feature} key={title}><View style={s.featureIcon}><Image source={featureImages[emoji]} style={{width: emoji === "⚠️" ? 19 : 20, height: emoji === "⚠️" ? 17 : 20}} resizeMode="contain" /></View><View style={s.flex}><T style={s.featureTitle}>{title}</T><T style={s.featureDesc}>{desc}</T></View></View>)}</View>
          </View>
          <View style={s.introActions}><Button secondary icon="google" onPress={() => proceed('google-demo')}>Google로 계속하기</Button><View style={s.divider}><View style={s.line} /><T style={{ fontSize: 11, color: MUTED }}>또는</T><View style={s.line} /></View><Button secondary icon="mail" onPress={() => go('login')}>이메일로 로그인</Button><T style={s.legal}>계속 진행하면 <T style={s.legalLink} onPress={() => setDialog('이용약관')}>이용약관</T> 및 <T style={s.legalLink} onPress={() => setDialog('개인정보처리방침')}>개인정보처리방침</T>에 동의하는 것으로 간주합니다. 본 서비스는 의학적 진단을 제공하지 않습니다.</T></View>
        </> : screen === 'login' ? <View style={s.loginBody}>
          <View><View style={s.brandRow}><Symbol /><T style={s.brand}>SkinCare AI</T></View><T style={s.loginTitle}>다시 만나 반가워요</T><T style={s.description}>{'로그인하고 나의 피부 기록과\n맞춤 케어 가이드를 이어서 확인하세요.'}</T>
            <View style={s.loginForm}><Field label="이메일" value={email} onChangeText={setEmail} placeholder="minji.kim@example.com" /><Field label="비밀번호" value={password} onChangeText={setPassword} password placeholder="비밀번호를 입력해 주세요" /><Pressable accessibilityRole="button" style={{ alignSelf: 'flex-end' }} onPress={() => setDialog('비밀번호 재설정')}><T style={s.link}>비밀번호를 잊으셨나요?</T></Pressable><Button onPress={() => proceed()}>로그인</Button><View style={s.switchRow}><T style={s.switchText}>아직 계정이 없으신가요?</T><Pressable accessibilityRole="button" onPress={() => go('signup')} hitSlop={10}><T style={[s.link, s.bold]}>이메일로 회원가입</T></Pressable></View></View>
          </View>
          <View style={s.privacy}><View style={s.privacyCard}><View style={s.shieldCircle}><Icon name="shield" /></View><View style={s.flex}><T style={s.label}>나의 기록을 안전하게</T><T style={s.privacyText}>{'피부 기록은 내 계정에서 관리되며,\n동의 없이 외부에 공유하지 않습니다.'}</T></View></View><T style={s.disclaimer}>본 서비스는 의학적 진단을 제공하지 않습니다.</T></View>
        </View> : <View style={s.signupBody}>
          <View><T style={s.title}>나를 위한 케어의 시작</T><T style={[s.description, { marginTop: 8 }]}>계정을 만들고 피부 변화를 꾸준히 기록하세요.</T></View>
          <View style={s.signupForm}><Field label="이메일" value={email} onChangeText={setEmail} placeholder="minji.kim@example.com" /><Field label="비밀번호" value={password} onChangeText={setPassword} password placeholder="비밀번호를 입력해 주세요" hint="영문, 숫자, 특수문자 중 2가지 이상 · 8~20자" /><Field label="비밀번호 확인" value={confirmation} onChangeText={setConfirmation} password placeholder="비밀번호를 다시 입력해 주세요" /></View>
          <View style={s.agreements}><Pressable accessibilityRole="checkbox" accessibilityLabel="전체 동의" aria-checked={all ? true : some ? "mixed" : false} accessibilityState={{ checked: all ? true : some ? 'mixed' : false }} onPress={() => setAgreed(agreed.map(() => !all))} style={s.allRow}><Check large checked={all} mixed={!all && some} /><T style={{ fontSize: 13, fontFamily: 'NotoSansKR_700Bold', color: NAVY }}>전체 동의</T><T style={s.hint}>선택 항목 포함</T></Pressable><View style={s.rule} /><View style={{ gap: 2 }}>{AGREEMENTS.map((title, index) => <View key={title} style={s.agreementRow}><Pressable accessibilityRole="checkbox" accessibilityLabel={title} aria-checked={agreed[index]} accessibilityState={{ checked: agreed[index] }} onPress={() => setAgreed(agreed.map((v, i) => i === index ? !v : v))} style={s.agreementToggle}><Check checked={agreed[index]} /><T style={{ fontSize: 11, flex: 1 }}><T style={{ color: index === 3 ? MUTED : BLUE, fontSize: 11 }}>{index === 3 ? '[선택] ' : '[필수] '}</T>{title}</T></Pressable>{index > 0 && <Pressable accessibilityRole="button" accessibilityLabel={`${title} 상세`} onPress={() => setDialog(title)} hitSlop={10}><Icon name="chevron" /></Pressable>}</View>)}</View></View>
          <View style={{ gap: 16 }}><Button onPress={() => { go('login'); setDialog('회원가입 화면 확인 완료'); }}>동의하고 회원가입</Button><View style={s.switchRow}><T style={s.switchText}>이미 계정이 있으신가요?</T><Pressable accessibilityRole="button" onPress={() => go('login')} hitSlop={10}><T style={[s.link, s.bold]}>로그인</T></Pressable></View></View>
        </View>}
      </ScrollView>
    </KeyboardAvoidingView>
    <Modal visible={!!dialog} transparent animationType="fade" onRequestClose={() => setDialog(null)}><View style={s.modalOverlay}><View style={s.modalCard}><T style={{ color: NAVY, fontSize: 18, fontFamily: 'NotoSansKR_700Bold' }}>{dialog}</T><T style={s.description}>{dialog === '회원가입 화면 확인 완료' ? '현재는 UI 시연 모드입니다. 계정은 생성하지 않으며, 로그인 버튼을 누르면 인증 없이 다음 화면으로 이동합니다.' : dialog === '비밀번호 재설정' ? '백엔드 연결 후 이메일 재설정 기능을 연결할 예정입니다. 지금은 로그인 버튼으로 바로 이동할 수 있습니다.' : '약관 본문이 아직 제공되지 않았습니다. 현재 체크 상태는 화면에서만 바뀌며 서버에 저장되지 않습니다.'}</T><Button onPress={() => setDialog(null)}>확인</Button></View></View></Modal>
  </SafeAreaView>;
}
export default function App() {
  const [assets, assetError] = useAssets(assetModules);
  const [fontsLoaded, fontError] = useFonts({ NotoSansKR_400Regular, NotoSansKR_500Medium, NotoSansKR_700Bold, NotoSansKR_900Black });
  const [demoSession, setDemoSession] = useState(null);
  if (assetError || fontError) return <View style={s.loading}><Text>리소스를 불러오지 못했습니다. 앱을 다시 실행해 주세요.</Text></View>;
  if (!assets || !fontsLoaded) return <View style={s.loading}><ActivityIndicator color={BLUE} /></View>;
  return <SafeAreaProvider><AssetContext.Provider value={assets}><StatusBar barStyle="dark-content" />{demoSession ? <AppShell onLogout={() => setDemoSession(null)} /> : <AuthScreen onContinue={setDemoSession} />}</AssetContext.Provider></SafeAreaProvider>;
}
const s = StyleSheet.create({
  text: { fontFamily: 'NotoSansKR_400Regular', color: '#0D1B3E', includeFontPadding: false }, flex: { flex: 1 }, safe: { flex: 1, backgroundColor: '#FFFFFF' }, loading: { flex: 1, alignItems: 'center', justifyContent: 'center', backgroundColor: '#FFF' },
  scroll: { flexGrow: 1, width: '100%', maxWidth: 520, alignSelf: 'center' }, nav: { height: 56, flexDirection: 'row', gap: 12, alignItems: 'center', paddingHorizontal: 24, width: '100%', maxWidth: 520, alignSelf: 'center' }, navText: { fontFamily: 'NotoSansKR_700Bold', fontSize: 14, color: NAVY },
  symbol: { width: 38, height: 38, borderRadius: 10, justifyContent: 'center', alignItems: 'center' }, symbolLarge: { width: 80, height: 80, borderRadius: 20 }, brandRow: { flexDirection: 'row', alignItems: 'center', gap: 10 }, brand: { fontSize: 17, fontFamily: 'NotoSansKR_700Bold', color: NAVY },
  title: { fontSize: 26, lineHeight: 36.4, fontFamily: 'NotoSansKR_700Bold', color: NAVY }, loginTitle: { fontSize: 26, lineHeight: 36.4, fontFamily: 'NotoSansKR_700Bold', color: NAVY, marginTop: 24, marginBottom: 10 }, description: { fontSize: 13, lineHeight: 22.1, color: MUTED },
  loginBody: { flex: 1, paddingTop: 28, paddingHorizontal: 24, paddingBottom: 24, justifyContent: 'space-between', gap: 32 }, loginForm: { gap: 20, marginTop: 32 }, field: { gap: 8 }, label: { fontFamily: 'NotoSansKR_700Bold', fontSize: 12, lineHeight: 18, color: NAVY }, inputBox: { height: 52, borderRadius: 16, backgroundColor: '#F4F6F9', paddingHorizontal: 16, flexDirection: 'row', alignItems: 'center', gap: 12 }, input: { flex: 1, minWidth: 0, height: '100%', padding: 0, fontFamily: 'NotoSansKR_400Regular', fontSize: 14, color: '#0D1B3E', outlineStyle: 'none' }, hint: { fontSize: 10, lineHeight: 15, color: MUTED },
  button: { minHeight: 54, paddingVertical: 14, borderRadius: 16, backgroundColor: BLUE, alignItems: 'center', justifyContent: 'center', flexDirection: 'row', gap: 12 }, buttonText: { fontFamily: 'NotoSansKR_700Bold', color: '#FFF', fontSize: 14, lineHeight: 21 }, secondary: { backgroundColor: '#FFF', borderWidth: 1, borderColor: '#E8EDF5' }, link: { fontFamily: 'NotoSansKR_500Medium', color: BLUE, fontSize: 12, lineHeight: 18 }, bold: { fontFamily: 'NotoSansKR_700Bold' }, switchRow: { flexDirection: 'row', flexWrap: 'wrap', gap: 8, alignItems: 'center', justifyContent: 'center' }, switchText: { color: MUTED, fontSize: 12, lineHeight: 18 },
  privacy: { gap: 16, marginTop: 40 }, privacyCard: { backgroundColor: '#F4F6F9', borderRadius: 16, padding: 16, flexDirection: 'row', gap: 12, alignItems: 'center' }, shieldCircle: { width: 36, height: 36, borderRadius: 18, backgroundColor: '#FFF', alignItems: 'center', justifyContent: 'center' }, privacyText: { marginTop: 4, color: MUTED, fontSize: 10, lineHeight: 16 }, disclaimer: { fontSize: 10, lineHeight: 16, color: MUTED, textAlign: 'center' },
  signupBody: { paddingHorizontal: 24, paddingTop: 16, paddingBottom: 20, gap: 24 }, signupForm: { gap: 16 }, agreements: { gap: 8 }, allRow: { height: 42, flexDirection: 'row', alignItems: 'center', gap: 10 }, rule: { height: 1, backgroundColor: '#E8EDF5' }, agreementRow: { minHeight: 32, flexDirection: 'row', alignItems: 'center', gap: 10 }, agreementToggle: { flex: 1, flexDirection: 'row', gap: 10, alignItems: 'center', minHeight: 32 }, check: { width: 18, height: 18, borderRadius: 5, borderColor: '#C7D2E3', borderWidth: 1, justifyContent: 'center', alignItems: 'center', backgroundColor: '#FFF' }, checked: { backgroundColor: BLUE, borderColor: BLUE }, mixed: { backgroundColor: '#EFF5FF', borderColor: BLUE },
  introScroll: { justifyContent: 'space-between' }, introTop: { flexGrow: 1, alignItems: 'center', justifyContent: 'center', paddingTop: 40, paddingBottom: 64, paddingHorizontal: 32 }, introTitle: { fontSize: 24, lineHeight: 32, fontFamily: 'NotoSansKR_900Black', color: NAVY, letterSpacing: -0.6, marginTop: 20 }, introDescription: { color: MUTED, fontSize: 13, lineHeight: 21.125, textAlign: 'center', marginTop: 4 }, features: { gap: 12, marginTop: 40, width: '100%' }, feature: { backgroundColor: '#F4F6F9', borderRadius: 16, paddingHorizontal: 16, paddingVertical: 12, flexDirection: 'row', gap: 14, alignItems: 'center' }, featureIcon: { width: 40, height: 40, borderRadius: 24, backgroundColor: '#FFF', justifyContent: 'center', alignItems: 'center' }, featureTitle: { fontSize: 13, lineHeight: 19.5, fontFamily: 'NotoSansKR_700Bold', color: NAVY }, featureDesc: { fontSize: 11, lineHeight: 16.5, color: MUTED }, introActions: { paddingHorizontal: 24, paddingBottom: 32 }, divider: { flexDirection: 'row', alignItems: 'center', gap: 12, paddingVertical: 12 }, line: { flex: 1, height: 1, backgroundColor: '#E8EDF5' }, legal: { textAlign: 'center', fontSize: 10, lineHeight: 16.25, color: MUTED, marginTop: 12, marginHorizontal: 8 }, legalLink: { fontSize: 10, lineHeight: 16.25, color: BLUE, fontFamily: 'NotoSansKR_500Medium' },
  modalOverlay: { flex: 1, backgroundColor: '#00000055', alignItems: 'center', justifyContent: 'center', padding: 24 }, modalCard: { backgroundColor: '#FFF', borderRadius: 20, padding: 24, gap: 20, maxWidth: 380, width: '100%' }, demo: { flex: 1, justifyContent: 'center', padding: 24, gap: 24 },
});

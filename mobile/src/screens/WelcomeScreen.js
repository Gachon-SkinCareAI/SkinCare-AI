import React from "react";
import {
  ActivityIndicator,
  Image,
  Pressable,
  ScrollView,
  StyleSheet,
  Text,
  View,
} from "react-native";
import { SafeAreaView } from "react-native-safe-area-context";
import { useAssets } from "expo-asset";
import { SvgUri } from "react-native-svg";
import { ErrorNote, PolicyLink } from "../components/ConsentControls";
const BLUE = "#2563EB",
  NAVY = "#0D2B6B",
  MUTED = "#6B7A99";
const modules = [require("../../assets/google.svg")];
const featureImages = {
  search: require("../../assets/feature-search.png"),
  warning: require("../../assets/feature-warning.png"),
  hospital: require("../../assets/feature-hospital.png"),
};
function T({ style, ...props }) {
  return <Text {...props} style={[s.text, style]} />;
}
export default function WelcomeScreen({
  api,
  policy,
  providers,
  busy,
  error,
  onLogin,
  onGuest,
  onReload,
}) {
  const [assets] = useAssets(modules);
  return (
    <SafeAreaView style={s.safe}>
      <ScrollView
        contentContainerStyle={[s.scroll, s.introScroll]}
        testID="develop-welcome"
      >
        <View style={s.introTop}>
          <View style={{ width: 80, height: 80 }}>
            <Image
              source={require("../../assets/symbol-large.png")}
              style={{
                position: "absolute",
                width: 128,
                height: 128,
                left: -24,
                top: -16,
              }}
            />
          </View>
          <T style={s.introTitle}>SkinCare AI</T>
          <T style={s.introDescription}>
            {
              "AI가 피부·상처 사진과 문진을 바탕으로\n위험 신호와 케어 정보를 안내합니다"
            }
          </T>
          <View style={s.features}>
            {[
              ["search", "AI 피부 이미지 분석", "사진과 문진으로 상태 확인"],
              ["warning", "위험 신호 확인", "관찰 · 경고 · 응급 정보 안내"],
              ["hospital", "주변 의료기관 찾기", "병원·약국 정보 확인"],
            ].map(([key, title, description]) => (
              <View style={s.feature} key={key}>
                <View style={s.featureIcon}>
                  <Image
                    source={featureImages[key]}
                    style={{ width: 20, height: 20 }}
                    resizeMode="contain"
                  />
                </View>
                <View style={s.flex}>
                  <T style={s.featureTitle}>{title}</T>
                  <T style={s.featureDesc}>{description}</T>
                </View>
              </View>
            ))}
          </View>
        </View>
        <View style={s.introActions}>
          <ErrorNote message={error} />
          {providers.map((provider) => (
            <Pressable
              key={provider.id}
              accessibilityRole="button"
              disabled={busy || !provider.enabled}
              accessibilityState={{ disabled: busy || !provider.enabled }}
              onPress={() => onLogin(provider.id)}
              style={({ pressed }) => [
                s.button,
                s.secondary,
                {
                  marginBottom: 10,
                  opacity:
                    busy || !provider.enabled ? 0.45 : pressed ? 0.75 : 1,
                },
              ]}
            >
              {provider.id === "google" && assets && (
                <SvgUri
                  uri={assets[0].localUri || assets[0].uri}
                  width={20}
                  height={20}
                />
              )}
              <T style={[s.buttonText, { color: NAVY }]}>
                {provider.label}로 계속하기
                {!provider.enabled ? " · 준비 중" : ""}
              </T>
            </Pressable>
          ))}
          {!providers.length && (
            <Pressable
              accessibilityRole="button"
              disabled={busy}
              onPress={onReload}
              style={[s.button, s.secondary, { marginBottom: 10 }]}
            >
              <T style={[s.buttonText, { color: NAVY }]}>
                {busy ? "연결 확인 중…" : "로그인 연결 다시 확인"}
              </T>
            </Pressable>
          )}
          <Pressable
            accessibilityRole="button"
            disabled={busy}
            accessibilityState={{ disabled: busy }}
            onPress={onGuest}
            style={[s.button, { opacity: busy ? 0.45 : 1 }]}
          >
            <T style={s.buttonText}>로그인 없이 시작하기</T>
          </Pressable>
          {busy && <ActivityIndicator color={BLUE} style={{ marginTop: 12 }} />}
          <T style={s.legal}>
            필수 동의는 가입하거나 사진을 분석할 때 별도로 확인해요. 분석 안내는
            의료진의 진단을 대신하지 않아요.
          </T>
          {policy?.terms?.url && (
            <PolicyLink base={api.base} url={policy.terms.url}>
              이용약관
            </PolicyLink>
          )}
          {policy?.privacy?.url && (
            <PolicyLink base={api.base} url={policy.privacy.url}>
              개인정보 처리 안내
            </PolicyLink>
          )}
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}
const s = StyleSheet.create({
  text: {
    fontFamily: "NotoSansKR_400Regular",
    color: "#0D1B3E",
    includeFontPadding: false,
  },
  flex: { flex: 1 },
  safe: { flex: 1, backgroundColor: "#FFFFFF" },
  loading: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "#FFF",
  },
  scroll: { flexGrow: 1, width: "100%", maxWidth: 520, alignSelf: "center" },
  nav: {
    height: 56,
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
    paddingHorizontal: 24,
    width: "100%",
    maxWidth: 520,
    alignSelf: "center",
  },
  navText: { fontFamily: "NotoSansKR_700Bold", fontSize: 14, color: NAVY },
  symbol: {
    width: 38,
    height: 38,
    borderRadius: 10,
    justifyContent: "center",
    alignItems: "center",
  },
  symbolLarge: { width: 80, height: 80, borderRadius: 20 },
  brandRow: { flexDirection: "row", alignItems: "center", gap: 10 },
  brand: { fontSize: 17, fontFamily: "NotoSansKR_700Bold", color: NAVY },
  title: {
    fontSize: 26,
    lineHeight: 36.4,
    fontFamily: "NotoSansKR_700Bold",
    color: NAVY,
  },
  loginTitle: {
    fontSize: 26,
    lineHeight: 36.4,
    fontFamily: "NotoSansKR_700Bold",
    color: NAVY,
    marginTop: 24,
    marginBottom: 10,
  },
  description: { fontSize: 13, lineHeight: 22.1, color: MUTED },
  loginBody: {
    flex: 1,
    paddingTop: 28,
    paddingHorizontal: 24,
    paddingBottom: 24,
    justifyContent: "space-between",
    gap: 32,
  },
  loginForm: { gap: 20, marginTop: 32 },
  field: { gap: 8 },
  label: {
    fontFamily: "NotoSansKR_700Bold",
    fontSize: 12,
    lineHeight: 18,
    color: NAVY,
  },
  inputBox: {
    height: 52,
    borderRadius: 16,
    backgroundColor: "#F4F6F9",
    paddingHorizontal: 16,
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
  },
  input: {
    flex: 1,
    minWidth: 0,
    height: "100%",
    padding: 0,
    fontFamily: "NotoSansKR_400Regular",
    fontSize: 14,
    color: "#0D1B3E",
    outlineStyle: "none",
  },
  hint: { fontSize: 10, lineHeight: 15, color: MUTED },
  button: {
    minHeight: 54,
    paddingVertical: 14,
    borderRadius: 16,
    backgroundColor: BLUE,
    alignItems: "center",
    justifyContent: "center",
    flexDirection: "row",
    gap: 12,
  },
  buttonText: {
    fontFamily: "NotoSansKR_700Bold",
    color: "#FFF",
    fontSize: 14,
    lineHeight: 21,
  },
  secondary: {
    backgroundColor: "#FFF",
    borderWidth: 1,
    borderColor: "#E8EDF5",
  },
  link: {
    fontFamily: "NotoSansKR_500Medium",
    color: BLUE,
    fontSize: 12,
    lineHeight: 18,
  },
  bold: { fontFamily: "NotoSansKR_700Bold" },
  switchRow: {
    flexDirection: "row",
    flexWrap: "wrap",
    gap: 8,
    alignItems: "center",
    justifyContent: "center",
  },
  switchText: { color: MUTED, fontSize: 12, lineHeight: 18 },
  privacy: { gap: 16, marginTop: 40 },
  privacyCard: {
    backgroundColor: "#F4F6F9",
    borderRadius: 16,
    padding: 16,
    flexDirection: "row",
    gap: 12,
    alignItems: "center",
  },
  shieldCircle: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: "#FFF",
    alignItems: "center",
    justifyContent: "center",
  },
  privacyText: { marginTop: 4, color: MUTED, fontSize: 10, lineHeight: 16 },
  disclaimer: {
    fontSize: 10,
    lineHeight: 16,
    color: MUTED,
    textAlign: "center",
  },
  signupBody: {
    paddingHorizontal: 24,
    paddingTop: 16,
    paddingBottom: 20,
    gap: 24,
  },
  signupForm: { gap: 16 },
  agreements: { gap: 8 },
  allRow: { height: 42, flexDirection: "row", alignItems: "center", gap: 10 },
  rule: { height: 1, backgroundColor: "#E8EDF5" },
  agreementRow: {
    minHeight: 32,
    flexDirection: "row",
    alignItems: "center",
    gap: 10,
  },
  agreementToggle: {
    flex: 1,
    flexDirection: "row",
    gap: 10,
    alignItems: "center",
    minHeight: 32,
  },
  check: {
    width: 18,
    height: 18,
    borderRadius: 5,
    borderColor: "#C7D2E3",
    borderWidth: 1,
    justifyContent: "center",
    alignItems: "center",
    backgroundColor: "#FFF",
  },
  checked: { backgroundColor: BLUE, borderColor: BLUE },
  mixed: { backgroundColor: "#EFF5FF", borderColor: BLUE },
  introScroll: { justifyContent: "space-between" },
  introTop: {
    flexGrow: 1,
    alignItems: "center",
    justifyContent: "center",
    paddingTop: 40,
    paddingBottom: 64,
    paddingHorizontal: 32,
  },
  introTitle: {
    fontSize: 24,
    lineHeight: 32,
    fontFamily: "NotoSansKR_900Black",
    color: NAVY,
    letterSpacing: -0.6,
    marginTop: 20,
  },
  introDescription: {
    color: MUTED,
    fontSize: 13,
    lineHeight: 21.125,
    textAlign: "center",
    marginTop: 4,
  },
  features: { gap: 12, marginTop: 40, width: "100%" },
  feature: {
    backgroundColor: "#F4F6F9",
    borderRadius: 16,
    paddingHorizontal: 16,
    paddingVertical: 12,
    flexDirection: "row",
    gap: 14,
    alignItems: "center",
  },
  featureIcon: {
    width: 40,
    height: 40,
    borderRadius: 24,
    backgroundColor: "#FFF",
    justifyContent: "center",
    alignItems: "center",
  },
  featureTitle: {
    fontSize: 13,
    lineHeight: 19.5,
    fontFamily: "NotoSansKR_700Bold",
    color: NAVY,
  },
  featureDesc: { fontSize: 11, lineHeight: 16.5, color: MUTED },
  introActions: { paddingHorizontal: 24, paddingBottom: 32 },
  divider: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12,
    paddingVertical: 12,
  },
  line: { flex: 1, height: 1, backgroundColor: "#E8EDF5" },
  legal: {
    textAlign: "center",
    fontSize: 10,
    lineHeight: 16.25,
    color: MUTED,
    marginTop: 12,
    marginHorizontal: 8,
  },
  legalLink: {
    fontSize: 10,
    lineHeight: 16.25,
    color: BLUE,
    fontFamily: "NotoSansKR_500Medium",
  },
  modalOverlay: {
    flex: 1,
    backgroundColor: "#00000055",
    alignItems: "center",
    justifyContent: "center",
    padding: 24,
  },
  modalCard: {
    backgroundColor: "#FFF",
    borderRadius: 20,
    padding: 24,
    gap: 20,
    maxWidth: 380,
    width: "100%",
  },
  demo: { flex: 1, justifyContent: "center", padding: 24, gap: 24 },
});

import React from 'react';
import { ActivityIndicator, Text, View } from 'react-native';
import { SafeAreaProvider } from 'react-native-safe-area-context';
import { useFonts } from 'expo-font';
import AppShell from './src/AppShell';
const fonts = {
  NotoSansKR_400Regular: require('@expo-google-fonts/noto-sans-kr/400Regular/NotoSansKR_400Regular.ttf'),
  NotoSansKR_500Medium: require('@expo-google-fonts/noto-sans-kr/500Medium/NotoSansKR_500Medium.ttf'),
  NotoSansKR_700Bold: require('@expo-google-fonts/noto-sans-kr/700Bold/NotoSansKR_700Bold.ttf'),
  NotoSansKR_900Black: require('@expo-google-fonts/noto-sans-kr/900Black/NotoSansKR_900Black.ttf'),
};
export default function App() {
  const [loaded, error] = useFonts(fonts);
  if (error) return <View style={{flex:1,justifyContent:'center',padding:24}}><Text>리소스를 불러오지 못했습니다. 앱을 다시 실행해 주세요.</Text></View>;
  if (!loaded) return <View style={{flex:1,justifyContent:'center'}}><ActivityIndicator color="#2563EB"/></View>;
  return <SafeAreaProvider><AppShell /></SafeAreaProvider>;
}

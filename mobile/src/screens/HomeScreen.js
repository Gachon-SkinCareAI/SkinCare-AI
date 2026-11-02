import { BottomTabs } from '../components/Common';
import React, { useRef, useState } from 'react';
import { ActivityIndicator, Image, Modal, Pressable, ScrollView, StyleSheet, Text, View } from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';
import { LinearGradient } from 'expo-linear-gradient';
import { useAssets } from 'expo-asset';
import { SvgUri } from 'react-native-svg';
import { recentAnalyses, recovery as demoRecovery } from '../data/homeDemo';

const NAVY = '#0D2B6B';
const modules = [require('../../assets/home/bell.svg'), require('../../assets/home/camera.svg'), require('../../assets/home/chevron.svg'), require('../../assets/home/home.svg'), require('../../assets/home/history.svg'), require('../../assets/home/book.svg'), require('../../assets/home/pin.svg'), require('../../assets/home/user.svg'), require('../../assets/home/arrow.svg'), require('../../assets/home/tip.svg'), require('../../assets/home/scanner.svg')];
const names = ['bell','camera','chevron','home','history','book','pin','user','arrow','tip','scanner'];
const dimensions = [20,17,16,22,22,22,22,22,12,14,96];
const tabs = [{key:'home',label:'홈'}, {key:'history',label:'기록'}, {key:'book',label:'백과'}, {key:'pin',label:'병원'}, {key:'user',label:'마이'}];
function T({style, ...props}) { return <Text {...props} style={[s.text, style]} />; }

// onNavigate(route, params)를 연결하면 기존 네비게이터로 화면 이동을 위임합니다.
// 아직 구현하지 않은 목적지는 홈을 유지하면서 안내 모달을 표시합니다.
export default function HomeScreen({ onLogout, onNavigate, analyses = recentAnalyses, recovery = demoRecovery }) {
  const [assets, error] = useAssets(modules);
  const [dialog, setDialog] = useState(null);
  const [unread, setUnread] = useState(true);
  const scroll = useRef(null);
  function open(route, title, params) {
    if (onNavigate) { onNavigate(route, params); return; }
    setDialog({route, title});
  }
  function Icon({name}) {
    const i = names.indexOf(name), a = assets[i];
    return <View style={{width:dimensions[i],height:dimensions[i]}}><SvgUri uri={a.localUri || a.uri} /></View>;
  }
  if (error) return <SafeAreaView style={s.safe}><T>홈 리소스를 불러오지 못했습니다.</T><Pressable onPress={onLogout}><T>로그인으로 돌아가기</T></Pressable></SafeAreaView>;
  if (!assets) return <View style={s.loading}><ActivityIndicator color={NAVY} /></View>;
  return <SafeAreaView style={s.safe}>
    <View style={s.shell}>
      <View style={s.header}>
        <View style={s.brandRow}><View style={s.brandCircle}><T style={s.brandInitial}>S</T></View><View><T style={s.brandTitle}>SkinCare AI</T><T style={s.brandSubtitle}>AI가 함께하는 상처 케어</T></View></View>
        <Pressable accessibilityRole="button" accessibilityLabel="알림" onPress={() => { setUnread(false); open('notifications','알림'); }} style={s.bell}><Icon name="bell" />{unread && <View style={s.dot} />}</Pressable>
      </View>
      <ScrollView ref={scroll} style={s.scroller} contentContainerStyle={s.content} showsVerticalScrollIndicator={false} testID="home-scroll">
        <LinearGradient colors={['#081F50','#0D2B6B','#17478F']} locations={[0,0.5,1]} start={{x:0,y:0}} end={{x:1,y:1}} style={s.banner}>
          <View pointerEvents="none" style={s.scanner}><Icon name="scanner" /></View>
          <T style={s.bannerTitle}>갑자기 다쳤나요?</T><T style={s.bannerDescription}>{'사진 한 장으로 상처 상태를\n확인해보세요.'}</T><T style={s.bannerCaption}>화상 · 찰과상 · 베임 등 외상 분석</T>
          <Pressable accessibilityRole="button" onPress={() => open('analysis','상처 분석 시작하기')} style={({pressed}) => [s.analyzeButton, pressed && s.pressed]}><Icon name="camera" /><T style={s.analyzeText}>상처 분석 시작하기</T></Pressable>
        </LinearGradient>
        <View style={s.recentSection}>
          <View style={s.sectionHeading}><T style={s.heading}>최근 분석</T><Pressable accessibilityRole="button" onPress={() => open('history','전체 기록')} style={s.inline}><T style={s.allText}>전체보기</T><Icon name="chevron" /></Pressable></View>
          <View style={s.recentCard}>{analyses.length ? analyses.slice(0,3).map((item,index) => <Pressable key={item.id} accessibilityRole="button" accessibilityLabel={`${item.title} 분석 상세`} onPress={() => open('analysisDetail',`${item.title} 분석 상세`,{id:item.id})} style={({pressed}) => [s.record, index > 0 && s.recordBorder, pressed && s.pressed]}>
            <Image source={item.image} accessibilityLabel={`${item.title} 예시 사진`} style={s.thumbnail} /><View style={s.recordInfo}><T style={s.recordTitle}>{item.title}</T><T style={s.recordSubtitle}>{item.summary}</T></View><View style={[s.badge,item.status==='경고' && s.warning]}><T style={[s.badgeText,item.status==='경고' && s.warningText]}>{item.status}</T></View><Icon name="chevron" />
          </Pressable>) : <T style={s.empty}>아직 분석 기록이 없어요.</T>}</View>
        </View>
        {recovery && <Pressable accessibilityRole="button" accessibilityLabel="상처 회복 과정 상세" onPress={() => open('recovery','상처 회복 과정',{id:recovery.id})} style={s.recovery}>
          <View style={s.recoveryHeading}><View><T style={s.eyebrow}>내 상처 회복 과정</T><T style={[s.heading,{marginTop:4}]}>{recovery.title}</T><T style={s.recoveryDescription}>회복 경과를 확인해보세요.</T></View><Icon name="chevron" /></View>
          <View style={s.photos}>{recovery.photos.slice(0,3).map((photo,index) => <React.Fragment key={photo.date}>{index > 0 && <View style={s.photoArrow}><Icon name="arrow" /></View>}<View style={s.photoColumn}><Image source={photo.image} accessibilityLabel={`${photo.date} 회복 예시 사진`} style={s.recoveryImage} /><T style={s.date}>{photo.date}</T></View></React.Fragment>)}</View>
          <View style={s.recoveryFooter}><T style={s.small}>최근 촬영 {recovery.latestDate}</T><T style={s.recoveryStatus}>{recovery.status}</T></View>
        </Pressable>}
        <View style={s.tip}><View style={s.tipIcon}><Icon name="tip" /></View><View style={s.tipCopy}><T style={s.tipTitle}>오늘의 상처 케어 TIP</T><T style={s.tipBody}>상처를 만지기 전에는 손을 깨끗이 씻고, 습윤 밴드는 오염되면 바로 교체해주세요.</T></View></View>
        <T style={s.disclaimer}>예시 기록이 포함된 시연 화면입니다. 의학적 진단이 아닙니다.</T>
      </ScrollView>
      <BottomTabs active="home" onNavigate={key => key==='home' ? scroll.current?.scrollTo({y:0,animated:true}) : open(key,key)} />
    </View>
    <Modal visible={!!dialog} transparent animationType="fade" onRequestClose={() => setDialog(null)}><View style={s.overlay}><View style={s.dialog}><T style={s.heading}>{dialog?.title}</T><T style={s.dialogBody}>{dialog?.route==='notifications' ? '현재는 화면 확인용 예시 데이터입니다. 실제 알림은 백엔드 연동 후 표시됩니다.' : dialog?.route==='user' ? '인증 없이 접속한 시연 모드입니다. 계정 정보 화면은 추후 연결할 예정입니다.' : '홈 화면 연결까지 구현되었습니다. 이 상세 기능은 다음 개발 단계에서 연결할 예정입니다.'}</T>{dialog?.route==='user' && <Pressable accessibilityRole="button" style={s.logout} onPress={onLogout}><T style={s.logoutText}>로그인 화면으로 돌아가기</T></Pressable>}<Pressable accessibilityRole="button" style={s.close} onPress={() => setDialog(null)}><T style={s.closeText}>확인</T></Pressable></View></View></Modal>
  </SafeAreaView>;
}
const s = StyleSheet.create({
  text:{fontFamily:'NotoSansKR_400Regular',color:'#0C1535',includeFontPadding:false},safe:{flex:1,backgroundColor:'#FFF'},shell:{flex:1,width:'100%',maxWidth:520,alignSelf:'center'},loading:{flex:1,alignItems:'center',justifyContent:'center'},header:{height:68,paddingHorizontal:20,borderBottomWidth:1,borderColor:'#E1E8F4',flexDirection:'row',alignItems:'center',justifyContent:'space-between',backgroundColor:'#FFF'},brandRow:{flexDirection:'row',alignItems:'center',gap:8},brandCircle:{width:32,height:32,borderRadius:20,backgroundColor:NAVY,alignItems:'center',justifyContent:'center'},brandInitial:{color:'#FFF',fontFamily:'NotoSansKR_700Bold',fontSize:14,lineHeight:20},brandTitle:{fontSize:14,lineHeight:18,color:NAVY,fontFamily:'NotoSansKR_700Bold'},brandSubtitle:{fontSize:12,lineHeight:16,color:'#8A9BB8',marginTop:5},bell:{padding:8},dot:{position:'absolute',left:22,top:6,width:8,height:8,borderRadius:4,borderColor:'#FFF',borderWidth:1,backgroundColor:'#FB2C36'},
  scroller:{flex:1,backgroundColor:'#F2F5FA'},content:{padding:20},banner:{height:215,borderRadius:24,overflow:'hidden',paddingHorizontal:20,paddingTop:24},scanner:{position:'absolute',right:16,top:20},bannerTitle:{fontSize:20,lineHeight:28,color:'#FFF',fontFamily:'NotoSansKR_700Bold',letterSpacing:-0.5},bannerDescription:{fontSize:14,lineHeight:22.75,color:'#FFFFFFD9',marginTop:8,maxWidth:208},bannerCaption:{fontSize:12,lineHeight:16,color:'#FFFFFF8C',marginTop:12},analyzeButton:{position:'absolute',left:20,right:20,bottom:12,minHeight:44,borderRadius:24,backgroundColor:'#FFF',flexDirection:'row',alignItems:'center',justifyContent:'center',gap:8},analyzeText:{fontSize:14,lineHeight:20,fontFamily:'NotoSansKR_700Bold',color:NAVY},pressed:{opacity:0.7},
  recentSection:{marginTop:28},sectionHeading:{flexDirection:'row',alignItems:'center',justifyContent:'space-between'},heading:{fontFamily:'NotoSansKR_700Bold',fontSize:16,lineHeight:24,color:NAVY,letterSpacing:-0.4},inline:{flexDirection:'row',alignItems:'center'},allText:{fontSize:12,lineHeight:16,fontFamily:'NotoSansKR_500Medium'},recentCard:{marginTop:14,borderWidth:1,borderColor:'#E1E8F4',borderRadius:20,backgroundColor:'#FFF',overflow:'hidden'},record:{flexDirection:'row',alignItems:'center',gap:12,paddingHorizontal:12,paddingVertical:14,minHeight:77},recordBorder:{borderTopWidth:1,borderColor:'#E1E8F4'},thumbnail:{width:48,height:48,borderRadius:20},recordInfo:{flex:1,minWidth:0},recordTitle:{fontSize:14,lineHeight:20,fontFamily:'NotoSansKR_700Bold',color:NAVY},recordSubtitle:{fontSize:12,lineHeight:16,color:'#8A9BB8'},badge:{backgroundColor:'#ECFDF5',paddingHorizontal:10,paddingVertical:4,borderRadius:99},badgeText:{fontSize:12,lineHeight:16,fontFamily:'NotoSansKR_700Bold',color:'#007A55'},warning:{backgroundColor:'#FFFBEB'},warningText:{color:'#BB4D00'},empty:{padding:24,color:'#8A9BB8',textAlign:'center'},
  recovery:{marginTop:28,paddingVertical:20,borderTopWidth:1,borderBottomWidth:1,borderColor:'#E1E8F4'},recoveryHeading:{flexDirection:'row',justifyContent:'space-between',alignItems:'flex-start'},eyebrow:{color:'#8A9BB8',fontSize:12,lineHeight:16,fontFamily:'NotoSansKR_500Medium'},recoveryDescription:{fontSize:12,lineHeight:16,marginTop:4},photos:{marginTop:14,flexDirection:'row',gap:8,alignItems:'center'},photoColumn:{flex:1,minWidth:0},recoveryImage:{width:'100%',aspectRatio:1,borderRadius:20},date:{textAlign:'center',marginTop:6,fontSize:12,lineHeight:16,color:'#8A9BB8',fontFamily:'NotoSansKR_500Medium'},photoArrow:{width:12},recoveryFooter:{flexDirection:'row',justifyContent:'space-between',gap:8,marginTop:12},small:{fontSize:12,lineHeight:16},recoveryStatus:{fontSize:12,lineHeight:16,fontFamily:'NotoSansKR_700Bold',color:'#007A55'},
  tip:{marginTop:28,flexDirection:'row',gap:12,paddingBottom:4},tipIcon:{marginTop:2,width:28,height:28,borderRadius:20,borderWidth:1,borderColor:'#E1E8F4',backgroundColor:'#FFF',alignItems:'center',justifyContent:'center'},tipCopy:{flex:1,minWidth:0},tipTitle:{fontSize:12,lineHeight:16,fontFamily:'NotoSansKR_700Bold',color:NAVY},tipBody:{fontSize:12,lineHeight:19.5,marginTop:4},disclaimer:{fontSize:12,lineHeight:16,color:'#8A9BB8',textAlign:'center',marginTop:28,marginBottom:8},tabs:{flexDirection:'row',paddingTop:8,paddingHorizontal:16,borderTopWidth:1,borderColor:'#E8EDF5',backgroundColor:'#FFF'},tab:{flex:1,alignItems:'center',gap:2,paddingVertical:4},tabText:{fontSize:10,lineHeight:15,fontFamily:'NotoSansKR_700Bold',color:'#6B7A99'},
  overlay:{flex:1,backgroundColor:'#00000055',alignItems:'center',justifyContent:'center',padding:24},dialog:{width:'100%',maxWidth:380,padding:24,borderRadius:20,backgroundColor:'#FFF',gap:20},dialogBody:{fontSize:13,lineHeight:22,color:'#6B7A99'},close:{backgroundColor:'#2563EB',borderRadius:16,padding:14,alignItems:'center'},closeText:{color:'#FFF',fontSize:14,fontFamily:'NotoSansKR_700Bold'},logout:{borderWidth:1,borderColor:'#E1E8F4',borderRadius:16,padding:14,alignItems:'center'},logoutText:{fontSize:13,color:NAVY},
});

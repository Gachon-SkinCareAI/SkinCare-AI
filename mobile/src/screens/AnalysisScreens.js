import React from 'react';
import {View,StyleSheet} from 'react-native';
import {LinearGradient} from 'expo-linear-gradient';
import {useAssets} from 'expo-asset';
import {SvgUri} from 'react-native-svg';
import {T,Card,Button,C} from '../components/Common';
import {analysisDemo,riskLevels,demoCare} from '../data/analysisDemo';

export function ResultIcon({warning=false}) {
 const [assets]=useAssets([require('../../assets/results/check-circle.svg'),require('../../assets/results/alert.svg')]);
 const asset=assets?.[warning?1:0];
 return asset?<SvgUri uri={asset.localUri||asset.uri} width={20} height={20}/>:<View style={{width:20,height:20}}/>;
}
export function AnalysisResult({record,onRisk,onHistory}) {
 const result=record?.demo?analysisDemo[record.id]:null;
 if(!result)return <Card><T style={s.title}>분석 대기</T><T>아직 AI 분석 결과가 없어요. 등록한 사진은 서버 연결 후 분석할 수 있어요.</T><Button onPress={onHistory}>상처 기록 보기</Button></Card>;
 return <>
  <View style={s.banner}><View style={{flex:1}}><T style={s.white}>분석 완료</T><T style={{fontSize:10,color:'#FFFFFFB3'}}>아래 결과를 확인하세요.</T></View><ResultIcon/></View>
  <T style={s.demo}>예시 분석 결과</T>
  <View style={s.summary}><LinearGradient colors={['#FED7D7','#FECACA']} style={s.preview}><View style={s.outline}><T style={{fontSize:12,color:C.navy}}>사진</T></View><T style={s.region}>분석 영역</T></LinearGradient><View style={{padding:12}}><T style={s.title}>분석 요약</T>{[['의심 유형',result.type],['분석 부위',result.part],['분석 신뢰도',`${result.confidence}% (예시)`]].map(([label,value])=><View key={label} style={s.row}><T style={{flex:1,fontSize:11,color:C.muted}}>{label}</T><T style={{fontSize:11,fontFamily:'NotoSansKR_700Bold',maxWidth:'56%',textAlign:'right'}}>{value}</T></View>)}</View></View>
  <Button style={s.button} onPress={onRisk}>위험도 보기 →</Button>
  <View style={s.notice}><T style={{fontSize:10,lineHeight:17,color:'#92400E'}}>본 분석은 의학적 진단이 아니며 참고 목적으로만 활용해주세요. 증상이 심각하거나 빠르게 악화되는 경우 의료진의 진료를 받으세요.</T></View>
 </>;
}
export function CareGuide({record,saved,onSave,onHospital}) {
 const result=record?.demo?analysisDemo[record.id]:null;
 if(!result)return <Card><T>분석 결과가 없어 위험도를 표시할 수 없어요.</T></Card>;
 const active=riskLevels.find(r=>r.name===result.risk);
 return <><T style={s.title}>위험도 평가 결과</T><View style={{flexDirection:'row',gap:8}}>{riskLevels.map(level=><View key={level.name} accessibilityLabel={`${level.name}${level.name===result.risk?' · 평가 결과':''}`} style={[s.level,{borderColor:level.name===result.risk?level.color:C.line,backgroundColor:level.name===result.risk?level.bg:'white'}]}><View style={{width:14,height:14,borderRadius:7,backgroundColor:level.color}}/><T style={{fontSize:12,color:level.color,fontFamily:'NotoSansKR_700Bold'}}>{level.name}</T><T style={{fontSize:9,color:C.muted,textAlign:'center'}}>{level.caption}</T></View>)}</View>
 <View style={[s.notice,{backgroundColor:active.bg,borderColor:active.color}]}><View style={{flexDirection:'row',gap:8,alignItems:'center'}}>{result.risk==='경고'&&<ResultIcon warning/>}<T style={{fontSize:12,fontFamily:'NotoSansKR_700Bold',flex:1}}>현재 상태는 '{result.risk}' 단계입니다.</T></View><T style={{fontSize:11,lineHeight:18,marginTop:8}}>피그마 예시 기록의 평가입니다. 실제 위험도와 개인별 관리 방법은 분석 및 문진 결과가 연결되면 제공돼요.</T></View>
 <View style={[s.summary,{padding:12,gap:12}]}><T style={s.title}>맞춤 케어 가이드</T><T style={s.demo}>화면 시연용 안내</T>{demoCare.map((line,i)=><View key={line} style={{flexDirection:'row',gap:8}}><View style={s.number}><T style={{fontSize:10}}>{i+1}</T></View><T style={{flex:1,fontSize:11,lineHeight:20}}>{line}</T></View>)}<Button secondary style={{backgroundColor:'white',borderWidth:1,borderColor:C.navy,borderRadius:24,minHeight:36,padding:6}} onPress={onSave}>{saved?'✓ 관리 가이드 저장됨':'관리 가이드 저장'}</Button>{saved&&<T accessibilityRole="status" style={s.demo}>이 앱 실행 중 저장돼요.</T>}</View>
 <Button style={s.button} onPress={onHospital}>주변 의료기관 찾기 →</Button></>;
}
const s=StyleSheet.create({title:{fontSize:12,fontFamily:'NotoSansKR_700Bold',color:C.navy},banner:{backgroundColor:C.navy,borderRadius:24,padding:12,flexDirection:'row',gap:12,alignItems:'center'},white:{color:'white',fontSize:12,fontFamily:'NotoSansKR_700Bold'},demo:{fontSize:10,lineHeight:16,color:C.muted},summary:{backgroundColor:'white',borderRadius:24,overflow:'hidden'},preview:{height:128,alignItems:'center',justifyContent:'center'},outline:{width:64,height:64,borderRadius:20,borderWidth:2,borderStyle:'dashed',borderColor:'#0D2B6B88',alignItems:'center',justifyContent:'center'},region:{position:'absolute',right:8,top:8,color:'white',fontSize:9,lineHeight:16,backgroundColor:C.navy,paddingHorizontal:8,borderRadius:20},row:{flexDirection:'row',alignItems:'center',gap:8,paddingVertical:12,borderBottomWidth:1,borderColor:C.line},button:{borderRadius:24,minHeight:44},notice:{backgroundColor:'#FEF3C7',borderWidth:1,borderColor:'#FDE68A',padding:12,borderRadius:24},level:{flex:1,borderWidth:2,borderRadius:24,paddingVertical:10,alignItems:'center',gap:2},number:{width:20,height:20,borderRadius:10,backgroundColor:'#EFF6FF',alignItems:'center',justifyContent:'center'}});

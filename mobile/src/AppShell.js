import React,{useEffect,useRef,useState} from 'react';
import {BackHandler,Pressable,StatusBar} from 'react-native';
import HomeScreen from './screens/HomeScreen';
import CaptureScreen from './screens/CaptureScreen';
import {AnalysisResult,CareGuide} from './screens/AnalysisScreens';
import {RecordList,RecordDetail,demoRecords} from './screens/RecordScreens';
import WoundFormScreen from './screens/WoundFormScreen';
import CompareScreen from './screens/CompareScreen';
import EncyclopediaScreen from './screens/EncyclopediaScreen';
import FacilitiesScreen from './screens/FacilitiesScreen';
import ProfileScreen from './screens/ProfileScreen';
import {Layout,Button,Card,T} from './components/Common';
import {today} from './utils/draft';
import {validateEntry,appendEntry} from './utils/serviceState';
export default function AppShell({onLogout}){
 const [stack,setStack]=useState([{name:'home'}]),[records,setRecords]=useState(demoRecords),[photo,setPhoto]=useState(null),[date,setDate]=useState(today),[target,setTarget]=useState(null);
 const [symptoms,setSymptoms]=useState({}),[memo,setMemo]=useState(''),[error,setError]=useState(''),[savedGuides,setSavedGuides]=useState({}),[inquiries,setInquiries]=useState([]);
 const saving=useRef(false),sequence=useRef(0),route=stack.at(-1),record=records.find(r=>r.id===route.id),targetRecord=records.find(r=>r.id===target);
 function back(){setStack(s=>s.length>1?s.slice(0,-1):[{name:'home'}]);}
 function push(name,params={}){setStack(s=>[...s,{name,...params}]);}
 function start(id=null){saving.current=false;setError('');setTarget(id);setPhoto(null);setDate(today());setSymptoms({});setMemo('');push('new');}
 function navigate(name,params={}){if(name==='analysis')return start();if(name==='analysisDetail')return push('result',params);if(name==='recovery')return push('detail',params);if(['home','history','book','pin','user'].includes(name))setStack([{name}]);else push(name,params);}
 useEffect(()=>{const h=BackHandler.addEventListener('hardwareBackPress',()=>{if(route.name==='home')return false;back();return true});return()=>h.remove()},[route.name,stack.length]);
 function save(){if(saving.current)return;const message=validateEntry({photo,date,symptoms},targetRecord);setError(message);if(message)return;saving.current=true;const id=`local-${Date.now()}-${++sequence.current}`,entry={date,image:{uri:photo.uri},symptoms:{...symptoms},memo:memo.trim()};setRecords(rs=>appendEntry(rs,target,entry,id));setStack([{name:'history'},{name:'detail',id:target||id}]);setPhoto(null);setTarget(null);}
 if(route.name==='home')return <><StatusBar barStyle="dark-content"/><HomeScreen onLogout={onLogout} onNavigate={navigate} analyses={records}/></>;
 if(route.name==='capture')return <CaptureScreen initialMode={route.mode} photo={photo} onSelect={setPhoto} onBack={back} onContinue={back}/>;
 const titles={result:'AI 분석 결과',care:'위험도 평가 & 케어 가이드',history:'경과 기록',detail:'상처 경과 상세',compare:'이전·현재 사진 비교',new:target?'경과 기록 추가':'신규 상처 등록',book:'피부 질환 백과',pin:'의료기관 연결',user:'마이',notifications:'알림'};
 let content;
 if(route.name==='history')content=<RecordList records={records} onOpen={id=>push('detail',{id})} onAdd={start}/>;
 else if(route.name==='detail')content=<RecordDetail key={record?.id} record={record} onAdd={start} onResult={()=>push('result',{id:record.id})} onCompare={()=>push('compare',{id:record.id})} onToggleComplete={()=>setRecords(rs=>rs.map(r=>r.id===record.id?{...r,complete:!r.complete}:r))}/>;
 else if(route.name==='compare')content=<CompareScreen record={record}/>;
 else if(route.name==='result')content=<AnalysisResult record={record} onRisk={()=>push('care',{id:record.id})} onHistory={()=>push('detail',{id:record?.id})}/>;
 else if(route.name==='care')content=<CareGuide record={record} saved={savedGuides[record?.id]} onSave={()=>setSavedGuides(g=>({...g,[record.id]:!g[record.id]}))} onHospital={()=>navigate('pin')}/>;
 else if(route.name==='new')content=<WoundFormScreen record={targetRecord} photo={photo} date={date} onDate={value=>{setDate(value);setError('')}} symptoms={symptoms} onSymptoms={value=>{setSymptoms(value);setError('')}} memo={memo} onMemo={setMemo} error={error} onPhoto={mode=>push('capture',{mode})} onRemove={()=>{setPhoto(null);setError('')}}/>;
 else if(route.name==='book')content=<EncyclopediaScreen/>;
 else if(route.name==='pin')content=<FacilitiesScreen/>;
 else if(route.name==='user')content=<ProfileScreen onLogout={onLogout} inquiries={inquiries} onSaveInquiry={q=>setInquiries(items=>[q,...items])}/>;
 else content=<Card><T>새로운 알림이 없어요.</T></Card>;
 const footer=route.name==='new'?<Button onPress={save}>{target?'경과 기록 저장':'새 상처 분석 시작'}</Button>:route.name==='compare'?<Button onPress={back}>상세 기록으로 돌아가기</Button>:null;
 const right=route.name==='history'?<Pressable accessibilityRole="button" accessibilityLabel="새 상처 등록" onPress={()=>start()} style={{width:34,height:34,borderRadius:20,backgroundColor:'#0D2B6B',alignItems:'center',justifyContent:'center'}}><T style={{color:'white',fontSize:24}}>＋</T></Pressable>:route.name==='new'?<Pressable accessibilityRole="button" onPress={back} style={{padding:8}}><T style={{fontSize:12}}>취소</T></Pressable>:null;
 return <><StatusBar barStyle="dark-content"/><Layout key={route.name+':'+(route.id||'')} showTabs={!['result','care','detail'].includes(route.name)} compact={['result','care','new','book','pin'].includes(route.name)} title={titles[route.name]||'SkinCare AI'} onBack={back} active={['book','pin','user'].includes(route.name)?route.name:'history'} onNavigate={navigate} footer={footer} right={right}>{content}</Layout></>;
}

import React,{useEffect,useState} from 'react';
import {BackHandler,Pressable,StatusBar} from 'react-native';
import HomeScreen from './screens/HomeScreen';
import {AnalysisResult,CareGuide} from './screens/AnalysisScreens';
import CaptureScreen from './screens/CaptureScreen';
import {RecordList,NewWound,RecordDetail,demoRecords} from './screens/RecordScreens';
import {Layout,Button,Card,T} from './components/Common';
import {today,validateDate} from './utils/draft';
export default function AppShell({onLogout}){
 const [error,setError]=useState('');
 const [savedGuides,setSavedGuides]=useState({});
 const [stack,setStack]=useState([{name:'home'}]),[records,setRecords]=useState(demoRecords),[photo,setPhoto]=useState(null),[date,setDate]=useState(today),[target,setTarget]=useState(null);const route=stack.at(-1);
 function back(){setStack(s=>s.length>1?s.slice(0,-1):[{name:'home'}]);}
 function push(name,params={}){setStack(s=>[...s,{name,...params}]);}
 function start(id=null){setError('');setTarget(id);setPhoto(null);setDate(today());push('capture');}
 function navigate(name,params={}){if(name==='analysis')return start();if(name==='analysisDetail')return push('result',params);if(name==='recovery')return push('detail',params);if(['home','history','book','pin','user'].includes(name))setStack([{name}]);else push(name,params);}
 useEffect(()=>{const h=BackHandler.addEventListener('hardwareBackPress',()=>{if(route.name==='home')return false;back();return true});return()=>h.remove()},[route.name,stack.length]);
 function save(){const message=validateDate(date)||(!photo?'사진을 선택해주세요.':'');setError(message);if(message)return;if(target){setRecords(rs=>rs.map(r=>r.id===target?{...r,count:(r.count||1)+1,updates:[{date,image:{uri:photo.uri}},...(r.updates||[])]}:r));setStack([{name:'history'},{name:'detail',id:target}]);}else{const id=`local-${Date.now()}`;setRecords(rs=>[{id,title:'신규 상처',summary:'사진 등록 · 분석 대기',status:'분석 대기',date,image:{uri:photo.uri},demo:false,count:1},...rs]);setStack([{name:'history'},{name:'detail',id}]);}setPhoto(null);setTarget(null);}
 if(route.name==='home')return <><StatusBar barStyle="dark-content"/><HomeScreen onLogout={onLogout} onNavigate={navigate} analyses={records}/></>;
 if(route.name==='capture')return <CaptureScreen initialMode={route.mode} photo={photo} onSelect={setPhoto} onBack={back} onContinue={()=>{if(stack.some(r=>r.name==='new'))back();else push('new')}}/>;
 const titles={result:'AI 분석 결과',care:'위험도 평가 & 케어 가이드',history:'경과 기록',detail:'상처 경과 상세',new:target?'경과 기록 추가':'신규 상처 등록',book:'피부 질환 백과',pin:'주변 병원',user:'마이',notifications:'알림'};
 const record=records.find(r=>r.id===route.id);
 return <><StatusBar barStyle="dark-content"/><Layout key={route.name+':'+(route.id||'')} showTabs={!['result','care','detail'].includes(route.name)} compact={['result','care'].includes(route.name)} title={titles[route.name]||'SkinCare AI'} onBack={back} active={['book','pin','user'].includes(route.name)?route.name:'history'} onNavigate={navigate} footer={route.name==='new'?<Button onPress={save}>{target?'경과 기록 저장':'새 상처 분석 시작'}</Button>:null} right={route.name==='history'?<Pressable accessibilityRole="button" accessibilityLabel="새 상처 등록" onPress={()=>start()} style={{width:34,height:34,borderRadius:20,backgroundColor:'#0D2B6B',alignItems:'center',justifyContent:'center'}}><T style={{color:'white',fontSize:24}}>＋</T></Pressable>:null}>
 {route.name==='history'?<RecordList records={records} onOpen={id=>push('detail',{id})} onAdd={start}/>:route.name==='detail'?<RecordDetail key={record?.id} record={record} onAdd={start} onResult={()=>push('result',{id:record.id})} onToggleComplete={()=>setRecords(rs=>rs.map(r=>r.id===record.id?{...r,complete:!r.complete}:r))}/>:route.name==='result'?<AnalysisResult record={record} onRisk={()=>push('care',{id:record.id})} onHistory={()=>push('detail',{id:record?.id})}/>:route.name==='care'?<CareGuide record={record} saved={savedGuides[record?.id]} onSave={()=>setSavedGuides(g=>({...g,[record.id]:!g[record.id]}))} onHospital={()=>navigate('pin')}/>:route.name==='new'?<NewWound photo={photo} date={date} setDate={value=>{setDate(value);setError('')}} error={error} target={target} onChangePhoto={()=>push('capture',{mode:'gallery'})} onSubmit={save}/>:<Card><T>{route.name==='user'?'인증 없이 접속한 시연 모드예요.':route.name==='notifications'?'새로운 알림이 없어요.':'이 화면은 준비 중이에요.'}</T>{route.name==='user'&&<Button onPress={onLogout}>로그인 화면으로 돌아가기</Button>}</Card>}
 </Layout></>;
}

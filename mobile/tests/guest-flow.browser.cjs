const {chromium}=require(process.env.PLAYWRIGHT_MODULE || 'playwright');
const fs=require('fs'),path=require('path'),assert=require('assert/strict');
const out=path.resolve(process.env.SKINCARE_TEST_OUTPUT || path.join(__dirname,'../test-results/guest-flow'));
const exported=path.resolve(process.env.SKINCARE_WEB_EXPORT || path.join(__dirname,'../dist-guest-test'));
fs.mkdirSync(out,{recursive:true});

const photo=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=','base64');
const checks=[],errors=[];
(async()=>{
 const browser=await chromium.launch({headless:true,executablePath:process.env.CHROMIUM_PATH||undefined,args:['--no-proxy-server']});
 const context=await browser.newContext({viewport:{width:412,height:915}});
 const page=await context.newPage();page.on('pageerror',e=>errors.push(e.message));
 await page.route('**/*',route=>route.abort()); // No requests to real accounts or production services.
 // Serve this exported build through a browser route; no shared host port is required.
 await page.route('http://127.0.0.1:8187/**',async route=>{
   let pathname=decodeURIComponent(new URL(route.request().url()).pathname);
   const file=path.resolve(exported,'.'+(pathname==='/'?'/index.html':pathname));
   if(!file.startsWith(exported+path.sep)||!fs.existsSync(file))return route.fulfill({status:404,body:'not found'});
   const ext=path.extname(file),types={'.html':'text/html','.js':'application/javascript','.css':'text/css','.svg':'image/svg+xml','.png':'image/png','.ttf':'font/ttf'};
   return route.fulfill({status:200,contentType:types[ext]||'application/octet-stream',body:fs.readFileSync(file)});
 });
 let rows=[],training=false,location=false,kind='guest',guestRequest=null,signupRequest=null,gpsCalls=0,uploads=0,inquiries=[],guestListCalls=0;
 const consent=()=>({trainingGranted:training,trainingAvailable:true,trainingVersion:'2026-11-04',trainingPolicyUrl:'/api/wc/policies/2026-11-04/training',locationGranted:location,locationVersion:'2026-11-04',locationPolicyUrl:'/api/wc/policies/2026-11-04/location'});
 const question={question_id:'qa.confirm',utterance:'테스트 질문: 현재 상태를 확인할까요?',choices:[{value:'none',label:'테스트 응답'}]};
 await page.route('http://127.0.0.1:9800/**',async route=>{
  const request=route.request(),url=new URL(request.url()),p=url.pathname,method=request.method();let body={};
  try{body=request.postDataJSON()||{}}catch{}
  let data={},status=200;
  if(method==='OPTIONS')return route.fulfill({status:204,headers:{'Access-Control-Allow-Origin':'*','Access-Control-Allow-Headers':'*','Access-Control-Allow-Methods':'GET,POST,PUT,DELETE,OPTIONS'}});
  if(p.endsWith('/image'))return route.fulfill({status:200,contentType:'image/png',body:photo,headers:{'Access-Control-Allow-Origin':'*'}});
  if(p==='/api/wc/auth/guest/policy')data={version:'2026-11-04',serviceUrl:'/api/wc/policies/2026-11-04/guest',healthUrl:'/api/wc/policies/2026-11-04/guest#health-data',trainingUrl:'/api/wc/policies/2026-11-04/training',trainingVersion:'2026-11-04'};
  else if(p==='/api/wc/auth/guest'){guestRequest=body;training=body.trainingGranted;data={token:'synthetic-guest-token',guest:true};}
  else if(p==='/api/wc/auth/guest/resume')data={token:'synthetic-guest-token'};
  else if(p==='/api/wc/auth/social/providers')data=['google','kakao','naver'].map(id=>({id,label:{google:'구글',kakao:'카카오',naver:'네이버'}[id],enabled:true}));
  else if(p==='/api/wc/auth/social/start')data={authorizationPath:'/oauth2/authorization/google?flow='+'a'.repeat(43)};
  else if(p==='/oauth2/authorization/google')return route.fulfill({status:200,contentType:'text/html',body:'<!doctype html><script>location.replace("http://127.0.0.1:8187/#oauth_code='+('b'.repeat(43))+'")</script>'});
  else if(p==='/api/wc/auth/social/exchange'){kind='member';data={token:'synthetic-member-token',consentRequired:true,userId:'synthetic-member'};}
  else if(p==='/api/wc/auth/consent-policy')data={requiredAvailable:true,trainingAvailable:true,...Object.fromEntries(['terms','privacy','healthData','location','training'].map(key=>[key,{version:['location','training'].includes(key)?'2026-11-04':'2026-11-03',url:'/api/wc/policies/2026-11-04/'+key}]))};
  else if(p.endsWith('/consents')&&method==='PUT'){signupRequest=body;location=body.agreedLocation;data={};}
  else if(p.endsWith('/consents'))data=consent();
  else if(p.endsWith('/consents/training')){training=body.granted;if(!training)rows.forEach(r=>{r.trainingEligible=false;r.trainingReviewStatus='WITHDRAWN'});status=204;}
  else if(p.endsWith('/consents/location')){location=body.granted;status=204;}
  else if(p==='/api/wc/users/me')data={userId:'synthetic-member',nickname:'테스트 사용자',provider:'GOOGLE',email:'qa@example.test'};
  else if(p==='/api/wc/inquiries'&&method==='POST'){data={id:'inquiry-'+(inquiries.length+1),...body,status:'WAITING',createdAt:'2026-11-06T12:00:00'};inquiries.unshift(data);status=201;}
  else if(p==='/api/wc/inquiries')data={items:inquiries,page:0,size:20,total:inquiries.length,hasNext:false};
  else if(p.startsWith('/api/wc/inquiries/'))data=inquiries.find(r=>p.endsWith(r.id));
  else if(p==='/api/wc/auth/logout')data={};
  else if(p==='/api/wc/analyses'&&method==='POST'){
    uploads++;assert.match(request.postData()||'',/clientRequestId/);
    const multipart=request.postData()||'',date=multipart.match(/name="capturedOn"\r?\n\r?\n([^\r\n]+)/)?.[1],parent=multipart.match(/name="parentId"\r?\n\r?\n([^\r\n]+)/)?.[1];
    const id='qa-record-'+uploads;data={id,caseId:rows.find(r=>r.id===parent)?.caseId||id,capturedOn:date,progress:{painScore:null,note:'',swelling:null,exudate:null},status:'QUESTIONNAIRE',version:1,turn:0,createdAt:'2026-11-04T10:00:00',imagePath:'/api/wc/analyses/'+id+'/image',slots:{},vision:{classification:{label:'UNKNOWN'}},question,result:null,answers:[],trainingEligible:training,trainingReviewStatus:training?'PENDING':'NOT_ELIGIBLE'};rows.unshift(data);status=201;
  } else if(p==='/api/wc/analyses'){if(kind==='guest')guestListCalls++;data=rows.filter(r=>!url.searchParams.get('caseId')||r.caseId===url.searchParams.get('caseId')).slice((+url.searchParams.get('page')||0)*20,(+url.searchParams.get('page')||0)*20+20);}
  else if(p.endsWith('/progress')){const row=rows.find(r=>p.includes(r.id));assert.equal(body.expectedVersion,row.version);row.version++;row.capturedOn=body.capturedOn;row.progress={painScore:body.painScore,note:body.note,swelling:body.swelling,exudate:body.exudate};data=row;}
  else if(p.endsWith('/comparison')){const current=rows.find(r=>p.includes(r.id)),reference=rows.find(r=>r.id===url.searchParams.get('referenceId'));assert.equal(current.caseId,reference.caseId);data={current,reference,sameRecord:current.id===reference.id,recordCount:rows.length};}
  else if(p.endsWith('/answers')){
    const row=rows.find(r=>p.includes(r.id));assert.equal(body.expectedVersion,row.version);assert.match(body.clientRequestId,/^[\da-f-]{36}$/);
    Object.assign(row,{status:'COMPLETED',version:2,turn:1,question:null,result:{level:'UNKNOWN',guide_available:false},level:'UNKNOWN',answers:[{questionId:question.question_id,questionText:question.utterance,answer:body.answer,answerLabel:'테스트 응답',userText:'',turn:1}]});data=row;
  } else if(p.startsWith('/api/wc/analyses/'))data=rows.find(r=>p.endsWith(r.id));
  else if(p==='/api/wc/guest'&&method==='DELETE'){rows=[];status=204;}
  else if(p==='/api/wc/hospitals/public/nearby'){gpsCalls++;data={status:'OK',items:[]};}
  else if(p==='/api/wc/hospitals/public')data={status:'OK',items:[]};
  else{status=404;data={message:'Unmocked QA route '+p};}
  await route.fulfill({status,contentType:'application/json',headers:{'Access-Control-Allow-Origin':'*'},body:status===204?'':JSON.stringify(data)});
 });
 await page.goto('http://127.0.0.1:8187/');
 await page.getByTestId('develop-welcome').waitFor();
 await page.screenshot({path:path.join(out,'develop-welcome.png'),fullPage:true});
 await page.getByRole('button',{name:'로그인 없이 시작하기',exact:true}).click();
 await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'구글로 계속하기'}).count(),0);
 assert.equal(await page.getByText('최근 분석',{exact:true}).count(),0);assert.equal(await page.getByTestId('risk-badge').count(),0);
 await page.screenshot({path:path.join(out,'home-guest.png')});checks.push('Development welcome leads to the original home layout with real empty records.');
 await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).click();
 await page.getByRole('tab',{name:'갤러리',exact:true}).click();
 const chooser=page.waitForEvent('filechooser');await page.getByRole('button',{name:'파일에서 직접 업로드',exact:true}).click();
 await (await chooser).setFiles({name:'synthetic-qa.png',mimeType:'image/png',buffer:photo});
 await page.getByRole('button',{name:'이 사진으로 계속',exact:true}).click();
 await page.getByRole('checkbox',{name:'[선택] 이후 제출할 자료의 AI 모델 개선용 이용 동의',exact:true}).waitFor();
 assert.equal(await page.getByRole('checkbox').count(),3);
 for(const c of await page.getByRole('checkbox').all())assert.equal(await c.getAttribute('aria-checked'),'false');
 assert.equal(await page.getByRole('button',{name:'사진 분석하고 문진 시작',exact:true}).getAttribute('aria-disabled'),'true');
 await page.getByRole('checkbox',{name:'[필수] 비회원 서비스 이용 및 개인정보 처리 동의',exact:true}).click();
 await page.getByRole('checkbox',{name:'[필수] 상처 사진·문진 등 건강정보 처리 동의',exact:true}).click();
 await page.screenshot({path:path.join(out,'guest-consents.png'),fullPage:true});
 await page.getByRole('button',{name:'사진 분석하고 문진 시작',exact:true}).click();
 await page.getByRole('button',{name:'테스트 응답',exact:true}).waitFor();
 assert.equal(guestRequest.trainingGranted,false);assert.match(guestRequest.deviceKey,/^[a-f0-9]{64}$/);checks.push('Guest upload requires both mandatory consents; training remains optional and false.');
 await page.getByRole('button',{name:'테스트 응답',exact:true}).click();
 await page.getByText('판단 불가',{exact:true}).waitFor();await page.screenshot({path:path.join(out,'guest-result.png'),fullPage:true});
 await page.getByRole('tab',{name:'홈',exact:true}).click();
 assert.equal(await page.getByText('최근 분석',{exact:true}).count(),0);assert.equal(await page.getByTestId('risk-badge').count(),0);
 await page.screenshot({path:path.join(out,'guest-home-no-history.png')});
 await page.getByRole('tab',{name:'기록',exact:true}).click();await page.getByText('비회원은 이번 분석만 확인해요',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'결과·경과 기록 보기',exact:true}).count(),0);assert.equal(guestListCalls,0);
 await page.screenshot({path:path.join(out,'guest-history-restricted.png')});
 await page.getByRole('button',{name:'이번 분석 이어서 보기',exact:true}).click();await page.getByText('저장된 문진 답변',{exact:true}).waitFor();
 assert.equal(await page.getByRole('button',{name:'경과 기록 저장',exact:true}).count(),0);assert.equal(await page.getByRole('button',{name:'촬영일 선택·사진 비교',exact:true}).count(),0);
 checks.push('Guest completes current questionnaire/result without home/history listings, API list requests or progress/comparison controls.');
 await page.getByRole('tab',{name:'기록',exact:true}).click();await page.getByRole('button',{name:'새 상처 사진 분석',exact:true}).click();
 await page.getByRole('button',{name:'뒤로',exact:true}).click();await page.getByRole('tab',{name:'기록',exact:true}).click();
 assert.equal(await page.getByRole('button',{name:'이번 분석 이어서 보기',exact:true}).count(),0);assert.equal(rows.length,1);assert.equal(guestListCalls,0);
 checks.push('Starting another guest analysis clears the previous result from navigation while preserving server data.');
 await page.getByRole('tab',{name:'마이',exact:true}).click();
 assert.equal(await page.getByText('선택 동의 관리',{exact:true}).count(),0);assert.equal(await page.getByRole('checkbox').count(),0);
 await page.screenshot({path:path.join(out,'my-guest.png'),fullPage:true});
 await page.getByRole('button',{name:'개인정보 설정',exact:true}).click();
 await page.getByRole('checkbox',{name:'이후 제출할 자료의 학습용 이용 동의',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('[role=checkbox]')?.getAttribute('aria-checked')==='true');
 await page.getByRole('checkbox',{name:'이후 제출할 자료의 학습용 이용 동의',exact:true}).click();
 await page.waitForFunction(()=>document.querySelector('[role=checkbox]')?.getAttribute('aria-checked')==='false');
 await page.getByRole('tab',{name:'마이',exact:true}).click();
 await page.getByRole('button',{name:'비회원 자료 전체 삭제',exact:true}).click();
 await page.getByRole('textbox',{name:'확인하려면 삭제 입력',exact:true}).fill('삭제');
 await page.getByRole('button',{name:'삭제 요청 확정',exact:true}).click();
 await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).waitFor();checks.push('Training opt-in/withdrawal and explicit guest erasure work.');
 await page.getByRole('tab',{name:'마이',exact:true}).click();await page.getByRole('button',{name:'소셜 로그인·회원가입',exact:true}).click();
 await page.getByRole('button',{name:'구글로 계속하기',exact:true}).click();
 await page.getByText('처음 이용하기 전에 확인해 주세요',{exact:true}).waitFor();
 assert.equal(await page.getByRole('tab',{name:'마이',exact:true}).getByText('마이',{exact:true}).evaluate(el=>getComputedStyle(el).color),'rgb(13, 43, 107)');
 const gps=page.getByRole('checkbox',{name:'[선택] 주변 병원·약국 검색을 위한 위치정보 수집·이용 동의',exact:true});
 assert.equal(await page.getByRole('checkbox').count(),5);for(const item of await page.getByRole('checkbox').all())assert.equal(await item.getAttribute('aria-checked'),'false');
 await page.getByRole('button',{name:'전체 동의 (선택 항목 포함)',exact:true}).click();
 for(const item of await page.getByRole('checkbox').all())assert.equal(await item.getAttribute('aria-checked'),'true');
 await gps.click();await page.getByRole('button',{name:'전체 동의 (선택 항목 포함)',exact:true}).waitFor();
 await page.getByRole('button',{name:'전체 동의 (선택 항목 포함)',exact:true}).click();
 await page.screenshot({path:path.join(out,'signup-all-selected.png'),fullPage:true});
 await page.getByRole('button',{name:'전체 동의 해제',exact:true}).click();
 for(const item of await page.getByRole('checkbox').all())assert.equal(await item.getAttribute('aria-checked'),'false');
 const signupAll=process.env.SKINCARE_TEST_SIGNUP_ALL==='1';
 if(signupAll)await page.getByRole('button',{name:'전체 동의 (선택 항목 포함)',exact:true}).click();
 else for(const text of ['[필수] 이용약관 동의','[필수] 개인정보 처리 동의','[필수] 건강정보 처리 동의'])await page.getByRole('checkbox',{name:text,exact:true}).click();
 checks.push('Signup starts unchecked; agree-all selects available optional items, individual changes update its state, and clear-all works.');
 await page.screenshot({path:path.join(out,'signup-location.png'),fullPage:true});
 await page.getByRole('button',{name:'선택한 내용으로 가입 완료',exact:true}).click();
 await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).waitFor();assert.equal(signupRequest.agreedLocation,signupAll);assert.equal(training,signupAll);assert.equal(gpsCalls,0);
 await page.getByRole('tab',{name:'마이',exact:true}).click();assert.equal(await page.getByText('선택 동의 관리',{exact:true}).count(),0);assert.equal(await page.getByRole('checkbox').count(),0);
 await page.screenshot({path:path.join(out,'my-member.png'),fullPage:true});
 await page.getByRole('button',{name:'개인정보 설정',exact:true}).click();
 const storedLocation=page.getByRole('checkbox',{name:'주변 병원·약국 검색을 위한 위치정보 수집·이용 동의',exact:true});
 assert.equal(await storedLocation.getAttribute('aria-checked'),String(signupAll));
 await page.screenshot({path:path.join(out,'privacy-settings.png'),fullPage:true});
 if(signupAll){await storedLocation.click();await page.waitForFunction(()=>document.querySelector('[aria-label="주변 병원·약국 검색을 위한 위치정보 수집·이용 동의"]')?.getAttribute('aria-checked')==='false');}
 checks.push('Signup optional choices reach existing APIs and remain editable in separate privacy settings; My has no inline consent card.');
 await page.getByRole('tab',{name:'병원',exact:true}).click();
 await page.getByRole('button',{name:'개인정보 설정에서 위치 동의',exact:true}).waitFor();assert.equal(gpsCalls,0);checks.push('Location refusal or withdrawal blocks nearby search while preserving signup and region search.');
 if(process.env.SKINCARE_TEST_RISK_LEVELS==='1'||process.env.SKINCARE_TEST_SERVICES==='1'){
   await page.getByRole('tab',{name:'홈',exact:true}).click();await page.getByText('최근 분석',{exact:true}).waitFor();
   await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).click();await page.getByRole('tab',{name:'갤러리',exact:true}).click();
   const memberChooser=page.waitForEvent('filechooser');await page.getByRole('button',{name:'파일에서 직접 업로드',exact:true}).click();await (await memberChooser).setFiles({name:'member-synthetic.png',mimeType:'image/png',buffer:photo});
   await page.getByRole('button',{name:'이 사진으로 계속',exact:true}).click();await page.getByRole('button',{name:'사진 분석하고 문진 시작',exact:true}).click();await page.getByRole('button',{name:'테스트 응답',exact:true}).click();await page.getByText('판단 불가',{exact:true}).waitFor();
   await page.getByRole('tab',{name:'기록',exact:true}).click();await page.getByRole('button',{name:'결과·경과 기록 보기',exact:true}).click();await page.getByText('저장된 문진 답변',{exact:true}).waitFor();
   checks.push('Member history and saved answers remain available after guest history is hidden.');
 }
 if(process.env.SKINCARE_TEST_RISK_LEVELS==='1'){
   for(const [code,title,stage,color]of [['OBSERVATION','관찰',1,'rgb(23, 99, 76)'],['WARNING','경고',2,'rgb(138, 73, 0)'],['EMERGENCY','응급',3,'rgb(185, 28, 28)'],['UNKNOWN','판단 불가',null,'rgb(71, 85, 105)'],['UNSUPPORTED','판단 불가',null,'rgb(71, 85, 105)'],['NORMAL','정상',null,'rgb(71, 85, 105)']]){
     rows[0].level=code;rows[0].result.level=code;
     await page.getByRole('button',{name:'저장된 상태 새로 불러오기',exact:true}).click();
     const headline=page.getByTestId('risk-summary').getByText(title,{exact:true});await headline.waitFor();
     assert.equal(await headline.evaluate(el=>getComputedStyle(el).color),color);
     assert.equal(await page.locator('[data-testid^="risk-step-"]').count(),stage===null?0:3);
     if(stage!==null){await page.getByText(`${stage}/3단계`,{exact:true}).waitFor();assert.equal(await page.getByText('현재 단계',{exact:true}).count(),1);}
     else await page.getByText(code==='NORMAL'?'현재 위험도 3단계에 포함되지 않는 이전 결과예요.':'판단 불가는 위험도 3단계와 별도 상태이며 정상 판정을 뜻하지 않아요.',{exact:true}).waitFor();
     await page.getByTestId('risk-summary').evaluate(el=>{for(let p=el.parentElement;p;p=p.parentElement)if(p.scrollHeight>p.clientHeight+1)p.scrollTop=0;});
     await page.screenshot({path:path.join(out,`risk-${code}.png`)});
     const badgeText=stage!==null?`${title} · ${stage}단계`:code==='NORMAL'?'정상 · 이전 결과':title;
     await page.getByRole('tab',{name:'홈',exact:true}).click();
     const badge=page.getByTestId('risk-badge').getByText(badgeText,{exact:true});await badge.waitFor();
     assert.equal(await badge.evaluate(el=>getComputedStyle(el).color),color);
     assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'Home must fit phone width');
     if(code==='EMERGENCY'||code==='NORMAL'){
       await page.setViewportSize({width:320,height:800});
       const bounds=await page.getByTestId('risk-badge').boundingBox();assert.ok(bounds.x>=0&&bounds.x+bounds.width<=320);
       assert.ok(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth));
       await page.screenshot({path:path.join(out,`home-risk-narrow-${code}.png`)});
       await page.setViewportSize({width:412,height:915});
     }
     await page.getByRole('tab',{name:'기록',exact:true}).click();
     await page.getByTestId('risk-badge').getByText(badgeText,{exact:true}).waitFor();
     await page.getByRole('button',{name:'결과·경과 기록 보기',exact:true}).click();await page.getByTestId('risk-summary').getByText(title,{exact:true}).waitFor();
     checks.push(`${code}: result, home and history agree; unknown and legacy NORMAL remain outside the three risk stages.`);
   }
 }
 if(process.env.SKINCARE_TEST_SERVICES==='1'){
   await page.getByRole('textbox',{name:'촬영일 (YYYY-MM-DD, 모르면 비워 두기)',exact:true}).fill('2025-02-30');
   await page.getByRole('button',{name:'경과 기록 저장',exact:true}).click();await page.getByText('올바른 날짜를 입력해주세요.',{exact:true}).waitFor();
   await page.getByRole('textbox',{name:'촬영일 (YYYY-MM-DD, 모르면 비워 두기)',exact:true}).fill('2025-02-28');
   await page.getByRole('textbox',{name:'통증 점수 0~10 (선택)',exact:true}).fill('4');
   await page.getByRole('button',{name:'붓기 있음',exact:true}).click();await page.getByRole('button',{name:'진물 잘 모르겠음',exact:true}).click();
   await page.getByRole('textbox',{name:'메모 (선택)',exact:true}).fill('합성 테스트 경과 메모');
   await page.getByRole('button',{name:'경과 기록 저장',exact:true}).click();await page.getByText('경과 기록을 저장했어요.',{exact:true}).waitFor();
   await page.getByRole('button',{name:'저장된 상태 새로 불러오기',exact:true}).click();
   assert.equal(await page.getByRole('textbox',{name:'통증 점수 0~10 (선택)',exact:true}).inputValue(),'4');
   await page.getByRole('button',{name:'붓기 있음 ✓',exact:true}).waitFor();checks.push('Capture date and pain/swelling/exudate/note save and reload; invalid calendar dates rejected.');
   await page.getByRole('button',{name:'이 상처의 경과 사진 추가',exact:true}).click();
   await page.getByRole('tab',{name:'갤러리',exact:true}).click();const nextChooser=page.waitForEvent('filechooser');await page.getByRole('button',{name:'파일에서 직접 업로드',exact:true}).click();
   await (await nextChooser).setFiles({name:'synthetic-followup.png',mimeType:'image/png',buffer:photo});await page.getByRole('button',{name:'이 사진으로 계속',exact:true}).click();
   await page.getByRole('textbox',{name:'사진 촬영일 (YYYY-MM-DD)',exact:true}).fill('2025-03-01');
   await page.getByRole('button',{name:'사진 분석하고 문진 시작',exact:true}).click();await page.getByRole('button',{name:'테스트 응답',exact:true}).click();
   await page.getByRole('button',{name:'촬영일 선택·사진 비교',exact:true}).click();await page.getByRole('button',{name:'이전 사진 확대',exact:true}).waitFor();
   assert.equal(rows[0].caseId,rows[1].caseId);assert.equal(rows[0].capturedOn,'2025-03-01');
   await page.getByText('통증 4',{exact:true}).waitFor();await page.screenshot({path:path.join(out,'comparison.png'),fullPage:true});
   await page.getByRole('button',{name:'이전 사진 확대',exact:true}).click();await page.getByRole('button',{name:'확대',exact:true}).click();await page.getByText('2배 · 확대 후 좌우·위아래로 이동할 수 있어요.',{exact:true}).waitFor();
   await page.getByRole('button',{name:'사진 닫기',exact:true}).click();checks.push('Follow-up retains same case; two shooting dates compare with authenticated zoomable photos.');
   await page.getByRole('tab',{name:'백과',exact:true}).click();await page.getByRole('textbox',{name:'백과 검색',exact:true}).fill('화상');await page.getByText('1개 자료',{exact:true}).waitFor();
   await page.getByRole('button',{name:'화상 자료 보기',exact:true}).click();await page.getByRole('link',{name:'화상 원문 보기 (한국어)',exact:true}).waitFor();
   await page.getByRole('button',{name:'백과 목록으로',exact:true}).click();assert.equal(await page.getByRole('textbox',{name:'백과 검색',exact:true}).inputValue(),'화상');
   await page.getByRole('textbox',{name:'백과 검색',exact:true}).fill('');await page.getByRole('button',{name:'피부 질환',exact:true}).click();await page.getByText('3개 자료',{exact:true}).waitFor();await page.screenshot({path:path.join(out,'encyclopedia.png'),fullPage:true});
   await page.getByRole('tab',{name:'병원',exact:true}).click();await page.getByRole('button',{name:'예시 지도·목록 체험',exact:true}).click();
   await page.getByRole('button',{name:'예시 약국',exact:true}).click();await page.getByRole('textbox',{name:'예시 기관 검색',exact:true}).fill('A');
   await page.getByRole('button',{name:'예시 약국 A 지도 표시',exact:true}).click();await page.getByRole('button',{name:'예시 약국 A · 선택됨',exact:true}).waitFor();
   assert.equal(gpsCalls,0);await page.screenshot({path:path.join(out,'sample-map.png'),fullPage:true});checks.push('Encyclopedia search/category/detail and clearly separated sample map search work without GPS.');
 }

 if(process.env.SKINCARE_TEST_SERVICES==='1'){
   await page.getByRole('tab',{name:'마이',exact:true}).click();await page.getByText('테스트 사용자',{exact:true}).waitFor();
   await page.getByRole('button',{name:'문의 내역',exact:true}).click();await page.getByRole('textbox',{name:'문의 제목',exact:true}).fill('합성 이용 문의');await page.getByRole('textbox',{name:'문의 내용',exact:true}).fill('테스트 내용. 실제 문의 아님.');
   await page.getByRole('button',{name:'문의 초안 저장',exact:true}).click();await page.getByText('문의 초안을 이 기기에 저장했어요.',{exact:true}).waitFor();assert.equal(inquiries.length,0);
   await page.getByRole('tab',{name:'마이',exact:true}).click();await page.getByRole('button',{name:'문의 내역',exact:true}).click();await page.waitForFunction(()=>document.querySelector('[aria-label="문의 제목"]')?.value==='합성 이용 문의');
   await page.getByRole('button',{name:'문의 접수하기',exact:true}).click();await page.getByText('아직 답변이 등록되지 않았어요.',{exact:true}).waitFor();assert.equal(inquiries.length,1);
   inquiries[0].answer={content:'합성 관리자 답변'};inquiries[0].status='COMPLETED';await page.getByRole('button',{name:'답변 새로고침',exact:true}).click();await page.getByText('합성 관리자 답변',{exact:true}).waitFor();
   await page.screenshot({path:path.join(out,'inquiry.png'),fullPage:true});await page.getByRole('tab',{name:'마이',exact:true}).click();await page.getByRole('button',{name:'로그아웃',exact:true}).click();await page.getByRole('button',{name:'상처 분석 시작하기',exact:true}).waitFor();assert.equal(await page.getByText('최근 분석',{exact:true}).count(),0);assert.equal(await page.getByTestId('risk-badge').count(),0);checks.push('Account profile, scoped draft/reply and logout work; member history is hidden after logout.');
 }
 assert.deepEqual(errors,[]);fs.writeFileSync(path.join(out,'browser-checks.json'),JSON.stringify({checks,pageErrors:errors,usesSyntheticApi:true,realSocialAndDeviceVerified:false},null,2));
 await browser.close();console.log(JSON.stringify({passed:checks.length,pageErrors:errors.length}));
})().catch(e=>{console.error(e);process.exit(1)});

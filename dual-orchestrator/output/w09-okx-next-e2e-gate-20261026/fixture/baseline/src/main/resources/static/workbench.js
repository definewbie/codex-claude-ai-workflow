const gridForm=document.querySelector('#grid-form');
const shadowForm=document.querySelector('#shadow-form');
const shadowRefreshControls=document.querySelector('#shadow-refresh-controls');
const shadowResult=document.querySelector('#shadow-result');
const empty=document.querySelector('#empty-state');
const loading=document.querySelector('#loading');
const result=document.querySelector('#result');
const submit=gridForm.querySelector('button[type="submit"]');
const capabilityNote=document.querySelector('#capability-note');
const shadowSelect=document.querySelector('#shadow-resume-id');
const shadowRefreshButton=document.querySelector('#shadow-refresh');
const shadowSyncButton=document.querySelector('#shadow-sync-candles');
const backtestSyncButton=document.querySelector('#backtest-sync-candles');
const marketRefreshButton=document.querySelector('#market-refresh');
const marketApplyWindowButton=document.querySelector('#market-apply-window');
let capabilities=[];
let shadowStudyId=Number(localStorage.getItem('gridShadowStudyId'))||null;
let backtestReady=false;
let persistedBacktest=null;
let latestMarketData=null;
let marketRefreshInFlight=false;
let marketPollingSuppressedExecutionId=null;
let pageOperationalTimesConfirmed=false;
const MARKET_SYNC_POLL_LIMIT_MS=20*60*1000;
const BACKTEST_SNAPSHOT_KEY='incomeWorkbench.latestBacktest.v1';

const money=value=>`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})} USDT`;
const btc=value=>`${Number(value).toLocaleString('zh-CN',{minimumFractionDigits:6,maximumFractionDigits:8})} BTC`;
const percent=value=>`${(Number(value)*100).toFixed(2)}%`;
const utc=local=>new Date(`${local}:00+08:00`).toISOString();
const utc8InputAfter=instant=>{
  const nextVisibleMinute=Math.ceil(new Date(instant).getTime()/60000)*60000;
  return new Date(nextVisibleMinute+8*60*60*1000).toISOString().slice(0,16);
};
const utc8Input=instant=>new Date(new Date(instant).getTime()+8*60*60*1000).toISOString().slice(0,16);
const utc8=instant=>new Intl.DateTimeFormat('zh-CN',{timeZone:'Asia/Shanghai',dateStyle:'medium',timeStyle:'short',hour12:false}).format(new Date(instant));
const optionalNumber=value=>value===''?null:Number(value);
const label=value=>({PREFER_SIMPLE_EARN:'优先简单赚币',KEEP_OBSERVING_GRID:'继续观察网格',MICRO_VALIDATE_GRID:'进入 Shadow',INSUFFICIENT_EVIDENCE:'证据不足'}[value]||value);
const exitLabel=value=>({WINDOW_END:'窗口结束',TAKE_PROFIT:'止盈',STOP_LOSS:'止损'}[value]||value);
const actionLabel=value=>({NO_CHANGE:'不调整',REJECT:'不采用',KEEP:'保持',KEEP_FROZEN:'冻结不变',
  FREEZE_FOR_SHADOW:'冻结后进入Shadow',READ_ONLY:'只读复盘',REJECT_CANDIDATE:'淘汰候选',BLOCKED:'证据不足',
  NO_INCREASE:'不增加',SHADOW_ONLY:'仅Shadow',MANUAL_MICRO_ONLY:'仅人工小额',PREFER_SIMPLE_EARN:'简单赚币优先',
  KEEP_BASE:'保持基础组合',REVIEW:'人工复核',NO_LIVE_REALLOCATION:'不调整实盘组合'}[value]||value);
const shadowStatusLabel=value=>({WAITING_FOR_DATA:'等待数据',OBSERVING:'观察中',TARGET_REACHED:'已到目标期',STOPPED:'已停止'}[value]||value);
const returnSummary=metrics=>{
  if(!metrics) return '等待数据';
  const cumulative=`累计 ${percent(metrics.cumulativeReturnRate)}`;
  if(metrics.simpleAnnualizedReturnRate===null){
    const days=(Number(metrics.observedMinutes)/1440).toFixed(1);
    return `${cumulative} · 年化样本不足（${days}/${metrics.minimumAnnualizationDays}天）`;
  }
  return `${cumulative} · 简单年化 ${percent(metrics.simpleAnnualizedReturnRate)}`;
};

function applyLatestOperationalTimes(data=null){
  const now=new Date();
  const fallbackTo=new Date(Math.floor(now.getTime()/60000)*60000);
  const windowTo=data?.candles?.to||fallbackTo.toISOString();
  const windowFrom=data?.candles?.from||new Date(fallbackTo.getTime()-90*24*60*60*1000).toISOString();
  const checkedAt=data?.checkedAt||now.toISOString();
  gridForm.elements.from.value=utc8Input(windowFrom);
  gridForm.elements.to.value=utc8Input(windowTo);
  gridForm.elements.evaluationAsOf.value=utc8InputAfter(checkedAt);
  document.querySelector('#shadow-valuation-at').value=utc8Input(windowTo);
  document.querySelector('#shadow-evaluation-as-of').value=utc8InputAfter(checkedAt);
  shadowForm.elements.startAt.value=utc8Input(windowTo);
  markBacktestFreshness();
}

const formSnapshot=()=>Object.fromEntries(new FormData(gridForm));
const sameSnapshot=(left,right)=>JSON.stringify(left)===JSON.stringify(right);

function backtestPayload(values=formSnapshot()){
  return {evaluationAsOf:utc(values.evaluationAsOf),from:utc(values.from),to:utc(values.to),
    configurationSnapshotId:Number(values.configurationSnapshotId),capital:Number(values.capital),
    simpleEarnApr:Number(values.simpleEarnApr),makerFeeRate:Number(values.makerFeeRate),
    takerFeeRate:Number(values.takerFeeRate),exitSlippageBps:Number(values.exitSlippageBps),
    maximumDrawdownRate:Number(values.maximumDrawdownRate),maximumPathDivergenceRate:Number(values.maximumPathDivergenceRate),
    maximumEndingBtcValueRatio:Number(values.maximumEndingBtcValueRatio),lowerPrice:Number(values.lowerPrice),
    upperPrice:Number(values.upperPrice),gridCount:Number(values.gridCount),
    takeProfitPrice:optionalNumber(values.takeProfitPrice),stopLossPrice:optionalNumber(values.stopLossPrice),
    simpleQuoteAsOf:utc(values.simpleQuoteAsOf),hedgedNotional:Number(values.hedgedNotional),costAsOf:utc(values.costAsOf),
    openingCost:optionalNumber(values.openingCost),exitCost:optionalNumber(values.exitCost),
    slippageCost:optionalNumber(values.slippageCost),rebalanceCost:optionalNumber(values.rebalanceCost),
    serviceCost:optionalNumber(values.serviceCost),smartReferenceSpotPrice:Number(values.smartReferenceSpotPrice),
    smartReservedMargin:Number(values.smartReservedMargin),portfolioCapital:Number(values.portfolioCapital),
    portfolioFixedEarnRatio:Number(values.portfolioFixedEarnPercent)/100,
    portfolioLiquidRatio:Number(values.portfolioLiquidPercent)/100,
    portfolioSmartRatio:Number(values.portfolioSmartPercent)/100,
    portfolioGridRatio:Number(values.portfolioGridPercent)/100};
}

function restoreForm(snapshot){
  Object.entries(snapshot||{}).forEach(([name,value])=>{
    const input=gridForm.elements[name];
    if(input&&typeof value==='string') input.value=value;
  });
  renderPortfolioRatioTotal();
}

function saveBacktest(snapshot,data){
  persistedBacktest={version:1,savedAt:new Date().toISOString(),form:snapshot,data};
  localStorage.setItem(BACKTEST_SNAPSHOT_KEY,JSON.stringify(persistedBacktest));
}

function markBacktestFreshness(){
  if(!backtestReady||!persistedBacktest) return;
  const stale=!sameSnapshot(formSnapshot(),persistedBacktest.form);
  const state=document.querySelector('#backtest-result-state');
  state.textContent=stale
    ?'参数已修改：下方仍显示最近一次回测快照，请重新运行后再使用结论。'
    :`最近一次回测快照 · 计算于 ${utc8(persistedBacktest.savedAt)}`;
  state.classList.toggle('stale',stale);
  document.querySelector('#portfolio-evaluation-status').textContent=stale?'结果已过期':'组合回测已计算';
}

function restoreLatestBacktest(){
  try{
    const snapshot=JSON.parse(localStorage.getItem(BACKTEST_SNAPSHOT_KEY));
    if(snapshot?.version!==1||!snapshot.form||!snapshot.data?.portfolioRecommendation) return;
    persistedBacktest=snapshot;restoreForm(snapshot.form);
    render(snapshot.data,{restored:true,savedAt:snapshot.savedAt});
  }catch(error){
    localStorage.removeItem(BACKTEST_SNAPSHOT_KEY);
  }
}

fetch('/api/workbench/capabilities').then(r=>r.json()).then(data=>{
  capabilities=data.modes;capabilityNote.textContent=data.modes[0].explanation;
});
refreshShadowOptions();

const portfolioRatioNames=['portfolioFixedEarnPercent','portfolioLiquidPercent','portfolioSmartPercent','portfolioGridPercent'];
portfolioRatioNames.forEach(name=>gridForm.elements[name].addEventListener('input',renderPortfolioRatioTotal));
gridForm.addEventListener('input',markBacktestFreshness);
renderPortfolioRatioTotal();
restoreLatestBacktest();
applyLatestOperationalTimes();
loadStoredMarketData().finally(()=>refreshMarketData(true));
setInterval(()=>refreshMarketData(true),60000);

marketRefreshButton.addEventListener('click',()=>refreshMarketData(false));
marketApplyWindowButton.addEventListener('click',()=>{
  if(!latestMarketData?.candles) return;
  applyLatestOperationalTimes(latestMarketData);
  document.querySelector('#market-data-message').textContent='已把同一滚动90天窗口和数据截止时间应用到回测与 Shadow。';
});

async function loadStoredMarketData(){
  try{
    const response=await fetch('/api/workbench/market-data');const data=await response.json();
    if(response.ok) renderMarketData(data);
  }catch(error){
    document.querySelector('#market-data-message').textContent='尚未读取到本地市场数据状态，正在连接 OKX。';
  }
}

async function refreshMarketData(automatic){
  if(marketRefreshInFlight) return;
  if(!automatic) marketPollingSuppressedExecutionId=null;
  const alignOperationalTimes=!automatic||!pageOperationalTimesConfirmed;
  marketRefreshInFlight=true;
  [marketRefreshButton,backtestSyncButton,shadowSyncButton,submit,shadowRefreshButton]
    .forEach(button=>button.disabled=true);
  const message=document.querySelector('#market-data-message');
  if(!automatic) message.textContent='正在从 OKX 获取最新成交价和共享分钟 K 线…';
  [document.querySelector('#backtest-sync-status'),document.querySelector('#shadow-sync-status')]
    .forEach(status=>{status.textContent='正在更新同一份 OKX 分钟 K 线事实…';status.classList.remove('error');});
  try{
    const refreshUrl=automatic?'/api/workbench/market-data/refresh':'/api/workbench/market-data/refresh/manual';
    const response=await fetch(refreshUrl,{method:'POST'});
    let data=await response.json();
    if(!response.ok) throw new Error(data.message||'OKX 行情刷新失败');
    renderMarketData(data);
    if(alignOperationalTimes){
      applyLatestOperationalTimes(data);
      pageOperationalTimesConfirmed=true;
    }
    if(data.candleSync&&(data.candleSync.state==='QUEUED'||data.candleSync.state==='RUNNING')){
      if(automatic&&marketPollingSuppressedExecutionId===Number(data.candleSync.id)) return;
      const pollResult=await pollAutomaticCandleSync(data.candleSync,message);
      if(pollResult.timedOut){marketPollingSuppressedExecutionId=Number(data.candleSync.id);return;}
      data=pollResult.execution;
      marketPollingSuppressedExecutionId=null;
      if(data?.state==='FAILED') throw new Error(data.failureMessage||'90天 K 线回补失败');
      const finalResponse=await fetch('/api/workbench/market-data/refresh',{method:'POST'});
      const finalData=await finalResponse.json();
      if(!finalResponse.ok) throw new Error(finalData.message||'完成后覆盖度复核失败');
      renderMarketData(finalData);
      if(alignOperationalTimes) applyLatestOperationalTimes(finalData);
      data=finalData;
    }
    return data;
  }catch(error){
    message.textContent=error.message;message.classList.add('error');
    document.querySelector('#market-readiness').textContent='刷新失败';
    document.querySelector('#market-readiness').className='market-readiness warning';
    [document.querySelector('#backtest-sync-status'),document.querySelector('#shadow-sync-status')]
      .forEach(status=>{status.textContent=`共享 K 线更新失败：${error.message}`;status.classList.add('error');});
    return null;
  }finally{
    marketRefreshInFlight=false;
    [marketRefreshButton,backtestSyncButton,shadowSyncButton,submit,shadowRefreshButton]
      .forEach(button=>button.disabled=false);
  }
}

async function pollAutomaticCandleSync(execution,message){
  let data=execution;
  const pollStartedAt=Date.now();
  while(data.state==='QUEUED'||data.state==='RUNNING'){
    const expected=129600;
    const progress=Math.min(100,Number(data.completedInWindow||0)/expected*100);
    const cursor=data.cursor?utc8(data.cursor):'等待第一页';
    message.textContent=`全量校验滚动90天 K 线：${progress.toFixed(1)}% · ${Number(data.pages||0).toLocaleString('zh-CN')}页 · 已到 ${cursor}`;
    document.querySelector('#market-sync-state').textContent=`全量校验 ${progress.toFixed(1)}%`;
    if(Date.now()-pollStartedAt>=MARKET_SYNC_POLL_LIMIT_MS){
      message.textContent='全量同步仍在后台运行，页面已停止持续轮询；稍后点击“立即更新 OKX 行情”即可查看最新进度。';
      return {execution:data,timedOut:true};
    }
    await new Promise(resolve=>setTimeout(resolve,1200));
    const response=await fetch(`/api/workbench/candles/sync/${data.id}`);data=await response.json();
    if(!response.ok) throw new Error(data.message||'读取 K 线同步进度失败');
  }
  return {execution:data,timedOut:false};
}

function renderMarketData(data){
  latestMarketData=data;
  const ticker=data.ticker;
  document.querySelector('#market-last-price').textContent=ticker
    ?`${Number(ticker.lastPrice).toLocaleString('zh-CN',{minimumFractionDigits:1,maximumFractionDigits:2})} USDT`:'—';
  document.querySelector('#market-ticker-time').textContent=ticker
    ?`OKX 时间 ${utc8(ticker.providerTimestamp)} · 本地观察 ${utc8(ticker.observedAt)}`:'尚无 OKX ticker 事实';
  const candles=data.candles;
  const progress=candles.expectedMinutes?candles.actualMinutes/candles.expectedMinutes*100:0;
  document.querySelector('#market-candle-progress').textContent=`${progress.toFixed(2)}% · ${Number(candles.actualMinutes).toLocaleString('zh-CN')}/${Number(candles.expectedMinutes).toLocaleString('zh-CN')}`;
  document.querySelector('#market-candle-window').textContent=`${utc8(candles.from)} 至 ${utc8(candles.to)} · 缺 ${Number(candles.missingMinutes).toLocaleString('zh-CN')} 根`;
  const readinessLabels={READY:'数据已就绪',WAITING_LATEST_CANDLE:'等待最新 K 线确认',SYNCING_CANDLES:'正在补 K 线',INCOMPLETE_CANDLES:'K 线不完整',STALE_TICKER:'行情已过期',NO_TICKER:'尚无行情'};
  const readiness=document.querySelector('#market-readiness');
  readiness.textContent=readinessLabels[data.readiness]||data.readiness;
  readiness.className=`market-readiness ${data.readiness==='READY'?'ready':'warning'}`;
  const sync=data.candleSync;
  document.querySelector('#market-sync-state').textContent=sync?({QUEUED:'等待全量校验',RUNNING:'全量校验中',FAILED:'校验失败',SUCCEEDED:'校验完成'}[sync.state]||sync.state):(candles.complete?'分钟增量同步':'等待全量同步');
  document.querySelector('#market-sync-detail').textContent=data.automaticRetryAt
    ?`自动全量重试暂停至 ${utc8(data.automaticRetryAt)}，人工刷新可立即重试`
    :data.tailRefresh
    ?`本轮实际收到 ${data.tailRefresh.received} 根，完成 ${data.tailRefresh.completed} 根，新增 ${data.tailRefresh.newFacts} 根`
    :'页面打开时每分钟从 OKX 公共接口刷新';
  const message=document.querySelector('#market-data-message');message.classList.remove('error');
  message.textContent=data.automaticRetryAt
    ?`上次全量校验失败，系统将在 ${utc8(data.automaticRetryAt)} 后自动重试；也可点击“立即更新 OKX 行情”人工重试。`
    :data.readiness==='WAITING_LATEST_CANDLE'
    ?'OKX 最新一分钟 K 线尚在确认，系统只等待下次增量刷新，不启动90天全量回补。'
    :data.readiness==='READY'
    ?'BTC 最新价与滚动90天已完成分钟 K 线均来自 OKX，数据库覆盖度校验通过。'
    :'系统不会用不完整 K 线生成新的网格收益结论。';
  const fullCheckRunning=sync&&(sync.state==='QUEUED'||sync.state==='RUNNING');
  marketApplyWindowButton.disabled=!candles.complete||fullCheckRunning;
  renderSharedCandleStatus(data);
}

function renderSharedCandleStatus(data){
  const candles=data.candles,sync=data.candleSync;
  let text;
  if(sync&&(sync.state==='QUEUED'||sync.state==='RUNNING')){
    const progress=Math.min(100,Number(sync.completedInWindow||0)/Number(candles.expectedMinutes||129600)*100);
    text=`共享 K 线正在全量校验：${progress.toFixed(1)}% · 回测与 Shadow 使用同一任务 #${sync.id}`;
  }else if(candles.complete){
    text=`共享 K 线已就绪：${Number(candles.actualMinutes).toLocaleString('zh-CN')} 根 · ${utc8(candles.from)} 至 ${utc8(candles.to)}`;
  }else if(data.readiness==='WAITING_LATEST_CANDLE'){
    text='共享 K 线仅等待 OKX 确认最新一分钟；无需分别为回测和 Shadow 同步。';
  }else{
    text=`共享 K 线尚不完整：缺 ${Number(candles.missingMinutes).toLocaleString('zh-CN')} 根，系统不会生成新结论。`;
  }
  [document.querySelector('#backtest-sync-status'),document.querySelector('#shadow-sync-status')]
    .forEach(status=>{status.textContent=text;status.classList.toggle('error',!candles.complete&&data.readiness!=='WAITING_LATEST_CANDLE'&&!sync);});
}

function renderPortfolioRatioTotal(){
  const total=portfolioRatioNames.reduce((sum,name)=>sum+Number(gridForm.elements[name].value||0),0);
  const node=document.querySelector('#portfolio-ratio-total');
  node.textContent=`当前合计：${total.toFixed(0)}%${total===100?'':'（必须等于100%）'}`;
  node.classList.toggle('error',total!==100);
}

document.querySelectorAll('.mode').forEach(button=>button.addEventListener('click',async()=>{
  document.querySelectorAll('.mode').forEach(item=>item.classList.toggle('active',item===button));
  const mode=capabilities.find(item=>item.mode===button.dataset.mode);
  capabilityNote.textContent=mode?.explanation||'';
  const selected=button.dataset.mode;
  gridForm.classList.toggle('hidden',selected!=='BACKTEST');
  shadowForm.classList.toggle('hidden',selected!=='SHADOW');
  result.classList.toggle('hidden',selected!=='BACKTEST'||!backtestReady);
  shadowResult.classList.add('hidden');
  if(selected==='SHADOW'){await refreshShadowOptions(shadowStudyId);await loadShadow();}
  if(selected==='LIVE') showEmpty('实盘尚不能生成收益结论',mode?.explanation||'当前能力不可用。');
  if(selected==='BACKTEST'&&!backtestReady) showEmpty('先运行一组情景','先同步窗口数据，再比较网格、简单赚币和 BTC 买入持有。');
  if(selected==='BACKTEST'&&backtestReady) empty.classList.add('hidden');
}));

gridForm.addEventListener('submit',async event=>{
  event.preventDefault();const snapshot=formSnapshot();const payload=backtestPayload(snapshot);
  empty.classList.add('hidden');result.classList.add('hidden');loading.classList.remove('hidden');
  loading.textContent='正在读取 129,600 根分钟 K 线并计算双路径…';submit.disabled=true;
  try{
    const response=await fetch('/api/workbench/backtests/grid',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    const data=await response.json();if(!response.ok) throw new Error(data.message||'回测失败');
    saveBacktest(snapshot,data);render(data,{restored:false,savedAt:persistedBacktest.savedAt});
  }catch(error){showEmpty('无法生成结论',error.message);}
  finally{loading.classList.add('hidden');submit.disabled=false;}
});

backtestSyncButton.addEventListener('click',()=>refreshMarketData(false));

shadowForm.addEventListener('submit',async event=>{
  event.preventDefault();const values=Object.fromEntries(new FormData(shadowForm));
  const payload={configurationSnapshotId:Number(values.configurationSnapshotId),startAt:utc(values.startAt),
    targetDays:Number(values.targetDays),capital:Number(values.capital),simpleEarnApr:Number(values.simpleEarnApr),
    makerFeeRate:Number(values.makerFeeRate),takerFeeRate:Number(values.takerFeeRate),
    exitSlippageBps:Number(values.exitSlippageBps),maximumDrawdownRate:Number(values.maximumDrawdownRate),
    maximumPathDivergenceRate:Number(values.maximumPathDivergenceRate),maximumEndingBtcValueRatio:Number(values.maximumEndingBtcValueRatio),
    candidateLowerPrice:Number(values.candidateLowerPrice),candidateUpperPrice:Number(values.candidateUpperPrice),
    candidateGridCount:Number(values.candidateGridCount),candidateTakeProfitPrice:null,candidateStopLossPrice:null,
    evidenceAsOf:utc(values.evidenceAsOf),evidenceReference:values.evidenceReference};
  await shadowRequest('/api/workbench/shadows/grid-pairs',payload,async data=>{
    shadowStudyId=data.study.id;localStorage.setItem('gridShadowStudyId',shadowStudyId);
    await refreshShadowOptions(shadowStudyId);renderShadow(data);
  });
});

shadowRefreshButton.addEventListener('click',async()=>{
  if(!shadowStudyId) return showEmpty('尚未创建 Shadow','先选择或创建一项观察。');
  const valuationAt=document.querySelector('#shadow-valuation-at').value;
  const evaluationAsOf=document.querySelector('#shadow-evaluation-as-of').value;
  if(!valuationAt||!evaluationAsOf) return showEmpty('请填写估值时间','两个时间都必须使用 UTC+8。');
  await shadowRequest(`/api/workbench/shadows/${shadowStudyId}/refresh`,{valuationAt:utc(valuationAt),evaluationAsOf:utc(evaluationAsOf)},async data=>{
    await refreshShadowOptions(shadowStudyId);renderShadow(data);
  });
});

shadowSyncButton.addEventListener('click',()=>refreshMarketData(false));

document.querySelector('#shadow-stop').addEventListener('click',async()=>{
  if(!shadowStudyId) return;
  await shadowRequest(`/api/workbench/shadows/${shadowStudyId}/stop`,{stoppedAt:new Date().toISOString()},async data=>{
    await refreshShadowOptions(shadowStudyId);renderShadow(data);
  });
});

document.querySelector('#shadow-load').addEventListener('click',async()=>{
  const id=Number(shadowSelect.value);
  if(!Number.isInteger(id)||id<1) return showEmpty('请选择一项 Shadow 观察','列表为空时可在下方创建第一项观察。');
  shadowStudyId=id;localStorage.setItem('gridShadowStudyId',id);await loadShadow();
});

async function refreshShadowOptions(preferredId=shadowStudyId){
  try{
    const response=await fetch('/api/workbench/shadows');const rows=await response.json();
    if(!response.ok) throw new Error(rows.message||'读取观察列表失败');
    shadowSelect.innerHTML=rows.length?rows.map(item=>{
      const days=(Number(item.observedMinutes)/1440).toFixed(1);
      return `<option value="${item.id}">#${item.id} · ${shadowStatusLabel(item.status)} · ${days}/${item.targetDays}天 · ${item.candidate.gridCount}格 vs ${item.baseline.gridCount}格</option>`;
    }).join(''):'<option value="">暂无观察，请在下方创建</option>';
    const ids=rows.map(item=>Number(item.id));
    const selected=ids.includes(Number(preferredId))?Number(preferredId):(ids[0]||null);
    shadowStudyId=selected;
    if(selected){shadowSelect.value=String(selected);localStorage.setItem('gridShadowStudyId',selected);}
    else localStorage.removeItem('gridShadowStudyId');
  }catch(error){shadowSelect.innerHTML=`<option value="">${escapeHtml(error.message)}</option>`;}
}

async function loadShadow(){
  result.classList.add('hidden');shadowRefreshControls.classList.add('hidden');
  if(!shadowStudyId) return showEmpty('创建第一组并行 Shadow','候选80%/30格将与确认配置中的原始50格并行观察。');
  try{
    const response=await fetch(`/api/workbench/shadows/${shadowStudyId}`);const data=await response.json();
    if(!response.ok) throw new Error(data.message||'读取Shadow失败');renderShadow(data);
  }catch(error){localStorage.removeItem('gridShadowStudyId');shadowStudyId=null;showEmpty('无法读取 Shadow',error.message);}
}

async function shadowRequest(url,payload,onSuccess){
  empty.classList.add('hidden');shadowResult.classList.add('hidden');loading.classList.remove('hidden');
  loading.textContent='正在读取样本外 K 线并更新只读事实…';
  try{
    const response=await fetch(url,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(payload)});
    const data=await response.json();if(!response.ok) throw new Error(data.message||'Shadow操作失败');await onSuccess(data);
  }catch(error){showEmpty('Shadow 无法更新',error.message);}
  finally{loading.classList.add('hidden');}
}

function renderShadow(data){
  empty.classList.add('hidden');result.classList.add('hidden');
  const report=data.dailyReport,study=data.study,candidate=data.latestCandidate,baseline=data.latestBaseline;
  shadowSelect.value=String(study.id);
  document.querySelector('#shadow-headline').textContent=report.headline;
  document.querySelector('#shadow-next').textContent=`下次复核：${report.nextReview} · 已观察 ${(data.observedMinutes/1440).toFixed(1)} 天`;
  document.querySelector('#shadow-study-title').textContent=`观察 #${study.id} · ${utc8(study.startAt)} 起`;
  document.querySelector('#shadow-status').textContent=shadowStatusLabel(data.status);
  document.querySelector('#shadow-candidate-params').textContent=`${Number(study.candidate.lowerPrice).toLocaleString()}–${Number(study.candidate.upperPrice).toLocaleString()} · ${study.candidate.gridCount}格`;
  document.querySelector('#shadow-baseline-params').textContent=`${Number(study.baseline.lowerPrice).toLocaleString()}–${Number(study.baseline.upperPrice).toLocaleString()} · ${study.baseline.gridCount}格`;
  document.querySelector('#shadow-candidate-income').textContent=candidate?money(candidate.netIncome):'等待数据';
  document.querySelector('#shadow-baseline-income').textContent=baseline?money(baseline.netIncome):'等待数据';
  document.querySelector('#shadow-simple-income').textContent=candidate?money(candidate.simpleEarnIncome):'等待数据';
  document.querySelector('#shadow-candidate-rate').textContent=returnSummary(data.candidateReturns);
  document.querySelector('#shadow-baseline-rate').textContent=returnSummary(data.baselineReturns);
  document.querySelector('#shadow-simple-rate').textContent=returnSummary(data.simpleEarnReturns);
  document.querySelector('#shadow-parameter-action').textContent=actionLabel(report.parameterAction);
  document.querySelector('#shadow-parameter-advice').textContent=report.parameterAdvice;
  document.querySelector('#shadow-position-action').textContent=actionLabel(report.positionAction);
  document.querySelector('#shadow-position-advice').textContent=report.positionAdvice;
  document.querySelector('#shadow-portfolio-action').textContent=actionLabel(report.portfolioAction);
  document.querySelector('#shadow-portfolio-advice').textContent=report.portfolioAdvice;
  document.querySelector('#shadow-boundaries').innerHTML=report.boundaries.map(item=>`<li>${escapeHtml(item)}</li>`).join('');
  shadowRefreshControls.classList.toggle('hidden',data.status==='STOPPED');
  document.querySelector('#shadow-stop').classList.toggle('hidden',data.status==='STOPPED');
  shadowResult.classList.remove('hidden');
}

function showEmpty(title,message){
  result.classList.add('hidden');shadowResult.classList.add('hidden');empty.classList.remove('hidden');
  empty.querySelector('h2').textContent=title;empty.querySelector('p').textContent=message;
}

function render(data,{restored=false,savedAt=null}={}){
  backtestReady=true;empty.classList.add('hidden');const report=data.dailyReport;
  document.querySelector('#report-headline').textContent=report.headline;
  document.querySelector('#report-next').textContent=`下次复核：${report.nextReview} · 数据截止 ${utc8(data.asOf)}`;
  document.querySelector('#metric-grid').textContent=money(data.conservativeNetIncome);
  document.querySelector('#metric-simple').textContent=money(data.simpleEarnIncome);
  document.querySelector('#metric-btc').textContent=money(data.buyAndHoldIncome);
  document.querySelector('#metric-grid-rate').textContent=returnSummary(data.gridReturns);
  document.querySelector('#metric-simple-rate').textContent=returnSummary(data.simpleEarnReturns);
  document.querySelector('#metric-btc-rate').textContent=returnSummary(data.buyHoldReturns);
  const earn=data.simpleEarn,smart=data.smartArbitrage,portfolio=data.portfolioRecommendation;
  const gridAllocation=data.gridInitialAllocation,smartAllocation=smart.allocation;
  const candidateCapital=portfolio.shadowCandidateCapitalUsdt;
  document.querySelector('#backtest-result-state').textContent=restored
    ?`已恢复最近一次回测快照 · 计算于 ${utc8(savedAt)}`
    :`本次回测已完成 · 计算于 ${utc8(savedAt||new Date().toISOString())}`;
  document.querySelector('#backtest-result-state').classList.remove('stale');
  document.querySelector('#single-product-title').textContent=`单产品独立回测｜每项 ${money(candidateCapital)}`;
  document.querySelector('#single-product-note').textContent=`上面四项都假设各自独立投入 ${money(candidateCapital)}，仅用于取得产品历史收益率；不是把这笔钱同时投入四次，也不是20万组合中的实际贡献。智能套利与定期按90天，网格按实际可运行期。`;
  document.querySelector('#product-fixed-income').textContent=money(earn.fixed90Days.income90Days);
  document.querySelector('#product-fixed-detail').textContent=`${money(candidateCapital)}单品基准 · 日收益 ${money(earn.fixed90Days.dailyIncome)} · APR ${percent(earn.fixed90Days.apr)}`;
  document.querySelector('#product-flex-income').textContent=money(earn.flexible.income90Days);
  document.querySelector('#product-flex-detail').textContent=`${money(candidateCapital)}单品基准 · 日收益 ${money(earn.flexible.dailyIncome)} · 净APR估算 ${percent(earn.flexible.apr)}`;
  document.querySelector('#product-smart-income').textContent=smart.netIncome===null?'证据不足':money(smart.netIncome);
  document.querySelector('#product-smart-detail').textContent=`${money(candidateCapital)}单品基准 · ${smartRecommendation(smart.recommendation)} · 已知成本 ${money(smart.knownCost)}`;
  document.querySelector('#product-grid-income').textContent=money(data.conservativeNetIncome);
  document.querySelector('#product-grid-detail').textContent=`${money(candidateCapital)}单品基准 · 保守路径 · ${returnSummary(data.gridReturns)}`;
  const portfolioComplete=portfolio.evaluationStatus==='COMPLETE';
  document.querySelector('#portfolio-title').textContent=`${money(portfolio.totalCapitalUsdt)}真实组合历史回测`;
  document.querySelector('#portfolio-evaluation-status').textContent=portfolioComplete?'组合回测已计算':'部分可计算';
  document.querySelector('#portfolio-policy').textContent=`当前实盘建议采用“${portfolio.policyName}”；它不会覆盖你的历史回测比例。`;
  document.querySelector('#portfolio-user-income').textContent=portfolioComplete?money(portfolio.proposedProjectedNetIncomeUsdt):'存在未知分项';
  document.querySelector('#portfolio-user-rate').textContent=portfolioComplete
    ?`累计 ${percent(portfolio.proposedProjectedReturnRate)} · 简单年化 ${percent(portfolio.proposedSimpleAnnualizedReturnRate)}`
    :'非零分配产品缺少可计算净收益，暂不合计';
  document.querySelector('#portfolio-baseline-income').textContent=money(portfolio.recommendedProjectedNetIncomeUsdt);
  document.querySelector('#portfolio-incremental').textContent=portfolioComplete?money(portfolio.projectedIncrementalVsRecommendedUsdt):'无法比较';
  const allocatedRows=portfolio.allocations.filter(row=>Number(row.proposedRatio)>0);
  document.querySelector('#portfolio-allocation-body').innerHTML=allocatedRows.map(row=>`<tr class="${row.evidenceStatus.includes('未通过')||row.evidenceStatus.includes('未知')?'evidence-warning':''}">
    <td>${escapeHtml(row.productName)}</td><td>${money(row.proposedAmountUsdt)} · ${percent(row.proposedRatio)}</td>
    <td>${row.proposedProjectedNetIncomeUsdt===null?'无法计算':money(row.proposedProjectedNetIncomeUsdt)}</td>
    <td>${row.proposedReturnRate===null?'未知':percent(row.proposedReturnRate)}</td>
    <td>${money(row.recommendedAmountUsdt)} · ${percent(row.recommendedRatio)}<br><small>历史净收益 ${money(row.recommendedProjectedNetIncomeUsdt)}</small></td>
    <td><strong>${escapeHtml(row.evidenceStatus)}</strong><br><small>${escapeHtml(row.reason)}</small></td></tr>`).join('');
  const incomeParts=allocatedRows
    .map(row=>`${row.productName} ${money(row.proposedProjectedNetIncomeUsdt)}`);
  document.querySelector('#portfolio-equation').textContent=portfolioComplete
    ?`${incomeParts.join(' + ')} = 组合历史净收益 ${money(portfolio.proposedProjectedNetIncomeUsdt)}`
    :'存在非零分配产品的收益未知，当前不能合计组合净收益。';
  document.querySelector('#portfolio-allocation-note').textContent=`真实组合总资金 ${money(portfolio.totalCapitalUsdt)}；你的方案已按比例投入并计算。单产品候选金额 ${money(portfolio.shadowCandidateCapitalUsdt)} 只用于取得产品历史净收益率。`;
  document.querySelector('#portfolio-boundaries').innerHTML=portfolio.boundaries.map(item=>`<li>${escapeHtml(item)}</li>`).join('');
  document.querySelector('#allocation-earn-usdt').textContent=`${money(earn.capital)} 可用于赚币`;
  document.querySelector('#allocation-smart-btc').textContent=`${btc(smartAllocation.estimatedBtcQuantity)} · ${money(smartAllocation.spotInvestmentUsdt)}`;
  document.querySelector('#allocation-smart-hedge').textContent=money(smartAllocation.shortHedgeNotionalUsdt);
  document.querySelector('#allocation-smart-usdt').textContent=`保证金 ${money(smartAllocation.reservedMarginUsdt)} · 未分配 ${money(smartAllocation.unallocatedUsdt)}`;
  document.querySelector('#allocation-smart-delta').textContent=money(smartAllocation.estimatedNetDirectionalNotionalUsdt);
  document.querySelector('#allocation-grid-btc').textContent=btc(gridAllocation.btcQuantity);
  document.querySelector('#allocation-grid-ratio').textContent=`初始BTC名义 ${percent(gridAllocation.btcCapitalRatio)}`;
  document.querySelector('#allocation-grid-usdt').textContent=`预留买单 ${money(gridAllocation.reservedBuyOrderUsdt)} · 初始费 ${money(gridAllocation.initialBuyFee)}`;
  document.querySelector('#allocation-note').textContent=`网格按首次进入区间时的开盘价 ${money(gridAllocation.initialPrice)} 以“仅USDT”模式初始化，预留USDT已被网格占用；智能套利按预览价 ${money(smartAllocation.referenceSpotPrice)} 换算现货BTC，并用等名义永续空头对冲。`;
  document.querySelector('#smart-recommendation').textContent=smartRecommendation(smart.recommendation);
  document.querySelector('#smart-funding-income').textContent=money(smart.fundingIncome);
  document.querySelector('#smart-cost-detail').textContent=`已知成本 ${money(smart.knownCost)} · 净收益 ${smart.netIncome===null?'无法计算':money(smart.netIncome)}`;
  document.querySelector('#smart-incremental').textContent=smart.incrementalVsSimpleEarn===null?'无法计算':money(smart.incrementalVsSimpleEarn);
  document.querySelector('#smart-settlements').textContent=`结算 ${smart.settlementCount} 次 · 正 ${smart.positiveSettlements} · 负 ${smart.negativeSettlements}`;
  document.querySelector('#smart-reasons').innerHTML=[...smart.reasons,...smart.unknowns].map(item=>`<li>${escapeHtml(item)}</li>`).join('');
  document.querySelector('#backtest-period').textContent=`统一按实际有效观察期 ${utc8(data.activeFrom)} 至 ${utc8(data.activeTo)} 做简单年化；包含网格利润、BTC库存盈亏、费用和退出结果。`;
  document.querySelector('#recommendation').textContent=label(data.recommendation);
  document.querySelector('#parameter-action').textContent=actionLabel(report.parameterAction);
  document.querySelector('#parameter-advice').textContent=report.parameterAdvice;
  document.querySelector('#position-action').textContent=actionLabel(report.positionAction);
  document.querySelector('#position-advice').textContent=report.positionAdvice;
  document.querySelector('#portfolio-action').textContent=actionLabel(report.portfolioAction);
  document.querySelector('#portfolio-advice').textContent=report.portfolioAdvice;
  document.querySelector('#path-divergence').textContent=`分歧 ${percent(data.pathDivergenceRate)}`;
  path('#path-a',data.pathLowThenHigh);path('#path-b',data.pathHighThenLow);
  document.querySelector('#sensitivity-body').innerHTML=data.sensitivity.map(row=>`<tr><td>${row.gridCount}</td><td>${money(row.worsePathIncome)}</td></tr>`).join('');
  document.querySelector('#boundaries').innerHTML=[...report.boundaries,...data.reasons].map(item=>`<li>${escapeHtml(item)}</li>`).join('');
  result.classList.remove('hidden');
}

function smartRecommendation(value){return ({PREFER_SIMPLE_EARN:'简单赚币优先',WATCH_SMART_ARBITRAGE:'继续观察',MICRO_VALIDATE_ARBITRAGE:'仅小额验证',INSUFFICIENT_EVIDENCE:'证据不足'}[value]||value);}

function path(prefix,pathData){
  document.querySelector(`${prefix}-income`).textContent=money(pathData.netIncome);
  document.querySelector(`${prefix}-components`).textContent=`网格 ${money(pathData.completedGridProfit)} · 库存 ${money(pathData.inventoryPnl)} · 已计费用 ${money(pathData.totalFees)}`;
  document.querySelector(`${prefix}-exit`).textContent=`${exitLabel(pathData.exitReason)} · ${utc8(pathData.exitTime)} · 最大回撤 ${percent(pathData.maximumDrawdownRate)}`;
}

function escapeHtml(value){const node=document.createElement('div');node.textContent=value;return node.innerHTML;}

const API_URL = '/api/scenario-evaluations';
const REGIMES = ['BEAR', 'RANGE', 'BULL'];

const scenarioMeta = {
  BEAR: {
    name: '单边下跌', badge: '下跌情景', inputPrefix: 'bear',
    explanation: '先看方向敞口与压力路径亏损。网格循环为正，也不能自动抵消持续买入 BTC 形成的库存风险。',
    title: '下跌时，先看总 BTC 敞口，不只看网格利润',
    copy: '后端已经用下行代表与压力路径同时检验组合。页面展示代表路径，压力路径仍然参与硬约束。',
    counter: '若价格快速回到区间，提前退出可能锁定亏损；情景结果用于比较取舍，不把单一止损阈值包装成必然正确。'
  },
  RANGE: {
    name: '区间震荡', badge: '震荡情景', inputPrefix: 'range',
    explanation: '网格能否贡献收益取决于路径往返，而不是只取决于期末价格。当前数字来自冻结路径的实际模拟。',
    title: '震荡有利，但不能假设震荡会持续',
    copy: '网格循环、库存盈亏、费用和独立 BTC 现货已分列，避免用绿色的循环利润掩盖方向风险。',
    counter: '同样的期末价格可以由完全不同的路径到达，因此代表路径与压力路径都必须保留。'
  },
  BULL: {
    name: '单边上涨', badge: '上涨情景', inputPrefix: 'bull',
    explanation: '上涨收益主要来自 BTC 方向敞口；网格逐格卖出会降低波动，也可能产生相对持币的踏空代价。',
    title: '上涨时，网格赚钱不等于组合赚得最多',
    copy: '结果把网格库存与独立 BTC 现货分开归因，因此不会把同一份 BTC 上涨收益重复计算。',
    counter: '收益优先方案仍必须通过下行情景亏损和 BTC 峰值敞口约束，不能只按上涨结果排名。'
  }
};

const productMeta = {
  FIXED_EARN_90D: { name: '90 天定期', color: '#789f32' },
  FLEXIBLE_EARN: { name: '流动 USDT', color: '#59645a' },
  SMART_ARBITRAGE: { name: 'BTC 智能套利', color: '#c2a45f' },
  SPOT_GRID: { name: 'BTC 固定网格', color: '#b5ed52' },
  BTC_SPOT: { name: 'BTC 现货', color: '#75a6b8' }
};

const contributionMeta = {
  FIXED_EARN_INTEREST: '90 天定期利息',
  FLEXIBLE_EARN_INTEREST: '活期利息',
  SMART_ARBITRAGE_FUNDING: '智能套利资金费收入',
  SMART_ARBITRAGE_COST: '智能套利已知成本',
  GRID_CYCLE_SPREAD: '网格循环价差',
  GRID_INVENTORY_PNL: '网格 BTC 库存盈亏',
  GRID_TRADING_FEE: '网格交易费',
  BTC_SPOT_PNL: 'BTC 独立现货盈亏',
  ENTRY_EXIT_FEE: '建仓与退出费',
  SLIPPAGE: '退出滑点',
  OTHER_KNOWN_COST: '其他已知成本'
};

const optionLabels = {
  CONSERVATIVE: { short: '保守收益', title: '优先改善下行情景结果' },
  BALANCED: { short: '平衡目标', title: '兼顾跨情景收益与风险' },
  RETURN_SEEKING: { short: '收益优先', title: '提高目标达成覆盖，不绕过风险约束' }
};

const DEMO_CORE_VALUES = {
  'starting-price': '64200',
  'bear-price': '48000',
  'range-price': '66000',
  'bull-price': '84000'
};
const MANUAL_CORE_INPUT_IDS = [...Object.keys(DEMO_CORE_VALUES), 'evidence-observed-at'];
const DEMO_EVIDENCE_AT = '2026-08-19T18:30';

let currentReadModel = null;
let activeScenario = 'RANGE';
let activeOptionId = null;
let analysisMode = 'demo';
let pendingRequest = null;

const form = document.querySelector('#scenario-form');
const asOfInput = document.querySelector('#evaluation-as-of');
const nowUtc8Input = () => new Date(Date.now() + 8 * 60 * 60 * 1000).toISOString().slice(0, 16);
asOfInput.value = nowUtc8Input();
const defaultValues = new Map([...form.querySelectorAll('input')].map(input => [input.id || input.getAttribute('aria-label'), input.value]));

const number = id => {
  const input = document.querySelector(`#${id}`);
  if (!input.value.trim()) throw new Error(`${document.querySelector(`label[for="${id}"]`)?.textContent || id} 不能为空`);
  const value = Number(input.value);
  if (!Number.isFinite(value)) throw new Error(`${document.querySelector(`label[for="${id}"]`)?.textContent || id} 不是有效数字`);
  return value;
};

const money = value => `${Number(value) >= 0 ? '+' : '−'}${Math.abs(Number(value)).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} USDT`;
const plainMoney = value => `${Number(value).toLocaleString('zh-CN', { maximumFractionDigits: 2 })} USDT`;
const rate = value => `${Number(value) >= 0 ? '+' : '−'}${Math.abs(Number(value) * 100).toFixed(2)}%`;
const ratio = value => `${(Number(value) * 100).toFixed(0)}%`;
const utc8Instant = value => new Date(`${value}:00+08:00`).toISOString();
const addDays = (instant, days) => new Date(new Date(instant).getTime() + days * 86400000).toISOString();

function readProbabilities() {
  const raw = [...document.querySelectorAll('#probabilities input')].map(input => input.value.trim());
  if (raw.every(value => value === '')) return null;
  if (raw.some(value => value === '')) throw new Error('概率必须三项全部填写，或三项全部清空');
  const values = raw.map(Number);
  if (values.some(value => !Number.isFinite(value) || value < 0 || value > 100)) throw new Error('概率必须在 0% 到 100% 之间');
  const total = values.reduce((sum, value) => sum + value, 0);
  if (Math.abs(total - 100) > 1e-9) throw new Error(`概率当前合计 ${total}%，必须等于 100%`);
  return values.map(value => value / 100);
}

function path(regime, pathCode, timeline, prices) {
  return {
    scenarioCode: regime,
    pathCode,
    points: timeline.map((at, index) => ({ at, btcUsdtPrice: prices[index] }))
  };
}

function scenario(regime, probability, timeline, startingPrice) {
  const prefix = scenarioMeta[regime].inputPrefix;
  const representative = [startingPrice, number(`${prefix}-rep-mid1`), number(`${prefix}-rep-mid2`), number(`${prefix}-price`)];
  const stress = [startingPrice, number(`${prefix}-stress-mid1`), number(`${prefix}-stress-mid2`), number(`${prefix}-stress-end`)];
  return {
    regime,
    probability,
    paths: [path(regime, 'REPRESENTATIVE', timeline, representative), path(regime, 'STRESS', timeline, stress)],
    sourceReference: analysisMode === 'demo' ? '情景实验室固定示例' : '情景实验室人工输入'
  };
}

function smartAssumption(cumulativeFundingRate) {
  return {
    hedgedNotionalRatio: number('smart-hedge-ratio'), cumulativeFundingRate,
    openingCost: number('smart-opening-cost'), exitCost: number('smart-exit-cost'),
    slippageCost: number('smart-slippage-cost'), rebalanceCost: number('smart-rebalance-cost'),
    serviceCost: number('smart-service-cost')
  };
}

function buildRequest() {
  const asOf = utc8Instant(asOfInput.value);
  const timeline = [asOf, addDays(asOf, 30), addDays(asOf, 60), addDays(asOf, 90)];
  const startingPrice = number('starting-price');
  const probabilities = readProbabilities();
  const sourceReference = analysisMode === 'demo' ? '固定示例证据快照' : '情景实验室人工证据快照';
  const evidenceAt = utc8Instant(document.querySelector('#evidence-observed-at').value);
  const evaluationId = `scenario-ui-${Date.now()}`;
  return {
    evaluation: {
      evaluationId, asOf, horizon: 'P90D', totalCapitalUsdt: number('total-capital'),
      capitalContext: 'NEW_MONEY_USDT', startingBtcPrice: startingPrice,
      scenarios: REGIMES.map((regime, index) => scenario(regime, probabilities ? probabilities[index] : null, timeline, startingPrice)),
      constraints: {
        minimumLiquidUsdtRatio: number('minimum-liquid'),
        maximumBtcDirectionalExposureRatio: number('maximum-btc-exposure'),
        maximumBearLossRate: number('maximum-bear-loss'),
        maximumProductRatios: {
          FIXED_EARN_90D: number('max-fixed'), FLEXIBLE_EARN: number('max-flex'),
          SMART_ARBITRAGE: number('max-smart'), SPOT_GRID: number('max-grid'), BTC_SPOT: number('max-btc')
        },
        allocationStep: 0.05, targetAprLow: 0.10, targetAprHigh: 0.20
      },
      calculationVersion: 'scenario-ui-v1'
    },
    productAssumptions: {
      fixedEarn: {
        productCode: 'FIXED_EARN_90D', netApr: number('fixed-apr'), maximumAllocationUsdt: number('fixed-cap'),
        observedAt: evidenceAt, sourceReference, shareRuleVersion: 'user-confirmed-net-apr-v1'
      },
      flexibleEarn: {
        productCode: 'FLEXIBLE_EARN', netApr: number('flex-apr'), maximumAllocationUsdt: number('flex-cap'),
        observedAt: evidenceAt, sourceReference, shareRuleVersion: 'user-confirmed-net-apr-v1'
      },
      smartArbitrage: {
        referenceCapitalUsdt: number('smart-reference-capital'),
        assumptions: {
          BEAR: smartAssumption(number('smart-bear-rate')),
          RANGE: smartAssumption(number('smart-range-rate')),
          BULL: smartAssumption(number('smart-bull-rate'))
        }
      },
      spotGrid: {
        referenceCapitalUsdt: number('grid-reference-capital'),
        assumption: {
          lowerPrice: number('grid-lower'), upperPrice: number('grid-upper'), gridCount: number('grid-count'),
          makerFeeRate: number('grid-maker-fee'), takerFeeRate: number('grid-taker-fee'),
          exitSlippageBps: number('grid-slippage'), takeProfitPrice: null, stopLossPrice: null,
          otherKnownCost: number('grid-other-cost')
        }
      },
      btcSpot: {
        entryFeeRate: number('btc-entry-fee'), exitFeeRate: number('btc-exit-fee'), exitSlippageBps: number('btc-slippage')
      }
    },
    productEvidence: [
      evidence('FIXED_EARN_90D', evidenceAt, sourceReference, ['NET_APR', 'MAX_ALLOCATION', 'TERMS_CONFIRMED', 'RULE_VERSION']),
      evidence('FLEXIBLE_EARN', evidenceAt, sourceReference, ['NET_APR', 'MAX_ALLOCATION', 'REDEMPTION_ASSUMPTION', 'RULE_VERSION']),
      evidence('SMART_ARBITRAGE', evidenceAt, sourceReference, ['HEDGED_NOTIONAL', 'FUNDING_SCENARIOS', 'ALL_KNOWN_COSTS']),
      evidence('SPOT_GRID', evidenceAt, sourceReference, ['GRID_PARAMETERS', 'TRADING_FEES', 'EXIT_SLIPPAGE', 'SHADOW_STATUS']),
      evidence('BTC_SPOT', evidenceAt, sourceReference, ['ENTRY_FEE', 'EXIT_FEE', 'EXIT_SLIPPAGE'])
    ]
  };
}

function evidence(productCode, observedAt, sourceReference, knownFields) {
  return { productCode, observedAt, sourceReference, knownFields, unknownFields: [] };
}

function restoreDefaultInputs() {
  defaultValues.forEach((value, key) => {
    const input = [...form.querySelectorAll('input')].find(item => (item.id || item.getAttribute('aria-label')) === key);
    if (input) input.value = value;
  });
  asOfInput.value = nowUtc8Input();
}

function clearManualCoreInputs() {
  MANUAL_CORE_INPUT_IDS.forEach(id => { document.querySelector(`#${id}`).value = ''; });
}

function applyDemoInputs() {
  Object.entries(DEMO_CORE_VALUES).forEach(([id, value]) => { document.querySelector(`#${id}`).value = value; });
  asOfInput.value = DEMO_EVIDENCE_AT;
  document.querySelector('#evidence-observed-at').value = DEMO_EVIDENCE_AT;
}

function setMode(mode) {
  analysisMode = mode;
  const demo = mode === 'demo';
  document.querySelector('#backend-mode').classList.toggle('active', !demo);
  document.querySelector('#demo-mode').classList.toggle('active', demo);
  restoreDefaultInputs();
  if (demo) {
    applyDemoInputs();
    document.querySelector('#mode-notice strong').textContent = '教学示例 · 后端计算';
    document.querySelector('#mode-notice > span').textContent = '使用页面内固定的情景与证据调用真实计算 API；这是功能演示，不是当前市场结论。';
    document.querySelector('#scenario-run span').textContent = '运行教学示例';
    document.querySelector('#mode-flag').textContent = '教学示例';
  } else {
    clearManualCoreInputs();
    document.querySelector('#mode-notice strong').textContent = '使用我的90天假设';
    document.querySelector('#mode-notice > span').textContent = '先抄录 BTC 起点价、三个期末价和产品证据时间；比例、收益、年化与资格全部由后端计算。';
    document.querySelector('#scenario-run span').textContent = '生成组合建议';
    document.querySelector('#mode-flag').textContent = '等待计算';
  }
  document.querySelectorAll('#scenario-form input').forEach(input => { input.disabled = demo; });
  REGIMES.forEach(regime => {
    const prefix = scenarioMeta[regime].inputPrefix;
    document.querySelector(`#${prefix}-card-source`).textContent = demo ? '教学示例，不代表当前市场' : '人工假设，不是价格预测';
  });
  activeScenario = 'RANGE';
  clearResults('模式已切换', demo ? '点击“运行教学示例”查看操作与结果。' : '填写核心输入后生成组合建议。');
  showMessage(demo ? '教学示例尚未运行，页面不会沿用上一轮结论。' : '请先填写空白的人工输入；证据模板可以展开核对。');
  updateScenarioCardsFromInputs();
  updateHeroSummary();
}

function setScenarioCardAvailability() {
  document.querySelectorAll('.scenario-card').forEach(card => {
    card.disabled = !currentReadModel;
    if (!currentReadModel) {
      card.classList.remove('active');
      card.setAttribute('aria-pressed', 'false');
      card.querySelector('.scenario-tags i').textContent = '结果生成后可查看';
    }
  });
}

function clearResults(title, detail, error = false) {
  currentReadModel = null;
  activeOptionId = null;
  document.body.classList.add('results-pending');
  document.body.classList.remove('results-ready', 'results-loading');
  const state = document.querySelector('#result-state');
  state.classList.toggle('error', error);
  state.querySelector('strong').textContent = title;
  state.querySelector('span').textContent = detail;
  setScenarioCardAvailability();
}

function markResultsStale() {
  if (!currentReadModel && !pendingRequest) return;
  if (pendingRequest) {
    pendingRequest.abort();
    pendingRequest = null;
    document.querySelector('#scenario-run').disabled = false;
  }
  clearResults('参数已修改，请重新生成', '旧结果对应修改前的输入，已停止展示。');
  document.querySelector('#mode-flag').textContent = '等待重新生成';
  showMessage('输入已经变化，请重新生成组合建议。');
}

function setLoading() {
  document.body.classList.add('results-pending', 'results-loading');
  document.body.classList.remove('results-ready');
  const state = document.querySelector('#result-state');
  state.classList.remove('error');
  state.querySelector('strong').textContent = '后端正在枚举组合';
  state.querySelector('span').textContent = '正在裁决产品资格、计算六条路径并应用风险约束。';
  document.querySelector('#scenario-run').disabled = true;
}

async function evaluate() {
  let request;
  try {
    request = buildRequest();
  } catch (error) {
    clearResults('输入不合法', error.message, true);
    showMessage(error.message, true);
    return;
  }
  if (pendingRequest) pendingRequest.abort();
  const controller = new AbortController();
  pendingRequest = controller;
  setLoading();
  showMessage('正在调用后端统一情景引擎……');
  try {
    const response = await fetch(API_URL, {
      method: 'POST', headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(request), signal: controller.signal
    });
    const payload = await response.json().catch(() => ({}));
    if (!response.ok) throw new Error(`${payload.code || `HTTP_${response.status}`}：${payload.message || '后端未返回可用说明'}`);
    renderReadModel(payload);
    showMessage(analysisMode === 'demo' ? '固定示例已由后端计算。请勿把示例数字视为当前市场结论。' : '计算完成。所有方案数字均来自本次后端响应。');
  } catch (error) {
    if (error.name === 'AbortError') return;
    clearResults('本次计算失败', error.message, true);
    showMessage(error.message, true);
    document.querySelector('#mode-flag').textContent = '计算失败';
  } finally {
    if (pendingRequest === controller) {
      pendingRequest = null;
      document.querySelector('#scenario-run').disabled = false;
    }
  }
}

function renderReadModel(readModel) {
  if (!readModel?.evaluation || !readModel?.optionSet?.options?.length) throw new Error('后端响应缺少完整组合读模型');
  currentReadModel = readModel;
  activeScenario = 'RANGE';
  const balanced = readModel.optionSet.options.find(option => option.labels.includes('BALANCED'));
  activeOptionId = (balanced || readModel.optionSet.options[0]).optionId;
  document.body.classList.remove('results-pending', 'results-loading');
  document.body.classList.add('results-ready');
  document.querySelector('#result-state').classList.remove('error');
  document.querySelector('#result-state strong').textContent = analysisMode === 'demo' ? '固定示例计算完成' : '后端计算完成';
  document.querySelector('#result-state span').textContent = '结果已经通过金额、贡献和路径完整性校验。';
  document.querySelector('#mode-flag').textContent = analysisMode === 'demo' ? '示例结果' : '当前计算';
  setScenarioCardAvailability();
  renderHeader(readModel);
  renderScenarioCards(readModel);
  renderOptionSwitcher();
  renderScenarioFocus();
  renderFooter();
}

function renderHeader(readModel) {
  document.querySelector('#hero-price').innerHTML = `${Number(readModel.evaluation.startingBtcPrice).toLocaleString('zh-CN')} <small>USDT</small>`;
  document.querySelector('#hero-capital').innerHTML = `${Number(readModel.evaluation.totalCapitalUsdt).toLocaleString('zh-CN')} <small>USDT</small>`;
  document.querySelector('#candidate-summary').textContent = `后端找到 ${readModel.optionSet.feasibleCandidateCount} 个可行候选、${readModel.optionSet.paretoCandidateCount} 个帕累托候选，最终返回 ${readModel.optionSet.options.length} 个去重方案。`;
}

function renderScenarioCards(readModel) {
  REGIMES.forEach(regime => {
    const scenario = readModel.evaluation.scenarios.find(item => item.regime === regime);
    const representative = scenario.paths.find(item => item.pathCode === 'REPRESENTATIVE');
    const stress = scenario.paths.find(item => item.pathCode === 'STRESS');
    const prefix = scenarioMeta[regime].inputPrefix;
    document.querySelector(`#${prefix}-card-price`).textContent = `${Number(representative.points.at(-1).btcUsdtPrice).toLocaleString('zh-CN')} USDT`;
    document.querySelector(`#${prefix}-stress-tag`).textContent = `压力终点 ${Number(stress.points.at(-1).btcUsdtPrice).toLocaleString('zh-CN')}`;
    document.querySelector(`#${prefix}-change`).textContent = `${rate(Number(representative.points.at(-1).btcUsdtPrice) / Number(readModel.evaluation.startingBtcPrice) - 1)} / 90天`;
    document.querySelector(`[data-scenario="${regime}"] .scenario-tags i`).textContent = '查看该情景结果';
  });
}

function renderOptionSwitcher() {
  const switcher = document.querySelector('#plan-switcher');
  const layout = document.querySelector('.portfolio-layout');
  const singleOption = currentReadModel.optionSet.options.length === 1;
  switcher.hidden = singleOption;
  layout.classList.toggle('single-option', singleOption);
  switcher.replaceChildren();
  currentReadModel.optionSet.options.forEach(option => {
    const primary = primaryLabel(option.labels);
    const button = document.createElement('button');
    button.type = 'button';
    button.dataset.optionId = option.optionId;
    button.classList.toggle('active', option.optionId === activeOptionId);
    const label = document.createElement('span');
    label.textContent = option.labels.map(item => optionLabels[item].short).join(' / ');
    const title = document.createElement('strong');
    title.textContent = optionLabels[primary].title;
    const allocation = document.createElement('small');
    allocation.textContent = option.allocations.map(item => `${productMeta[item.productCode].name} ${ratio(item.ratio)}`).join(' · ');
    button.append(label, title, allocation);
    button.addEventListener('click', () => { activeOptionId = option.optionId; renderOptionSwitcher(); renderPlan(); renderMatrix(); });
    switcher.appendChild(button);
  });
}

function renderScenarioFocus() {
  document.querySelectorAll('.scenario-card').forEach(card => {
    const active = card.dataset.scenario === activeScenario;
    card.classList.toggle('active', active);
    card.setAttribute('aria-pressed', String(active));
  });
  document.querySelectorAll('[data-column]').forEach(cell => cell.classList.toggle('focused', cell.dataset.column === activeScenario));
  const meta = scenarioMeta[activeScenario];
  document.querySelector('#advisor-title').textContent = meta.title;
  document.querySelector('#advisor-copy').textContent = meta.copy;
  document.querySelector('#advisor-counter').textContent = meta.counter;
  renderPlan();
  renderMatrix();
}

function currentOption() {
  return currentReadModel.optionSet.options.find(option => option.optionId === activeOptionId);
}

function representativeOutcome(option, regime) {
  return option.pathOutcomes.find(outcome => outcome.scenarioCode === regime && outcome.pathCode === 'REPRESENTATIVE');
}

function renderPlan() {
  const option = currentOption();
  const outcome = representativeOutcome(option, activeScenario);
  const primary = primaryLabel(option.labels);
  document.querySelector('#plan-label').textContent = option.labels.map(item => optionLabels[item].short).join(' / ');
  document.querySelector('#plan-title').textContent = optionLabels[primary].title;
  const attainment = option.targetAttainment;
  const probability = attainment.lowTargetProbability == null ? '' : ` · 概率 ${(Number(attainment.lowTargetProbability) * 100).toFixed(0)}% / ${(Number(attainment.highTargetProbability) * 100).toFixed(0)}%`;
  document.querySelector('#plan-target').textContent = `10%：${attainment.lowTargetScenarioCount}/${attainment.scenarioCount} · 20%：${attainment.highTargetScenarioCount}/${attainment.scenarioCount}${probability}`;
  document.querySelector('#focused-return').textContent = rate(outcome.holdingReturnRate);
  document.querySelector('#focused-return').className = Number(outcome.netIncomeUsdt) < 0 ? 'loss' : 'gain';
  document.querySelector('#focused-income').textContent = `${money(outcome.netIncomeUsdt)} / 90天`;
  document.querySelector('#focused-apr').textContent = rate(outcome.simpleAnnualizedRate);
  document.querySelector('#plan-drawdown').textContent = outcome.maximumDrawdownRate == null ? '未知' : rate(outcome.maximumDrawdownRate);
  document.querySelector('#plan-exposure').textContent = ratio(outcome.conservativePeakBtcExposureRate);
  document.querySelector('#scenario-badge').textContent = scenarioMeta[activeScenario].badge;
  document.querySelector('#scenario-explanation').textContent = scenarioMeta[activeScenario].explanation;
  renderAllocations(option);
  renderContributions(option, outcome);
  renderExplanation(option);
}

function renderAllocations(option) {
  const bar = document.querySelector('#allocation-bar');
  const legend = document.querySelector('#allocation-legend');
  bar.replaceChildren();
  legend.replaceChildren();
  option.allocations.forEach(allocation => {
    const meta = productMeta[allocation.productCode];
    const segment = document.createElement('span');
    segment.style.width = `${Number(allocation.ratio) * 100}%`;
    segment.style.background = meta.color;
    segment.title = `${meta.name} ${ratio(allocation.ratio)}`;
    const item = document.createElement('span');
    const marker = document.createElement('i');
    marker.style.background = meta.color;
    item.append(marker, document.createTextNode(`${meta.name} ${ratio(allocation.ratio)} · ${plainMoney(allocation.amountUsdt)}`));
    bar.appendChild(segment);
    legend.appendChild(item);
  });

  const allocated = new Set(option.allocations.map(item => item.productCode));
  const qualifiedNotAllocated = currentReadModel.qualifiedProducts.filter(item => !allocated.has(item.productCode));
  const rows = [
    ...qualifiedNotAllocated.map(item => ({ productCode: item.productCode, reason: '证据合格，但未进入当前方案' })),
    ...currentReadModel.excludedProducts.map(item => ({ productCode: item.productCode, reason: item.reasonCodes.join('、') }))
  ];
  const panel = document.querySelector('#allocation-exclusions');
  const list = document.querySelector('#allocation-exclusion-list');
  panel.hidden = rows.length === 0;
  list.replaceChildren();
  rows.forEach(row => {
    const item = document.createElement('li');
    const strong = document.createElement('strong');
    strong.textContent = productMeta[row.productCode].name;
    const reason = document.createElement('span');
    reason.textContent = row.reason;
    item.append(strong, reason);
    list.appendChild(item);
  });
}

function renderMatrix() {
  const body = document.querySelector('#matrix-body');
  body.replaceChildren();
  currentReadModel.optionSet.options.forEach(option => {
    const row = document.createElement('tr');
    row.classList.toggle('selected', option.optionId === activeOptionId);
    const name = document.createElement('td');
    name.textContent = option.labels.map(item => optionLabels[item].short).join(' / ');
    row.appendChild(name);
    REGIMES.forEach(regime => {
      const outcome = representativeOutcome(option, regime);
      const cell = document.createElement('td');
      cell.classList.toggle('focused', regime === activeScenario);
      const income = document.createElement('strong');
      income.className = Number(outcome.netIncomeUsdt) < 0 ? 'loss' : 'gain';
      income.textContent = money(outcome.netIncomeUsdt);
      const detail = document.createElement('small');
      detail.textContent = `90天 ${rate(outcome.holdingReturnRate)} · 年化 ${rate(outcome.simpleAnnualizedRate)}`;
      cell.append(income, detail);
      row.appendChild(cell);
    });
    const judgment = document.createElement('td');
    judgment.textContent = option.labels.map(item => optionLabels[item].short).join(' / ');
    row.appendChild(judgment);
    body.appendChild(row);
  });
}

function renderContributions(option, outcome) {
  document.querySelector('#contribution-plan').textContent = option.labels.map(item => optionLabels[item].short).join(' / ');
  document.querySelector('#contribution-scenario').textContent = scenarioMeta[activeScenario].badge;
  const list = document.querySelector('#contribution-list');
  list.replaceChildren();
  const maximum = Math.max(...outcome.contributions.map(item => Math.abs(Number(item.amountUsdt))), 1);
  outcome.contributions.forEach(contribution => {
    const value = Number(contribution.amountUsdt);
    const row = document.createElement('div');
    row.className = `contribution-row ${value < 0 ? 'negative' : ''}`;
    const label = document.createElement('span');
    label.textContent = contributionMeta[contribution.code] || contribution.code;
    const track = document.createElement('div');
    track.className = 'contribution-track';
    const fill = document.createElement('i');
    fill.style.width = `${Math.max(4, Math.abs(value) / maximum * 100)}%`;
    track.appendChild(fill);
    const amount = document.createElement('strong');
    amount.textContent = money(value);
    row.append(label, track, amount);
    list.appendChild(row);
  });
  document.querySelector('#contribution-check').textContent = `后端贡献合计 ${money(outcome.netIncomeUsdt)}，与组合净收益一致`;
}

function renderExplanation(option) {
  const actions = document.querySelector('#advisor-actions');
  actions.replaceChildren();
  [
    `当前方案：${option.allocations.map(item => `${productMeta[item.productCode].name} ${ratio(item.ratio)}`).join('，')}`,
    ...option.limitations,
    '这是条件情景分析；最终仍由你在 OKX 手工核对并决定是否购买。'
  ].forEach(text => {
    const item = document.createElement('li');
    item.textContent = text;
    actions.appendChild(item);
  });
}

function renderFooter() {
  const evaluation = currentReadModel.evaluation;
  document.querySelector('#footer-evaluation').textContent = `${evaluation.evaluationId} · ${new Date(evaluation.asOf).toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })}`;
  document.querySelector('#footer-evidence').textContent = `${currentReadModel.qualifiedProducts.length} 项合格 · ${currentReadModel.excludedProducts.length} 项排除`;
  document.querySelector('#footer-state').textContent = analysisMode === 'demo' ? '固定示例 · 后端只读结果' : '自定义输入 · 后端只读结果';
}

function primaryLabel(labels) {
  if (labels.includes('BALANCED')) return 'BALANCED';
  if (labels.includes('CONSERVATIVE')) return 'CONSERVATIVE';
  return 'RETURN_SEEKING';
}

function showMessage(message, error = false) {
  const target = document.querySelector('#form-message');
  target.textContent = message;
  target.classList.toggle('error', error);
}

function updateScenarioCardsFromInputs() {
  const pairs = [
    ['bear-price', 'bear-card-price'], ['range-price', 'range-card-price'], ['bull-price', 'bull-card-price'],
    ['bear-stress-end', 'bear-stress-tag'], ['range-stress-end', 'range-stress-tag'], ['bull-stress-end', 'bull-stress-tag']
  ];
  pairs.forEach(([inputId, outputId], index) => {
    const raw = document.querySelector(`#${inputId}`).value.trim();
    const output = document.querySelector(`#${outputId}`);
    if (!raw) {
      output.textContent = index < 3 ? '请填写' : '风险终点待填写';
      return;
    }
    const value = Number(raw);
    if (!Number.isFinite(value)) return;
    output.textContent = index < 3 ? `${value.toLocaleString('zh-CN')} USDT` : `风险终点 ${value.toLocaleString('zh-CN')}`;
  });
  const startingRaw = document.querySelector('#starting-price').value.trim();
  REGIMES.forEach(regime => {
    const prefix = scenarioMeta[regime].inputPrefix;
    const endingRaw = document.querySelector(`#${prefix}-price`).value.trim();
    const output = document.querySelector(`#${prefix}-change`);
    if (!startingRaw || !endingRaw) output.textContent = '等待起点和期末价格';
    else output.textContent = `${rate(Number(endingRaw) / Number(startingRaw) - 1)} / 90天`;
  });
}

function updateHeroSummary() {
  const price = document.querySelector('#starting-price').value.trim();
  const capital = document.querySelector('#total-capital').value.trim();
  document.querySelector('#hero-price').innerHTML = price
    ? `${Number(price).toLocaleString('zh-CN')} <small>USDT · ${analysisMode === 'demo' ? '教学示例' : '人工录入'}</small>`
    : '— <small>等待人工录入</small>';
  document.querySelector('#hero-capital').innerHTML = capital
    ? `${Number(capital).toLocaleString('zh-CN')} <small>USDT</small>`
    : '— <small>等待输入</small>';
}

document.querySelectorAll('.scenario-card').forEach(card => card.addEventListener('click', () => {
  if (!currentReadModel) return;
  activeScenario = card.dataset.scenario;
  renderScenarioFocus();
}));
form.addEventListener('input', () => {
  updateScenarioCardsFromInputs();
  updateHeroSummary();
  markResultsStale();
});
form.addEventListener('submit', event => { event.preventDefault(); evaluate(); });
document.querySelector('#backend-mode').addEventListener('click', () => setMode('custom'));
document.querySelector('#demo-mode').addEventListener('click', () => setMode('demo'));

setMode('demo');

/* 环境监测平台 · 后台公共部分：接口、格式化、曲线图、工单详情、智能助手 */
(function () {
  const { reactive, ref, computed, watch, nextTick } = Vue;
  const { ElMessage, ElMessageBox } = ElementPlus;

  const state = reactive({ me: null, ready: false, page: '', unread: 0, agentOpen: false, orderId: null, orderTick: 0 });

  async function api(path, { method = 'GET', body } = {}) {
    const opt = { method, headers: {}, credentials: 'same-origin' };
    if (body !== undefined) {
      opt.headers['Content-Type'] = 'application/json';
      opt.body = JSON.stringify(body);
    }
    const resp = await fetch('api' + path, opt);
    const ct = resp.headers.get('content-type') || '';
    const data = ct.includes('json') ? await resp.json() : null;
    if (resp.status === 401) { state.me = null; throw new Error((data && data.message) || '请先登录'); }
    if (!resp.ok) throw new Error((data && data.message) || '请求失败：' + resp.status);
    return data;
  }
  const fail = e => ElMessage.error(e.message || '操作失败');
  const ok = msg => ElMessage.success(msg || '已保存');
  const pad = n => String(n).padStart(2, '0');
  function fmt(s, withSec) {
    if (!s) return '';
    const d = new Date(s);
    if (isNaN(d)) return s;
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + (withSec ? ':' + pad(d.getSeconds()) : '');
  }
  const short = s => s ? fmt(s).slice(5) : '';
  /**
   * 平台按北京时间存储和返回时间（不带时区），浏览器所在时区可能不同。
   * 页面上统一按北京时间显示和计算：nowCN() 返回"北京时间的当前时刻"，和接口返回的时间可以直接比较
   */
  const nowCN = () => new Date(Date.now() + (480 + new Date().getTimezoneOffset()) * 60000);
  function ago(s) {
    if (!s) return '无数据';
    const sec = Math.max(0, Math.round((nowCN() - new Date(s)) / 1000));
    return sec < 60 ? sec + ' 秒前' : sec < 3600 ? Math.round(sec / 60) + ' 分钟前' : fmt(s);
  }
  /** 后端查询参数用的时间格式 */
  function qtime(d) {
    return d.getFullYear() + '-' + pad(d.getMonth() + 1) + '-' + pad(d.getDate()) + ' ' + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
  }
  const has = p => !!(state.me && state.me.permissions.includes(p));
  const qs = o => Object.entries(o).filter(([, v]) => v !== undefined && v !== null && v !== '').map(([k, v]) => k + '=' + encodeURIComponent(v)).join('&');

  const STATUS_TYPE = { PENDING: 'danger', PROCESSING: 'warning', REVIEWING: 'primary', SUBMITTED: 'primary', CLOSED: 'success', APPROVED: 'success' };
  const LEVEL_CLASS = { 1: 'l1', 2: 'l2', 3: 'l3' };

  /** 只认几种常用写法的 Markdown，先转义再替换，不会执行任何 HTML */
  function md(text) {
    if (!text) return '';
    const esc = String(text).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
    let html = '', inList = false;
    const inline = s => s.replace(/\*\*(.+?)\*\*/g, '<b>$1</b>');
    for (const raw of esc.split('\n')) {
      const line = raw.trimEnd();
      const li = line.match(/^\s*[-*]\s+(.*)$/);
      if (li) {
        if (!inList) { html += '<ul>'; inList = true; }
        html += '<li>' + inline(li[1]) + '</li>';
        continue;
      }
      if (inList) { html += '</ul>'; inList = false; }
      const h = line.match(/^#{1,4}\s+(.*)$/);
      if (h) html += '<div class="md-h">' + inline(h[1]) + '</div>';
      else if (line.trim() === '') html += '<div class="md-gap"></div>';
      else html += '<div>' + inline(line) + '</div>';
    }
    if (inList) html += '</ul>';
    return html;
  }

  // ---------- 曲线图（SVG，不依赖图表库） ----------
  const LineChart = {
    props: { points: Array, upper: Number, lower: Number, unit: String, height: { type: Number, default: 260 } },
    setup(props) {
      const W = 900, P = { l: 52, r: 16, t: 16, b: 30 };
      const hover = ref(null);
      const data = computed(() => (props.points || []).map(p => Array.isArray(p) ? { t: new Date(p[0]), v: p[1] } : { t: new Date(p.t), v: p.v })
        .filter(p => p.v !== null && p.v !== undefined && !isNaN(p.t)));
      const box = computed(() => {
        const d = data.value;
        if (!d.length) return null;
        let min = Math.min(...d.map(p => p.v)), max = Math.max(...d.map(p => p.v));
        if (props.upper != null) max = Math.max(max, props.upper);
        if (props.lower != null) min = Math.min(min, props.lower);
        if (min === max) { min -= 1; max += 1; }
        const margin = (max - min) * 0.08; min -= margin; max += margin;
        if (min < 0 && Math.min(...d.map(p => p.v)) >= 0) min = 0;
        const t0 = d[0].t.getTime(), t1 = Math.max(d[d.length - 1].t.getTime(), t0 + 1);
        const H = props.height;
        const x = t => P.l + (t - t0) / (t1 - t0) * (W - P.l - P.r);
        const y = v => P.t + (1 - (v - min) / (max - min)) * (H - P.t - P.b);
        const path = d.map((p, i) => (i ? 'L' : 'M') + x(p.t.getTime()).toFixed(1) + ' ' + y(p.v).toFixed(1)).join(' ');
        const ticks = [0, 1, 2, 3, 4].map(i => { const v = min + (max - min) * i / 4; return { v: +v.toFixed(Math.abs(max - min) < 5 ? 2 : 1), y: y(v) }; });
        const xt = [0, 1, 2, 3, 4].map(i => { const t = t0 + (t1 - t0) * i / 4; const dd = new Date(t); return { x: x(t), label: (t1 - t0 > 86400000 * 2 ? (dd.getMonth() + 1) + '/' + dd.getDate() + ' ' : '') + pad(dd.getHours()) + ':' + pad(dd.getMinutes()) }; });
        const over = p => (props.upper != null && p.v > props.upper) || (props.lower != null && p.v < props.lower);
        const dots = d.length <= 120 ? d.map(p => ({ x: x(p.t.getTime()), y: y(p.v), over: over(p) })) : d.filter(over).map(p => ({ x: x(p.t.getTime()), y: y(p.v), over: true }));
        return { H, path, ticks, xt, dots, x, y, upperY: props.upper != null ? y(props.upper) : null, lowerY: props.lower != null ? y(props.lower) : null };
      });
      function move(e) {
        const b = box.value; if (!b) return;
        const rect = e.currentTarget.getBoundingClientRect();
        const px = (e.clientX - rect.left) / rect.width * W;
        let best = null, bd = 1e9;
        for (const p of data.value) { const dx = Math.abs(b.x(p.t.getTime()) - px); if (dx < bd) { bd = dx; best = p; } }
        hover.value = best ? { x: b.x(best.t.getTime()), y: b.y(best.v), label: fmt(best.t, true) + '  ' + best.v + (props.unit || '') } : null;
      }
      return { W, P, box, hover, move };
    },
    template: `<div class="chart-wrap">
      <svg v-if="box" :viewBox="'0 0 ' + W + ' ' + box.H" preserveAspectRatio="none" class="line-chart" :style="{height: box.H + 'px'}" @mousemove="move" @mouseleave="hover=null">
        <g v-for="t in box.ticks" :key="'y'+t.y"><line :x1="P.l" :x2="W-P.r" :y1="t.y" :y2="t.y" class="grid"/><text :x="P.l-6" :y="t.y+4" text-anchor="end" class="ax">{{ t.v }}</text></g>
        <text v-for="t in box.xt" :key="'x'+t.x" :x="t.x" :y="box.H-8" text-anchor="middle" class="ax">{{ t.label }}</text>
        <g v-if="box.upperY!=null"><line :x1="P.l" :x2="W-P.r" :y1="box.upperY" :y2="box.upperY" class="limit"/><text :x="W-P.r" :y="box.upperY-4" text-anchor="end" class="limit-t">上限 {{ upper }}</text></g>
        <g v-if="box.lowerY!=null"><line :x1="P.l" :x2="W-P.r" :y1="box.lowerY" :y2="box.lowerY" class="limit"/><text :x="W-P.r" :y="box.lowerY+12" text-anchor="end" class="limit-t">下限 {{ lower }}</text></g>
        <path :d="box.path" class="line"/>
        <circle v-for="(d,i) in box.dots" :key="i" :cx="d.x" :cy="d.y" :r="d.over?3.5:2" :class="d.over?'dot over':'dot'"/>
        <g v-if="hover"><line :x1="hover.x" :x2="hover.x" :y1="P.t" :y2="box.H-P.b" class="cursor"/><circle :cx="hover.x" :cy="hover.y" r="4" class="dot hover"/></g>
      </svg>
      <div v-if="box && hover" class="chart-tip">{{ hover.label }}</div>
      <div v-if="!box" class="empty">这段时间没有数据</div>
    </div>`,
  };

  // ---------- 工单详情抽屉 ----------
  const OrderDrawer = {
    setup() {
      const d = ref(null);
      const loading = ref(false);
      const form = reactive({ result: '', comment: '' });
      const visible = computed({ get: () => !!state.orderId, set: v => { if (!v) state.orderId = null; } });
      async function load() {
        if (!state.orderId) { d.value = null; return; }
        loading.value = true;
        try { d.value = await api('/orders/' + state.orderId); } catch (e) { fail(e); state.orderId = null; } finally { loading.value = false; }
      }
      watch(() => [state.orderId, state.orderTick], () => { form.result = ''; form.comment = ''; load(); });
      async function submit() {
        try { await api('/orders/' + state.orderId + '/submit', { method: 'POST', body: { result: form.result } }); ok('已提交，等待审核'); done(); } catch (e) { fail(e); }
      }
      async function review(approve) {
        try { await api('/orders/' + state.orderId + '/review', { method: 'POST', body: { approve, comment: form.comment } }); ok(approve ? '已审核通过' : '已驳回'); done(); } catch (e) { fail(e); }
      }
      function done() { state.orderTick++; window.dispatchEvent(new Event('order-changed')); }
      return { d, loading, form, visible, submit, review, fmt, STATUS_TYPE, LEVEL_CLASS };
    },
    template: `<el-drawer v-model="visible" size="560px" :title="d ? '工单 ' + d.order.code : '工单'" destroy-on-close>
      <div v-loading="loading" v-if="d">
        <div class="od-head">
          <el-tag :type="STATUS_TYPE[d.order.status]" effect="dark">{{ d.order.statusLabel }}</el-tag>
          <el-tag v-if="d.order.rejectCount" type="info">驳回 {{ d.order.rejectCount }} 次</el-tag>
        </div>
        <el-descriptions :column="2" border size="small" class="mt">
          <el-descriptions-item label="站点" :span="2">{{ d.alarm && d.alarm.siteName }}</el-descriptions-item>
          <el-descriptions-item label="预警" :span="2"><span :class="'lv ' + LEVEL_CLASS[d.alarm.level]">{{ d.alarm.levelText }}</span> {{ d.alarm.content }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ d.alarm.deviceName }}</el-descriptions-item>
          <el-descriptions-item label="触发">{{ d.alarm.hitCount }} 次<el-tag v-if="d.alarm.recovered" size="small" type="success" style="margin-left:6px">已恢复</el-tag></el-descriptions-item>
          <el-descriptions-item label="处理人">{{ d.order.handlerName }}</el-descriptions-item>
          <el-descriptions-item label="审核人">{{ d.order.reviewerName || '有审核权限的人员' }}</el-descriptions-item>
          <el-descriptions-item label="派单方式" :span="2">{{ d.order.dispatchReason }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="d.canSubmit" class="od-box">
          <div class="od-title">提交处理结果</div>
          <el-input v-model="form.result" type="textarea" :rows="3" placeholder="原因、采取的措施、处理后的数据情况" />
          <div class="mt"><el-button type="primary" @click="submit">提交审核</el-button></div>
        </div>
        <div v-if="d.canReview" class="od-box">
          <div class="od-title">审核</div>
          <div class="muted" style="margin-bottom:6px">处理结果：{{ d.order.resultText }}</div>
          <el-input v-model="form.comment" type="textarea" :rows="2" placeholder="审核意见（驳回时必填）" />
          <div class="mt"><el-button type="success" @click="review(true)">审核通过</el-button><el-button type="danger" plain @click="review(false)">驳回重办</el-button></div>
        </div>

        <div class="od-title mt">流转记录</div>
        <div v-for="l in d.logs" :key="l.id" class="tl" :class="'tl-' + l.action">
          <div class="a">{{ l.actionLabel }}<span class="w">{{ l.operatorName }} · {{ fmt(l.createdAt, true) }}<template v-if="l.via"> · {{ l.via }}</template></span></div>
          <div class="c" v-if="l.content">{{ l.content }}</div>
        </div>
      </div>
    </el-drawer>`,
  };

  // ---------- 智能助手（查询） ----------
  const AgentPanel = {
    components: { LineChart },
    setup() {
      const msgs = ref([]);
      const input = ref('');
      const busy = ref(false);
      const info = ref(null);
      const box = ref(null);
      const visible = computed({ get: () => state.agentOpen, set: v => state.agentOpen = v });
      watch(visible, async v => {
        if (v && !info.value) {
          try { info.value = await api('/agent/info'); } catch (e) { fail(e); }
          if (!msgs.value.length) msgs.value.push({ role: 'assistant', text: '你好，' + state.me.name + '。我可以帮你查运行情况、预警、设备、实时和历史数据、待办工单。', suggestions: (info.value && info.value.examples) || [] });
        }
      });
      const scroll = () => nextTick(() => { if (box.value) box.value.scrollTop = box.value.scrollHeight; });
      async function send(text) {
        const m = (text || input.value).trim();
        if (!m || busy.value) return;
        input.value = '';
        msgs.value.push({ role: 'user', text: m });
        busy.value = true; scroll();
        try {
          const r = await api('/agent/chat', { method: 'POST', body: { message: m } });
          msgs.value.push({ role: 'assistant', text: r.text, charts: r.charts, suggestions: r.suggestions, mode: r.mode });
        } catch (e) { msgs.value.push({ role: 'assistant', text: '出错了：' + e.message }); }
        finally { busy.value = false; scroll(); }
      }
      async function reset() { await api('/agent/reset', { method: 'POST' }).catch(() => { }); msgs.value = []; info.value = null; visible.value = false; }
      const key = e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(); } };
      return { visible, msgs, input, busy, info, box, send, reset, key, md };
    },
    template: `<el-drawer v-model="visible" size="520px" :with-header="false" class="agent-drawer">
      <div class="agent">
        <div class="agent-head"><b>智能助手</b><span class="muted" v-if="info">{{ info.mode === 'llm' ? '大模型模式' : '规则模式（未接入大模型）' }}</span><span class="grow"></span><el-button link @click="reset">清空对话</el-button><el-button link @click="visible=false">关闭</el-button></div>
        <div class="agent-body" ref="box">
          <div v-for="(m,i) in msgs" :key="i" :class="'msg ' + m.role">
            <div class="bubble"><div class="md" v-html="md(m.text)"></div>
              <div v-for="(c,j) in m.charts || []" :key="'c'+j" class="agent-chart"><div class="muted">{{ c.title }}</div><line-chart :points="c.points" :upper="c.upper" :lower="c.lower" :unit="c.unit" :height="180" /></div>
            </div>
            <div v-if="m.suggestions && i===msgs.length-1" class="sugs"><a v-for="s in m.suggestions" :key="s" @click="send(s)">{{ s }}</a></div>
          </div>
          <div v-if="busy" class="msg assistant"><div class="bubble muted">思考中…</div></div>
        </div>
        <div class="agent-input"><el-input v-model="input" type="textarea" :rows="2" resize="none" placeholder="问点什么，比如：哪些设备离线了" @keydown="key" /><el-button type="primary" :loading="busy" @click="send()">发送</el-button></div>
      </div>
    </el-drawer>`,
  };

  window.EM = { state, api, fail, ok, fmt, short, qtime, nowCN, ago, has, qs, md, STATUS_TYPE, LEVEL_CLASS, LineChart, OrderDrawer, AgentPanel, ElMessageBox };
})();

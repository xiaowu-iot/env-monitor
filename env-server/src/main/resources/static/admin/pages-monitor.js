/* 环境监测平台 · 后台页面：概览、实时数据、历史数据、预警、工单、消息 */
(function () {
  const { reactive, ref, computed, watch, onMounted, onUnmounted } = Vue;
  const { state, api, fail, ok, fmt, short, qtime, nowCN, ago, has, qs, STATUS_TYPE, LEVEL_CLASS, LineChart } = EM;

  /** 定时刷新，离开页面自动停 */
  function useTimer(fn, ms) {
    let t = null;
    onMounted(() => { fn(); t = setInterval(fn, ms); });
    onUnmounted(() => clearInterval(t));
  }
  /** 工单在别处变了（抽屉、智能助手），列表跟着刷新 */
  function onOrderChanged(fn) {
    onMounted(() => window.addEventListener('order-changed', fn));
    onUnmounted(() => window.removeEventListener('order-changed', fn));
  }

  // ---------- 概览 ----------
  const Dashboard = {
    setup() {
      const d = ref(null);
      const load = async () => { try { d.value = await api('/dashboard'); } catch (e) { fail(e); } };
      useTimer(load, 15000);
      const maxTrend = computed(() => d.value ? Math.max(1, ...d.value.trend.map(t => t.count)) : 1);
      const openOrder = id => { if (id) state.orderId = id; };
      return { d, maxTrend, openOrder, short, LEVEL_CLASS, STATUS_TYPE, state };
    },
    template: `<div v-if="d">
      <div class="kpis">
        <div class="kpi ok"><div class="bar"></div><div class="k">设备在线</div><div class="v">{{ d.summary.deviceOnline }}<small>/ {{ d.summary.deviceTotal }} 台</small></div><div class="d">在线率 {{ d.summary.deviceTotal ? Math.round(d.summary.deviceOnline*100/d.summary.deviceTotal) : 0 }}%</div></div>
        <div class="kpi warn"><div class="bar"></div><div class="k">今日预警</div><div class="v">{{ d.summary.alarmTotal }}<small>条</small></div><div class="d">工单新建 {{ d.summary.ordersCreated }}，办结 {{ d.summary.ordersClosed }}</div></div>
        <div class="kpi danger"><div class="bar"></div><div class="k">未关闭预警</div><div class="v">{{ d.summary.openAlarms }}<small>条</small></div><div class="d">待派单 {{ d.summary.pendingDispatch }}</div></div>
        <div class="kpi"><div class="bar"></div><div class="k">我的工作</div><div class="v">{{ d.myTodo }}<small>待办</small> {{ d.myReview }}<small>待审核</small></div><div class="d"><a href="#/orders">去处理 ›</a></div></div>
      </div>
      <div class="grid2">
        <div class="panel"><div class="panel-title">最近 7 天预警</div>
          <div class="bars"><div v-for="t in d.trend" :key="t.date" class="col"><div class="b" :style="{height: (t.count*100/maxTrend) + '%'}"><span>{{ t.count }}</span></div><em>{{ t.date }}</em></div></div>
        </div>
        <div class="panel"><div class="panel-title">今日预警最多的站点</div>
          <div class="dist" v-if="d.summary.topSites.length">
            <div class="row" v-for="e in d.summary.topSites" :key="Object.keys(e)[0]"><span class="n">{{ Object.keys(e)[0] }}</span><span class="c">{{ Object.values(e)[0] }} 条</span></div>
          </div>
          <div v-else class="empty">今天还没有预警</div>
        </div>
      </div>
      <div class="panel"><div class="panel-title">未关闭的预警</div>
        <el-table :data="d.recentAlarms" size="small" @row-click="r => openOrder(r.orderId)" row-class-name="clickable">
          <el-table-column label="级别" width="70"><template #default="{row}"><span :class="'lv ' + LEVEL_CLASS[row.level]">{{ row.levelText }}</span></template></el-table-column>
          <el-table-column prop="siteName" label="站点" width="200" />
          <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
          <el-table-column label="状态" width="90"><template #default="{row}"><el-tag size="small" :type="STATUS_TYPE[row.status]">{{ row.statusLabel }}</el-tag></template></el-table-column>
          <el-table-column label="最近" width="110"><template #default="{row}">{{ short(row.lastAt) }}</template></el-table-column>
        </el-table>
      </div>
    </div>`,
  };

  // ---------- 实时数据 ----------
  const Realtime = {
    setup() {
      const list = ref([]);
      const sites = ref([]);
      const siteId = ref(null);
      const load = async () => { try { list.value = await api('/data/latest?' + qs({ siteId: siteId.value })); } catch (e) { fail(e); } };
      onMounted(async () => { sites.value = await api('/sites').catch(() => []); });
      useTimer(load, 5000);
      watch(siteId, load);
      const history = (d, v) => { location.hash = '/history?device=' + d.deviceId + '&factor=' + v.code; };
      return { list, sites, siteId, ago, history };
    },
    template: `<div>
      <div class="toolbar"><el-select v-model="siteId" clearable placeholder="全部站点" style="width:240px"><el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" /></el-select><span class="muted">每 5 秒自动刷新，点数值看历史曲线</span></div>
      <div class="rt-grid">
        <div v-for="d in list" :key="d.deviceId" class="panel rt-card">
          <div class="rt-head"><div><b>{{ d.name }}</b><div class="muted">{{ d.siteName }} · {{ d.mn }}</div></div><el-tag :type="d.online?'success':'info'" effect="dark" size="small">{{ d.online ? '在线' : '离线' }}</el-tag></div>
          <div class="rt-values">
            <div v-for="v in d.values" :key="v.code" class="rt-v" :class="{over: v.overLimit, bad: v.flag && v.flag!=='N'}" @click="history(d, v)">
              <div class="n">{{ v.name }}</div>
              <div class="x">{{ v.value ?? '—' }}<small>{{ v.unit }}</small></div>
              <div class="f" v-if="v.flag && v.flag!=='N'">标记 {{ v.flag }}</div>
            </div>
          </div>
          <div class="muted">最后上报：{{ ago(d.lastSeenAt) }}</div>
        </div>
      </div>
      <el-empty v-if="!list.length" description="还没有设备，先到「设备管理」登记设备，或者用「设备模拟器」跑起来" />
    </div>`,
  };

  // ---------- 历史数据 ----------
  const History = {
    components: { LineChart },
    setup() {
      const devices = ref([]);
      const types = ref([]);
      const q = reactive({ deviceId: null, factor: null, type: 'REALTIME', range: null });
      const series = ref(null);
      const table = ref([]);
      const loading = ref(false);
      const device = computed(() => devices.value.find(d => d.id === q.deviceId));
      function readHash() {
        const p = new URLSearchParams(location.hash.split('?')[1] || '');
        if (p.get('device')) q.deviceId = +p.get('device');
        if (p.get('factor')) q.factor = p.get('factor');
      }
      onMounted(async () => {
        try {
          devices.value = await api('/devices');
          types.value = await api('/data/types');
        } catch (e) { fail(e); }
        readHash();
        if (!q.deviceId && devices.value.length) q.deviceId = devices.value[0].id;
        if (device.value && !device.value.factorCodes.includes(q.factor)) q.factor = device.value.factorCodes[0];
        const now = nowCN(); q.range = [new Date(now - 6 * 3600 * 1000), now];
        load();
      });
      watch(() => q.deviceId, () => { if (device.value && !device.value.factorCodes.includes(q.factor)) q.factor = device.value.factorCodes[0]; });
      async function load() {
        if (!q.deviceId || !q.factor) return;
        loading.value = true;
        const from = q.range ? qtime(q.range[0]) : '', to = q.range ? qtime(q.range[1]) : '';
        try {
          series.value = await api('/data/history?' + qs({ deviceId: q.deviceId, factor: q.factor, type: q.type, from, to }));
          table.value = await api('/data/table?' + qs({ deviceId: q.deviceId, type: q.type, from, to }));
        } catch (e) { fail(e); } finally { loading.value = false; }
      }
      const shortcuts = [
        { text: '最近 1 小时', value: () => [new Date(nowCN() - 3600e3), nowCN()] },
        { text: '最近 6 小时', value: () => [new Date(nowCN() - 6 * 3600e3), nowCN()] },
        { text: '最近 24 小时', value: () => [new Date(nowCN() - 24 * 3600e3), nowCN()] },
        { text: '最近 7 天', value: () => [new Date(nowCN() - 7 * 86400e3), nowCN()] },
      ];
      return { devices, types, q, series, table, loading, device, load, shortcuts, fmt };
    },
    template: `<div>
      <div class="panel">
        <div class="toolbar">
          <el-select v-model="q.deviceId" placeholder="设备" style="width:220px" filterable><el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" /></el-select>
          <el-select v-model="q.factor" placeholder="因子" style="width:160px"><el-option v-for="(c,i) in (device ? device.factorCodes : [])" :key="c" :label="device.factorNames[i]" :value="c" /></el-select>
          <el-select v-model="q.type" style="width:120px"><el-option v-for="t in types" :key="t.value" :label="t.label" :value="t.value" /></el-select>
          <el-date-picker v-model="q.range" type="datetimerange" :shortcuts="shortcuts" start-placeholder="开始" end-placeholder="结束" style="width:360px" />
          <el-button type="primary" @click="load" :loading="loading">查询</el-button>
        </div>
        <div v-if="series" class="stats">
          <span>{{ series.factorName }}（{{ series.unit }}）</span><span>数据 <b>{{ series.count }}</b> 个</span><span>平均 <b>{{ series.avg ?? '—' }}</b></span><span>最小 <b>{{ series.min ?? '—' }}</b></span><span>最大 <b>{{ series.max ?? '—' }}</b></span>
          <span v-if="series.upper!=null || series.lower!=null">超限 <b :class="{red: series.overCount}">{{ series.overCount }}</b> 次</span>
        </div>
        <line-chart v-if="series" :points="series.points" :upper="series.upper" :lower="series.lower" :unit="series.unit" />
      </div>
      <div class="panel" v-if="device && table.length"><div class="panel-title">数据表<small>最多显示 500 行</small></div>
        <el-table :data="table" size="small" max-height="420">
          <el-table-column label="时间" width="170"><template #default="{row}">{{ fmt(row.time, true) }}</template></el-table-column>
          <el-table-column v-for="(c,i) in device.factorCodes" :key="c" :label="device.factorNames[i]" min-width="110">
            <template #default="{row}">{{ row[c] ?? '' }}<el-tag v-if="row[c + '_flag']" size="small" type="warning" style="margin-left:4px">{{ row[c + '_flag'] }}</el-tag></template>
          </el-table-column>
        </el-table>
      </div>
    </div>`,
  };

  // ---------- 预警列表 ----------
  const Alarms = {
    setup() {
      const q = reactive({ open: true, status: '', level: '', siteId: null, keyword: '', page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const sites = ref([]);
      const users = ref([]);
      const dlg = reactive({ show: false, alarm: null, handlerId: null, reviewerId: null });
      const load = async () => {
        try { data.value = await api('/alarms?' + qs({ ...q, open: q.open && !q.status ? true : null })); } catch (e) { fail(e); }
      };
      onMounted(async () => { sites.value = await api('/sites').catch(() => []); load(); });
      onOrderChanged(load);
      const search = () => { q.page = 0; load(); };
      async function openDispatch(a) {
        Object.assign(dlg, { show: true, alarm: a, handlerId: null, reviewerId: null });
        if (!users.value.length) users.value = await api('/users/options').catch(() => []);
      }
      async function dispatch() {
        try { await api('/alarms/' + dlg.alarm.id + '/dispatch', { method: 'POST', body: { handlerId: dlg.handlerId, reviewerId: dlg.reviewerId } }); ok('已派单'); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      const handlers = computed(() => users.value.filter(u => u.permissions.includes('ORDER_HANDLE')));
      const reviewers = computed(() => users.value.filter(u => u.permissions.includes('ORDER_REVIEW')));
      return { q, data, sites, dlg, load, search, openDispatch, dispatch, handlers, reviewers, state, has, fmt, short, STATUS_TYPE, LEVEL_CLASS };
    },
    template: `<div class="panel">
      <div class="toolbar">
        <el-checkbox v-model="q.open" @change="search">只看未关闭</el-checkbox>
        <el-select v-model="q.status" clearable placeholder="状态" style="width:110px" @change="search"><el-option label="待派单" value="PENDING" /><el-option label="处理中" value="PROCESSING" /><el-option label="待审核" value="REVIEWING" /><el-option label="已关闭" value="CLOSED" /></el-select>
        <el-select v-model="q.level" clearable placeholder="级别" style="width:100px" @change="search"><el-option label="严重" :value="3" /><el-option label="较重" :value="2" /><el-option label="一般" :value="1" /></el-select>
        <el-select v-model="q.siteId" clearable placeholder="站点" style="width:200px" @change="search"><el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" /></el-select>
        <el-input v-model="q.keyword" placeholder="设备、MN、内容" style="width:180px" clearable @change="search" />
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table :data="data.items" size="small">
        <el-table-column prop="id" label="#" width="60" />
        <el-table-column label="级别" width="70"><template #default="{row}"><span :class="'lv ' + LEVEL_CLASS[row.level]">{{ row.levelText }}</span></template></el-table-column>
        <el-table-column prop="typeLabel" label="类型" width="100" />
        <el-table-column prop="siteName" label="站点" width="180" show-overflow-tooltip />
        <el-table-column prop="content" label="内容" min-width="260" show-overflow-tooltip />
        <el-table-column label="次数" width="60" prop="hitCount" />
        <el-table-column label="首次 / 最近" width="120"><template #default="{row}">{{ short(row.firstAt) }}<br><span class="muted">{{ short(row.lastAt) }}</span></template></el-table-column>
        <el-table-column label="状态" width="110"><template #default="{row}"><el-tag size="small" :type="STATUS_TYPE[row.status]">{{ row.statusLabel }}</el-tag><div v-if="row.recovered" class="muted">数据已恢复</div></template></el-table-column>
        <el-table-column label="操作" width="90" fixed="right"><template #default="{row}">
          <el-button v-if="row.orderId" link type="primary" @click="state.orderId=row.orderId">工单</el-button>
          <el-button v-else-if="has('ORDER_DISPATCH') && row.status==='PENDING'" link type="danger" @click="openDispatch(row)">派单</el-button>
        </template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page+1" @current-change="p => { q.page = p-1; load(); }" /></div>
      <el-dialog v-model="dlg.show" title="手动派单" width="460px">
        <div v-if="dlg.alarm" class="muted" style="margin-bottom:12px">{{ dlg.alarm.siteName }}：{{ dlg.alarm.content }}</div>
        <el-form label-width="70px">
          <el-form-item label="处理人"><el-select v-model="dlg.handlerId" style="width:100%"><el-option v-for="u in handlers" :key="u.id" :label="u.name + '（' + u.roleNames.join('、') + '）'" :value="u.id" /></el-select></el-form-item>
          <el-form-item label="审核人"><el-select v-model="dlg.reviewerId" clearable style="width:100%" placeholder="不指定则有审核权限的人都能审"><el-option v-for="u in reviewers" :key="u.id" :label="u.name" :value="u.id" /></el-select></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" :disabled="!dlg.handlerId" @click="dispatch">派单</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ---------- 工单 ----------
  const Orders = {
    setup() {
      const q = reactive({ box: has('ORDER_HANDLE') ? 'todo' : has('ORDER_REVIEW') ? 'review' : 'all', status: '', keyword: '', page: 0, size: 20 });
      const data = ref({ items: [], total: 0 });
      const load = async () => { try { data.value = await api('/orders?' + qs(q)); } catch (e) { fail(e); } };
      onMounted(load);
      onOrderChanged(load);
      watch(() => q.box, () => { q.page = 0; load(); });
      return { q, data, load, state, has, fmt, short, STATUS_TYPE };
    },
    template: `<div class="panel">
      <div class="toolbar">
        <el-radio-group v-model="q.box"><el-radio-button v-if="has('ORDER_HANDLE')" value="todo">我的待办</el-radio-button><el-radio-button v-if="has('ORDER_REVIEW')" value="review">待我审核</el-radio-button><el-radio-button value="mine">和我有关</el-radio-button><el-radio-button value="all">全部</el-radio-button></el-radio-group>
        <el-select v-if="q.box==='all' || q.box==='mine'" v-model="q.status" clearable placeholder="状态" style="width:110px" @change="load"><el-option label="处理中" value="PROCESSING" /><el-option label="待审核" value="SUBMITTED" /><el-option label="已完成" value="APPROVED" /></el-select>
        <el-input v-model="q.keyword" placeholder="工单号、标题、处理人" style="width:200px" clearable @change="load" />
        <el-button @click="load">刷新</el-button>
      </div>
      <el-table :data="data.items" size="small" @row-click="r => state.orderId = r.id" row-class-name="clickable">
        <el-table-column prop="code" label="工单号" width="150" />
        <el-table-column prop="title" label="标题" min-width="220" show-overflow-tooltip />
        <el-table-column prop="handlerName" label="处理人" width="90" />
        <el-table-column label="审核人" width="90"><template #default="{row}">{{ row.reviewerName || '—' }}</template></el-table-column>
        <el-table-column label="状态" width="120"><template #default="{row}"><el-tag size="small" :type="STATUS_TYPE[row.status]">{{ row.statusLabel }}</el-tag> <el-tag v-if="row.rejectCount" size="small" type="info">驳回{{ row.rejectCount }}</el-tag></template></el-table-column>
        <el-table-column label="创建" width="110"><template #default="{row}">{{ short(row.createdAt) }}</template></el-table-column>
      </el-table>
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="q.size" :current-page="q.page+1" @current-change="p => { q.page = p-1; load(); }" /></div>
    </div>`,
  };

  // ---------- 站内消息 ----------
  const Inbox = {
    setup() {
      const data = ref({ items: [], total: 0 });
      const page = ref(0);
      const load = async () => {
        try { data.value = await api('/inbox?page=' + page.value); state.unread = (await api('/inbox/unread')).count; } catch (e) { fail(e); }
      };
      onMounted(load);
      async function open(m) {
        if (!m.readFlag) { await api('/inbox/' + m.id + '/read', { method: 'POST' }).catch(() => { }); m.readFlag = true; state.unread = Math.max(0, state.unread - 1); }
        if (m.link && m.link.startsWith('order:')) state.orderId = +m.link.slice(6);
      }
      async function readAll() { await api('/inbox/read-all', { method: 'POST' }); load(); }
      return { data, page, load, open, readAll, fmt };
    },
    template: `<div class="panel">
      <div class="toolbar"><el-button @click="readAll">全部标为已读</el-button><el-button @click="load">刷新</el-button></div>
      <div v-for="m in data.items" :key="m.id" class="inbox-item" :class="{unread: !m.readFlag}" @click="open(m)">
        <div class="t"><i v-if="!m.readFlag"></i>{{ m.title }}<span class="muted">{{ fmt(m.createdAt) }}</span></div>
        <div class="c">{{ m.content }}</div>
      </div>
      <el-empty v-if="!data.items.length" description="没有消息" />
      <div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="20" :current-page="page+1" @current-change="p => { page = p-1; load(); }" /></div>
    </div>`,
  };

  Object.assign(window.EM, { useTimer, onOrderChanged, Pages: { Dashboard, Realtime, History, Alarms, Orders, Inbox } });
})();

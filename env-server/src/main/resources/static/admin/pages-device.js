/* 环境监测平台 · 后台页面：站点、设备、因子、模拟器 */
(function () {
  const { reactive, ref, onMounted, onUnmounted } = Vue;
  const { api, fail, ok, fmt, short, has, qs, useTimer, ElMessageBox } = EM;

  // 系统编码 ST，取自 HJ212-2017 6.6.1 表 5、HJ212-2025 6.7.1.2 表 7《系统编码表》
  const ST_OPTIONS = [
    { v: '32', l: '32 地表水体环境污染源' }, { v: '31', l: '31 大气环境污染源' }, { v: '21', l: '21 地表水质量监测' },
    { v: '22', l: '22 空气质量监测' }, { v: '23', l: '23 声环境质量监测' }, { v: '24', l: '24 地下水质量监测' },
    { v: '27', l: '27 挥发性有机物监测' }];

  // ---------- 站点 ----------
  const Sites = {
    setup() {
      const list = ref([]), users = ref([]);
      const dlg = reactive({ show: false, id: null, f: {} });
      const load = async () => { try { list.value = await api('/sites'); } catch (e) { fail(e); } };
      onMounted(async () => { users.value = await api('/users/options').catch(() => []); load(); });
      const userName = id => (users.value.find(u => u.id === id) || {}).name || '—';
      function edit(s) { dlg.id = s ? s.id : null; dlg.f = s ? { ...s } : { name: '', address: '', handlerId: null, reviewerId: null, remark: '' }; dlg.show = true; }
      async function save() {
        try { await api('/sites' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function del(s) {
        await ElMessageBox.confirm('删除站点「' + s.name + '」？', '确认', { type: 'warning' });
        try { await api('/sites/' + s.id, { method: 'DELETE' }); ok('已删除'); load(); } catch (e) { fail(e); }
      }
      return { list, users, dlg, edit, save, del, userName, has };
    },
    template: `<div class="panel">
      <div class="toolbar" v-if="has('DEVICE_MANAGE')"><el-button type="primary" @click="edit()">新增站点</el-button><span class="muted">站点上的预警自动派给站点负责人处理、由站点审核人审核；没配负责人的要手动派单</span></div>
      <el-table :data="list" size="small">
        <el-table-column prop="name" label="站点" min-width="200" />
        <el-table-column prop="address" label="地址" min-width="160" />
        <el-table-column label="设备" width="90"><template #default="{row}">{{ row.onlineCount }} / {{ row.deviceCount }}</template></el-table-column>
        <el-table-column label="负责人" width="90"><template #default="{row}">{{ userName(row.handlerId) }}</template></el-table-column>
        <el-table-column label="审核人" width="90"><template #default="{row}">{{ userName(row.reviewerId) }}</template></el-table-column>
        <el-table-column label="操作" width="110" v-if="has('DEVICE_MANAGE')"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link type="danger" @click="del(row)">删除</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑站点' : '新增站点'" width="500px">
        <el-form label-width="80px">
          <el-form-item label="名称"><el-input v-model="dlg.f.name" /></el-form-item>
          <el-form-item label="地址"><el-input v-model="dlg.f.address" /></el-form-item>
          <el-form-item label="负责人"><el-select v-model="dlg.f.handlerId" clearable style="width:100%"><el-option v-for="u in users.filter(x => x.permissions.includes('ORDER_HANDLE'))" :key="u.id" :label="u.name" :value="u.id" /></el-select></el-form-item>
          <el-form-item label="审核人"><el-select v-model="dlg.f.reviewerId" clearable style="width:100%"><el-option v-for="u in users.filter(x => x.permissions.includes('ORDER_REVIEW'))" :key="u.id" :label="u.name" :value="u.id" /></el-select></el-form-item>
          <el-form-item label="备注"><el-input v-model="dlg.f.remark" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ---------- 设备 ----------
  const Devices = {
    setup() {
      const list = ref([]), sites = ref([]), factors = ref([]), unknown = ref([]);
      const q = reactive({ siteId: null, keyword: '', online: null });
      const dlg = reactive({ show: false, id: null, f: {} });
      const detail = reactive({ show: false, d: null, raws: { items: [], total: 0 }, rawPage: 0, rawCn: '' });
      const load = async () => {
        try {
          list.value = await api('/devices?' + qs(q));
          if (has('DEVICE_MANAGE')) unknown.value = await api('/devices/unknown');
        } catch (e) { fail(e); }
      };
      onMounted(async () => {
        sites.value = await api('/sites').catch(() => []);
        factors.value = await api('/factors').catch(() => []);
        load();
      });
      let timer = null;
      onUnmounted(() => clearInterval(timer));
      function edit(d, mn) {
        dlg.id = d ? d.id : null;
        dlg.f = d ? { mn: d.mn, name: d.name, siteId: d.siteId, password: '', st: d.st, enabled: d.enabled, factorCodes: [...d.factorCodes], remark: d.remark,
            protocolVersion: d.protocolVersion || '', requireEncryption: d.requireEncryption, hasSecretKey: d.hasSecretKey, secretKeyCreateTime: d.secretKeyCreateTime }
          : { mn: mn || '', name: '', siteId: null, password: '123456', st: '32', enabled: true, factorCodes: ['w01018', 'w21003', 'w01001'], remark: '',
            protocolVersion: '', requireEncryption: false, hasSecretKey: false };
        keyResult.value = null;
        dlg.show = true;
      }
      // HJ212-2025：登记通信密钥，随机生成的密钥只显示这一次
      const keyResult = ref(null);
      async function registerKey() {
        try {
          keyResult.value = await api('/devices/' + dlg.id + '/secret-key', { method: 'POST', body: {} });
          dlg.f.hasSecretKey = true; dlg.f.secretKeyCreateTime = keyResult.value.createTime; load();
        } catch (e) { fail(e); }
      }
      async function save() {
        const body = { ...dlg.f };
        if (dlg.id && !body.password) delete body.password;
        try { await api('/devices' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function toggle(d) {
        try { await api('/devices/' + d.id, { method: 'PUT', body: { mn: d.mn, name: d.name, siteId: d.siteId, st: d.st, enabled: !d.enabled, factorCodes: d.factorCodes, remark: d.remark,
          requireEncryption: d.requireEncryption } }); ok(d.enabled ? '已停用' : '已启用'); load(); } catch (e) { fail(e); }
      }
      async function del(d) {
        await ElMessageBox.confirm('删除设备「' + d.name + '」？历史数据保留。', '确认', { type: 'warning' });
        try { await api('/devices/' + d.id, { method: 'DELETE' }); ok('已删除'); load(); } catch (e) { fail(e); }
      }
      async function open(d) {
        Object.assign(detail, { show: true, d, rawPage: 0, rawCn: '' });
        await refreshRaw();
        clearInterval(timer);
        timer = setInterval(() => { if (detail.show) refreshRaw(); else clearInterval(timer); }, 3000);
      }
      async function refreshRaw() {
        if (!detail.d) return;
        try { detail.raws = await api('/devices/' + detail.d.id + '/raw?' + qs({ page: detail.rawPage, size: 30, cn: detail.rawCn })); } catch (e) { /* 刷新失败不打扰 */ }
      }
      return { list, sites, factors, unknown, q, dlg, detail, load, edit, save, toggle, del, open, refreshRaw, has, fmt, short, ST_OPTIONS, keyResult, registerKey };
    },
    template: `<div>
      <el-alert v-if="unknown.length" type="warning" :closable="false" class="panel" style="padding:12px 16px">
        <template #title>有 {{ unknown.length }} 个 MN 发来了报文，但还没登记：
          <span v-for="u in unknown" :key="u.mn" style="margin-right:12px"><b>{{ u.mn }}</b>（{{ u.count }} 条，{{ short(u.lastAt) }}）<el-button link type="primary" @click="edit(null, u.mn)">登记</el-button></span>
        </template>
      </el-alert>
      <div class="panel">
        <div class="toolbar">
          <el-select v-model="q.siteId" clearable placeholder="全部站点" style="width:200px" @change="load"><el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" /></el-select>
          <el-select v-model="q.online" clearable placeholder="在线状态" style="width:110px" @change="load"><el-option label="在线" :value="true" /><el-option label="离线" :value="false" /></el-select>
          <el-input v-model="q.keyword" placeholder="名称或 MN" clearable style="width:180px" @change="load" />
          <el-button @click="load">刷新</el-button><span class="grow"></span>
          <el-button v-if="has('DEVICE_MANAGE')" type="primary" @click="edit()">登记设备</el-button>
        </div>
        <el-table :data="list" size="small">
          <el-table-column label="状态" width="70"><template #default="{row}"><el-tag size="small" :type="!row.enabled ? 'info' : row.online ? 'success' : 'danger'" effect="dark">{{ !row.enabled ? '停用' : row.online ? '在线' : '离线' }}</el-tag></template></el-table-column>
          <el-table-column prop="name" label="设备" min-width="160" />
          <el-table-column prop="mn" label="MN" width="200" />
          <el-table-column label="站点" min-width="160"><template #default="{row}">{{ row.siteName || '—' }}</template></el-table-column>
          <el-table-column label="监测因子" min-width="200"><template #default="{row}"><span class="muted">{{ row.factorNames.join('、') }}</span></template></el-table-column>
          <el-table-column label="协议" width="110"><template #default="{row}"><span v-if="row.protocolVersion">{{ row.protocolVersion }}</span><span v-else class="muted">—</span>
            <el-tag v-if="row.protocolVersion === '2025'" size="small" :type="row.hasSecretKey ? 'success' : 'info'" style="margin-left:4px">{{ row.hasSecretKey ? '加密' : '明文' }}</el-tag></template></el-table-column>
          <el-table-column label="最后上报" width="120"><template #default="{row}">{{ short(row.lastSeenAt) || '—' }}<div class="muted">{{ row.lastIp }}</div></template></el-table-column>
          <el-table-column label="操作" width="190" fixed="right"><template #default="{row}">
            <el-button link type="primary" @click="open(row)">报文</el-button>
            <template v-if="has('DEVICE_MANAGE')"><el-button link type="primary" @click="edit(row)">编辑</el-button><el-button link @click="toggle(row)">{{ row.enabled ? '停用' : '启用' }}</el-button><el-button link type="danger" @click="del(row)">删除</el-button></template>
          </template></el-table-column>
        </el-table>
      </div>

      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑设备' : '登记设备'" width="560px">
        <el-form label-width="90px">
          <el-form-item label="MN 号"><el-input v-model="dlg.f.mn" placeholder="2017 版是设备唯一标识，2025 版是入网编码，一般 24 位" /></el-form-item>
          <el-form-item label="协议版本"><el-select v-model="dlg.f.protocolVersion" style="width:100%"><el-option label="自动识别（按收到的报文）" value="" /><el-option label="HJ212-2017" value="2017" /><el-option label="HJ212-2025" value="2025" /><el-option label="HJ/T 212-2005" value="2005" /></el-select></el-form-item>
          <el-form-item label="通信密钥" v-if="dlg.f.protocolVersion === '2025'">
            <div>
              <span v-if="dlg.f.hasSecretKey" class="muted">已登记（{{ dlg.f.secretKeyCreateTime }}）</span><span v-else class="muted">未登记，按明文接收</span>
              <el-button v-if="dlg.id" link type="primary" @click="registerKey">{{ dlg.f.hasSecretKey ? '重新生成' : '随机生成' }}</el-button>
              <span v-else class="muted">（保存后再登记）</span>
              <div v-if="keyResult">请把密钥写进数采仪，关闭后不再显示：<code style="user-select:all">{{ keyResult.key }}</code></div>
              <div><el-switch v-model="dlg.f.requireEncryption" /><span class="muted" style="margin-left:8px">要求加密传输：数据命令不加密的不收（标准 6.4）</span></div>
            </div>
          </el-form-item>
          <el-form-item label="名称"><el-input v-model="dlg.f.name" /></el-form-item>
          <el-form-item label="站点"><el-select v-model="dlg.f.siteId" clearable style="width:100%"><el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
          <el-form-item label="系统编码"><el-select v-model="dlg.f.st" style="width:100%"><el-option v-for="o in ST_OPTIONS" :key="o.v" :label="o.l" :value="o.v" /></el-select></el-form-item>
          <el-form-item label="访问密码"><el-input v-model="dlg.f.password" :placeholder="dlg.id ? '不修改请留空' : '报文里的 PW，为空不校验'" /></el-form-item>
          <el-form-item label="监测因子"><el-select v-model="dlg.f.factorCodes" multiple filterable style="width:100%"><el-option v-for="f in factors" :key="f.code" :label="f.name + ' ' + f.code" :value="f.code" /></el-select></el-form-item>
          <el-form-item label="启用"><el-switch v-model="dlg.f.enabled" /><span class="muted" style="margin-left:8px">停用后断开连接，报文不入库、不预警</span></el-form-item>
          <el-form-item label="备注"><el-input v-model="dlg.f.remark" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>

      <el-drawer v-model="detail.show" size="760px" :title="detail.d ? detail.d.name + '（' + detail.d.mn + '）原始报文' : ''">
        <div v-if="detail.d">
          <div class="toolbar"><el-input v-model="detail.rawCn" placeholder="按 CN 筛选，如 2011" style="width:180px" clearable @change="detail.rawPage=0;refreshRaw()" /><span class="muted">每 3 秒刷新</span></div>
          <div class="raws">
            <div v-for="r in detail.raws.items" :key="r.id" class="raw" :class="[r.direction, {bad: !r.ok}]">
              <span class="dir">{{ r.direction === 'IN' ? '↓ 收' : '↑ 发' }}</span><span class="tm">{{ fmt(r.createdAt, true) }}</span><el-tag v-if="r.encrypted" size="small" type="success" style="margin-right:6px">SM4 加密 · 已解密显示</el-tag><span v-if="!r.ok" class="err">{{ r.error }}</span>
              <code>{{ r.content }}</code>
            </div>
          </div>
          <div class="pager"><el-pagination small layout="prev, pager, next" :total="detail.raws.total" :page-size="30" :current-page="detail.rawPage+1" @current-change="p => { detail.rawPage = p-1; refreshRaw(); }" /></div>
        </div>
      </el-drawer>
    </div>`,
  };

  // ---------- 因子字典 ----------
  const Factors = {
    setup() {
      const list = ref([]);
      const dlg = reactive({ show: false, f: {} });
      const load = async () => { list.value = await api('/factors').catch(e => { fail(e); return []; }); };
      onMounted(load);
      function edit(f) { dlg.f = f ? { ...f } : { code: '', name: '', unit: '', category: '水', decimals: 2 }; dlg.show = true; }
      async function save() { try { await api('/factors', { method: 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); } }
      return { list, dlg, edit, save, has };
    },
    template: `<div class="panel">
      <div class="toolbar"><el-button v-if="has('DEVICE_MANAGE')" type="primary" @click="edit()">新增因子</el-button><span class="muted">初始数据是 HJ212 附录 B 里的常用编码，名称和单位以标准原文为准；地方平台约定的编码可以在这里补充</span></div>
      <el-table :data="list" size="small">
        <el-table-column prop="code" label="编码" width="120" /><el-table-column prop="name" label="名称" /><el-table-column prop="unit" label="单位" width="120" />
        <el-table-column prop="category" label="类别" width="80" /><el-table-column prop="decimals" label="小数位" width="80" />
        <el-table-column label="操作" width="80" v-if="has('DEVICE_MANAGE')"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" title="监测因子" width="420px">
        <el-form label-width="70px">
          <el-form-item label="编码"><el-input v-model="dlg.f.code" placeholder="如 w01018" /></el-form-item>
          <el-form-item label="名称"><el-input v-model="dlg.f.name" /></el-form-item>
          <el-form-item label="单位"><el-input v-model="dlg.f.unit" /></el-form-item>
          <el-form-item label="类别"><el-radio-group v-model="dlg.f.category"><el-radio value="水">水</el-radio><el-radio value="气">气</el-radio><el-radio value="其他">其他</el-radio></el-radio-group></el-form-item>
          <el-form-item label="小数位"><el-input-number v-model="dlg.f.decimals" :min="0" :max="6" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ---------- 设备模拟器 ----------
  const Simulator = {
    setup() {
      const rows = ref([]), scenarios = ref([]);
      const expanded = ref(null);
      const load = async () => { try { rows.value = await api('/simulator'); } catch (e) { fail(e); } };
      onMounted(async () => { scenarios.value = await api('/simulator/scenarios').catch(() => []); });
      useTimer(load, 2000);
      const start = async (ids, scenario) => { try { await api('/simulator/start', { method: 'POST', body: { deviceIds: ids, scenario: scenario || 'NORMAL' } }); ok('已启动'); load(); } catch (e) { fail(e); } };
      const stop = async ids => { try { await api('/simulator/stop', { method: 'POST', body: { deviceIds: ids } }); ok('已停止'); load(); } catch (e) { fail(e); } };
      const scenario = async (r, s, f) => { try { await api('/simulator/scenario', { method: 'POST', body: { mn: r.mn, scenario: s, factor: f } }); ok('已切换'); load(); } catch (e) { fail(e); } };
      return { rows, scenarios, expanded, start, stop, scenario };
    },
    template: `<div>
      <div class="panel">
        <div class="toolbar"><el-button type="primary" @click="start([])">全部启动</el-button><el-button @click="stop([])">全部停止</el-button>
          <span class="muted">模拟器按设备的 MN、密码、因子生成数采仪，通过真实 TCP 连接按 HJ212 上报（设备设成 2025 版就按 2025 版加密上报），平台这边和接真设备完全一样。切到"超标"就能看到预警 → 派单 → 钉钉通知。</span></div>
        <el-table :data="rows" size="small" row-key="mn" :expand-row-keys="expanded ? [expanded] : []">
          <el-table-column type="expand"><template #default="{row}"><div v-if="row.sim" class="sim-logs"><div v-for="(l,i) in row.sim.logs" :key="i">{{ l }}</div></div><div v-else class="muted">没有运行</div></template></el-table-column>
          <el-table-column label="设备" min-width="170"><template #default="{row}">{{ row.name }}<div class="muted">{{ row.mn }}</div></template></el-table-column>
          <el-table-column label="模拟状态" width="110"><template #default="{row}"><el-tag v-if="row.sim" size="small" :type="row.sim.connected ? 'success' : 'warning'" effect="dark">{{ row.sim.connected ? '已连接' : '连接中' }}</el-tag><el-tag v-else size="small" type="info">未运行</el-tag></template></el-table-column>
          <el-table-column label="场景" width="250"><template #default="{row}">
            <template v-if="row.sim">
              <el-select :model-value="row.sim.scenario" size="small" style="width:110px" @change="v => scenario(row, v, row.sim.scenarioFactor)"><el-option v-for="s in scenarios" :key="s.value" :label="s.label" :value="s.value" /></el-select>
              <el-select :model-value="row.sim.scenarioFactor || row.factorCodes[0]" size="small" style="width:110px;margin-left:4px" @change="f => scenario(row, row.sim.scenario, f)"><el-option v-for="c in row.factorCodes" :key="c" :label="c" :value="c" /></el-select>
            </template></template></el-table-column>
          <el-table-column label="最新值" min-width="220"><template #default="{row}"><span v-if="row.sim" class="muted">{{ Object.entries(row.sim.lastValues).map(([k,v]) => k + '=' + v).join('  ') }}</span></template></el-table-column>
          <el-table-column label="统计" width="140"><template #default="{row}"><span v-if="row.sim" class="muted">发 {{ row.sim.sent }} · 应答 {{ row.sim.acked }}<br>间隔 {{ row.sim.rtdInterval }}s</span></template></el-table-column>
          <el-table-column label="操作" width="140" fixed="right"><template #default="{row}">
            <el-button v-if="!row.sim" link type="primary" @click="start([row.deviceId])">启动</el-button>
            <template v-else><el-button link @click="expanded = expanded === row.mn ? null : row.mn">日志</el-button><el-button link type="danger" @click="stop([row.deviceId])">停止</el-button></template>
          </template></el-table-column>
        </el-table>
      </div>
      <div class="panel muted">命令行也能跑：<code>java -jar env-simulator/target/env-simulator-0.1.0-SNAPSHOT-exec.jar --port 9212 --count 5 --interval 10</code>，MN 不在平台里的会出现在「设备管理」的未登记提示里。</div>
    </div>`,
  };

  Object.assign(window.EM.Pages, { Sites, Devices, Factors, Simulator });
})();

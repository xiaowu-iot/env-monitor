/* 环境监测平台 · 后台页面：预警规则、钉钉通知、用户 */
(function () {
  const { reactive, ref, onMounted } = Vue;
  const { api, fail, ok, fmt, has, md, ElMessageBox } = EM;

  const LEVELS = [{ v: 1, l: '一般' }, { v: 2, l: '较重' }, { v: 3, l: '严重' }];

  // ---------- 预警规则 ----------
  const AlarmRules = {
    setup() {
      const list = ref([]), factors = ref([]), sites = ref([]), devices = ref([]);
      const dlg = reactive({ show: false, id: null, f: {} });
      const load = async () => { list.value = await api('/alarm-rules').catch(e => { fail(e); return []; }); };
      onMounted(async () => {
        factors.value = await api('/factors').catch(() => []);
        sites.value = await api('/sites').catch(() => []);
        devices.value = await api('/devices').catch(() => []);
        load();
      });
      function edit(r) {
        dlg.id = r ? r.id : null;
        dlg.f = r ? { ...r } : { name: '', factorCode: 'w01018', dataType: 'REALTIME', upperLimit: null, lowerLimit: null, level: 2, siteId: null, deviceId: null, enabled: true, remark: '' };
        dlg.show = true;
      }
      async function save() {
        try { await api('/alarm-rules' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); }
      }
      async function del(r) {
        await ElMessageBox.confirm('确定删除「' + r.name + '」？', '确认', { type: 'warning' });
        try { await api('/alarm-rules/' + r.id, { method: 'DELETE' }); ok('已删除'); load(); } catch (e) { fail(e); }
      }
      async function toggle(r) { try { await api('/alarm-rules/' + r.id, { method: 'PUT', body: { ...r, enabled: !r.enabled } }); load(); } catch (e) { fail(e); } }
      const factorName = c => { const f = factors.value.find(x => x.code === c); return f ? f.name + (f.unit ? '（' + f.unit + '）' : '') : c; };
      const scope = r => r.deviceId ? (devices.value.find(d => d.id === r.deviceId) || {}).name : r.siteId ? (sites.value.find(s => s.id === r.siteId) || {}).name : '全部';
      return { list, factors, sites, devices, dlg, edit, save, del, toggle, factorName, scope, has, LEVELS };
    },
    template: `<div class="panel">
      <div class="toolbar" v-if="has('RULE_MANAGE')"><el-button type="primary" @click="edit()">新增规则</el-button><span class="muted">超标判断会跳过停运、维护、故障、校准、通讯异常标记的数据；同一规则、设备、因子没关闭前只保留一条预警</span></div>
      <el-table :data="list" size="small">
        <el-table-column label="启用" width="60"><template #default="{row}"><el-switch :model-value="row.rule.enabled" size="small" :disabled="!has('RULE_MANAGE')" @change="toggle(row.rule)" /></template></el-table-column>
        <el-table-column label="名称" min-width="140"><template #default="{row}">{{ row.rule.name }}</template></el-table-column>
        <el-table-column label="因子" min-width="140"><template #default="{row}">{{ factorName(row.rule.factorCode) }}</template></el-table-column>
        <el-table-column label="限值" width="120" prop="thresholdText" />
        <el-table-column label="级别" width="70"><template #default="{row}">{{ LEVELS[row.rule.level-1].l }}</template></el-table-column>
        <el-table-column label="范围" width="160"><template #default="{row}">{{ scope(row.rule) }}</template></el-table-column>
        <el-table-column label="操作" width="110" v-if="has('RULE_MANAGE')"><template #default="{row}"><el-button link type="primary" @click="edit(row.rule)">编辑</el-button><el-button link type="danger" @click="del(row.rule)">删除</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑超标规则' : '新增超标规则'" width="540px">
        <el-form label-width="90px">
          <el-form-item label="名称"><el-input v-model="dlg.f.name" /></el-form-item>
          <el-form-item label="因子"><el-select v-model="dlg.f.factorCode" filterable style="width:100%"><el-option v-for="f in factors" :key="f.code" :label="f.name + ' ' + f.code + (f.unit ? '（' + f.unit + '）' : '')" :value="f.code" /></el-select></el-form-item>
          <el-form-item label="数据类型"><el-select v-model="dlg.f.dataType"><el-option label="实时数据" value="REALTIME" /><el-option label="分钟数据" value="MINUTE" /><el-option label="小时数据" value="HOUR" /><el-option label="日数据" value="DAY" /></el-select></el-form-item>
          <el-form-item label="上限"><el-input-number v-model="dlg.f.upperLimit" :controls="false" placeholder="不填不判断" /></el-form-item>
          <el-form-item label="下限"><el-input-number v-model="dlg.f.lowerLimit" :controls="false" placeholder="不填不判断" /><span class="muted" style="margin-left:8px">pH 这类有上下限的都填</span></el-form-item>
          <el-form-item label="级别"><el-radio-group v-model="dlg.f.level"><el-radio v-for="l in LEVELS" :key="l.v" :value="l.v">{{ l.l }}</el-radio></el-radio-group></el-form-item>
          <el-form-item label="适用站点"><el-select v-model="dlg.f.siteId" clearable placeholder="全部站点" style="width:100%"><el-option v-for="s in sites" :key="s.id" :label="s.name" :value="s.id" /></el-select></el-form-item>
          <el-form-item label="适用设备"><el-select v-model="dlg.f.deviceId" clearable placeholder="全部设备" style="width:100%"><el-option v-for="d in devices" :key="d.id" :label="d.name" :value="d.id" /></el-select></el-form-item>
          <el-form-item label="备注"><el-input v-model="dlg.f.remark" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  // ---------- 钉钉通知 ----------
  const Notify = {
    setup() {
      const status = ref(null), mock = ref([]);
      const load = async () => {
        try { status.value = await api('/notify/status'); mock.value = await api('/notify/mock-messages'); } catch (e) { fail(e); }
      };
      onMounted(load);
      async function clear() { await api('/notify/mock-messages', { method: 'DELETE' }); load(); }
      return { status, mock, load, clear, fmt, md };
    },
    template: `<div>
      <div class="panel" v-if="status">
        <div class="panel-title">钉钉群机器人</div>
        <div v-if="!status.dingtalkConfigured">没有配置钉钉，只发站内消息。</div>
        <div v-else-if="status.mock">当前发往平台内置的<b>模拟群</b>（没有真实群也能演示），收到的消息在下面。</div>
        <div v-else>已配置真实的钉钉群机器人{{ status.signed ? '（加签）' : '' }}。</div>
        <div class="muted mt">在 <code>application.yml</code> 里配置：<code>env.dingtalk.webhook</code> 填群机器人的 Webhook，<code>env.dingtalk.secret</code> 填加签密钥（SEC 开头）。用户的手机号填了，消息里会 @ 到人。</div>
      </div>
      <div class="panel">
        <div class="panel-title">模拟群收到的消息<span><el-button size="small" @click="load">刷新</el-button><el-button size="small" @click="clear">清空</el-button></span></div>
        <div v-for="(m,i) in mock" :key="i" class="ding">
          <div class="ding-head"><b>{{ m.title }}</b><el-tag size="small" :type="m.signOk ? 'success' : 'danger'">{{ m.signOk ? '加签校验通过' : m.error }}</el-tag><span class="muted">{{ fmt(m.time, true) }}</span></div>
          <div class="md" v-html="md(m.text)"></div>
        </div>
        <el-empty v-if="!mock.length" description="还没有收到消息" />
      </div>
    </div>`,
  };

  // ---------- 用户 ----------
  const Users = {
    setup() {
      const list = ref([]), roles = ref([]);
      const dlg = reactive({ show: false, id: null, f: {} });
      const load = async () => { list.value = await api('/users').catch(e => { fail(e); return []; }); };
      onMounted(async () => { roles.value = await api('/roles').catch(() => []); load(); });
      function edit(u) {
        dlg.id = u ? u.id : null;
        dlg.f = u ? { username: u.username, name: u.name, phone: u.phone || '', password: '', enabled: u.enabled, roleIds: [...u.roleIds] }
          : { username: '', name: '', phone: '', password: '', enabled: true, roleIds: [] };
        dlg.show = true;
      }
      async function save() { try { await api('/users' + (dlg.id ? '/' + dlg.id : ''), { method: dlg.id ? 'PUT' : 'POST', body: dlg.f }); ok(); dlg.show = false; load(); } catch (e) { fail(e); } }
      return { list, roles, dlg, edit, save };
    },
    template: `<div class="panel">
      <div class="toolbar"><el-button type="primary" @click="edit()">新增用户</el-button><span class="muted">内置三个角色：系统管理员、审核员、处理人。手机号用于钉钉 @ 提醒</span></div>
      <el-table :data="list" size="small">
        <el-table-column prop="username" label="用户名" width="120" /><el-table-column prop="name" label="姓名" width="100" /><el-table-column prop="phone" label="手机" width="130" />
        <el-table-column label="角色" min-width="160"><template #default="{row}"><el-tag v-for="r in row.roleNames" :key="r" size="small" style="margin:1px">{{ r }}</el-tag></template></el-table-column>
        <el-table-column label="状态" width="70"><template #default="{row}"><el-tag size="small" :type="row.enabled ? 'success' : 'info'">{{ row.enabled ? '正常' : '停用' }}</el-tag></template></el-table-column>
        <el-table-column label="操作" width="70"><template #default="{row}"><el-button link type="primary" @click="edit(row)">编辑</el-button></template></el-table-column>
      </el-table>
      <el-dialog v-model="dlg.show" :title="dlg.id ? '编辑用户' : '新增用户'" width="480px">
        <el-form label-width="70px">
          <el-form-item label="用户名"><el-input v-model="dlg.f.username" /></el-form-item>
          <el-form-item label="姓名"><el-input v-model="dlg.f.name" /></el-form-item>
          <el-form-item label="手机号"><el-input v-model="dlg.f.phone" /></el-form-item>
          <el-form-item label="密码"><el-input v-model="dlg.f.password" type="password" show-password :placeholder="dlg.id ? '不修改请留空' : '至少 6 位'" /></el-form-item>
          <el-form-item label="角色"><el-select v-model="dlg.f.roleIds" multiple style="width:100%"><el-option v-for="r in roles" :key="r.id" :label="r.name + '（' + r.remark + '）'" :value="r.id" /></el-select></el-form-item>
          <el-form-item label="启用"><el-switch v-model="dlg.f.enabled" /></el-form-item>
        </el-form>
        <template #footer><el-button @click="dlg.show=false">取消</el-button><el-button type="primary" @click="save">保存</el-button></template>
      </el-dialog>
    </div>`,
  };

  Object.assign(window.EM.Pages, { AlarmRules, Notify, Users });
})();

/* 环境监测平台 · 后台页面：登录记录、操作记录 */
(function () {
  const { reactive, ref, onMounted, watch } = Vue;
  const { api, fail, fmt, has, qs } = EM;

  const hashParam = k => new URLSearchParams(location.hash.split('?')[1] || '').get(k);

  // 两个页面共用：筛选条件、分页、用户下拉（系统管理员才有）
  function useAuditList(url, extra) {
    const admin = has('SYSTEM_ADMIN');
    const users = ref([]);
    const q = reactive({ userId: hashParam('userId') ? Number(hashParam('userId')) : null, keyword: '', range: null, ...extra });
    const data = ref({ items: [], total: 0 }), page = ref(0), loading = ref(false);
    async function load() {
      loading.value = true;
      const { range, ...rest } = q;
      try {
        data.value = await api(url + '?' + qs({ ...rest, from: range && range[0], to: range && range[1], page: page.value, size: 20 }));
      } catch (e) { fail(e); } finally { loading.value = false; }
    }
    const search = () => { page.value = 0; load(); };
    const reset = () => { Object.assign(q, { userId: null, keyword: '', range: null }, Object.fromEntries(Object.keys(extra).map(k => [k, null]))); search(); };
    onMounted(async () => {
      if (admin) users.value = await api('/users').catch(() => []);
      load();
    });
    return { admin, users, q, data, page, loading, load, search, reset, fmt };
  }

  const toolbar = (extra) => `<div class="toolbar">
        <el-select v-if="admin" v-model="q.userId" filterable clearable placeholder="全部用户" style="width:160px" @change="search">
          <el-option v-for="u in users" :key="u.id" :label="u.name + '（' + u.username + '）'" :value="u.id" /></el-select>
        ${extra}
        <el-date-picker v-model="q.range" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" style="width:240px" @change="search" />
        <el-button type="primary" @click="search">查询</el-button><el-button @click="reset">重置</el-button>
        <span class="muted" v-if="!admin">只显示你自己的记录</span>
      </div>`;
  const pager = `<div class="pager"><el-pagination layout="total, prev, pager, next" :total="data.total" :page-size="20" :current-page="page+1" @current-change="p => { page = p-1; load(); }" /></div>`;

  // ---------- 登录记录 ----------
  const Logins = {
    setup() {
      const s = useAuditList('/audit/logins', { action: null });
      const TYPE = { LOGIN: 'success', LOGIN_FAILED: 'danger', LOGOUT: 'info' };
      return { ...s, TYPE };
    },
    template: `<div class="panel">
      ${toolbar(`<el-input v-if="admin" v-model="q.keyword" clearable placeholder="用户名 / 姓名" style="width:150px" @keyup.enter="search" @clear="search" />
        <el-select v-model="q.action" clearable placeholder="全部类型" style="width:120px" @change="search">
          <el-option label="登录成功" value="LOGIN" /><el-option label="登录失败" value="LOGIN_FAILED" /><el-option label="退出登录" value="LOGOUT" /></el-select>`)}
      <el-table :data="data.items" size="small" v-loading="loading">
        <el-table-column label="时间" width="160"><template #default="{row}">{{ fmt(row.createdAt, true) }}</template></el-table-column>
        <el-table-column label="用户" width="150"><template #default="{row}">{{ row.name || '—' }}<span class="muted">（{{ row.username }}）</span></template></el-table-column>
        <el-table-column label="类型" width="100"><template #default="{row}"><el-tag size="small" :type="TYPE[row.action]">{{ row.actionLabel }}</el-tag></template></el-table-column>
        <el-table-column label="说明" min-width="130"><template #default="{row}">{{ row.message || '' }}</template></el-table-column>
        <el-table-column prop="client" label="登录端" width="90" />
        <el-table-column prop="ip" label="IP" width="140" />
        <el-table-column prop="browser" label="浏览器" min-width="140" />
      </el-table>
      ${pager}
    </div>`,
  };

  // ---------- 操作记录 ----------
  const Operations = {
    setup() {
      const s = useAuditList('/audit/operations', { module: null, success: null });
      const modules = ref([]);
      const detail = reactive({ show: false, r: null });
      onMounted(async () => { modules.value = await api('/audit/modules').catch(() => []); });
      const open = r => { detail.r = r; detail.show = true; };
      return { ...s, modules, detail, open };
    },
    template: `<div class="panel">
      ${toolbar(`<el-input v-model="q.keyword" clearable :placeholder="admin ? '用户 / 操作名称' : '操作名称'" style="width:150px" @keyup.enter="search" @clear="search" />
        <el-select v-model="q.module" clearable placeholder="全部模块" style="width:120px" @change="search"><el-option v-for="m in modules" :key="m" :label="m" :value="m" /></el-select>
        <el-select v-model="q.success" clearable placeholder="全部结果" style="width:110px" @change="search"><el-option label="成功" :value="true" /><el-option label="失败" :value="false" /></el-select>`)}
      <el-table :data="data.items" size="small" v-loading="loading" @row-click="open" style="cursor:pointer">
        <el-table-column label="时间" width="160"><template #default="{row}">{{ fmt(row.createdAt, true) }}</template></el-table-column>
        <el-table-column label="用户" width="150"><template #default="{row}">{{ row.name || '—' }}<span class="muted">（{{ row.username }}）</span></template></el-table-column>
        <el-table-column prop="module" label="模块" width="100" />
        <el-table-column label="操作" min-width="150"><template #default="{row}">{{ row.action }}<span class="muted" v-if="row.targetId"> #{{ row.targetId }}</span></template></el-table-column>
        <el-table-column label="结果" min-width="160"><template #default="{row}"><el-tag size="small" :type="row.success ? 'success' : 'danger'">{{ row.success ? '成功' : '失败' }}</el-tag><span class="muted" v-if="!row.success && row.message" style="margin-left:6px">{{ row.message }}</span></template></el-table-column>
        <el-table-column prop="client" label="操作端" width="90" />
        <el-table-column prop="ip" label="IP" width="140" />
      </el-table>
      ${pager}
      <el-dialog v-model="detail.show" title="操作详情" width="520px">
        <el-descriptions v-if="detail.r" :column="1" border size="small">
          <el-descriptions-item label="时间">{{ fmt(detail.r.createdAt, true) }}</el-descriptions-item>
          <el-descriptions-item label="用户">{{ detail.r.name }}（{{ detail.r.username }}）</el-descriptions-item>
          <el-descriptions-item label="操作">{{ detail.r.module }} · {{ detail.r.action }}<template v-if="detail.r.targetId">（对象 ID {{ detail.r.targetId }}）</template></el-descriptions-item>
          <el-descriptions-item label="结果">{{ detail.r.success ? '成功' : '失败' }}<template v-if="detail.r.message">：{{ detail.r.message }}</template></el-descriptions-item>
          <el-descriptions-item label="接口">{{ detail.r.method }} {{ detail.r.path }}（HTTP {{ detail.r.status }}，{{ detail.r.durationMs }} ms）</el-descriptions-item>
          <el-descriptions-item label="来源">{{ detail.r.client }} · {{ detail.r.ip }}</el-descriptions-item>
        </el-descriptions>
      </el-dialog>
    </div>`,
  };

  Object.assign(window.EM.Pages, { Logins, Operations });
})();

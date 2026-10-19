/* 环境监测平台 · 后台外框：登录、菜单、顶栏 */
(function () {
  const { createApp, reactive, ref, computed, onMounted } = Vue;
  const { state, api, fail, ok, has, Pages, OrderDrawer, AgentPanel } = EM;

  const LoginView = {
    setup() {
      const form = reactive({ username: '', password: '' });
      const loading = ref(false);
      const opts = ref({ demo: false });
      onMounted(async () => { opts.value = await api('/auth/options').catch(() => ({ demo: false })); });
      async function submit() {
        loading.value = true;
        try { const me = await api('/auth/login', { method: 'POST', body: form }); state.me = me.user; state.unread = me.unread; }
        catch (e) { fail(e); } finally { loading.value = false; }
      }
      function demo(u) { form.username = u; form.password = opts.value.demoPassword; submit(); }
      return { form, loading, opts, submit, demo };
    },
    template: `<div class="login-wrap">
      <div class="login-left">
        <h1>环境监测平台</h1>
        <p>按 HJ212-2017 接收在线监测数据，超标自动预警并派给站点负责人，处理、审核全程留痕，钉钉实时提醒。</p>
        <ul><li>✓ HJ212-2017 数据接收、CRC 校验、自动应答</li><li>✓ 实时数据、历史曲线</li><li>✓ 超标预警，派单、处理、审核留痕</li><li>✓ 钉钉群机器人加签推送、站内消息</li><li>✓ 智能助手：一句话查数据</li></ul>
      </div>
      <div class="login-right">
        <div class="login-card">
          <h3>登录</h3>
          <el-form @submit.prevent="submit" size="large">
            <el-form-item><el-input v-model="form.username" placeholder="用户名" autocomplete="username" /></el-form-item>
            <el-form-item><el-input v-model="form.password" type="password" placeholder="密码" show-password autocomplete="current-password" /></el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">登 录</el-button>
          </el-form>
          <div v-if="opts.demo" class="demo-accounts">
            <div class="muted">演示账号（密码 {{ opts.demoPassword }}），点一下直接登录：</div>
            <el-button v-for="a in opts.accounts" :key="a.username" size="small" @click="demo(a.username)">{{ a.label }}</el-button>
            <div class="muted">管理员 admin 的初始密码在服务器 data/initial-admin.txt</div>
          </div>
        </div>
      </div>
    </div>`,
  };

  const PAGES = [
    { k: 'dashboard', t: '运行概览', ic: '📊', g: '监测', c: Pages.Dashboard },
    { k: 'realtime', t: '实时数据', ic: '📈', g: '监测', c: Pages.Realtime, p: 'DEVICE_VIEW' },
    { k: 'history', t: '历史数据', ic: '🕘', g: '监测', c: Pages.History, p: 'DEVICE_VIEW' },
    { k: 'alarms', t: '预警列表', ic: '🚨', g: '预警处理', c: Pages.Alarms, p: 'ALARM_VIEW' },
    { k: 'orders', t: '工单处理', ic: '📋', g: '预警处理', c: Pages.Orders },
    { k: 'inbox', t: '站内消息', ic: '✉️', g: '预警处理', c: Pages.Inbox },
    { k: 'devices', t: '设备管理', ic: '📟', g: '设备', c: Pages.Devices, p: 'DEVICE_VIEW' },
    { k: 'sites', t: '站点管理', ic: '📍', g: '设备', c: Pages.Sites, p: 'DEVICE_VIEW' },
    { k: 'factors', t: '监测因子', ic: '🧪', g: '设备', c: Pages.Factors, p: 'DEVICE_VIEW' },
    { k: 'simulator', t: '设备模拟器', ic: '🧰', g: '设备', c: Pages.Simulator, p: 'SIMULATOR' },
    { k: 'alarm-rules', t: '预警规则', ic: '⚙️', g: '配置', c: Pages.AlarmRules, p: 'ALARM_VIEW' },
    { k: 'notify', t: '钉钉通知', ic: '🔔', g: '配置', c: Pages.Notify, p: 'SYSTEM_ADMIN' },
    { k: 'users', t: '用户管理', ic: '👤', g: '配置', c: Pages.Users, p: 'SYSTEM_ADMIN' },
  ];

  const App = {
    components: { LoginView, OrderDrawer, AgentPanel },
    setup() {
      const pages = computed(() => PAGES.filter(p => !p.p || has(p.p)));
      const current = computed(() => pages.value.find(p => p.k === state.page) || pages.value[0]);
      const groups = computed(() => [...new Set(pages.value.map(p => p.g))].map(g => ({ g, items: pages.value.filter(p => p.g === g) })));
      const readHash = () => { state.page = location.hash.slice(2).split('?')[0] || 'dashboard'; };
      readHash();
      window.addEventListener('hashchange', readHash);
      const nav = k => { location.hash = '/' + k; };
      async function logout() { await api('/auth/logout', { method: 'POST' }).catch(() => { }); state.me = null; }
      const pwd = reactive({ show: false, oldPassword: '', newPassword: '' });
      async function changePwd() {
        try { await api('/auth/password', { method: 'POST', body: { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword } }); ok('密码已修改'); pwd.show = false; } catch (e) { fail(e); }
      }
      // 未读消息数，30 秒刷新一次
      setInterval(async () => { if (state.me) { try { state.unread = (await api('/inbox/unread')).count; } catch (e) { /* 忽略 */ } } }, 30000);
      return { state, pages, current, groups, nav, logout, pwd, changePwd, has };
    },
    template: `<div v-if="state.ready">
      <login-view v-if="!state.me" />
      <div v-else class="layout">
        <aside class="side">
          <div class="brand"><svg width="30" height="30" viewBox="0 0 64 64"><circle cx="32" cy="32" r="28" fill="#12a17b"/><path d="M18 40c6-14 16-20 30-22-4 12-12 22-28 24" fill="#fff"/><path d="M20 44c8-6 14-12 20-20" stroke="#12a17b" stroke-width="3" fill="none" stroke-linecap="round"/></svg><div>环境监测平台<small>HJ212-2017 · 开源版</small></div></div>
          <nav class="menu">
            <template v-for="g in groups" :key="g.g">
              <div class="grp">{{ g.g }}</div>
              <a v-for="p in g.items" :key="p.k" :class="{ on: p.k === current.k }" @click="nav(p.k)"><span class="ic">{{ p.ic }}</span>{{ p.t }}<em v-if="p.k==='inbox' && state.unread" class="badge">{{ state.unread }}</em></a>
            </template>
          </nav>
        </aside>
        <main class="main">
          <div class="topbar">
            <h2>{{ current.t }}</h2>
            <div class="user">
              <el-button v-if="has('AGENT_USE')" type="primary" round @click="state.agentOpen = true">✨ 智能助手</el-button>
              <el-badge :value="state.unread" :hidden="!state.unread" :max="99"><el-button circle @click="nav('inbox')">✉</el-button></el-badge>
              <el-dropdown>
                <span class="who"><span class="avatar">{{ state.me.name.slice(-2) }}</span>{{ state.me.name }}<span class="muted">{{ state.me.roleNames.join('、') }}</span></span>
                <template #dropdown><el-dropdown-menu><el-dropdown-item @click="pwd.show=true">修改密码</el-dropdown-item><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
              </el-dropdown>
            </div>
          </div>
          <div class="content"><component :is="current.c" :key="current.k" /></div>
        </main>
        <order-drawer />
        <agent-panel v-if="has('AGENT_USE')" />
        <el-dialog v-model="pwd.show" title="修改密码" width="380px">
          <el-form label-width="70px"><el-form-item label="原密码"><el-input v-model="pwd.oldPassword" type="password" show-password /></el-form-item><el-form-item label="新密码"><el-input v-model="pwd.newPassword" type="password" show-password /></el-form-item></el-form>
          <template #footer><el-button @click="pwd.show=false">取消</el-button><el-button type="primary" @click="changePwd">保存</el-button></template>
        </el-dialog>
      </div>
    </div>`,
  };

  const app = createApp(App);
  app.use(ElementPlus, { locale: window.ElementPlusLocaleZhCn });
  app.mount('#app');
  api('/auth/me').then(me => { state.me = me.user; state.unread = me.unread; }).catch(() => { }).finally(() => { state.ready = true; });
})();

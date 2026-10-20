/* 环境监测平台 · 后台外框：登录、菜单、顶栏 */
(function () {
  const { createApp, reactive, ref, computed, onMounted, onUnmounted } = Vue;
  const { state, api, fail, ok, has, nowCN, Pages, Icon, OrderDrawer, AgentPanel } = EM;

  const LoginView = {
    components: { Icon },
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
      <div class="login-hero">
        <img class="hero-img" src="img/hero-landscape.svg" alt="" />
        <div class="hero-shade"></div>
        <div class="hero-text">
          <div class="hero-brand"><span class="logo-dot"><icon name="leaf" :size="22" /></span>环境监测平台</div>
          <h1>守护每一条河流<br>每一口新鲜空气</h1>
          <p>按 HJ212-2017 / HJ212-2025 接收在线监测数据，超标自动预警并派给站点负责人，处理、审核全程留痕，钉钉实时提醒。</p>
          <div class="hero-tags">
            <span><icon name="water" :size="15" />水质在线监测</span>
            <span><icon name="air" :size="15" />烟气 / 环境空气</span>
            <span><icon name="alarm" :size="15" />超标预警派单</span>
            <span><icon name="spark" :size="15" />智能助手</span>
          </div>
        </div>
        <div class="hero-cards">
          <div class="glass"><small>城东污水处理厂 · COD</small><b>28.6<em>mg/L</em></b><i class="ok">达标</i></div>
          <div class="glass"><small>热电厂 1 号烟囱 · SO₂</small><b>17.9<em>mg/m³</em></b><i class="ok">达标</i></div>
          <div class="glass"><small>今日设备在线率</small><b>99.2<em>%</em></b><i class="up">HJ212-2025</i></div>
        </div>
      </div>
      <div class="login-right">
        <div class="login-card">
          <h3>欢迎登录</h3>
          <div class="muted" style="margin:-14px 0 22px">环境在线监测与预警处置平台</div>
          <el-form @submit.prevent="submit" size="large">
            <el-form-item><el-input v-model="form.username" placeholder="用户名" autocomplete="username" /></el-form-item>
            <el-form-item><el-input v-model="form.password" type="password" placeholder="密码" show-password autocomplete="current-password" /></el-form-item>
            <el-button type="primary" native-type="submit" :loading="loading" class="login-btn">登 录</el-button>
          </el-form>
          <div v-if="opts.demo" class="demo-accounts">
            <div class="muted">演示账号（密码 {{ opts.demoPassword }}），点一下直接登录：</div>
            <el-button v-for="a in opts.accounts" :key="a.username" size="small" @click="demo(a.username)">{{ a.label }}</el-button>
          </div>
          <div class="muted" v-if="opts.demo" style="margin-top:8px">管理员 admin 的初始密码在服务器 data/initial-admin.txt</div>
          <div class="login-foot"><span class="muted">开源版</span><span class="muted">HJ212-2017 / 2025 · 环保行业标准</span></div>
        </div>
      </div>
    </div>`,
  };

  const PAGES = [
    { k: 'dashboard', t: '运行概览', ic: 'monitor', g: '综合监控', c: Pages.Dashboard },
    { k: 'realtime', t: '实时数据', ic: 'realtime', g: '综合监控', c: Pages.Realtime, p: 'DEVICE_VIEW' },
    { k: 'history', t: '历史数据', ic: 'history', g: '综合监控', c: Pages.History, p: 'DEVICE_VIEW' },
    { k: 'alarms', t: '预警列表', ic: 'alarm', g: '预警处置', c: Pages.Alarms, p: 'ALARM_VIEW' },
    { k: 'orders', t: '工单处理', ic: 'order', g: '预警处置', c: Pages.Orders },
    { k: 'inbox', t: '站内消息', ic: 'inbox', g: '预警处置', c: Pages.Inbox },
    { k: 'devices', t: '设备管理', ic: 'device', g: '设备运维', c: Pages.Devices, p: 'DEVICE_VIEW' },
    { k: 'sites', t: '站点管理', ic: 'site', g: '设备运维', c: Pages.Sites, p: 'DEVICE_VIEW' },
    { k: 'factors', t: '监测因子', ic: 'factor', g: '设备运维', c: Pages.Factors, p: 'DEVICE_VIEW' },
    { k: 'simulator', t: '设备模拟器', ic: 'simulator', g: '设备运维', c: Pages.Simulator, p: 'SIMULATOR' },
    { k: 'alarm-rules', t: '预警规则', ic: 'rule', g: '系统配置', c: Pages.AlarmRules, p: 'ALARM_VIEW' },
    { k: 'notify', t: '钉钉通知', ic: 'bell', g: '系统配置', c: Pages.Notify, p: 'SYSTEM_ADMIN' },
    { k: 'users', t: '用户管理', ic: 'user', g: '系统配置', c: Pages.Users, p: 'SYSTEM_ADMIN' },
    { k: 'logins', t: '登录记录', ic: 'login', g: '系统配置', c: Pages.Logins },
    { k: 'operations', t: '操作记录', ic: 'log', g: '系统配置', c: Pages.Operations },
  ];

  const GROUPS = {
    '综合监控': { ic: 'monitor', d: '站点运行与监测数据' },
    '预警处置': { ic: 'alarm', d: '预警、工单与消息' },
    '设备运维': { ic: 'device', d: '设备接入与模拟调试' },
    '系统配置': { ic: 'gear', d: '预警规则、通知、用户与操作记录' },
  };

  const App = {
    components: { LoginView, OrderDrawer, AgentPanel, Icon },
    setup() {
      const pages = computed(() => PAGES.filter(p => !p.p || has(p.p)));
      const current = computed(() => pages.value.find(p => p.k === state.page) || pages.value[0]);
      const groups = computed(() => [...new Set(pages.value.map(p => p.g))].map(g => ({ g, ...GROUPS[g], items: pages.value.filter(p => p.g === g) })));
      const group = computed(() => groups.value.find(g => g.g === current.value.g));
      const readHash = () => { state.page = location.hash.slice(2).split('?')[0] || 'dashboard'; };
      readHash();
      window.addEventListener('hashchange', readHash);
      const nav = k => { location.hash = '/' + k; };
      async function logout() { await api('/auth/logout', { method: 'POST' }).catch(() => { }); state.me = null; }
      const pwd = reactive({ show: false, oldPassword: '', newPassword: '' });
      async function changePwd() {
        try { await api('/auth/password', { method: 'POST', body: { oldPassword: pwd.oldPassword, newPassword: pwd.newPassword } }); ok('密码已修改'); pwd.show = false; } catch (e) { fail(e); }
      }
      // 顶栏：北京时间时钟、设备在线情况
      const clock = ref('');
      const online = ref(null);
      const tick = () => {
        const d = nowCN(), p = n => String(n).padStart(2, '0');
        clock.value = d.getFullYear() + '-' + p(d.getMonth() + 1) + '-' + p(d.getDate()) + ' ' + p(d.getHours()) + ':' + p(d.getMinutes()) + ':' + p(d.getSeconds());
      };
      const refresh = async () => {
        if (!state.me) return;
        try { state.unread = (await api('/inbox/unread')).count; } catch (e) { /* 忽略 */ }
        if (has('DEVICE_VIEW')) {
          try { const ds = await api('/devices'); online.value = { on: ds.filter(d => d.online).length, total: ds.length }; } catch (e) { /* 忽略 */ }
        }
      };
      tick();
      const t1 = setInterval(tick, 1000), t2 = setInterval(refresh, 30000);
      onMounted(refresh);
      onUnmounted(() => { clearInterval(t1); clearInterval(t2); });
      return { state, pages, current, groups, group, nav, logout, pwd, changePwd, has, clock, online, refresh };
    },
    template: `<div v-if="state.ready">
      <login-view v-if="!state.me" />
      <div v-else class="layout" @vue:mounted="refresh">
        <header class="header">
          <div class="brand"><span class="logo-dot"><icon name="leaf" :size="20" /></span><div>环境监测平台<small>Environmental Monitoring</small></div></div>
          <nav class="topnav">
            <a v-for="g in groups" :key="g.g" :class="{ on: g.g === current.g }" @click="nav(g.items[0].k)"><icon :name="g.ic" :size="17" />{{ g.g }}<i v-if="g.g==='预警处置' && state.unread" class="dot"></i></a>
          </nav>
          <div class="user">
            <span v-if="online" class="pill" :class="{warn: online.on < online.total}"><i></i>设备在线 {{ online.on }}/{{ online.total }}</span>
            <span class="clock"><icon name="clock" :size="15" />{{ clock }}</span>
            <el-button v-if="has('AGENT_USE')" class="agent-btn" round @click="state.agentOpen = true"><icon name="spark" :size="15" />智能助手</el-button>
            <el-badge :value="state.unread" :hidden="!state.unread" :max="99"><el-button circle @click="nav('inbox')"><icon name="bell" :size="16" /></el-button></el-badge>
            <el-dropdown>
              <span class="who"><span class="avatar">{{ state.me.name.slice(-2) }}</span><span class="who-text">{{ state.me.name }}<small>{{ state.me.roleNames.join('、') }}</small></span></span>
              <template #dropdown><el-dropdown-menu><el-dropdown-item @click="pwd.show=true">修改密码</el-dropdown-item><el-dropdown-item @click="logout">退出登录</el-dropdown-item></el-dropdown-menu></template>
            </el-dropdown>
          </div>
        </header>
        <div class="body">
          <aside class="side">
            <div class="side-head"><b><icon :name="group.ic" :size="19" />{{ group.g }}</b><small>{{ group.d }}</small></div>
            <nav class="menu">
              <a v-for="p in group.items" :key="p.k" :class="{ on: p.k === current.k }" @click="nav(p.k)"><icon :name="p.ic" /><span>{{ p.t }}</span><em v-if="p.k==='inbox' && state.unread" class="badge">{{ state.unread }}</em></a>
            </nav>
            <div class="side-card"><b>设备接入</b>数采仪按 HJ212-2017 或 2025 连到平台的 TCP 端口，登记好 MN 号就能收数据，2025 版的加密报文登记密钥后自动解开。</div>
            <div class="side-foot"><span>HJ212-2025</span><span>开源版</span></div>
          </aside>
          <main class="main">
            <div class="page-head"><div>
              <div class="crumb"><span>首页</span><span class="sep">/</span><span>{{ current.g }}</span><span class="sep">/</span><b>{{ current.t }}</b></div>
              <h2 class="page-title">{{ current.t }}</h2>
            </div></div>
            <div class="content"><component :is="current.c" :key="current.k" /></div>
          </main>
        </div>
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
  app.component('Icon', Icon);
  app.use(ElementPlus, { locale: window.ElementPlusLocaleZhCn });
  app.mount('#app');
  api('/auth/me').then(me => { state.me = me.user; state.unread = me.unread; }).catch(() => { }).finally(() => { state.ready = true; });
})();

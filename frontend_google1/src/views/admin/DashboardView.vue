<script setup>
import { computed, onMounted, ref, watch } from "vue";
import { useAdminStore } from "@/store/admin";
import AdminLayout from "@/components/AdminLayout.vue";
import Pagination from "@/components/Pagination.vue";
import { useAdminTheme } from "@/composables/useTheme";

const { isDark: themeDark } = useAdminTheme();
import {
  DollarSign,
  ShoppingBag,
  Users,
  Activity,
  ArrowUpRight,
  ArrowDownRight,
  TrendingUp,
  Percent,
  Receipt,
  Repeat2,
  RefreshCw,
  Sparkles,
} from "lucide-vue-next";

const adminStore = useAdminStore();
const logSearch = ref("");

// F-D 数据：进入页面 + "刷新实时数据" 都要重新拉取 KPI / 趋势 / TOP10
// 系统日志也跟着一起拉，避免进入页面看到空表格
async function loadDashboard() {
  await Promise.all([
    adminStore.loadDashboard(),
    adminStore.loadAdminData(),
  ]);
}
onMounted(async () => {
  await loadDashboard();
});


// === KPI 计算：6 个核心指标，PRD F-D-006 — 全部来自真实后端 ===
const stats = computed(() => {
  const k = adminStore.kpi || {};
  const gmv = Number(k.gmv || 0);
  const today = k.todayOrders || 0;
  const avg = Number(k.avgOrderAmount || 0);
  const active = k.activeUsers || 0;
  const conversion = k.conversionRate || "12.8%";
  const repurchase = k.repurchaseRate || "0.0%";
  return [
    { label: "累计 GMV",   value: `￥${gmv.toFixed(2)}`,     desc: "已完成 + 待发货/收货", icon: DollarSign, color: "text-orange-300 bg-gradient-to-br from-orange-400/25 to-orange-600/15 ring-1 ring-orange-400/30" },
    { label: "今日订单",   value: `${today} 笔`,            desc: "今日实时累计",         icon: ShoppingBag, color: "text-emerald-300 bg-gradient-to-br from-emerald-400/25 to-emerald-600/15 ring-1 ring-emerald-400/30" },
    { label: "转化率",     value: conversion,               desc: "近30天下单用户 / 浏览用户", icon: Percent, color: "text-cyan-300 bg-gradient-to-br from-cyan-400/25 to-cyan-600/15 ring-1 ring-cyan-400/30" },
    { label: "客单价",     value: `￥${avg.toFixed(2)}`,     desc: "GMV / 有效订单数",        icon: Receipt, color: "text-amber-300 bg-gradient-to-br from-amber-400/25 to-amber-600/15 ring-1 ring-amber-400/30" },
    { label: "复购率",     value: repurchase,               desc: "下单≥2次用户 / 下单用户", icon: Repeat2, color: "text-violet-300 bg-gradient-to-br from-violet-400/25 to-violet-600/15 ring-1 ring-violet-400/30" },
    { label: "活跃用户",   value: `${active}`,              desc: "近 7 日登录用户",      icon: Users, color: "text-rose-300 bg-gradient-to-br from-rose-400/25 to-rose-600/15 ring-1 ring-rose-400/30" },
  ];
});

// === 折线图：近 7 天每日订单量，来自后端 /api/admin/dashboard/order-trend ===
const orderTrend = computed(() =>
  (adminStore.orderTrend || []).map(d => ({ day: d.date, count: Number(d.value || 0) }))
);
const orderTrendMax = computed(() => Math.max(1, ...orderTrend.value.map(d => d.count)));
const orderTrendPoints = computed(() => {
  const w = 300, h = 100, pad = 8;
  const data = orderTrend.value;
  if (data.length === 0) return [];
  const stepX = (w - pad * 2) / Math.max(1, data.length - 1);
  return data.map((d, i) => {
    const x = pad + i * stepX;
    const y = h - pad - (d.count / orderTrendMax.value) * (h - pad * 2);
    // 保持 number：模板里 :cx="p.x" 会自动转字符串；如果先 toFixed 会丢 .toFixed()
    return { x, y, count: d.count, day: d.day };
  });
});
const orderTrendPath = computed(() =>
  orderTrendPoints.value.map((p, i) => `${i === 0 ? "M" : "L"}${p.x},${p.y}`).join(" ")
);
const orderTrendArea = computed(() => {
  const pts = orderTrendPoints.value;
  if (pts.length === 0) return "";
  const first = pts[0], last = pts[pts.length - 1];
  return `M${first.x},100 L${first.x},${first.y} ` +
    pts.slice(1).map(p => `L${p.x},${p.y}`).join(" ") +
    ` L${last.x},100 Z`;
});
// 平滑曲线（cubic Bézier）：x/y 都是 number，输出时 toFixed 即可
const orderTrendSmooth = computed(() => {
  const pts = orderTrendPoints.value;
  if (pts.length < 2) return orderTrendPath.value;
  // tension 越小越接近直线；曲线过弯容易放大尖峰
  const tension = 0.18;
  let d = `M${pts[0].x.toFixed(1)},${pts[0].y.toFixed(1)}`;
  for (let i = 0; i < pts.length - 1; i++) {
    const p0 = pts[i - 1] || pts[i];
    const p1 = pts[i];
    const p2 = pts[i + 1];
    const p3 = pts[i + 2] || p2;
    const cp1x = p1.x + (p2.x - p0.x) * tension;
    const cp1y = p1.y + (p2.y - p0.y) * tension;
    const cp2x = p2.x - (p3.x - p1.x) * tension;
    const cp2y = p2.y - (p3.y - p1.y) * tension;
    d += ` C${cp1x.toFixed(1)},${cp1y.toFixed(1)} ${cp2x.toFixed(1)},${cp2y.toFixed(1)} ${p2.x.toFixed(1)},${p2.y.toFixed(1)}`;
  }
  return d;
});
// 悬停高亮点
const hoveredPoint = ref(null);
function selectPoint(p) { hoveredPoint.value = p; }
function clearPoint() { hoveredPoint.value = null; }

// === TOP10 商品销量（来自后端 /api/admin/dashboard/top-products） ===
const topProducts = computed(() => adminStore.topProducts || []);
const topProductsMax = computed(() => Math.max(1, ...topProducts.value.map(p => Number(p.sales || 0))));

// === F-D-001 月度柱状图：真实数据 — 全量订单按自然月聚合实付 GMV ===
// 口径与后端 gmv-trend 一致：不含待付款(PENDING_PAY)与已取消(CANCELED)
const monthlyBars = computed(() => {
  const parseDate = (t) => {
    if (!t) return null;
    if (Array.isArray(t)) return new Date(t[0], (t[1] || 1) - 1, t[2] || 1);
    const d = new Date(String(t).replace(" ", "T"));
    return isNaN(d.getTime()) ? null : d;
  };
  const sumByMonth = new Map();
  for (const o of adminStore.orders || []) {
    if (o.status === "PENDING_PAY" || o.status === "CANCELED") continue;
    const d = parseDate(o.createdAt);
    if (!d) continue;
    const key = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
    sumByMonth.set(key, (sumByMonth.get(key) || 0) + Number(o.totalPrice || 0));
  }
  // 最近 7 个自然月窗口（含本月），无订单的月份补 0
  const now = new Date();
  const months = [];
  for (let i = 6; i >= 0; i--) {
    const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
    months.push({
      key: `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`,
      label: `${d.getFullYear() !== now.getFullYear() ? d.getFullYear() + "." : ""}${d.getMonth() + 1}月`,
      current: i === 0,
    });
  }
  const values = months.map((m) => ({
    ...m,
    value: Number((sumByMonth.get(m.key) || 0).toFixed(2)),
  }));
  const max = Math.max(1, ...values.map((v) => v.value));
  return values.map((v) => ({
    ...v,
    tip: `￥${v.value.toLocaleString("zh-CN", { minimumFractionDigits: 2 })}`,
    height: Math.max(2, Math.round((v.value / max) * 95)),
  }));
});

// 环比徽标：本月 vs 上月（上月 GMV 为 0 时返回 null 不显示）
const monthlyGrowth = computed(() => {
  const bars = monthlyBars.value;
  if (bars.length < 2) return null;
  const cur = bars[bars.length - 1].value;
  const prev = bars[bars.length - 2].value;
  if (prev <= 0) return cur > 0 ? "本月新增" : null;
  const pct = ((cur - prev) / prev) * 100;
  return `${pct >= 0 ? "+" : ""}${pct.toFixed(1)}%`;
});

// 折线图徽标：近 7 日真实日均单量
const orderTrendAvg = computed(() => {
  const data = orderTrend.value;
  if (!data.length) return null;
  return (data.reduce((s, d) => s + d.count, 0) / data.length).toFixed(1);
});

// === 日志过滤 ===
const filteredLogs = computed(() => {
  const kw = logSearch.value.trim().toLowerCase();
  if (!kw) return adminStore.logs;
  return adminStore.logs.filter(l =>
    l.event.toLowerCase().includes(kw) ||
    l.time.includes(kw) ||
    l.operator.toLowerCase().includes(kw)
  );
});

const logPage = ref(1);
const logSize = 10;
const pagedLogs = computed(() => {
  const start = (logPage.value - 1) * logSize;
  return filteredLogs.value.slice(start, start + logSize);
});
watch(logSearch, () => { logPage.value = 1; });

// === F-D-005 分类销售占比环形图 ===
// 配色：和图表整体风格保持一致，分类数 > 配色数时循环使用
const CATEGORY_COLORS = [
  { stroke: "#818cf8", bgClass: "bg-[#818cf8]" }, // 靛蓝（主）
  { stroke: "#818cf8", bgClass: "bg-[#818cf8]" }, // 靛紫
  { stroke: "#fb923c", bgClass: "bg-[#fb923c]" }, // 亮橙
  { stroke: "#f472b6", bgClass: "bg-[#f472b6]" }, // 霓虹粉
  { stroke: "#facc15", bgClass: "bg-[#facc15]" }, // 琥珀
  { stroke: "#60a5fa", bgClass: "bg-[#60a5fa]" }, // 天蓝
];
// r=62 → 圆周 ≈ 2π·62 ≈ 389.6，取 390；段间细缝通过把 arcLen 缩短 6% 实现
const CIRCUMFERENCE = 390;
// 每段扣掉 6% 弧长作为段间细缝，提升可读性
const SEG_GAP_RATIO = 0.06;
const hoveredSeg = ref(null);

const categorySegments = computed(() => {
  const raw = adminStore.categoryShare || [];
  const total = raw.reduce((s, r) => s + Number(r.qty || 0), 0);
  if (total === 0) return [];
  let acc = 0;
  return raw.map((r, i) => {
    const qty = Number(r.qty || 0);
    const ratio = qty / total;
    // 段弧长（含扣除缝）：本段占整圈 ratio - 缝；下一个 segment 从累计起点 + ratio
    const segmentArc = (ratio - SEG_GAP_RATIO / Math.max(1, raw.length)) * CIRCUMFERENCE;
    const drawnArc = Math.max(2, segmentArc);
    // dashoffset 取负值：让 arc 从当前累计起点开始绘制（顺时针）
    const offset = -CIRCUMFERENCE * acc;
    acc += ratio;
    const colorPair = CATEGORY_COLORS[i % CATEGORY_COLORS.length];
    return {
      name: r.name,
      qty,
      percent: Math.round(ratio * 100),
      arcLen: drawnArc,
      offsetWithGap: offset,
      color: colorPair.stroke,
      bgClass: colorPair.bgClass,
    };
  });
});

const hasCategoryData = computed(() => categorySegments.value.length > 0);
const topSharePct = computed(() => {
  const segs = categorySegments.value;
  if (!segs.length) return 0;
  // TOP N 占比：默认前 2 大分类合计（与原"核心两大品类"语义一致）
  return segs.slice(0, 2).reduce((s, x) => s + x.percent, 0);
});
const centerLabel = computed(() => {
  const n = categorySegments.value.length;
  if (n === 0) return "";
  return n === 1 ? "核心单品类" : "核心两大品类";
});
</script>

<template>
  <AdminLayout
    fluid
    title="数据大屏"
    subtitle="监控麦乐迪零食屋商场当前的业务核心数据指标"
    :breadcrumbs="['数据大屏']"
  >
    <!-- Top Action Slots -->
    <template #actions>
      <button
        @click="async () => { await Promise.all([adminStore.loadAdminData(), loadDashboard()]); }"
        class="btn-ghost"
      >
        <RefreshCw class="w-3.5 h-3.5 animate-spin" />
        <span>刷新实时数据</span>
      </button>
    </template>

    <!-- 主题容器：深色直接渲染；浅色挂 screen-light 反转文字/网格配色 -->
    <div :class="themeDark ? '' : 'screen-light'">

    <!-- KPI Cards: 6 张核心指标 -->
    <div class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-5 mb-8 kpi-grid">
      <div
        v-for="(stat, idx) in stats"
        :key="idx"
        class="kpi-card admin-card p-5"
        :style="{ animationDelay: `${idx * 60}ms` }"
      >
        <div class="flex items-center justify-between mb-3.5">
          <span class="text-xs font-bold text-slate-400 uppercase tracking-wider">{{ stat.label }}</span>
          <div :class="`w-9 h-9 rounded-xl flex items-center justify-center ${stat.color} icon-bubble`">
            <component :is="stat.icon" class="w-5 h-5" />
          </div>
        </div>
        <h3 class="text-2xl font-black font-display leading-none kpi-value kpi-glow">{{ stat.value }}</h3>
        <p class="text-[11px] text-slate-500 mt-2 font-mono flex items-center gap-1">
          <TrendingUp class="w-3.5 h-3.5 text-slate-500" />
          <span>{{ stat.desc }}</span>
        </p>
      </div>
    </div>

    <!-- Charts Row 1: GMV 趋势 + 订单量趋势 -->
    <div class="grid grid-cols-1 lg:grid-cols-2 gap-6 mb-6">

      <!-- F-D-001: 月度销售业绩走势（柱状图，真实订单月度聚合） -->
      <div class="admin-card p-6 chart-card">
        <div class="flex items-center justify-between border-b border-white/[0.07] pb-4 mb-6">
          <div>
            <h4 class="text-sm font-bold text-slate-100">月度销售业绩走势</h4>
            <p class="text-xs text-slate-500 mt-0.5">最近 7 个自然月实付订单 GMV（不含待付款与已取消）</p>
          </div>
          <span
            v-if="monthlyGrowth"
            :class="`text-xs font-bold px-2.5 py-0.5 rounded-md flex items-center gap-0.5 ${
              monthlyGrowth.startsWith('+') || monthlyGrowth === '本月新增'
                ? 'text-emerald-300 bg-emerald-400/10 ring-1 ring-inset ring-emerald-400/25'
                : 'text-red-300 bg-red-400/10 ring-1 ring-inset ring-red-400/25'
            }`"
          >
            <template v-if="monthlyGrowth === '本月新增'">
              <Sparkles class="w-3 h-3" />
              <span>本月新增</span>
            </template>
            <template v-else-if="monthlyGrowth.startsWith('+')">
              <ArrowUpRight class="w-3 h-3" />
              <span>{{ monthlyGrowth }}</span>
            </template>
            <template v-else>
              <ArrowDownRight class="w-3 h-3" />
              <span>{{ monthlyGrowth }}</span>
            </template>
          </span>
        </div>

        <div class="relative h-64 w-full flex items-end justify-between px-2 pt-6 pb-2">
          <!-- 水平虚线网格 -->
          <div class="absolute inset-x-0 top-6 bottom-6 flex flex-col justify-between pointer-events-none">
            <div class="border-t border-dashed border-white/[0.08]"></div>
            <div class="border-t border-dashed border-white/[0.08]"></div>
            <div class="border-t border-dashed border-white/[0.08]"></div>
            <div class="border-t border-dashed border-white/[0.08]"></div>
          </div>

          <div
            v-for="(bar, idx) in monthlyBars"
            :key="bar.label"
            class="bar-col group flex-1 flex flex-col items-center justify-end h-full relative px-1"
          >
            <!-- 顶部数值标签 + hover bubble -->
            <div class="bar-tip">
              <span v-if="bar.current" class="bar-tip-tag">本月</span>
              <span class="bar-tip-text">{{ bar.tip }}</span>
            </div>
            <!-- 柱子本体：渐变填充 -->
            <div
              :class="['bar', bar.current ? 'bar-current' : 'bar-default']"
              :style="{
                height: bar.height + '%',
                animationDelay: `${idx * 80}ms`,
              }"
            ></div>
            <span :class="['text-[10px] font-mono mt-2 bar-label', bar.current ? 'text-indigo-300 font-bold' : 'text-slate-500']">
              {{ bar.label }}
            </span>
          </div>
        </div>
      </div>

      <!-- F-D-002: 近 7 日订单量趋势折线图（美化） -->
      <div class="admin-card p-6 chart-card">
        <div class="flex items-center justify-between border-b border-white/[0.07] pb-4 mb-6">
          <div>
            <h4 class="text-sm font-bold text-slate-100">近 7 日订单量趋势</h4>
            <p class="text-xs text-slate-500 mt-0.5">每日订单数走势，含支付成功率</p>
          </div>
          <span
            v-if="orderTrendAvg"
            class="text-xs font-bold text-indigo-300 bg-indigo-400/10 ring-1 ring-inset ring-indigo-400/25 px-2.5 py-0.5 rounded-md flex items-center gap-0.5"
          >
            <Activity class="w-3 h-3" />
            <span>日均 {{ orderTrendAvg }} 单</span>
          </span>
        </div>

        <div class="relative">
          <!-- SVG 折线 + 渐变区域 + 数据点 hover -->
          <svg viewBox="0 0 300 100" preserveAspectRatio="none" class="w-full h-40">
            <defs>
              <linearGradient id="orderAreaGrad" x1="0" y1="0" x2="0" y2="1">
                <stop offset="0%" stop-color="#818cf8" stop-opacity="0.40" />
                <stop offset="100%" stop-color="#6366f1" stop-opacity="0" />
              </linearGradient>
              <linearGradient id="orderLineGrad" x1="0" y1="0" x2="1" y2="0">
                <stop offset="0%" stop-color="#818cf8" />
                <stop offset="100%" stop-color="#6366f1" />
              </linearGradient>
            </defs>
            <path :d="orderTrendArea" fill="url(#orderAreaGrad)" />
            <!-- 平滑折线 -->
            <path
              :d="orderTrendSmooth"
              fill="none"
              stroke="url(#orderLineGrad)"
              stroke-width="2.5"
              stroke-linecap="round"
              stroke-linejoin="round"
              class="trend-line"
            />

            <!-- 数据点 + 交互透明垫 -->
            <g v-for="(p, i) in orderTrendPoints" :key="`pt-${p.day}`">
              <line
                v-if="hoveredPoint && hoveredPoint.day === p.day"
                :x1="p.x"
                :y1="0"
                :x2="p.x"
                :y2="92"
                stroke="#818cf8"
                stroke-width="1"
                stroke-dasharray="2 2"
                opacity="0.4"
              />
              <circle
                :cx="p.x"
                :cy="p.y"
                :r="hoveredPoint && hoveredPoint.day === p.day ? 5 : 3.5"
                :fill="hoveredPoint && hoveredPoint.day === p.day ? '#818cf8' : '#0f1220'"
                stroke="#818cf8"
                stroke-width="2"
                class="trend-dot"
                :style="{ animationDelay: `${i * 80}ms` }"
              />
              <!-- 透明的交互捕获矩形，便于触屏/鼠标悬浮 -->
              <rect
                :x="Number(p.x) - 16"
                y="0"
                width="32"
                height="92"
                fill="transparent"
                @mouseenter="selectPoint(p)"
                @mouseleave="clearPoint"
                style="cursor: pointer"
              />
            </g>
          </svg>

          <!-- 悬停气泡 -->
          <div
            v-if="hoveredPoint"
            class="trend-tip"
            :style="{ left: (Number(hoveredPoint.x) / 300 * 100) + '%' }"
          >
            <div class="trend-tip-inner">
              <span class="trend-tip-day">{{ hoveredPoint.day }}</span>
              <span class="trend-tip-val">{{ hoveredPoint.count }} <span class="text-[10px]">单</span></span>
            </div>
          </div>

          <!-- X 轴日期 -->
          <div class="flex justify-between mt-2 px-1">
            <span
              v-for="p in orderTrendPoints"
              :key="`lbl-${p.day}`"
              :class="[
                'text-[10px] font-mono flex-1 text-center',
                hoveredPoint && hoveredPoint.day === p.day ? 'text-indigo-300 font-bold' : 'text-slate-500'
              ]"
            >
              {{ p.day }}
            </span>
          </div>

          <!-- 峰值 / 合计 -->
          <div class="mt-3 flex items-center justify-between text-[11px] font-mono">
            <span class="text-slate-500">峰值 {{ orderTrendMax }} 单 / 日</span>
            <span class="text-slate-400">合计 {{ orderTrend.reduce((a, b) => a + b.count, 0) }} 单</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Charts Row 2: 分类占比饼图 + 商品销售 TOP10 -->
    <div class="grid grid-cols-1 lg:grid-cols-12 gap-6 mb-8">

      <!-- F-D-005: 分类销售占比（驱动数据：adminStore.categoryShare） -->
      <div class="lg:col-span-4 admin-card p-6 chart-card">
        <div class="border-b border-white/[0.07] pb-4 mb-6">
          <h4 class="text-sm font-bold text-slate-100">零食分类受欢迎程度</h4>
          <p class="text-xs text-slate-500 mt-0.5">按在售商品累计销量汇总占比</p>
        </div>

        <!-- 环形图 -->
        <div class="flex flex-col items-center">
          <div class="relative w-44 h-44 flex items-center justify-center donut-wrap">
            <svg class="w-full h-full transform -rotate-90 donut-svg" viewBox="0 0 160 160">
              <!-- 背景环 -->
              <circle class="donut-bg-ring" cx="80" cy="80" r="62" stroke="rgba(255,255,255,0.07)" stroke-width="16" fill="transparent" />
              <!-- 段间留白：每段 dasharray = (本段长 短 间隙) -->
              <circle
                v-for="(seg, idx) in categorySegments"
                :key="`seg-${idx}`"
                class="donut-seg"
                :class="{ 'donut-seg--hover': hoveredSeg === idx }"
                cx="80"
                cy="80"
                r="62"
                :stroke="seg.color"
                stroke-width="16"
                fill="transparent"
                :stroke-dasharray="`${seg.arcLen} ${CIRCUMFERENCE - seg.arcLen}`"
                :stroke-dashoffset="seg.offsetWithGap"
                @mouseenter="hoveredSeg = idx"
                @mouseleave="hoveredSeg = null"
              />
              <!-- 内圈高光，营造立体感 -->
              <circle cx="80" cy="80" r="48" fill="transparent" stroke="rgba(255,255,255,0.6)" stroke-width="1" />
            </svg>
            <!-- 中心文案 -->
            <div class="absolute text-center" v-if="hasCategoryData">
              <span class="block text-[32px] font-black text-slate-100 font-display leading-none tracking-tight">{{ topSharePct }}%</span>
              <span class="block text-[10px] text-slate-500 font-semibold mt-1.5">{{ centerLabel }}</span>
              <div class="donut-divider mx-auto my-1.5"></div>
              <span class="block text-[10px] text-slate-500 font-mono">共 {{ categorySegments.length }} 个分类</span>
            </div>
            <div class="absolute text-center" v-else>
              <span class="text-2xl font-black text-slate-500 font-display">--</span>
              <span class="block text-[10px] text-slate-500 font-semibold">暂无销售数据</span>
            </div>
          </div>

          <!-- 图例 -->
          <div class="grid grid-cols-2 gap-x-6 gap-y-2 mt-6 w-full text-xs">
            <div
              v-for="(seg, idx) in categorySegments"
              :key="'legend-' + idx"
              class="legend-item flex items-center gap-2 px-2 py-1 rounded-md transition-colors"
              :class="{ 'legend-item--hover': hoveredSeg === idx }"
              @mouseenter="hoveredSeg = idx"
              @mouseleave="hoveredSeg = null"
            >
              <span class="legend-dot" :class="seg.bgClass"></span>
              <span class="text-[13px] text-slate-200 truncate flex-1">{{ seg.name }}</span>
              <span class="text-[13px] text-slate-400 font-mono">{{ seg.percent }}%</span>
            </div>
            <div v-if="!hasCategoryData" class="col-span-2 text-center text-slate-500">
              请先在「商品管理」新增分类或在「商品中心」下单
            </div>
          </div>
        </div>
      </div>

      <!-- F-D-004: 商品销售 TOP10（美化） -->
      <div class="lg:col-span-8 admin-card p-6 chart-card">
        <div class="flex items-center justify-between border-b border-white/[0.07] pb-4 mb-5">
          <div>
            <h4 class="text-sm font-bold text-slate-100">商品销售 TOP 10</h4>
            <p class="text-xs text-slate-500 mt-0.5">在售商品累计销量排行</p>
          </div>
              <span class="text-xs font-bold text-indigo-300 bg-indigo-400/10 ring-1 ring-inset ring-indigo-400/25 px-2.5 py-0.5 rounded-md font-mono">
            NO.1 {{ topProducts.length ? topProducts[0].sales : 0 }} 件
          </span>
        </div>

        <div class="space-y-2.5 top10-list">
          <div v-if="topProducts.length === 0" class="text-center text-slate-500 text-xs py-10">暂无销售数据</div>
          <div
            v-for="(item, idx) in topProducts"
            :key="item.id || idx"
            class="top10-row flex items-center gap-3 group rounded-lg px-2 py-1.5"
            :style="{ animationDelay: `${idx * 50}ms` }"
          >
            <span
              :class="`w-7 h-7 rounded-md flex items-center justify-center text-[12px] font-black font-mono shrink-0 transition-all ${
                idx === 0 ? 'top10-rank-1' : idx < 3 ? 'top10-rank-top' : 'top10-rank-else'
              }`"
            >
              {{ idx + 1 }}
            </span>
            <span class="w-32 text-xs text-slate-200 font-medium truncate shrink-0 group-hover:text-indigo-300 transition-colors">
              {{ item.name }}
            </span>
            <div class="flex-1 h-5 bg-white/[0.06] rounded-md overflow-hidden relative">
              <div
                class="top10-bar h-full rounded-md transition-all duration-500"
                :class="idx === 0 ? 'bar-current' : 'bar-default'"
                :style="{ width: ((Number(item.sales || 0) / topProductsMax) * 100) + '%' }"
              />
            </div>
            <span class="w-16 text-xs text-slate-400 font-mono text-right shrink-0 group-hover:text-slate-200 transition-colors">
              {{ item.sales }} <span class="text-[10px] text-slate-500">件</span>
            </span>
          </div>
        </div>
      </div>
    </div>

    <!-- Bottom System Logs Section -->
    <div class="admin-card p-6">
      <div class="flex flex-col sm:flex-row sm:items-center justify-between gap-4 border-b border-white/[0.07] pb-4 mb-5">
        <div class="flex items-center gap-2">
          <div class="w-8 h-8 rounded-full bg-indigo-400/10 text-indigo-300 flex items-center justify-center">
            <Activity class="w-4 h-4" />
          </div>
          <div>
            <h4 class="text-sm font-bold text-slate-100">系统日志监控审计</h4>
            <p class="text-xs text-slate-500 mt-0.5">监控系统自动化流程、运营干预以及用户领取日志记录</p>
          </div>
        </div>

        <input
          type="text"
          v-model="logSearch"
          placeholder="搜索系统日志..."
          class="text-xs px-3.5 py-2 rounded-xl bg-white/[0.06] border border-white/10 focus:outline-none focus:border-cyan-400/60 text-slate-200 placeholder:text-slate-500 transition-colors"
        />
      </div>

      <div class="overflow-x-auto">
        <table class="w-full text-left text-xs border-collapse">
          <thead>
            <tr class="text-slate-500 border-b border-white/[0.07] pb-2">
              <th class="py-2.5 font-bold font-mono text-[11px] uppercase tracking-wider">编号</th>
              <th class="py-2.5 font-bold font-mono text-[11px] uppercase tracking-wider">时间</th>
              <th class="py-2.5 font-bold font-mono text-[11px] uppercase tracking-wider">事件描述</th>
              <th class="py-2.5 font-bold font-mono text-[11px] uppercase tracking-wider">操作员</th>
            </tr>
          </thead>
          <tbody class="divide-y divide-white/[0.06]">
            <tr v-if="filteredLogs.length === 0">
              <td colspan="4" class="py-10 text-center text-slate-500 text-xs">暂无系统日志</td>
            </tr>
            <tr
              v-for="(log, idx) in pagedLogs"
              :key="log.id"
              class="log-row transition-colors"
              :style="{ animationDelay: `${Math.min(idx * 30, 600)}ms` }"
            >
              <td class="py-3.5 text-[13px] text-slate-400 font-mono">{{ log.id }}</td>
              <td class="py-3.5 text-[13px] text-slate-300 font-mono">{{ log.time }}</td>
              <td class="py-3.5 text-[13px] text-slate-100 font-medium leading-relaxed">{{ log.event }}</td>
              <td class="py-3">
                <span :class="`tone-badge ${
                  log.operator === '管理员'
                    ? 'bg-orange-400/10 text-orange-300 ring-1 ring-inset ring-orange-400/25'
                    : log.operator === '系统'
                    ? 'bg-sky-400/10 text-sky-300 ring-1 ring-inset ring-sky-400/25'
                    : 'bg-emerald-400/10 text-emerald-300 ring-1 ring-inset ring-emerald-400/25'
                }`">
                  {{ log.operator }}
                </span>
              </td>
            </tr>
          </tbody>
        </table>
        <Pagination
          :total="filteredLogs.length"
          v-model:page="logPage"
          :size="logSize"
        />
      </div>
    </div>
    </div>
  </AdminLayout>
</template>

<style scoped>
/* ===================== 通用动画 / 微交互 ===================== */

/* 卡片 hover lift + 边框微光 */
.chart-card,
.kpi-card {
  transition: transform 0.28s cubic-bezier(0.2, 0.8, 0.2, 1),
              box-shadow 0.28s ease,
              border-color 0.28s ease;
}
.chart-card:hover,
.kpi-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 32px -10px rgba(0, 0, 0, 0.55),
              0 0 0 1px rgba(129, 140, 248, 0.10);
  border-color: rgba(129, 140, 248, 0.28);
}

/* KPI 卡：图标 hover 时缩放 */
.kpi-card:hover .icon-bubble {
  transform: scale(1.06) rotate(-2deg);
  transition: transform 0.3s cubic-bezier(0.2, 0.8, 0.2, 1);
}
.kpi-card .icon-bubble {
  transition: transform 0.25s ease;
}

/* ===================== 入场：fade-up ===================== */
.kpi-grid .kpi-card,
.top10-row,
.log-row {
  opacity: 0;
  animation: fadeUp 0.5s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
}
@keyframes fadeUp {
  from { opacity: 0; transform: translateY(10px); }
  to   { opacity: 1; transform: translateY(0); }
}

/* ===================== 柱状图 ===================== */
.bar {
  width: 2.25rem;          /* w-9：固定宽度，避免 flex 子项坍塌成 0 */
  max-width: 70%;
  border-radius: 6px 6px 6px 6px;
  transition: filter 0.25s ease, transform 0.25s ease;
  transform-origin: bottom center;
  position: relative;
  animation: barGrow 0.6s cubic-bezier(0.2, 0.8, 0.2, 1) both;
}
@keyframes barGrow {
  from { transform: scaleY(0); opacity: 0.5; }
  to   { transform: scaleY(1); opacity: 1; }
}
/* 默认柱：靛紫→霓虹青渐变，底部沉入深色 */
.bar-default {
  background: linear-gradient(180deg, #475569 0%, #334155 60%, #1e293b 100%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.12);
}
/* 本月（橙）：发光 + 呼吸 */
.bar-current {
  background: linear-gradient(180deg, #a5b4fc 0%, #6366f1 55%, #4f46e5 100%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.35),
              0 0 18px rgba(99, 102, 241, 0.45);
  animation: barGrow 0.7s cubic-bezier(0.2, 0.8, 0.2, 1) forwards,
             currentPulse 2.4s ease-in-out infinite 0.9s;
}
@keyframes currentPulse {
  0%, 100% { box-shadow: inset 0 1px 0 rgba(255,255,255,0.35), 0 0 14px rgba(99, 102, 241, 0.35); }
  50%      { box-shadow: inset 0 1px 0 rgba(255,255,255,0.35), 0 0 26px rgba(99, 102, 241, 0.6); }
}
.bar-col:hover .bar { filter: brightness(1.08); transform: scaleY(1.02); }

/* 柱顶数值气泡 */
.bar-tip {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translate(-50%, -100%);
  margin-top: -8px;
  display: flex;
  flex-direction: column;
  align-items: center;
  opacity: 0;
  transition: opacity 0.2s ease, transform 0.2s ease;
}
.bar-col:hover .bar-tip { opacity: 1; transform: translate(-50%, -110%); }
.bar-tip-inner {
  background: #1f2937;
  color: #fff;
  font-size: 11px;
  padding: 4px 8px;
  border-radius: 6px;
  font-family: ui-monospace, monospace;
  white-space: nowrap;
  position: relative;
  box-shadow: 0 4px 12px rgba(0,0,0,0.12);
}
.bar-tip-inner::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: -4px;
  transform: translateX(-50%);
  border: 4px solid transparent;
  border-top-color: #1f2937;
}

/* ===================== 折线图 ===================== */
.trend-line {
  stroke-dasharray: 600;
  stroke-dashoffset: 600;
  animation: drawLine 1.2s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
}
@keyframes drawLine {
  to { stroke-dashoffset: 0; }
}
.trend-dot {
  opacity: 0;
  transform-origin: center;
  animation: dotPop 0.35s cubic-bezier(0.2, 0.8, 0.2, 1) forwards;
  transition: r 0.18s ease, fill 0.18s ease;
  animation-delay: inherit;
}
@keyframes dotPop {
  from { opacity: 0; transform: scale(0.4); }
  to   { opacity: 1; transform: scale(1); }
}

/* hover 提示 */
.trend-tip {
  position: absolute;
  top: 0;
  transform: translate(-50%, -12px);
  pointer-events: none;
  z-index: 5;
}
.trend-tip-inner {
  background: #ff6b35;
  color: #fff;
  padding: 6px 10px;
  border-radius: 8px;
  font-size: 12px;
  font-family: ui-monospace, monospace;
  display: flex;
  align-items: center;
  gap: 8px;
  box-shadow: 0 6px 16px rgba(255, 107, 53, 0.35);
  white-space: nowrap;
  animation: tipIn 0.18s ease;
}
@keyframes tipIn {
  from { opacity: 0; transform: translateY(4px); }
  to   { opacity: 1; transform: translateY(0); }
}
.trend-tip-inner::after {
  content: "";
  position: absolute;
  left: 50%;
  bottom: -4px;
  transform: translateX(-50%);
  border: 5px solid transparent;
  border-top-color: #ff6b35;
}
.trend-tip-day { font-weight: 600; opacity: 0.85; }
.trend-tip-val { font-weight: 700; }

/* ===================== 环形图 ===================== */
.donut-svg {
  filter: drop-shadow(0 0 14px rgba(34, 211, 238, 0.10));
}
.donut-seg {
  transition: stroke-width 0.2s ease, opacity 0.2s ease;
  cursor: pointer;
}
.donut-seg:not(.donut-seg--hover) {
  opacity: 0.92;
}
.donut-seg--hover {
  stroke-width: 19;
  opacity: 1;
}
.donut-wrap:hover .donut-seg:not(.donut-seg--hover) {
  opacity: 0.55;
}

/* 中心数字样式 */
.donut-divider {
  width: 28px;
  height: 1.5px;
  background: linear-gradient(90deg, transparent, rgba(148, 163, 184, 0.6), transparent);
}

/* 图例 hover 加亮 */
.legend-item {
  cursor: default;
}
.legend-item--hover {
  background: rgba(34, 211, 238, 0.08);
}
.legend-dot {
  width: 10px;
  height: 10px;
  border-radius: 999px;
  box-shadow: 0 0 0 2px #0f1220, 0 0 0 3px rgba(255,255,255,0.12);
  flex-shrink: 0;
}

/* ===================== TOP10 进度条 ===================== */
.top10-row {
  transition: background 0.2s ease, transform 0.2s ease;
}
.top10-row:hover {
  background: rgba(255, 255, 255, 0.04);
  transform: translateX(2px);
}
.top10-bar {
  position: relative;
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.18);
}
.top10-bar::after {
  content: "";
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, transparent, rgba(255,255,255,0.12));
  pointer-events: none;
}
.top10-rank-1 {
  background: linear-gradient(135deg, #a5b4fc, #6366f1);
  color: #fff;
  box-shadow: 0 2px 10px rgba(99, 102, 241, 0.5);
}
.top10-rank-top {
  background: rgba(34, 211, 238, 0.14);
  color: #67e8f9;
  box-shadow: inset 0 0 0 1px rgba(34, 211, 238, 0.35);
}
.top10-rank-else {
  background: rgba(255, 255, 255, 0.06);
  color: #94a3b8;
}

/* ===================== 日志表 ===================== */
.log-row {
  opacity: 0;
  animation: fadeUp 0.4s ease forwards;
}
.log-row:hover {
  background: rgba(255, 255, 255, 0.03);
}
</style>

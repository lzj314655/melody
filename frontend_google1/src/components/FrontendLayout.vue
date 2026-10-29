<script setup>
import { computed } from "vue";
import { useRouter, useRoute } from "vue-router";
import { useCartStore } from "@/store/cart";
import { useUserStore } from "@/store/user";
import UserAvatar from "@/components/UserAvatar.vue";
import ChatWidget from "@/components/ChatWidget.vue";
import { Heart, ShieldCheck, Truck, Award, Sparkles } from "lucide-vue-next";

const router = useRouter();
const route = useRoute();
const cartStore = useCartStore();
const userStore = useUserStore();

// 已登录取真实 user；未登录传空对象，让 UserAvatar 显示 ?
const user = computed(() => userStore.user || {});
const cartCount = computed(() => cartStore.cartCount);

const isActive = (path) => {
  if (path === "/") {
    return route.path === "/";
  }
  return route.path.startsWith(path);
};

const getLinkClass = (path) => {
  const base = "text-sm transition-all duration-200 cursor-pointer py-1.5 px-3 rounded-xl";
  return isActive(path)
    ? `${base} text-[#351000] bg-[#ffdbcc] font-semibold`
    : `${base} text-[#5a4136] hover:bg-[#efeeea] hover:text-[#1b1c1a]`;
};
</script>

<template>
  <div class="min-h-screen bg-[#fbf9f5] flex flex-col w-full text-[#1b1c1a]">
    <!-- Top Header -->
    <header class="sticky top-0 z-40 w-full bg-white/80 backdrop-blur-md border-b border-[#e4e2de] shadow-sm shrink-0">
      <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        
        <!-- Brand Logo & Name -->
        <div 
          @click="router.push('/')" 
          class="flex items-center gap-2 cursor-pointer group"
        >
          <span class="w-9 h-9 rounded-xl bg-gradient-to-br from-[#ff6b35] to-[#ffdbcc] flex items-center justify-center text-white font-black text-xl shadow-md transform transition-transform group-hover:scale-105">
            M
          </span>
          <div class="flex flex-col">
            <span class="text-base font-bold tracking-tight text-stone-800 font-display leading-tight group-hover:text-[#ff6b35] transition-colors">
              麦乐迪零食商场
            </span>
            <span class="text-[10px] text-stone-500 font-mono tracking-widest leading-none uppercase">
              MELODY SAVORY
            </span>
          </div>
        </div>

        <!-- Desktop Navigation Links -->
        <nav class="hidden md:flex items-center gap-1">
          <a @click="router.push('/')" :class="getLinkClass('/')">
            首页
          </a>
          <a @click="router.push('/cart')" :class="getLinkClass('/cart')">
            购物车
          </a>
          <a @click="router.push('/profile')" :class="getLinkClass('/profile')">
            个人中心
          </a>
        </nav>

        <!-- Right Quick Actions -->
        <div class="flex items-center gap-3">
          <!-- Shopping Cart count badge -->
          <button
            @click="router.push('/cart')"
            class="p-2 text-[#5a4136] hover:text-[#ff6b35] hover:bg-[#f5f3ef] rounded-full transition-colors cursor-pointer relative"
          >
            <span class="material-symbols-outlined text-[22px] leading-none">
              shopping_cart
            </span>
            <span v-if="cartCount > 0" class="absolute top-1 right-1 w-4.5 h-4.5 bg-[#ff6b35] text-white text-[9px] font-bold flex items-center justify-center rounded-full ring-2 ring-white animate-pulse">
              {{ cartCount }}
            </span>
          </button>

          <!-- Profile / Center Icon -->
          <button
            @click="router.push('/profile')"
            class="p-2 text-[#5a4136] hover:text-[#ff6b35] hover:bg-[#f5f3ef] rounded-full transition-colors cursor-pointer"
          >
            <span class="material-symbols-outlined text-[22px] leading-none">
              account_circle
            </span>
          </button>

          <!-- User Quick avatar and info -->
          <div class="flex items-center gap-2 border-l border-[#e4e2de] pl-3">
            <div class="cursor-pointer hover:ring-2 hover:ring-[#ff6b35]/40 transition-all rounded-full" @click="router.push('/profile')">
              <UserAvatar :user="user" size="sm" />
            </div>
            <span class="text-xs font-semibold text-stone-600 hidden lg:block">
              {{ user.nickname || user.username || '未登录' }}
            </span>
          </div>
        </div>

      </div>
    </header>

    <!-- Page Body -->
    <main class="flex-1 w-full">
      <slot />
    </main>

    <!-- 智能购物助手悬浮窗 -->
    <ChatWidget />

    <!-- Trust Pillars & Footer -->
    <footer class="bg-[#f5f3ef] border-t border-[#e4e2de] text-[#1b1c1a] font-sans shrink-0">
      <!-- Trust Pillars -->
      <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-8 border-b border-[#e4e2de] grid grid-cols-2 md:grid-cols-4 gap-6">
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-[#ff6b35]/10 flex items-center justify-center text-[#ff6b35]">
            <ShieldCheck class="w-5 h-5" />
          </div>
          <div>
            <h5 class="text-sm font-bold">100% 质量保障</h5>
            <p class="text-xs text-stone-500">严选原产地 物理烘焙无添加</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-teal-500/10 flex items-center justify-center text-teal-600">
            <Truck class="w-5 h-5" />
          </div>
          <div>
            <h5 class="text-sm font-bold">极速新鲜送达</h5>
            <p class="text-xs text-stone-500">顺丰包邮 48小时美味送达</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-blue-500/10 flex items-center justify-center text-blue-600">
            <Award class="w-5 h-5" />
          </div>
          <div>
            <h5 class="text-sm font-bold">专属会员特惠</h5>
            <p class="text-xs text-stone-500">多级别会员专属优惠券</p>
          </div>
        </div>
        <div class="flex items-center gap-3">
          <div class="w-10 h-10 rounded-full bg-orange-500/10 flex items-center justify-center text-orange-600">
            <Sparkles class="w-5 h-5" />
          </div>
          <div>
            <h5 class="text-sm font-bold">新品研发室</h5>
            <p class="text-xs text-stone-500">每周上新 快乐不停歇</p>
          </div>
        </div>
      </div>

      <!-- Main Footer grid links -->
      <div class="max-w-[1280px] mx-auto px-4 sm:px-6 lg:px-8 py-12 grid grid-cols-1 sm:grid-cols-2 md:grid-cols-4 gap-8">
        <div>
          <div class="flex items-center gap-2 mb-4">
            <span class="w-8 h-8 rounded-lg bg-[#ff6b35] flex items-center justify-center text-white font-black text-lg">M</span>
            <span class="text-base font-bold font-display text-[#1b1c1a]">麦乐迪零食商场</span>
          </div>
          <p class="text-xs text-stone-500 leading-relaxed">
            极简、优雅的年轻化休闲食品平台。我们精选长白山野生松子、秘制碳烤牛肉干等极佳美味，用更真诚的态度为您烘焙每一颗自然的馈赠。
          </p>
        </div>

        <div>
          <h4 class="text-sm font-bold text-stone-800 mb-4">快速导航</h4>
          <ul class="space-y-2 text-xs">
            <li>
              <a @click="router.push('/')" class="text-stone-500 hover:text-[#ff6b35] transition-colors cursor-pointer">
                精选大厅
              </a>
            </li>
            <li>
              <a @click="router.push('/cart')" class="text-stone-500 hover:text-[#ff6b35] transition-colors cursor-pointer">
                购物车
              </a>
            </li>
            <li>
              <a @click="router.push('/profile')" class="text-stone-500 hover:text-[#ff6b35] transition-colors cursor-pointer">
                个人中心
              </a>
            </li>
          </ul>
        </div>

        <div>
          <h4 class="text-sm font-bold text-stone-800 mb-4">联系支持</h4>
          <p class="text-xs text-stone-500 mb-2">服务时间：09:00 - 22:00</p>
          <p class="text-xs text-stone-800 font-bold mb-1">专线：400-888-9999</p>
          <p class="text-xs text-stone-500">邮箱：support@melody-savory.com</p>
        </div>
      </div>

      <!-- Copyright bottom bar -->
      <div class="bg-[#e4e2de] py-6 text-center text-xs text-stone-600 border-t border-[#e4e2de]">
        <div class="max-w-[1280px] mx-auto px-4 flex flex-col sm:flex-row justify-between items-center gap-2">
          <p>© 2026 麦乐迪零食商场 (Melody Savory System) 版权所有。</p>
          <div class="flex gap-4">
            <a class="hover:text-[#ff6b35] transition-colors cursor-pointer">服务协议</a>
            <span>|</span>
            <a class="hover:text-[#ff6b35] transition-colors cursor-pointer">隐私保护</a>
          </div>
        </div>
      </div>
    </footer>
  </div>
</template>

<script setup>
import { computed } from "vue";

/**
 * 用户头像：
 * - 默认渲染用户昵称（或用户名/手机号）的首字
 * - 有合法 url 时仍可显示为图片（向后兼容）
 *
 * props:
 *   user    { id, nickname, username, phone, avatar }
 *   size    'xs' | 'sm' | 'md' | 'lg' | 'xl' 默认 'sm'
 *   rounded 是否圆形（默认 true）
 */
const props = defineProps({
  user: { type: Object, default: () => ({}) },
  size: { type: String, default: "sm" },
  rounded: { type: Boolean, default: true },
});

const sizeMap = {
  xs: "w-6 h-6 text-[10px]",
  sm: "w-7 h-7 text-xs",
  md: "w-9 h-9 text-sm",
  lg: "w-14 h-14 text-xl",
  xl: "w-18 h-18 text-2xl",
};
const cls = computed(() => sizeMap[props.size] || sizeMap.sm);

/** 计算"首字" */
const initial = computed(() => {
  if (!props.user) return "?";
  // 优先 nickname，其次 username，最后 phone
  const raw = (props.user.nickname || props.user.username || props.user.phone || "").toString().trim();
  if (!raw) return "?";
  const ch = raw.charAt(0);
  // 中文/数字/英文首字直接返回；其它字符回退到 '?'
  if (/[一-龥A-Za-z0-9]/.test(ch)) return ch.toUpperCase();
  return ch;
});

/** 头像颜色：根据用户 id 选固定色，循环一套 6 色 */
const palette = ["#ff6b35", "#004e64", "#5a4136", "#ffb55a", "#7d5a50", "#3b6e8f"];
const bg = computed(() => {
  const id = Number(props.user?.id || 0);
  return palette[id % palette.length];
});

/** 有图片地址就显示图片，否则显示首字 */
const hasImage = computed(() => {
  const a = props.user?.avatar;
  return typeof a === "string" && /^https?:\/\//.test(a);
});
</script>

<template>
  <span
    :class="[
      cls,
      rounded ? 'rounded-full' : 'rounded-xl',
      'inline-flex items-center justify-center font-black text-white select-none overflow-hidden border border-white/40 shrink-0'
    ]"
    :style="{ backgroundColor: hasImage ? 'transparent' : bg }"
    :title="(user && (user.nickname || user.username)) || ''"
  >
    <img v-if="hasImage" :src="user.avatar" :alt="user.nickname || user.username" class="w-full h-full object-cover" />
    <template v-else>{{ initial }}</template>
  </span>
</template>

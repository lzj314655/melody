<!--
  通用分页组件 — 前端 slice 方案

  用法:
    <Pagination
      :total="items.length"
      v-model:page="currentPage"
      :size="pageSize"
    />

    const pagedItems = computed(() =>
      items.value.slice((currentPage.value - 1) * pageSize, currentPage.value * pageSize)
    );
-->
<script setup>
import { computed } from "vue";
import { ChevronLeft, ChevronRight, ChevronsLeft, ChevronsRight } from "lucide-vue-next";

const props = defineProps({
  total: { type: Number, required: true },
  page: { type: Number, required: true },
  size: { type: Number, default: 10 },
  /** 显示几个页码按钮(单侧) */
  windowSize: { type: Number, default: 2 },
});
const emit = defineEmits(["update:page"]);

const totalPages = computed(() => Math.max(1, Math.ceil(props.total / props.size)));

const pages = computed(() => {
  const t = totalPages.value;
  const cur = props.page;
  const w = props.windowSize;
  const set = new Set([1, t, cur, cur - 1, cur + 1, cur - w, cur + w]);
  const arr = [...set].filter((p) => p >= 1 && p <= t).sort((a, b) => a - b);
  // 用 0 表示省略号
  const out = [];
  for (let i = 0; i < arr.length; i++) {
    if (i > 0 && arr[i] - arr[i - 1] > 1) out.push(0);
    out.push(arr[i]);
  }
  return out;
});

const go = (p) => {
  if (p < 1 || p > totalPages.value || p === props.page) return;
  emit("update:page", p);
};
</script>

<template>
  <div v-if="total > 0" class="flex items-center justify-between gap-3 px-4 py-3 text-xs">
    <div class="text-stone-500">
      共 <span class="font-bold text-stone-700">{{ total }}</span> 条
      <span v-if="totalPages > 1" class="ml-2 text-stone-400">/ {{ totalPages }} 页</span>
    </div>
    <div v-if="totalPages > 1" class="flex items-center gap-1">
      <button
        @click="go(1)"
        :disabled="page === 1"
        class="w-7 h-7 rounded-lg border border-[#e4e2de] flex items-center justify-center text-stone-500 hover:bg-stone-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        title="首页"
      >
        <ChevronsLeft class="w-3.5 h-3.5" />
      </button>
      <button
        @click="go(page - 1)"
        :disabled="page === 1"
        class="w-7 h-7 rounded-lg border border-[#e4e2de] flex items-center justify-center text-stone-500 hover:bg-stone-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
      >
        <ChevronLeft class="w-3.5 h-3.5" />
      </button>
      <template v-for="(p, i) in pages" :key="i">
        <span v-if="p === 0" class="w-7 h-7 flex items-center justify-center text-stone-400">…</span>
        <button
          v-else
          @click="go(p)"
          :class="`min-w-[28px] h-7 px-2 rounded-lg border text-xs font-bold transition-colors ${
            p === page
              ? 'bg-[#ff6b35] text-white border-[#ff6b35]'
              : 'border-[#e4e2de] text-stone-600 hover:bg-stone-100'
          }`"
        >
          {{ p }}
        </button>
      </template>
      <button
        @click="go(page + 1)"
        :disabled="page === totalPages"
        class="w-7 h-7 rounded-lg border border-[#e4e2de] flex items-center justify-center text-stone-500 hover:bg-stone-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
      >
        <ChevronRight class="w-3.5 h-3.5" />
      </button>
      <button
        @click="go(totalPages)"
        :disabled="page === totalPages"
        class="w-7 h-7 rounded-lg border border-[#e4e2de] flex items-center justify-center text-stone-500 hover:bg-stone-100 disabled:opacity-30 disabled:cursor-not-allowed transition-colors"
        title="末页"
      >
        <ChevronsRight class="w-3.5 h-3.5" />
      </button>
    </div>
  </div>
</template>
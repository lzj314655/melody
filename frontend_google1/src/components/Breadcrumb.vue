<script setup>
import { ChevronRight, Home } from "lucide-vue-next";
import { useRouter } from "vue-router";

defineProps({
  items: {
    type: Array,
    required: true
  }
});

const router = useRouter();
</script>

<template>
  <nav class="flex items-center gap-2 text-xs text-stone-500 font-mono py-2">
    <button @click="router.push('/')" class="flex items-center gap-1 hover:text-[#ff6b35] transition-colors cursor-pointer">
      <Home class="w-3.5 h-3.5" />
      <span>首页</span>
    </button>
    <template v-for="(item, index) in items" :key="index">
      <ChevronRight class="w-3 h-3 text-stone-400" />
      <span 
        v-if="index === items.length - 1" 
        class="text-stone-800 font-medium"
      >
        {{ item.label }}
      </span>
      <button 
        v-else 
        @click="item.path ? router.push(item.path) : null"
        class="hover:text-[#ff6b35] transition-colors cursor-pointer"
      >
        {{ item.label }}
      </button>
    </template>
  </nav>
</template>

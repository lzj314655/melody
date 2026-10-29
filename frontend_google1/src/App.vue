<script setup>
// Root layout setup
</script>

<template>
  <div class="min-h-screen bg-[#fbf9f5] w-full text-[#1b1c1a]">
    <router-view v-slot="{ Component }">
      <transition name="fade-page" mode="out-in">
        <component :is="Component" />
      </transition>
    </router-view>
  </div>
</template>

<style>
/* Page transition styles */
.fade-page-enter-active,
.fade-page-leave-active {
  transition: opacity 0.2s ease, transform 0.2s ease;
}

.fade-page-enter-from {
  opacity: 0;
  transform: translateY(4px);
}

.fade-page-leave-to {
  opacity: 0;
  transform: translateY(-4px);
}
</style>

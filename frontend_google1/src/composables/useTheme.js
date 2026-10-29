/**
 * 后台主题切换（深色/浅色），localStorage 持久化。
 * 用法：
 *   import { useAdminTheme } from "@/composables/useTheme";
 *   const { theme, isDark, toggle } = useAdminTheme();
 * AdminLayout 根节点根据 isDark 挂 .admin-theme-dark，CSS 覆盖层接管全部页面配色。
 */
import { computed, ref, watch } from "vue";

const KEY = "admin_theme";
const theme = ref(localStorage.getItem(KEY) === "dark" ? "dark" : "light");

watch(theme, (v) => {
  localStorage.setItem(KEY, v);
});

export function useAdminTheme() {
  const isDark = computed(() => theme.value === "dark");
  const toggle = () => {
    theme.value = theme.value === "dark" ? "light" : "dark";
  };
  return { theme, isDark, toggle };
}

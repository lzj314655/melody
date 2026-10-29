import { createApp } from "vue";
import { createPinia } from "pinia";
import router from "./router";
import App from "./App.vue";
import "./styles/global.css";

// 引入 axios http，统一 baseURL=http://localhost:8080/api
// 引入这个副作用是初始化请求/响应拦截器，并把 localStorage 里的 token 设到默认 header
import "@/api/http";

const pinia = createPinia();
const app = createApp(App);
app.use(pinia);
app.use(router);
app.mount("#root");

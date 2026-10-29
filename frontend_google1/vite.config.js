import tailwindcss from '@tailwindcss/vite';
import vue from '@vitejs/plugin-vue';
import path from 'path';
import { defineConfig } from 'vite';

// 行为埋点 Mock：内存里记录所有上报，F-R-001 验证用
const behaviorMock = () => ({
  name: 'behavior-mock',
  configureServer(server) {
    const behaviors = [];
    server.middlewares.use('/api/behavior', (req, res, next) => {
      const p = req.url.split('?')[0];
      if (req.method === 'POST' && p === '/report') {
        let body = '';
        req.on('data', chunk => (body += chunk));
        req.on('end', () => {
          try {
            const data = body ? JSON.parse(body) : {};
            behaviors.push({ ...data, receivedAt: new Date().toISOString() });
            if (behaviors.length > 500) behaviors.shift();
            res.statusCode = 204;
            res.end();
          } catch (e) {
            res.statusCode = 400;
            res.end(JSON.stringify({ error: 'bad json' }));
          }
        });
        return;
      }
      if (req.method === 'GET' && p === '/list') {
        res.setHeader('Content-Type', 'application/json');
        res.end(JSON.stringify(behaviors));
        return;
      }
      if (req.method === 'DELETE' && p === '/list') {
        behaviors.length = 0;
        res.statusCode = 204;
        res.end();
        return;
      }
      next();
    });
  },
});

export default defineConfig(() => {
  return {
    plugins: [vue(), tailwindcss(), behaviorMock()],
    resolve: {
      alias: {
        '@': path.resolve(__dirname, './src'),
      },
    },
    server: {
      // HMR is disabled in AI Studio via DISABLE_HMR env var.
      hmr: process.env.DISABLE_HMR !== 'true',
      watch: process.env.DISABLE_HMR === 'true' ? null : {},
      // 代理本地商品图片到 Spring Boot (D:\bishe\image)
      // 前端拿到的 /img/<id>/<file> 会被转发到 8080
      proxy: {
        '/img': {
          target: 'http://localhost:8080',
          changeOrigin: false,
        },
      },
    },
  };
});

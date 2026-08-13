import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  server: {
    host: '127.0.0.1',
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://127.0.0.1:8090',
        changeOrigin: true,
        // 文档对比等大文件上传需更长超时
        timeout: 600_000,
        proxyTimeout: 600_000,
      },
    },
  },
});

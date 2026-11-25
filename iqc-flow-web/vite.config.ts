import { fileURLToPath } from 'node:url';
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  // Sistema de diseño compartido con el portal de consultas (../iqc-flow-diseno)
  resolve: { alias: { '@iqc/diseno': fileURLToPath(new URL('../iqc-flow-diseno', import.meta.url)) } },
  server: {
    port: 5173,
    fs: { allow: ['..'] },
    proxy: {
      // Desarrollo: la API se ve como del mismo origen. Se quita `Origin` porque la API solo admite los orígenes
      // de producción (IQC_ORIGENES_PANEL) y rechazaría con 403 el de localhost.
      '/api': {
        target: 'http://localhost:8080',
        configure: proxy => proxy.on('proxyReq', solicitud => solicitud.removeHeader('origin')),
      },
    },
  },
});

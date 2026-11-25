import { writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, loadEnv, type Plugin } from 'vite';
import react from '@vitejs/plugin-react';

/** .htaccess para Apache (BanaHosting) con la URL real de la API: las rutas impresas en PDF y QR siguen funcionando */
function htaccess(api: string, portal: string): Plugin {
  let salida = 'dist';
  return {
    name: 'iqc-htaccess',
    configResolved(c) { salida = c.build.outDir; },
    closeBundle() {
      const base = api.replace(/\/+$/, '');
      // Origen de la API (vacío si va por el mismo dominio): la CSP solo le permite conectarse y enmarcar ahí
      const origen = base ? new URL(base).origin : '';
      const csp = [
        "default-src 'self'", "script-src 'self'", "style-src 'self'", "img-src 'self' data:", "font-src 'self'",
        `connect-src 'self' ${origen}`.trim(),
        // PDF embebido: lo entrega la API o, en certificados importados, el almacén anterior de Google Cloud
        `frame-src 'self' ${origen} https://storage.googleapis.com`.replace('  ', ' '),
        "object-src 'none'", "base-uri 'self'", "form-action 'self'", "frame-ancestors 'none'", 'upgrade-insecure-requests',
      ].join('; ');
      writeFileSync(resolve(salida, '.htaccess'), [
        `# Generado al compilar el portal ${portal}. Las URL impresas en certificados y QR deben seguir funcionando.`,
        'RewriteEngine On',
        '# Siempre por HTTPS',
        'RewriteCond %{HTTPS} !=on',
        'RewriteRule ^ https://%{HTTP_HOST}%{REQUEST_URI} [R=301,L]',
        '# PDF de certificados personales ya impresos: los entrega la API, que comprueba que sigan emitidos',
        `RewriteRule ^personal-pdf/(.+)$ ${base}/api/v1/publico/consultas/${portal}/personal-pdf/$1 [R=302,L]`,
        '# Cualquier otra ruta que no sea un archivo es un QR (/<identificador>): la resuelve la aplicación',
        'RewriteCond %{REQUEST_FILENAME} !-f',
        'RewriteCond %{REQUEST_FILENAME} !-d',
        'RewriteRule ^ index.html [L]',
        '<IfModule mod_headers.c>',
        '  Header always set X-Content-Type-Options "nosniff"',
        '  Header always set Referrer-Policy "strict-origin-when-cross-origin"',
        `  Header always set Content-Security-Policy "${csp}"`,
        '  Header always set X-Frame-Options "DENY"',
        '  Header always set Permissions-Policy "camera=(), microphone=(), geolocation=(), payment=(), usb=()"',
        '  Header always set Strict-Transport-Security "max-age=31536000"',
        '  Header always set Cross-Origin-Opener-Policy "same-origin"',
        '</IfModule>',
        '',
      ].join('\n'));
    },
  };
}

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [react(), htaccess(env.VITE_API_URL ?? '', env.VITE_PORTAL === 'LATAM' ? 'LATAM' : 'PE')],
    // Sistema de diseño compartido con el panel (../iqc-flow-diseno)
    resolve: { alias: { '@iqc/diseno': fileURLToPath(new URL('../iqc-flow-diseno', import.meta.url)) } },
    server: { port: 5174, proxy: { '/api': 'http://localhost:8080' }, fs: { allow: ['..'] } },
  };
});

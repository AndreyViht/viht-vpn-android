import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const directory = path.dirname(fileURLToPath(import.meta.url));
export default defineConfig({
  plugins: [react()], base: './',
  build: { outDir: path.resolve(directory, '../app/src/main/assets/web'), emptyOutDir: true, target: 'es2020' },
});

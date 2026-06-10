import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { resolve } from 'node:path'

export default defineConfig({
  plugins: [vue()],
  build: {
    rollupOptions: {
      input: {
        index: resolve(__dirname, 'index.html'),
        demo: resolve(__dirname, 'demo/index.html'),
        admin: resolve(__dirname, 'admin/index.html'),
      },
      output: {
        manualChunks(id) {
          if (id.includes('node_modules/echarts') || id.includes('node_modules/vue-echarts')) {
            return 'charts'
          }
          if (id.includes('node_modules/vue') || id.includes('node_modules/pinia')) {
            return 'vue'
          }
          return undefined
        },
      },
    },
  },
})

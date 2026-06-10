import { createApp } from 'vue'
import { createPinia } from 'pinia'
import DemoApp from './DemoApp.vue'
import './styles.css'

createApp(DemoApp)
  .use(createPinia())
  .mount('#app')

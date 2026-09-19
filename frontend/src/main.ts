import { createApp } from 'vue'
import { createPinia } from 'pinia'
import App from './App.vue'
import router from './router'

import './assets/styles/tokens.css'
import './assets/styles/base.css'
import './assets/styles/utilities.css'

createApp(App).use(createPinia()).use(router).mount('#app')

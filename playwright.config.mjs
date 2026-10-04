import { defineConfig,devices } from '@playwright/test';
export default defineConfig({
  testDir:'./web/tests',timeout:45_000,fullyParallel:false,workers:1,
  use:{serviceWorkers:'block',baseURL:process.env.WEB_BASE_URL||'http://127.0.0.1:4173',trace:'retain-on-failure',screenshot:'only-on-failure'},
  projects:[{name:'webkit-iphone',use:{...devices['iPhone 13'],browserName:'webkit'}},{name:'webkit-ipad',use:{...devices['iPad Pro 11'],browserName:'webkit'}},{name:'chromium-android',use:{...devices['Pixel 7'],browserName:'chromium',launchOptions:{args:['--use-gl=angle','--use-angle=swiftshader','--enable-unsafe-swiftshader']}}}],
  webServer:process.env.WEB_BASE_URL?undefined:{command:'npm run build:web && npm run dev:web',url:'http://127.0.0.1:4173',reuseExistingServer:!process.env.CI},
  reporter:[['list'],['html',{open:'never'}]]
});

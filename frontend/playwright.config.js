import { defineConfig } from '@playwright/test'
export default defineConfig({
 testDir:'./tests',timeout:60000,workers:1,
 reporter:[['list'],['json',{outputFile:'test-results/results.json'}]],
 use:{baseURL:process.env.UI_TEST_URL||'http://127.0.0.1:15173',channel:'chrome',headless:true,viewport:{width:390,height:844},isMobile:true,hasTouch:true,screenshot:'only-on-failure',trace:'retain-on-failure'}
})

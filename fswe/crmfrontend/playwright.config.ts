import {defineConfig} from "@playwright/test";
export default defineConfig({testDir:"./e2e",fullyParallel:false,workers:1,timeout:30000,use:{baseURL:`http://127.0.0.1:${process.env.CRM_E2E_FRONTEND_PORT||5174}`,headless:true,channel:process.env.PLAYWRIGHT_CHANNEL||"msedge",screenshot:"only-on-failure",trace:"retain-on-failure"},reporter:[["list"],["html",{open:"never"}]]});

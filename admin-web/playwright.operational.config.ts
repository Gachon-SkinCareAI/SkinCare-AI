import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests-operational',
  workers: 1,
  use: {
    baseURL: 'http://127.0.0.1:5182',
    launchOptions: { executablePath: process.env.CHROMIUM_PATH || undefined },
    trace: 'retain-on-failure',
  },
})

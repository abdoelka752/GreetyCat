import { defineConfig } from '@playwright/test';

const port = 18765;
export default defineConfig({
  testDir: './tests',
  workers: 1,
  fullyParallel: false,
  retries: 0,
  reporter: 'list',
  outputDir: 'test-results',
  use: { baseURL: `http://127.0.0.1:${port}`, headless: true, viewport: { width: 1440, height: 1100 }, trace: 'off', screenshot: 'off' },
  webServer: {
    command: `"../.venv/Scripts/python.exe" -m greedycat.server --port ${port}`,
    cwd: '../backend',
    url: `http://127.0.0.1:${port}/api/v1/health`,
    reuseExistingServer: false,
    timeout: 20000,
    stdout: 'ignore', stderr: 'pipe',
  },
});

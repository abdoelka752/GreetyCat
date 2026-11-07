import { expect, test } from '@playwright/test';
import type { Discovery } from '../src/api/client';

// Explicit synthetic responses for failure/recovery UI tests only.
const device = { device_id: '00000000-0000-4000-8000-000000000001', state: 'AVAILABLE', authorized: true, screen_available: 'UNKNOWN', game_visible: 'UNKNOWN', capture_active: 'UNKNOWN', analyzer_active: 'UNKNOWN' };
const response = (changes = {}) => ({ schema_version: '1.0', status: 'OK', devices: [], observed_at: '2026-01-01T12:00:00.000Z', duration_ms: 12, ...changes });

test('actual local API flows into the dashboard and manual refresh', async ({ page, request }) => {
  const errors: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  const discoveryResponse = page.waitForResponse('**/api/v1/devices');
  await page.goto('/');
  const snapshot = await (await discoveryResponse).json() as Discovery;
  await expect(page.getByRole('heading', { name: 'System overview' })).toBeVisible();
  await expect(page.getByText('Reachable', { exact: true })).toBeVisible();
  if (snapshot.status === 'OK') {
    await expect(page.getByText(`${snapshot.devices.filter((item) => item.state === 'AVAILABLE').length} available at last scan`)).toBeVisible();
  } else {
    await expect(page.getByRole('alert')).toBeVisible();
  }
  await expect(page.getByRole('button', { name: /Android device/ })).toHaveCount(snapshot.devices.length);
  await expect(page.getByRole('button', { name: 'Refresh diagnostics' })).toBeEnabled();
  await expect(page.getByRole('checkbox', { name: /Auto refresh/ })).not.toBeChecked();
  await page.getByRole('button', { name: 'Devices', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Device workspace' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Connect your Android phone' })).toBeVisible();
  const refreshed = page.waitForResponse('**/api/v1/devices');
  await page.getByRole('button', { name: 'Refresh diagnostics' }).click();
  await refreshed;
  await expect(page.getByRole('button', { name: 'Refresh diagnostics' })).toBeEnabled();
  const root = await request.get('/');
  expect(root.headers()['content-security-policy']).toContain("connect-src 'self'");
  expect(root.headers()['access-control-allow-origin']).toBeUndefined();
  expect(errors).toEqual([]);
  await page.getByRole('button', { name: 'Overview', exact: true }).click();
  await page.screenshot({ path: '../artifacts/T004/desktop.png', fullPage: true });
});

test('loading, empty list and recovery use actual refresh controls', async ({ page }) => {
  let finish: (() => void) | undefined;
  const waiting = new Promise<void>((resolve) => { finish = resolve; });
  await page.route('**/api/v1/devices', async (route) => { await waiting; await route.fulfill({ json: response() }); });
  await page.goto('/');
  await expect(page.getByRole('status')).toContainText('Checking device transports');
  await expect(page.getByRole('button', { name: 'Refreshing…' })).toBeDisabled();
  await expect(page.getByText('Reachable', { exact: true })).toBeVisible();
  finish!();
  await expect(page.getByText('No devices found', { exact: true })).toBeVisible();
  await page.unroute('**/api/v1/devices');
  await page.route('**/api/v1/devices', (route) => route.fulfill({ json: response({ devices: [device] }) }));
  await page.getByRole('button', { name: 'Refresh diagnostics' }).click();
  await expect(page.getByRole('button', { name: /Android device 1/ })).toBeVisible();
  await expect(page.getByText('No devices found', { exact: true })).toHaveCount(0);
});

test('every diagnostic failure has an explicit actionable state', async ({ page }) => {
  for (const [status, message] of Object.entries({ ADB_MISSING: 'ADB is not installed or not found', ADB_ERROR: 'Device discovery could not run', TIMEOUT: 'Device discovery timed out', OUTPUT_LIMIT: 'Device response exceeded the limit', INVALID_OUTPUT: 'Device response could not be read', BUSY: 'Another device scan is running' })) {
    await page.route('**/api/v1/devices', (route) => route.fulfill({ json: response({ status }) }));
    await page.goto('/');
    await expect(page.getByRole('alert')).toContainText(message);
    await expect(page.getByText('No devices found', { exact: true })).toHaveCount(0);
    await expect(page.getByText('Reachable', { exact: true })).toBeVisible();
    await page.unroute('**/api/v1/devices');
  }
});

test('device selection distinguishes unauthorized, offline and unknown transports', async ({ page }) => {
  await page.route('**/api/v1/devices', (route) => route.fulfill({ json: response({ devices: [
    device,
    { ...device, device_id: '00000000-0000-4000-8000-000000000002', state: 'UNAUTHORIZED', authorized: false },
    { ...device, device_id: '00000000-0000-4000-8000-000000000003', state: 'OFFLINE', authorized: null },
    { ...device, device_id: '00000000-0000-4000-8000-000000000004', state: 'UNKNOWN', authorized: null },
  ] }) }));
  await page.goto('/');
  await page.getByRole('button', { name: /Android device 2/ }).click();
  await expect(page.getByText('Review the USB debugging prompt', { exact: false })).toBeVisible();
  await expect(page.getByRole('definition').filter({ hasText: 'Required' })).toBeVisible();
  await page.getByRole('button', { name: /Android device 3/ }).click();
  await expect(page.getByRole('button', { name: /Android device 3/ })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: /Android device 4/ }).click();
  await expect(page.getByRole('button', { name: /Android device 4/ })).toHaveAttribute('aria-pressed', 'true');
  await expect(page.getByText('Unknown', { exact: true }).first()).toBeVisible();
});

test('backend disconnect, bad payload and HTTP errors recover without stale success', async ({ page }) => {
  await page.route('**/api/v1/health', (route) => route.abort());
  await page.route('**/api/v1/devices', (route) => route.abort());
  await page.goto('/');
  await expect(page.getByText('Unreachable', { exact: true })).toBeVisible();
  await expect(page.getByText('Device diagnostics unavailable', { exact: true })).toBeVisible();
  await page.unroute('**/api/v1/health');
  await page.unroute('**/api/v1/devices');
  await page.route('**/api/v1/devices', (route) => route.fulfill({ json: { schema_version: '9.0' } }));
  await page.getByRole('button', { name: 'Refresh diagnostics' }).click();
  await expect(page.getByText('Reachable', { exact: true })).toBeVisible();
  await expect(page.getByRole('alert')).toContainText('incompatible response');
  await page.unroute('**/api/v1/devices');
  await page.route('**/api/v1/devices', (route) => route.fulfill({ status: 403, json: { code: 'ORIGIN_DENIED' } }));
  await page.getByRole('button', { name: 'Refresh diagnostics' }).click();
  await expect(page.getByRole('alert')).toContainText('Local access was refused');
});

test('polling is opt-in, bounded and pauses while the document is hidden', async ({ page }) => {
  let requests = 0;
  await page.route('**/api/v1/devices', (route) => { requests++; return route.fulfill({ json: response() }); });
  await page.clock.install();
  await page.goto('/');
  await expect(page.getByText('No devices found', { exact: true })).toBeVisible();
  expect(requests).toBe(1);
  await page.clock.fastForward(16000);
  expect(requests).toBe(1);
  await page.getByRole('checkbox', { name: /Auto refresh/ }).check();
  await page.clock.fastForward(15001);
  await expect.poll(() => requests).toBe(2);
  await expect(page.getByRole('button', { name: 'Refresh diagnostics' })).toBeEnabled();
  await page.evaluate(() => Object.defineProperty(document, 'visibilityState', { configurable: true, get: () => 'hidden' }));
  await page.clock.fastForward(30000);
  expect(requests).toBe(2);
  await page.getByRole('checkbox', { name: /Auto refresh/ }).uncheck();
  await page.clock.fastForward(30000);
  expect(requests).toBe(2);
});

test('phone layout stays within viewport and exposes keyboard-operable navigation', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Refresh diagnostics' })).toBeEnabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('button', { name: 'Devices', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Device workspace' })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: '../artifacts/T004/mobile.png', fullPage: true });
});

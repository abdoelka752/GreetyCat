import { afterEach, describe, expect, it, vi } from 'vitest';
import { getHealth, parseDiscovery, parseHealth } from './client';

const health = { schema_version: '1.0', service: 'greedycat-local', status: 'OK', api_version: 'v1', gameplay_contract_version: '1.0', access_mode: 'LOOPBACK_ONLY', pairing: 'NOT_IMPLEMENTED' };
const discovery = { schema_version: '1.0', status: 'OK', devices: [], observed_at: '2026-01-01T12:00:00.000Z', duration_ms: 12 };
const device = { device_id: '00000000-0000-4000-8000-000000000001', state: 'AVAILABLE', authorized: true, screen_available: 'UNKNOWN', game_visible: 'UNKNOWN', capture_active: 'UNKNOWN', analyzer_active: 'UNKNOWN' };

afterEach(() => { vi.unstubAllGlobals(); vi.useRealTimers(); });

describe('diagnostic response boundary', () => {
  it('accepts the actual v1 health shape and rejects incompatible claims', () => {
    expect(parseHealth(health).status).toBe('OK');
    for (const value of [null, { ...health, schema_version: '2.0' }, { ...health, pairing: 'PAIRED' }, { ...health, extra: 'private' }]) expect(() => parseHealth(value)).toThrow('incompatible');
  });
  it('accepts an empty list and validates selected-device capabilities', () => {
    expect(parseDiscovery(discovery).devices).toEqual([]);
    expect(parseDiscovery({ ...discovery, devices: [device] }).devices[0].authorized).toBe(true);
  });
  it('rejects duplicate identities, contradictory authorization and unsupported capture claims', () => {
    for (const devices of [[device, device], [{ ...device, authorized: false }], [{ ...device, capture_active: 'ACTIVE' }], [{ ...device, device_id: 'raw-serial' }]]) expect(() => parseDiscovery({ ...discovery, devices })).toThrow('incompatible');
  });
  it('rejects impossible dates, nonfinite durations and error payloads that pretend to contain devices', () => {
    for (const changes of [{ observed_at: '2026-02-30T12:00:00.000Z' }, { duration_ms: Infinity }, { duration_ms: -1 }, { status: 'ERROR' }, { status: 'TIMEOUT', devices: [device] }, { schema_version: '2.0' }]) expect(() => parseDiscovery({ ...discovery, ...changes })).toThrow('incompatible');
  });
  it('distinguishes unavailable diagnostics from a successful empty list', () => {
    for (const status of ['ADB_MISSING', 'ADB_ERROR', 'TIMEOUT', 'OUTPUT_LIMIT', 'INVALID_OUTPUT', 'BUSY']) expect(parseDiscovery({ ...discovery, status }).status).toBe(status);
  });
});

describe('HTTP error and cancellation behavior', () => {
  it('uses same-origin diagnostics with no cache, cookies or redirects', async () => {
    const fetcher = vi.fn().mockResolvedValue(new Response(JSON.stringify(health), { headers: { 'Content-Type': 'application/json' } }));
    vi.stubGlobal('fetch', fetcher);
    await expect(getHealth(new AbortController().signal)).resolves.toEqual(health);
    expect(fetcher.mock.calls[0][0]).toBe('/api/v1/health');
    expect(fetcher.mock.calls[0][1]).toMatchObject({ cache: 'no-store', credentials: 'omit', redirect: 'error' });
  });
  it('rejects HTML and incompatible JSON without showing response content', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('<private-content>', { headers: { 'Content-Type': 'text/html' } })));
    await expect(getHealth(new AbortController().signal)).rejects.toThrow('did not return diagnostic data');
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('{', { headers: { 'Content-Type': 'application/json' } })));
    await expect(getHealth(new AbortController().signal)).rejects.toThrow('unreadable diagnostic data');
  });
  it('reports denied access and network failures with actionable messages', async () => {
    vi.stubGlobal('fetch', vi.fn().mockResolvedValue(new Response('private error', { status: 403 })));
    await expect(getHealth(new AbortController().signal)).rejects.toThrow('Local access was refused');
    vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('private network details')));
    await expect(getHealth(new AbortController().signal)).rejects.toThrow('Cannot reach the local server');
  });
  it('bounds a hanging request and clears its timer', async () => {
    vi.useFakeTimers();
    vi.stubGlobal('fetch', vi.fn((_path, options) => new Promise((_resolve, reject) => options.signal.addEventListener('abort', () => reject(new Error('aborted'))))));
    const pending = expect(getHealth(new AbortController().signal)).rejects.toThrow('too long');
    await vi.advanceTimersByTimeAsync(8000);
    await pending;
    expect(vi.getTimerCount()).toBe(0);
  });
});

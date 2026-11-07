export type Health = {
  schema_version: '1.0'; service: 'greedycat-local'; status: 'OK'; api_version: 'v1';
  gameplay_contract_version: '1.0'; access_mode: 'LOOPBACK_ONLY'; pairing: 'NOT_IMPLEMENTED';
};
export type Device = {
  device_id: string; state: 'AVAILABLE' | 'UNAUTHORIZED' | 'OFFLINE' | 'UNKNOWN';
  authorized: boolean | null;
  screen_available: 'UNKNOWN'; game_visible: 'UNKNOWN'; capture_active: 'UNKNOWN'; analyzer_active: 'UNKNOWN';
};
export type Discovery = {
  schema_version: '1.0';
  status: 'OK' | 'ADB_MISSING' | 'ADB_ERROR' | 'TIMEOUT' | 'OUTPUT_LIMIT' | 'INVALID_OUTPUT' | 'BUSY';
  devices: Device[]; observed_at: string; duration_ms: number;
};
export type DiagnosticState<T> =
  | { status: 'loading' }
  | { status: 'success'; data: T }
  | { status: 'error'; message: string };

export class ApiError extends Error {}

function object(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}
function exact(value: Record<string, unknown>, keys: string[]) {
  return Object.keys(value).length === keys.length && keys.every((key) => key in value);
}
const uuid = /^[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}$/;

export function parseHealth(value: unknown): Health {
  if (!object(value) || !exact(value, ['schema_version', 'service', 'status', 'api_version', 'gameplay_contract_version', 'access_mode', 'pairing']) ||
    value.schema_version !== '1.0' || value.service !== 'greedycat-local' || value.status !== 'OK' || value.api_version !== 'v1' ||
    value.gameplay_contract_version !== '1.0' || value.access_mode !== 'LOOPBACK_ONLY' || value.pairing !== 'NOT_IMPLEMENTED') {
    throw new ApiError('The server response is incompatible. Update the dashboard and server together.');
  }
  return value as Health;
}

export function parseDiscovery(value: unknown): Discovery {
  const invalid = () => { throw new ApiError('Device diagnostics returned an incompatible response. Refresh after updating the server.'); };
  if (!object(value) || !exact(value, ['schema_version', 'status', 'devices', 'observed_at', 'duration_ms']) || value.schema_version !== '1.0' ||
    !['OK', 'ADB_MISSING', 'ADB_ERROR', 'TIMEOUT', 'OUTPUT_LIMIT', 'INVALID_OUTPUT', 'BUSY'].includes(String(value.status)) ||
    !Array.isArray(value.devices) || value.devices.length > 64 || typeof value.observed_at !== 'string' ||
    !/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}\.\d{3}Z$/.test(value.observed_at) || !Number.isFinite(Date.parse(value.observed_at)) ||
    new Date(value.observed_at).toISOString() !== value.observed_at ||
    typeof value.duration_ms !== 'number' || !Number.isFinite(value.duration_ms) || value.duration_ms < 0) return invalid();
  const seen = new Set<string>();
  for (const item of value.devices) {
    if (!object(item) || !exact(item, ['device_id', 'state', 'authorized', 'screen_available', 'game_visible', 'capture_active', 'analyzer_active']) ||
      typeof item.device_id !== 'string' || !uuid.test(item.device_id) || seen.has(item.device_id) ||
      !['AVAILABLE', 'UNAUTHORIZED', 'OFFLINE', 'UNKNOWN'].includes(String(item.state)) ||
      item.authorized !== (item.state === 'AVAILABLE' ? true : item.state === 'UNAUTHORIZED' ? false : null) ||
      [item.screen_available, item.game_visible, item.capture_active, item.analyzer_active].some((state) => state !== 'UNKNOWN')) return invalid();
    seen.add(item.device_id);
  }
  if (value.status !== 'OK' && value.devices.length !== 0) return invalid();
  return value as Discovery;
}

async function request<T>(path: string, parse: (value: unknown) => T, signal: AbortSignal): Promise<T> {
  const timeout = new AbortController();
  const timer = setTimeout(() => timeout.abort(), 8000);
  try {
    const response = await fetch(path, {
      signal: AbortSignal.any([signal, timeout.signal]), cache: 'no-store', credentials: 'omit', redirect: 'error',
      headers: { Accept: 'application/json' },
    });
    if (!response.ok) {
      throw new ApiError(response.status === 403
        ? 'Local access was refused. Open this dashboard from the local server address.'
        : `The local server returned HTTP ${response.status}. Try refreshing.`);
    }
    if (!response.headers.get('content-type')?.includes('application/json')) throw new ApiError('The local server did not return diagnostic data.');
    return parse(await response.json());
  } catch (error) {
    if (signal.aborted) throw error;
    if (timeout.signal.aborted) throw new ApiError('The local server took too long to respond. Try refreshing.');
    if (error instanceof ApiError) throw error;
    if (error instanceof SyntaxError) throw new ApiError('The server returned unreadable diagnostic data. Try refreshing.');
    throw new ApiError('Cannot reach the local server. Start it on this computer, then refresh.');
  } finally {
    clearTimeout(timer);
  }
}

export const getHealth = (signal: AbortSignal) => request('/api/v1/health', parseHealth, signal);
export const getDevices = (signal: AbortSignal) => request('/api/v1/devices', parseDiscovery, signal);

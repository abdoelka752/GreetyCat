import { useState } from 'react';
import type { Device, Discovery, DiagnosticState } from './api/client';
import { Icon } from './components/Icon';
import { useDiagnostics } from './useDiagnostics';

const stateNames = { AVAILABLE: 'Available', UNAUTHORIZED: 'Unauthorized', OFFLINE: 'Offline', UNKNOWN: 'Unknown' };
const messages: Record<Exclude<Discovery['status'], 'OK'>, [string, string]> = {
  ADB_MISSING: ['ADB is not installed or not found', 'Install Android SDK Platform Tools and add ADB to PATH, then refresh.'],
  ADB_ERROR: ['Device discovery could not run', 'Check that ADB can run on this computer, then refresh.'],
  TIMEOUT: ['Device discovery timed out', 'Check your USB connection and try again.'],
  OUTPUT_LIMIT: ['Device response exceeded the limit', 'ADB returned more diagnostic output than expected. Check your local ADB setup.'],
  INVALID_OUTPUT: ['Device response could not be read', 'ADB returned an unexpected response. Check your local ADB setup, then refresh.'],
  BUSY: ['Another device scan is running', 'Wait a moment, then refresh to get a new snapshot.'],
};

function Badge({ children, tone = 'muted' }: { children: React.ReactNode; tone?: 'good' | 'warn' | 'muted' }) {
  return <span className={`badge ${tone}`}><span className="dot"/>{children}</span>;
}

function DevicePanel({ state, selected, onSelect }: { state: DiagnosticState<Discovery>; selected: Device | null; onSelect: (device: Device) => void }) {
  if (state.status === 'loading') return <div className="blank compact" role="status"><span className="scan-line"/><strong>Checking device transports…</strong><p>Reading the local ADB device list.</p></div>;
  if (state.status === 'error') return <div className="blank compact error" role="alert"><Icon name="signal"/><strong>Device diagnostics unavailable</strong><p>{state.message}</p></div>;
  if (state.data.status !== 'OK') {
    const [title, message] = messages[state.data.status];
    return <div className="blank compact error" role="alert"><Icon name="phone"/><strong>{title}</strong><p>{message}</p></div>;
  }
  if (state.data.devices.length === 0) return <div className="blank compact"><span className="empty-icon"><Icon name="phone" size={25}/></span><strong>No devices found</strong><p>Connect an Android phone with a data-capable USB cable.<br/>Enable USB debugging when you’re ready to observe.</p></div>;
  return <div className="device-list" aria-label="Discovered devices">
    <div className="device-labels"><span>DEVICE</span><span>ADB TRANSPORT</span><span className="device-arrow"/></div>
    {state.data.devices.map((device, index) => <button key={device.device_id} className={`device-row ${selected?.device_id === device.device_id ? 'selected' : ''}`} onClick={() => onSelect(device)} aria-pressed={selected?.device_id === device.device_id}>
      <span className="device-name"><span className="phone-chip"><Icon name="phone"/></span><span><strong>Android device {index + 1}</strong><small>Session ID · {device.device_id.slice(0, 8)}</small></span></span>
      <Badge tone={device.state === 'AVAILABLE' ? 'good' : device.state === 'UNKNOWN' ? 'muted' : 'warn'}>{stateNames[device.state]}</Badge>
      <span className="device-arrow"><Icon name="arrow" size={17}/></span>
    </button>)}
  </div>;
}

export default function App() {
  const [view, setView] = useState<'overview' | 'devices'>('overview');
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const { health, devices, refreshing, autoRefresh, setAutoRefresh, checkedAt, refresh } = useDiagnostics();
  const discovered = devices.status === 'success' && devices.data.status === 'OK' ? devices.data.devices : [];
  const selected = discovered.find((device) => device.device_id === selectedId) ?? discovered[0] ?? null;
  const available = discovered.filter((device) => device.state === 'AVAILABLE').length;
  const healthy = health.status === 'success';
  const deviceSnapshot = devices.status === 'success' ? devices.data : null;

  return <div className="app-shell">
    <a href="#main" className="skip-link">Skip to content</a>
    <aside className="sidebar" aria-label="Workspace navigation">
      <div className="brand"><span className="brand-mark">G<span>c</span></span><span className="brand-name">Greedy Cat<small>INTELLIGENCE</small></span></div>
      <div className="nav-label">WORKSPACE</div>
      <nav>
        <button aria-label="Overview" aria-current={view === 'overview' ? 'page' : undefined} onClick={() => setView('overview')}><Icon name="grid"/><span>Overview</span></button>
        <button aria-label="Devices" aria-current={view === 'devices' ? 'page' : undefined} onClick={() => setView('devices')}><Icon name="phone"/><span>Devices</span><span className="nav-count">{devices.status === 'success' && devices.data.status === 'OK' ? discovered.length : '—'}</span></button>
      </nav>
      <div className="sidebar-note"><Icon name="shield" size={18}/><strong>Your research stays local.</strong><p>No cloud account.<br/>No automatic wagers.</p></div>
      <div className="workspace-tag"><span className="dot"/><span>LOCAL WORKSPACE<small>Research foundation · v0.4</small></span></div>
    </aside>

    <div className="workspace">
      <header className="topbar"><span className="breadcrumb">Workspace <span>/</span> <strong>{view === 'overview' ? 'Overview' : 'Devices'}</strong></span><span className="local-label"><Icon name="shield" size={14}/>Local only</span></header>
      <main id="main" tabIndex={-1}>
        <div className="page-heading"><div><div className="eyebrow">GREEDY CAT RESEARCH</div><h1>{view === 'overview' ? 'System overview' : 'Device workspace'}<span className="heading-dot">.</span></h1><p>{view === 'overview' ? 'Know what’s connected. Keep the evidence clear.' : 'Inspect the latest device snapshot from this computer.'}</p></div><button className="primary-button" onClick={() => void refresh()} disabled={refreshing}><span className={refreshing ? 'spinning' : ''}><Icon name="refresh" size={16}/></span>{refreshing ? 'Refreshing…' : 'Refresh diagnostics'}</button></div>

        <div className="snapshot-strip"><span><Icon name="clock" size={14}/>{checkedAt ? `Last checked ${checkedAt.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}` : 'Waiting for first check'}</span><label className="auto-toggle"><input type="checkbox" checked={autoRefresh} onChange={(event) => setAutoRefresh(event.target.checked)}/><span className="toggle-track"/>Auto refresh · 15s</label></div>

        <div className="metric-grid">
          <section className="metric"><div className="metric-label">LOCAL SERVER<Icon name="server" size={17}/></div><strong className={healthy ? 'text-good' : ''}>{health.status === 'loading' ? 'Checking' : healthy ? 'Reachable' : 'Unreachable'}</strong><span>{healthy ? 'At the last health check' : health.status === 'loading' ? 'Request in progress' : 'Refresh after starting the server'}</span></section>
          <section className="metric"><div className="metric-label">ADB TRANSPORTS<Icon name="phone" size={17}/></div><strong>{devices.status === 'success' && devices.data.status === 'OK' ? <>{discovered.length}<small>detected</small></> : '—'}</strong><span>{devices.status === 'success' && devices.data.status === 'OK' ? `${available} available at last scan` : devices.status === 'loading' ? 'Scanning this computer' : 'Diagnostics unavailable'}</span></section>
          <section className="metric"><div className="metric-label">GAME VISIBILITY<Icon name="eye" size={17}/></div><strong className="soft-value">Unknown</strong><span>No game observation yet</span></section>
          <section className="metric"><div className="metric-label">PREDICTIVE EDGE<Icon name="signal" size={17}/></div><strong className="soft-value">Unproven</strong><span>No evaluated model available</span></section>
        </div>

        {health.status === 'error' && <div className="health-error" role="alert"><Icon name="server" size={18}/><span><strong>Local server unavailable.</strong> {health.message}</span></div>}

        <div className={`content-grid ${view === 'devices' ? 'devices-view' : ''}`}>
          <div className="main-column">
            {view === 'overview' && <section className="panel observation-panel"><div className="panel-heading"><h2>Observation workspace</h2><Badge>Not observing</Badge></div><div className="observation-empty"><div className="observation-symbol"><Icon name="eye" size={34}/><span/></div><div><span className="section-kicker">AWAITING VERIFIED OBSERVATIONS</span><h3>Evidence comes first.</h3><p>Round history and probability estimates will appear when verified gameplay data is available.</p><span className="quiet-note">Capture support is still being built. No screen is being recorded by this workspace.</span></div></div><div className="observation-footer"><span><span className="hollow-dot"/>Current round <b>Unknown</b></span><span>Active model <b>Unavailable</b></span></div></section>}

            <section className="panel"><div className="panel-heading"><div className="panel-title"><h2>Device discovery</h2><span>Read-only ADB diagnostics</span></div><Badge tone={deviceSnapshot?.status === 'OK' ? 'good' : 'muted'}>{devices.status === 'loading' ? 'Scanning' : deviceSnapshot?.status === 'OK' ? 'Scan complete' : 'Unavailable'}</Badge></div>
              <DevicePanel state={devices} selected={selected} onSelect={(device) => setSelectedId(device.device_id)}/>
              <div className="panel-footer"><Icon name="shield" size={14}/><span>Device discovery does not grant capture permission.</span>{deviceSnapshot && <span className="query-time">{Math.round(deviceSnapshot.duration_ms)} ms scan</span>}</div>
            </section>

            {view === 'devices' && <section className="panel connection-guide"><div className="panel-heading"><h2>Connect your Android phone</h2><span className="section-kicker">USB WORKFLOW</span></div><ol><li><span>01</span><div><strong>Connect with a USB data cable</strong><p>Use a cable that supports data, not just charging.</p></div></li><li><span>02</span><div><strong>Enable USB debugging</strong><p>Find USB debugging in your phone’s Developer options.</p></div></li><li><span>03</span><div><strong>Review the authorization prompt</strong><p>Approve only if you choose to trust this computer, then refresh diagnostics.</p></div></li></ol></section>}
          </div>

          <div className="side-column">
            <section className="panel capability-panel"><div className="panel-heading"><h2>{selected ? 'Selected device' : 'Observation readiness'}</h2><Icon name="phone" size={17}/></div>{selected && <div className="selected-summary"><strong>Android device {discovered.indexOf(selected) + 1}</strong><span className="mono">Session · {selected.device_id.slice(0, 8)}</span></div>}<dl className="readiness-list"><div><dt>ADB transport</dt><dd className={selected?.state === 'AVAILABLE' ? 'text-good' : ''}>{selected ? stateNames[selected.state] : 'Not selected'}</dd></div><div><dt>Debug authorization</dt><dd>{selected?.authorized === true ? 'Authorized' : selected?.authorized === false ? 'Required' : 'Unknown'}</dd></div><div><dt>Screen available</dt><dd>Unknown</dd></div><div><dt>Game visible</dt><dd>Unknown</dd></div><div><dt>Capture active</dt><dd>Unknown</dd></div><div><dt>Analyzer active</dt><dd>Unknown</dd></div></dl><p className="panel-note">An available ADB transport does not confirm that the game is open or a screen is being shared.</p>{selected?.state === 'UNAUTHORIZED' && <p className="authorization-note">Review the USB debugging prompt on your phone if you choose to authorize this computer.</p>}</section>

            <section className="research-note"><span className="note-icon"><Icon name="shield" size={20}/></span><h2>No proven predictive edge</h2><p>Outcomes may be unpredictable. Model performance must be measured on verified, unseen rounds.</p><div><span className="hollow-dot"/>No prediction is being generated.</div></section>

            <div className="service-meta"><span>Data source<strong>Local diagnostics API</strong></span><span>Refresh mode<strong>{autoRefresh ? '15s while visible' : 'Manual snapshots'}</strong></span><span>Phone pairing<strong>Not available yet</strong></span></div>
          </div>
        </div>
        <footer className="page-footer"><span>Built around evidence. Designed for uncertainty.</span><span>Greedy Cat Intelligence <b>LOCAL / 0.4</b></span></footer>
      </main>
    </div>
  </div>;
}

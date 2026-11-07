import { useCallback, useEffect, useRef, useState } from 'react';
import { getDevices, getHealth } from './api/client';
import type { DiagnosticState, Discovery, Health } from './api/client';

export function useDiagnostics() {
  const [health, setHealth] = useState<DiagnosticState<Health>>({ status: 'loading' });
  const [devices, setDevices] = useState<DiagnosticState<Discovery>>({ status: 'loading' });
  const [refreshing, setRefreshing] = useState(true);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [checkedAt, setCheckedAt] = useState<Date | null>(null);
  const active = useRef<AbortController | null>(null);

  const refresh = useCallback(async () => {
    if (active.current) return;
    const controller = new AbortController();
    active.current = controller;
    setRefreshing(true);
    setHealth({ status: 'loading' });
    setDevices({ status: 'loading' });
    const run = async <T,>(fetcher: (signal: AbortSignal) => Promise<T>, update: (value: DiagnosticState<T>) => void) => {
      try {
        const data = await fetcher(controller.signal);
        if (!controller.signal.aborted) update({ status: 'success', data });
      } catch (error) {
        if (!controller.signal.aborted) update({ status: 'error', message: error instanceof Error ? error.message : 'Diagnostics are unavailable.' });
      }
    };
    await Promise.allSettled([run(getHealth, setHealth), run(getDevices, setDevices)]);
    if (active.current === controller && !controller.signal.aborted) {
      active.current = null;
      setRefreshing(false);
      setCheckedAt(new Date());
    }
  }, []);

  useEffect(() => {
    void refresh();
    return () => { active.current?.abort(); active.current = null; };
  }, [refresh]);

  useEffect(() => {
    if (!autoRefresh) return;
    const timer = setInterval(() => {
      if (document.visibilityState === 'visible') void refresh();
    }, 15000);
    return () => clearInterval(timer);
  }, [autoRefresh, refresh]);

  return { health, devices, refreshing, autoRefresh, setAutoRefresh, checkedAt, refresh };
}

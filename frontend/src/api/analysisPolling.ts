import { getSkinAnalysis } from './skinAnalysisApi';
import type { AnalysisStatus, SkinAnalysis } from '../types/skinAnalysis';

const POLL_INTERVAL_MS = 1_500;
const POLL_TIMEOUT_MS = 180_000;

export class AnalysisPollingTimeoutError extends Error {
  constructor() {
    super('The analysis is taking longer than expected.');
    this.name = 'AnalysisPollingTimeoutError';
  }
}

export class SkinAnalysisFailedError extends Error {
  readonly analysis: SkinAnalysis;

  constructor(analysis: SkinAnalysis) {
    super(analysis.errorMessage || 'The skin analysis could not be completed.');
    this.name = 'SkinAnalysisFailedError';
    this.analysis = analysis;
  }
}

interface PollOptions {
  signal?: AbortSignal;
  intervalMs?: number;
  timeoutMs?: number;
  onStatus?: (status: AnalysisStatus) => void;
}

export async function waitForCompletedAnalysis(
  analysisId: string,
  { signal, intervalMs = POLL_INTERVAL_MS, timeoutMs = POLL_TIMEOUT_MS, onStatus }: PollOptions = {},
): Promise<SkinAnalysis> {
  const deadline = Date.now() + timeoutMs;
  while (true) {
    if (signal?.aborted) throw new DOMException('The request was cancelled.', 'AbortError');
    const analysis = await getSkinAnalysis(analysisId, signal);
    onStatus?.(analysis.status);
    if (analysis.status === 'COMPLETED') return analysis;
    if (analysis.status === 'FAILED') throw new SkinAnalysisFailedError(analysis);
    if (analysis.status !== 'CREATED' && analysis.status !== 'PROCESSING') {
      throw new Error('The backend returned an unsupported analysis status.');
    }

    const remaining = deadline - Date.now();
    if (remaining <= 0) throw new AnalysisPollingTimeoutError();
    await delay(Math.min(intervalMs, remaining), signal);
  }
}

function delay(milliseconds: number, signal?: AbortSignal): Promise<void> {
  return new Promise((resolve, reject) => {
    if (signal?.aborted) {
      reject(new DOMException('The request was cancelled.', 'AbortError'));
      return;
    }
    const timer = window.setTimeout(() => {
      signal?.removeEventListener('abort', abort);
      resolve();
    }, milliseconds);
    const abort = () => {
      window.clearTimeout(timer);
      signal?.removeEventListener('abort', abort);
      reject(new DOMException('The request was cancelled.', 'AbortError'));
    };
    signal?.addEventListener('abort', abort, { once: true });
  });
}

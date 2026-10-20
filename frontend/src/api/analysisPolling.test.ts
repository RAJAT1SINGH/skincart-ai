import { beforeEach, describe, expect, it, vi } from 'vitest';
import { getSkinAnalysis } from './skinAnalysisApi';
import { AnalysisPollingTimeoutError, SkinAnalysisFailedError, waitForCompletedAnalysis } from './analysisPolling';
import type { SkinAnalysis } from '../types/skinAnalysis';

vi.mock('./skinAnalysisApi', () => ({ getSkinAnalysis: vi.fn() }));

const analysis = (status: SkinAnalysis['status']): SkinAnalysis => ({
  analysisId: 'analysis-1', createdAt: '2026-01-01T00:00:00Z', status,
  overallScore: null, concerns: [], errorCode: status === 'FAILED' ? 'PROVIDER_ERROR' : null,
  errorMessage: status === 'FAILED' ? 'The analysis could not be completed.' : null,
});

describe('waitForCompletedAnalysis', () => {
  beforeEach(() => vi.mocked(getSkinAnalysis).mockReset());

  it('reports backend statuses and returns after PROCESSING becomes COMPLETED', async () => {
    vi.mocked(getSkinAnalysis).mockResolvedValueOnce(analysis('PROCESSING')).mockResolvedValueOnce(analysis('COMPLETED'));
    const onStatus = vi.fn();
    const resultPromise = waitForCompletedAnalysis('analysis-1', { intervalMs: 1, onStatus });
    await vi.waitFor(() => expect(getSkinAnalysis).toHaveBeenCalledTimes(1));
    await vi.waitFor(() => expect(getSkinAnalysis).toHaveBeenCalledTimes(2));
    await expect(resultPromise).resolves.toMatchObject({ status: 'COMPLETED' });
    expect(onStatus.mock.calls).toEqual([['PROCESSING'], ['COMPLETED']]);
  });

  it('surfaces a backend FAILED status', async () => {
    vi.mocked(getSkinAnalysis).mockResolvedValue(analysis('FAILED'));
    await expect(waitForCompletedAnalysis('analysis-1')).rejects.toBeInstanceOf(SkinAnalysisFailedError);
  });

  it('stops when the local polling deadline is exhausted', async () => {
    vi.mocked(getSkinAnalysis).mockResolvedValue(analysis('CREATED'));
    await expect(waitForCompletedAnalysis('analysis-1', { timeoutMs: 0 })).rejects.toBeInstanceOf(AnalysisPollingTimeoutError);
  });
});

import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import App from './App';
import { createSkinAnalysis, createSkinSimulation, getRecommendations, getRoutine } from './api/skinAnalysisApi';
import { SkinAnalysisFailedError, waitForCompletedAnalysis } from './api/analysisPolling';
import type { SkinAnalysis } from './types/skinAnalysis';

vi.mock('./api/skinAnalysisApi', () => ({
  createSkinAnalysis: vi.fn(), createSkinSimulation: vi.fn(), getRecommendations: vi.fn(), getRoutine: vi.fn(), userFacingError: (_error: unknown, fallback: string) => fallback,
}));
vi.mock('./api/analysisPolling', () => ({
  AnalysisPollingTimeoutError: class extends Error {},
  SkinAnalysisFailedError: class extends Error { constructor(readonly analysis: SkinAnalysis) { super(analysis.errorMessage ?? 'failed'); } },
  waitForCompletedAnalysis: vi.fn(),
}));

const completed: SkinAnalysis = {
  analysisId: 'analysis-1', createdAt: '2026-01-01T00:00:00Z', status: 'COMPLETED', overallScore: null,
  concerns: [{ code: 'acne', displayName: 'Acne', severity: 'ignored', uiScore: 33, rawScore: 0.33, maskUrls: [] }],
  errorCode: null, errorMessage: null,
};

function pendingUntilAborted(signal?: AbortSignal): Promise<never> {
  return new Promise((_resolve, reject) => {
    if (!signal) return;
    signal.addEventListener('abort', () => {
      const error = new Error('request canceled');
      error.name = 'CanceledError';
      reject(error);
    }, { once: true });
  });
}

describe('App flow', () => {
  beforeEach(() => {
    vi.mocked(URL.createObjectURL).mockReset().mockReturnValue('blob:skincart-preview');
    vi.mocked(URL.revokeObjectURL).mockReset();
    vi.mocked(createSkinAnalysis).mockReset().mockResolvedValue({ analysisId: 'analysis-1', status: 'COMPLETED' });
    vi.mocked(createSkinSimulation).mockReset().mockResolvedValue({
      analysisId: 'analysis-1', concernCode: 'acne', focusLabel: 'Acne appearance',
      resultImageUrl: '/mock-skin-simulation-after.svg', mock: true,
    });
    vi.mocked(waitForCompletedAnalysis).mockReset().mockResolvedValue(completed);
    vi.mocked(getRecommendations).mockReset().mockResolvedValue({ analysisId: 'analysis-1', recommendations: [] });
    vi.mocked(getRoutine).mockReset().mockResolvedValue({ analysisId: 'analysis-1', morning: [], evening: [] });
  });

  it('completes analysis, loads results, and lets the user start again', async () => {
    const user = userEvent.setup();
    render(<App />);
    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    expect(await screen.findByRole('heading', { name: 'Your skin analysis' })).toBeInTheDocument();
    expect(createSkinAnalysis).toHaveBeenCalledOnce();
    expect(waitForCompletedAnalysis).toHaveBeenCalledWith('analysis-1', expect.objectContaining({ signal: expect.any(AbortSignal), onStatus: expect.any(Function) }));
    expect(getRecommendations).toHaveBeenCalledOnce();
    expect(getRoutine).toHaveBeenCalledOnce();
    await user.click(screen.getAllByRole('button', { name: /analyze another photo/i })[0]);
    expect(await screen.findByRole('heading', { name: /Find your skin’s kind of care/i })).toBeInTheDocument();
  });

  it('shows analysis failure and retries with the selected photo', async () => {
    const user = userEvent.setup();
    vi.mocked(waitForCompletedAnalysis).mockRejectedValueOnce(new SkinAnalysisFailedError({ ...completed, status: 'FAILED', errorMessage: 'The analysis could not be completed.' }));
    render(<App />);
    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    expect(await screen.findByRole('heading', { name: 'Your analysis couldn’t be completed' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Try this photo again' }));
    expect(await screen.findByRole('heading', { name: 'Your skin analysis' })).toBeInTheDocument();
    expect(createSkinAnalysis).toHaveBeenCalledTimes(2);
  });

  it('does not request a simulation on page load and shows the result only after an explicit click', async () => {
    const user = userEvent.setup();
    let resolveSimulation!: (value: {
      analysisId: string; concernCode: string; focusLabel: string; resultImageUrl: string; mock: boolean;
    }) => void;
    vi.mocked(createSkinSimulation).mockReturnValueOnce(new Promise((resolve) => { resolveSimulation = resolve; }));
    render(<App />);
    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    await screen.findByRole('heading', { name: 'Your skin analysis' });
    expect(createSkinSimulation).not.toHaveBeenCalled();

    await user.click(screen.getByRole('button', { name: /see visual simulation/i }));
    expect(createSkinSimulation).toHaveBeenCalledWith('analysis-1', 'acne', expect.any(AbortSignal));
    expect(screen.getByRole('status', { name: /Creating your visual simulation/ })).toHaveTextContent('Creating your visual simulation');
    resolveSimulation({
      analysisId: 'analysis-1', concernCode: 'acne', focusLabel: 'Acne appearance',
      resultImageUrl: '/mock-skin-simulation-after.svg', mock: true,
    });
    expect(await screen.findByRole('img', { name: 'Illustrative demo simulation' })).toHaveAttribute('src', '/mock-skin-simulation-after.svg');
    expect(screen.getByText('Mock mode uses fixed demo images and did not process your photo.')).toBeInTheDocument();
  });

  it('retains the uploaded preview through analysis and simulation, then clears it on Start Again', async () => {
    const user = userEvent.setup();
    vi.mocked(URL.createObjectURL).mockReset().mockReturnValue('blob:uploaded-selfie');
    vi.mocked(createSkinSimulation).mockResolvedValue({
      analysisId: 'analysis-1', concernCode: 'acne', focusLabel: 'Acne appearance',
      resultImageUrl: 'https://images.example.test/simulation.jpg', mock: false,
    });
    render(<App />);

    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    expect(screen.getByRole('img', { name: 'Selected selfie preview' })).toHaveAttribute('src', 'blob:uploaded-selfie');
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    await screen.findByRole('heading', { name: 'Your skin analysis' });
    await user.click(screen.getByRole('button', { name: /see visual simulation/i }));

    expect(await screen.findByRole('img', { name: 'Original uploaded selfie' })).toHaveAttribute('src', 'blob:uploaded-selfie');
    expect(screen.getByRole('img', { name: 'AI-generated visual simulation' })).toHaveAttribute('src', 'https://images.example.test/simulation.jpg');

    await user.click(screen.getAllByRole('button', { name: /analyze another photo/i })[0]);

    expect(await screen.findByRole('heading', { name: /Find your skin’s kind of care/i })).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Selected selfie preview' })).not.toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'Original uploaded selfie' })).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:uploaded-selfie');
  });

  it('returns to the home screen when the user cancels an upload with start again', async () => {
    const user = userEvent.setup();
    vi.mocked(createSkinAnalysis).mockImplementationOnce((_file, signal) => new Promise((_resolve, reject) => {
      signal?.addEventListener('abort', () => {
        const error = new Error('request canceled');
        error.name = 'CanceledError';
        reject(error);
      }, { once: true });
    }));
    render(<App />);
    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    expect(await screen.findByRole('heading', { name: 'Sending your photo…' })).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Start again' }));
    expect(await screen.findByRole('heading', { name: /Find your skin’s kind of care/i })).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('aborts active requests and releases the preview when the app unmounts', async () => {
    const user = userEvent.setup();
    let uploadSignal: AbortSignal | undefined;
    vi.mocked(createSkinAnalysis).mockImplementationOnce((_file, signal) => {
      uploadSignal = signal;
      return pendingUntilAborted(signal);
    });
    const { unmount } = render(<App />);

    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    await screen.findByRole('heading', { name: 'Sending your photo…' });

    unmount();

    expect(uploadSignal?.aborted).toBe(true);
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:skincart-preview');
  });

  it('aborts outstanding result requests and resets the flow when starting again', async () => {
    const user = userEvent.setup();
    let recommendationSignal: AbortSignal | undefined;
    let routineSignal: AbortSignal | undefined;
    vi.mocked(getRecommendations).mockImplementationOnce((_analysisId, signal) => {
      recommendationSignal = signal;
      return pendingUntilAborted(signal);
    });
    vi.mocked(getRoutine).mockImplementationOnce((_analysisId, signal) => {
      routineSignal = signal;
      return pendingUntilAborted(signal);
    });
    vi.mocked(createSkinSimulation).mockImplementationOnce((_analysisId, _concernCode, signal) => new Promise((_resolve, reject) => {
      signal?.addEventListener('abort', () => {
        const error = new Error('request canceled');
        error.name = 'CanceledError';
        reject(error);
      }, { once: true });
    }));
    render(<App />);
    await user.upload(screen.getByTestId('image-input'), new File(['photo'], 'selfie.png', { type: 'image/png' }));
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    await screen.findByRole('heading', { name: 'Your skin analysis' });
    await user.click(screen.getByRole('button', { name: /see visual simulation/i }));
    expect(screen.getByRole('status', { name: /Creating your visual simulation/ })).toHaveTextContent('Creating your visual simulation');
    const simulationSignal = vi.mocked(createSkinSimulation).mock.calls[0]?.[2];

    await user.click(screen.getAllByRole('button', { name: /analyze another photo/i })[0]);

    expect(await screen.findByRole('heading', { name: /Find your skin’s kind of care/i })).toBeInTheDocument();
    expect(recommendationSignal?.aborted).toBe(true);
    expect(routineSignal?.aborted).toBe(true);
    expect(simulationSignal?.aborted).toBe(true);
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
  });
});

import { useCallback, useEffect, useRef, useState, type Dispatch, type SetStateAction } from 'react';
import AnalysisPage from './pages/AnalysisPage';
import HomePage from './pages/HomePage';
import ResultsPage from './pages/ResultsPage';
import { createSkinAnalysis, createSkinSimulation, getRecommendations, getRoutine, userFacingError } from './api/skinAnalysisApi';
import { AnalysisPollingTimeoutError, SkinAnalysisFailedError, waitForCompletedAnalysis } from './api/analysisPolling';
import type { RecommendationResponse } from './types/recommendation';
import type { RoutineResponse } from './types/routine';
import type { AnalysisStatus, SkinAnalysis } from './types/skinAnalysis';
import type { SkinSimulationResponse } from './types/skinSimulation';

type Loadable<T> = { status: 'loading' } | { status: 'loaded'; data: T } | { status: 'error'; message: string };
type SimulationLoadable = { status: 'idle' } | { status: 'loading'; concernCode: string } | { status: 'loaded'; data: SkinSimulationResponse } | { status: 'error'; message: string; concernCode: string };
type ScreenState =
  | { kind: 'home' }
  | { kind: 'analysis'; file: File; previewUrl: string; phase: 'uploading' | 'polling' | 'error'; analysisId?: string; status?: AnalysisStatus; errorTitle?: string; errorMessage?: string; retry?: 'upload' | 'poll'; retryLabel?: string }
  | { kind: 'results'; analysis: SkinAnalysis; previewUrl: string; recommendations: Loadable<RecommendationResponse>; routine: Loadable<RoutineResponse>; simulation: SimulationLoadable };

export default function App() {
  const [screen, setScreen] = useState<ScreenState>({ kind: 'home' });
  const controllerRef = useRef<AbortController | null>(null);
  const simulationRequestedRef = useRef(false);
  const previewUrlRef = useRef<string | null>(null);

  useEffect(() => () => {
    controllerRef.current?.abort();
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
  }, []);

  const startAgain = useCallback(() => {
    controllerRef.current?.abort();
    controllerRef.current = null;
    simulationRequestedRef.current = false;
    if (previewUrlRef.current) URL.revokeObjectURL(previewUrlRef.current);
    previewUrlRef.current = null;
    setScreen({ kind: 'home' });
  }, []);

  const beginAnalysis = useCallback((file: File, previewUrl: string) => {
    controllerRef.current?.abort();
    if (previewUrlRef.current && previewUrlRef.current !== previewUrl) {
      URL.revokeObjectURL(previewUrlRef.current);
    }
    previewUrlRef.current = previewUrl;
    const controller = new AbortController();
    controllerRef.current = controller;
    simulationRequestedRef.current = false;
    setScreen({ kind: 'analysis', file, previewUrl, phase: 'uploading' });
    void uploadAndPoll(file, previewUrl, controller, setScreen);
  }, []);

  const retryPolling = useCallback((analysisId: string, file: File, previewUrl: string) => {
    controllerRef.current?.abort();
    const controller = new AbortController();
    controllerRef.current = controller;
    setScreen({ kind: 'analysis', file, previewUrl, phase: 'polling', analysisId, status: 'PROCESSING' });
    void pollAndLoad(analysisId, file, previewUrl, controller, setScreen);
  }, []);

  const loadRecommendations = useCallback(async (analysisId: string) => {
    setScreen((current) => current.kind === 'results' ? { ...current, recommendations: { status: 'loading' } } : current);
    try {
      const data = await getRecommendations(analysisId, controllerRef.current?.signal);
      setScreen((current) => current.kind === 'results' ? { ...current, recommendations: { status: 'loaded', data } } : current);
    } catch (error) {
      if (isAbortError(error)) return;
      setScreen((current) => current.kind === 'results' ? { ...current, recommendations: { status: 'error', message: userFacingError(error, 'We couldn’t load product matches. Please try again.') } } : current);
    }
  }, []);

  const loadRoutine = useCallback(async (analysisId: string) => {
    setScreen((current) => current.kind === 'results' ? { ...current, routine: { status: 'loading' } } : current);
    try {
      const data = await getRoutine(analysisId, controllerRef.current?.signal);
      setScreen((current) => current.kind === 'results' ? { ...current, routine: { status: 'loaded', data } } : current);
    } catch (error) {
      if (isAbortError(error)) return;
      setScreen((current) => current.kind === 'results' ? { ...current, routine: { status: 'error', message: userFacingError(error, 'We couldn’t load your routine. Please try again.') } } : current);
    }
  }, []);

  const loadSimulation = useCallback(async (analysisId: string, concernCode: string) => {
    if (simulationRequestedRef.current) return;
    simulationRequestedRef.current = true;
    setScreen((current) => current.kind === 'results'
      ? { ...current, simulation: { status: 'loading', concernCode } }
      : current);
    try {
      const data = await createSkinSimulation(analysisId, concernCode, controllerRef.current?.signal);
      setScreen((current) => current.kind === 'results'
        ? { ...current, simulation: { status: 'loaded', data } }
        : current);
    } catch (error) {
      if (isAbortError(error)) return;
      setScreen((current) => current.kind === 'results'
        ? { ...current, simulation: { status: 'error', concernCode, message: userFacingError(error, 'We couldn’t create a visual simulation. Please try again.') } }
        : current);
    } finally {
      simulationRequestedRef.current = false;
    }
  }, []);

  if (screen.kind === 'home') return <HomePage onAnalyze={beginAnalysis} />;
  if (screen.kind === 'analysis') {
    const onRetry = screen.retry === 'upload'
      ? () => beginAnalysis(screen.file, screen.previewUrl)
      : screen.retry === 'poll' && screen.analysisId
        ? () => retryPolling(screen.analysisId!, screen.file, screen.previewUrl)
        : undefined;
    return <AnalysisPage errorMessage={screen.errorMessage} errorTitle={screen.errorTitle} fileName={screen.file.name} onRetry={onRetry} onStartAgain={startAgain} phase={screen.phase} retryLabel={screen.retryLabel} status={screen.status} />;
  }
  return <ResultsPage analysis={screen.analysis} originalImageUrl={screen.previewUrl} onRetryRecommendations={() => void loadRecommendations(screen.analysis.analysisId)} onRetryRoutine={() => void loadRoutine(screen.analysis.analysisId)} onSimulate={(concernCode) => void loadSimulation(screen.analysis.analysisId, concernCode)} onStartAgain={startAgain} recommendations={screen.recommendations} routine={screen.routine} simulation={screen.simulation} />;
}

async function uploadAndPoll(file: File, previewUrl: string, controller: AbortController, setScreen: Dispatch<SetStateAction<ScreenState>>) {
  try {
    const created = await createSkinAnalysis(file, controller.signal);
    if (controller.signal.aborted) return;
    setScreen({ kind: 'analysis', file, previewUrl, phase: 'polling', analysisId: created.analysisId, status: created.status });
    await pollAndLoad(created.analysisId, file, previewUrl, controller, setScreen);
  } catch (error) {
    if (controller.signal.aborted || isAbortError(error)) return;
    setScreen({ kind: 'analysis', file, previewUrl, phase: 'error', errorTitle: 'We couldn’t upload your photo', errorMessage: userFacingError(error, 'We couldn’t send your photo for analysis. Check your connection and try again.'), retry: 'upload', retryLabel: 'Retry upload' });
  }
}

async function pollAndLoad(analysisId: string, file: File, previewUrl: string, controller: AbortController, setScreen: Dispatch<SetStateAction<ScreenState>>) {
  try {
    const analysis = await waitForCompletedAnalysis(analysisId, {
      signal: controller.signal,
      onStatus: (status) => setScreen((current) => current.kind === 'analysis' ? { ...current, phase: 'polling', analysisId, status } : current),
    });
    if (controller.signal.aborted) return;
    setScreen({ kind: 'results', analysis, previewUrl, recommendations: { status: 'loading' }, routine: { status: 'loading' }, simulation: { status: 'idle' } });
    const [recommendations, routine] = await Promise.allSettled([getRecommendations(analysisId, controller.signal), getRoutine(analysisId, controller.signal)]);
    if (controller.signal.aborted) return;
    setScreen({ kind: 'results', analysis, previewUrl, recommendations: resultToLoadable(recommendations, 'We couldn’t load product matches. Please try again.'), routine: resultToLoadable(routine, 'We couldn’t load your routine. Please try again.'), simulation: { status: 'idle' } });
  } catch (error) {
    if (controller.signal.aborted || isAbortError(error)) return;
    const failure = error instanceof SkinAnalysisFailedError;
    const timeout = error instanceof AnalysisPollingTimeoutError;
    setScreen({
      kind: 'analysis', file, previewUrl, phase: 'error', analysisId,
      errorTitle: failure ? 'Your analysis couldn’t be completed' : timeout ? 'Your analysis is taking longer than expected' : 'We lost connection to your analysis',
      errorMessage: failure ? (error.analysis.errorMessage?.trim().slice(0, 240) || 'The skin analysis could not be completed. Please try another photo.') : timeout ? 'The analysis is still running. You can check its status again in a moment.' : userFacingError(error, 'We couldn’t check the analysis status. Check your connection and try again.'),
      retry: failure ? 'upload' : 'poll', retryLabel: failure ? 'Try this photo again' : 'Check again',
    });
  }
}

function resultToLoadable<T>(result: PromiseSettledResult<T>, fallback: string): Loadable<T> {
  return result.status === 'fulfilled' ? { status: 'loaded', data: result.value } : { status: 'error', message: userFacingError(result.reason, fallback) };
}

function isAbortError(error: unknown): boolean {
  if (error instanceof DOMException && error.name === 'AbortError') return true;
  return typeof error === 'object' && error !== null && 'name' in error
    && (error.name === 'AbortError' || error.name === 'CanceledError');
}

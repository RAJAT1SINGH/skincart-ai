import axios from 'axios';
import type { ApiErrorResponse, SkinAnalysis, SkinAnalysisCreatedResponse } from '../types/skinAnalysis';
import type { RecommendationResponse } from '../types/recommendation';
import type { RoutineResponse } from '../types/routine';
import type { SkinSimulationResponse } from '../types/skinSimulation';

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL?.trim() || 'http://localhost:8080',
  timeout: 300_000,
  headers: { Accept: 'application/json' },
});

export async function createSkinAnalysis(
  image: File,
  signal?: AbortSignal,
): Promise<SkinAnalysisCreatedResponse> {
  const formData = new FormData();
  formData.append('image', image);
  const response = await api.post<SkinAnalysisCreatedResponse>('/api/v1/skin-analysis', formData, { signal });
  return response.data;
}

export async function getSkinAnalysis(analysisId: string, signal?: AbortSignal): Promise<SkinAnalysis> {
  const response = await api.get<SkinAnalysis>(`/api/v1/skin-analysis/${analysisId}`, { signal });
  return response.data;
}

export async function getRecommendations(
  analysisId: string,
  signal?: AbortSignal,
): Promise<RecommendationResponse> {
  const response = await api.get<RecommendationResponse>(
    `/api/v1/skin-analysis/${analysisId}/recommendations`,
    { signal },
  );
  return response.data;
}

export async function getRoutine(analysisId: string, signal?: AbortSignal): Promise<RoutineResponse> {
  const response = await api.get<RoutineResponse>(`/api/v1/skin-analysis/${analysisId}/routine`, { signal });
  return response.data;
}

export async function createSkinSimulation(
  analysisId: string,
  concernCode: string,
  signal?: AbortSignal,
): Promise<SkinSimulationResponse> {
  const response = await api.post<SkinSimulationResponse>(
    `/api/v1/skin-analysis/${analysisId}/simulation`,
    { concernCode },
    { signal },
  );
  return response.data;
}

export function userFacingError(error: unknown, fallback: string): string {
  if (axios.isAxiosError<ApiErrorResponse>(error)) {
    const message = error.response?.data?.message;
    if (typeof message === 'string' && message.trim()) return message.trim().slice(0, 240);
    if (!error.response) return 'We could not reach SkinCart AI. Check your connection and try again.';
  }
  return fallback;
}

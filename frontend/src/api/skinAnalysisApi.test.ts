import { beforeEach, describe, expect, it, vi } from 'vitest';

const axiosHarness = vi.hoisted(() => ({ client: { post: vi.fn(), get: vi.fn() } }));
vi.mock('axios', () => ({ default: { create: () => axiosHarness.client, isAxiosError: () => false } }));

import { createSkinAnalysis, createSkinSimulation, getRecommendations, getRoutine, getSkinAnalysis } from './skinAnalysisApi';

describe('skinAnalysisApi', () => {
  beforeEach(() => {
    axiosHarness.client.post.mockReset();
    axiosHarness.client.get.mockReset();
  });

  it('posts the selected image as the backend multipart image field', async () => {
    axiosHarness.client.post.mockResolvedValue({ data: { analysisId: 'analysis-1', status: 'COMPLETED' } });
    const image = new File(['photo'], 'selfie.jpg', { type: 'image/jpeg' });

    await expect(createSkinAnalysis(image)).resolves.toEqual({ analysisId: 'analysis-1', status: 'COMPLETED' });
    const [path, body] = axiosHarness.client.post.mock.calls[0];
    expect(path).toBe('/api/v1/skin-analysis');
    expect(body).toBeInstanceOf(FormData);
    expect((body as FormData).get('image')).toBe(image);
  });

  it('uses the three documented result endpoints', async () => {
    axiosHarness.client.get.mockResolvedValue({ data: {} });
    await getSkinAnalysis('analysis-2');
    await getRecommendations('analysis-2');
    await getRoutine('analysis-2');
    expect(axiosHarness.client.get.mock.calls.map(([path]) => path)).toEqual([
      '/api/v1/skin-analysis/analysis-2',
      '/api/v1/skin-analysis/analysis-2/recommendations',
      '/api/v1/skin-analysis/analysis-2/routine',
    ]);
  });

  it('posts the selected SkinCart concern to the backend simulation endpoint', async () => {
    const simulation = {
      analysisId: 'analysis-3', concernCode: 'pore', focusLabel: 'Pore appearance',
      resultImageUrl: 'https://images.example.test/result.jpg', mock: false,
    };
    axiosHarness.client.post.mockResolvedValue({ data: simulation });

    await expect(createSkinSimulation('analysis-3', 'pore')).resolves.toEqual(simulation);
    expect(axiosHarness.client.post).toHaveBeenCalledWith(
      '/api/v1/skin-analysis/analysis-3/simulation',
      { concernCode: 'pore' },
      { signal: undefined },
    );
  });
});

import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import ResultsPage from './ResultsPage';
import type { RecommendationResponse } from '../types/recommendation';
import type { RoutineResponse } from '../types/routine';
import type { SkinAnalysis } from '../types/skinAnalysis';

const analysis: SkinAnalysis = {
  analysisId: 'analysis-1', createdAt: '2026-01-01T00:00:00Z', status: 'COMPLETED', overallScore: null,
  concerns: [{ code: 'pore', displayName: 'Pore appearance', severity: 'ignored', uiScore: 41, rawScore: 0.41, maskUrls: [] }],
  errorCode: null, errorMessage: null,
};
const recommendations: RecommendationResponse = {
  analysisId: 'analysis-1', recommendations: [{
    productId: 'product-1', productName: 'Demo Gentle Cleanser', brand: 'SkinCart Studio', category: 'CLEANSER',
    description: 'A demo cleanser.', score: 82.5, matchedConcerns: ['PORE'], matchedIngredients: ['Niacinamide'],
    reason: 'Matched to the analyzed concern.',
  }],
};
const routine: RoutineResponse = {
  analysisId: 'analysis-1',
  morning: [{ step: 'CLEANSER', productId: 'product-1', productName: 'Demo Gentle Cleanser', category: 'CLEANSER', reason: 'Selected from matched products.' }],
  evening: [],
};
const callbacks = { onRetryRecommendations: vi.fn(), onRetryRoutine: vi.fn(), onSimulate: vi.fn(), onStartAgain: vi.fn() };
const props = {
  analysis,
  originalImageUrl: 'blob:uploaded-selfie',
  recommendations: { status: 'loaded' as const, data: recommendations },
  routine: { status: 'loaded' as const, data: routine },
  simulation: { status: 'idle' as const },
  ...callbacks,
};

describe('ResultsPage', () => {
  beforeEach(() => vi.clearAllMocks());

  it('renders only the concern display name and score plus recommendation fields', () => {
    render(<ResultsPage {...props} />);
    expect(screen.getAllByText('Pore appearance')).toHaveLength(2);
    expect(screen.getByText('41')).toBeInTheDocument();
    expect(screen.getByRole('img', { name: 'Pore appearance analysis score 41' })).toBeInTheDocument();
    expect(screen.getByText('Analysis score')).toBeInTheDocument();
    expect(screen.queryByText(/severity|diagnosis|confidence/i)).not.toBeInTheDocument();
    expect(screen.getAllByText('Demo Gentle Cleanser')).toHaveLength(2);
    expect(screen.getByText('82.5')).toBeInTheDocument();
    expect(screen.getByText('Matched to the analyzed concern.')).toBeInTheDocument();
  });

  it('renders available AM/PM steps and leaves missing routine steps out', () => {
    render(<ResultsPage {...props} />);
    expect(screen.getAllByText('Demo Gentle Cleanser')).toHaveLength(2);
    expect(screen.queryByText(/placeholder/i)).not.toBeInTheDocument();
    const eveningButton = screen.getByRole('button', { name: /evening/i });
    expect(eveningButton).toHaveAttribute('aria-pressed', 'false');
    fireEvent.click(eveningButton);
    expect(eveningButton).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByText('No steps are available for this part of the routine.')).toBeInTheDocument();
  });

  it('keeps analysis visible and offers a local retry when recommendations fail', () => {
    render(<ResultsPage {...props} recommendations={{ status: 'error', message: 'Try later.' }} />);
    expect(screen.getAllByRole('heading', { name: 'Pore appearance' })).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(callbacks.onRetryRecommendations).toHaveBeenCalledOnce();
  });

  it('does not request a simulation until the user clicks the CTA', () => {
    render(<ResultsPage {...props} />);
    expect(callbacks.onSimulate).not.toHaveBeenCalled();
    expect(screen.getByRole('button', { name: /see visual simulation/i })).toBeInTheDocument();
  });

  it('lets the user select a supported focus and starts only that simulation', () => {
    render(<ResultsPage {...props} />);
    fireEvent.click(screen.getByRole('button', { name: 'See Visual Simulation' }));
    expect(callbacks.onSimulate).toHaveBeenCalledWith('pore');
  });

  it('shows simulation loading and the original and result images after success', () => {
    const simulation = {
      analysisId: 'analysis-1', concernCode: 'pore', focusLabel: 'Pore appearance',
      resultImageUrl: 'https://images.example.test/simulated.jpg', mock: false,
    };
    const { rerender } = render(<ResultsPage {...props} simulation={{ status: 'loading', concernCode: 'pore' }} />);
    expect(screen.getByRole('status')).toHaveTextContent('Creating your visual simulation');

    rerender(<ResultsPage {...props} simulation={{ status: 'loaded', data: simulation }} />);
    expect(screen.getByRole('img', { name: 'Original uploaded selfie' })).toHaveAttribute('src', 'blob:uploaded-selfie');
    expect(screen.getByRole('img', { name: 'AI-generated visual simulation' })).toHaveAttribute('src', simulation.resultImageUrl);
    expect(screen.getByText('This visualization is illustrative and does not predict medical or clinical outcomes.')).toBeInTheDocument();
  });

  it('renders the uploaded original and simulation result independently', () => {
    const simulation = {
      analysisId: 'analysis-1', concernCode: 'pore', focusLabel: 'Pore appearance',
      resultImageUrl: 'https://images.example.test/simulated.jpg', mock: false,
    };
    render(<ResultsPage {...props} simulation={{ status: 'loaded', data: simulation }} />);

    fireEvent.error(screen.getByRole('img', { name: 'Original uploaded selfie' }));

    expect(screen.getByRole('img', { name: 'AI-generated visual simulation' })).toHaveAttribute('src', simulation.resultImageUrl);
  });

  it('shows a friendly simulation error with an explicit retry action', () => {
    render(<ResultsPage {...props} simulation={{ status: 'error', concernCode: 'pore', message: 'Please wait before trying again.' }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Please wait before trying again.');
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(callbacks.onSimulate).toHaveBeenCalledWith('pore');
  });

  it('reports when no detected concern has a supported simulation focus', () => {
    render(<ResultsPage {...props} analysis={{ ...analysis, concerns: [{ ...analysis.concerns[0], code: 'redness' }] }} />);
    expect(screen.getByText('Visual simulation isn’t available for the analyzed focus yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /see visual simulation/i })).not.toBeInTheDocument();
  });

  it('offers an explicit request when a temporary result image cannot load', () => {
    const simulation = {
      analysisId: 'analysis-1', concernCode: 'pore', focusLabel: 'Pore appearance',
      resultImageUrl: 'https://images.example.test/expired.jpg', mock: false,
    };
    render(<ResultsPage {...props} simulation={{ status: 'loaded', data: simulation }} />);

    fireEvent.error(screen.getByRole('img', { name: 'AI-generated visual simulation' }));

    expect(screen.getByRole('alert')).toHaveTextContent('temporary link has expired');
    fireEvent.click(screen.getByRole('button', { name: 'Create a new preview' }));
    expect(callbacks.onSimulate).toHaveBeenCalledWith('pore');
  });
});

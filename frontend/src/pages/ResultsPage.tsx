import { useEffect, useState } from 'react';
import Brand from '../components/Brand';
import ConcernCard from '../components/ConcernCard';
import ErrorState from '../components/ErrorState';
import LoadingState from '../components/LoadingState';
import ProductCard from '../components/ProductCard';
import RoutineCard, { getStepNumber } from '../components/RoutineCard';
import type { RecommendationResponse } from '../types/recommendation';
import type { RoutineResponse, RoutineStepResponse } from '../types/routine';
import type { SkinAnalysis } from '../types/skinAnalysis';
import type { SkinSimulationResponse } from '../types/skinSimulation';

type Loadable<T> = { status: 'loading' } | { status: 'loaded'; data: T } | { status: 'error'; message: string };

interface ResultsPageProps {
  analysis: SkinAnalysis;
  originalImageUrl: string;
  recommendations: Loadable<RecommendationResponse>;
  routine: Loadable<RoutineResponse>;
  simulation: SimulationLoadable;
  onRetryRecommendations: () => void;
  onRetryRoutine: () => void;
  onSimulate: (concernCode: string) => void;
  onStartAgain: () => void;
}

type SimulationLoadable =
  | { status: 'idle' }
  | { status: 'loading'; concernCode: string }
  | { status: 'loaded'; data: SkinSimulationResponse }
  | { status: 'error'; message: string; concernCode: string };

const supportedConcernCodes = new Set(['acne', 'wrinkle', 'pore', 'texture']);

type SimulationFocusArea = { label: string; centerX: number; centerY: number; width: number; height: number };
const simulationFocusAreas: Record<string, SimulationFocusArea | undefined> = {
  pore: { label: 'Pore focus', centerX: 0.50, centerY: 0.57, width: 0.28, height: 0.20 },
  acne: { label: 'Acne focus', centerX: 0.64, centerY: 0.58, width: 0.28, height: 0.24 },
  wrinkle: { label: 'Wrinkle focus', centerX: 0.50, centerY: 0.28, width: 0.38, height: 0.16 },
  texture: { label: 'Texture focus', centerX: 0.64, centerY: 0.52, width: 0.30, height: 0.26 },
};
const focusCropScale = 1.3;
const asPercent = (value: number) => String(Number((value * 100).toFixed(3))) + '%';

export default function ResultsPage({ analysis, originalImageUrl, recommendations, routine, simulation, onRetryRecommendations, onRetryRoutine, onSimulate, onStartAgain }: ResultsPageProps) {
  const [routineTab, setRoutineTab] = useState<'morning' | 'evening'>('morning');
  const supportedConcerns = analysis.concerns.filter((concern) => supportedConcernCodes.has(concern.code));
  const [selectedConcernCode, setSelectedConcernCode] = useState(() => supportedConcerns[0]?.code ?? '');
  const [resultImageUnavailable, setResultImageUnavailable] = useState(false);
  useEffect(() => setResultImageUnavailable(false), [simulation]);
  const activeRoutine = routine.status === 'loaded' ? routineTab === 'morning' ? routine.data.morning : routine.data.evening : [];

  return (
    <div className="page-shell results-shell">
      <header className="site-header"><Brand /><button className="button button-outline button-small" onClick={onStartAgain}>Analyze another photo <span aria-hidden="true">↗</span></button></header>
      <main>
        <section className="results-hero"><div><p className="eyebrow"><span className="eyebrow-dot" /> YOUR PERSONAL SKINCARE GUIDE</p><h1>A little more insight.<br /><em>A lot more you.</em></h1><p className="results-intro">Here’s what your skin analysis found, along with product matches and a routine to explore.</p></div><div className="results-stamp"><span>MADE FOR</span><strong>your<br />skin</strong><span>WITH CARE ✳</span></div></section>

        <section className="results-section" aria-labelledby="analysis-heading">
          <div className="section-heading"><div><p className="eyebrow">THE STARTING POINT</p><h2 id="analysis-heading">Your skin analysis</h2></div><span className="section-index">01</span></div>
          {analysis.concerns.length > 0 ? <div className="concern-grid">{analysis.concerns.map((concern) => <ConcernCard concern={concern} key={concern.code} />)}</div> : <div className="empty-card"><span aria-hidden="true">✳</span><p>No concerns were returned for this analysis.</p></div>}
        </section>

        <section className="results-section products-section" aria-labelledby="recommendations-heading">
          <div className="section-heading"><div><p className="eyebrow">A FEW THOUGHTFUL MATCHES</p><h2 id="recommendations-heading">Picked for your skin</h2><p className="section-description">A small, synthetic demo catalog selected using your analysis.</p></div><span className="section-index">02</span></div>
          {recommendations.status === 'loading' && <div className="loading-panel"><LoadingState label="Finding your product matches…" /></div>}
          {recommendations.status === 'error' && <ErrorState actionLabel="Try again" message={recommendations.message} onAction={onRetryRecommendations} title="Your matches aren’t ready yet" />}
          {recommendations.status === 'loaded' && (recommendations.data.recommendations.length > 0 ? <div className="product-grid">{recommendations.data.recommendations.map((product, index) => <ProductCard index={index} key={product.productId} product={product} />)}</div> : <div className="empty-card"><span aria-hidden="true">✳</span><p>There are no product matches for this analysis yet.</p></div>)}
        </section>

        <section className="results-section routine-section" aria-labelledby="routine-heading">
          <div className="section-heading"><div><p className="eyebrow">A RITUAL THAT FITS YOUR DAY</p><h2 id="routine-heading">Your daily routine</h2><p className="section-description">Steps are shown when a suitable catalog product is available.</p></div><span className="section-index">03</span></div>
          {routine.status === 'loading' && <div className="loading-panel"><LoadingState label="Putting your routine together…" /></div>}
          {routine.status === 'error' && <ErrorState actionLabel="Try again" message={routine.message} onAction={onRetryRoutine} title="Your routine isn’t ready yet" />}
          {routine.status === 'loaded' && <>
            <div aria-label="Choose routine time of day" className="routine-tabs" role="group">
              <button aria-pressed={routineTab === 'morning'} className={routineTab === 'morning' ? 'routine-tab active' : 'routine-tab'} onClick={() => setRoutineTab('morning')} type="button">☼ Morning <span>AM</span></button>
              <button aria-pressed={routineTab === 'evening'} className={routineTab === 'evening' ? 'routine-tab active' : 'routine-tab'} onClick={() => setRoutineTab('evening')} type="button">☾ Evening <span>PM</span></button>
            </div>
            {activeRoutine.length > 0 ? <RoutineSteps steps={activeRoutine} /> : <div className="empty-card routine-empty"><span aria-hidden="true">☼</span><p>No steps are available for this part of the routine.</p></div>}
          </>}
        </section>
        <section className="results-section simulation-section" aria-labelledby="simulation-heading">
          <div className="section-heading"><div><p className="eyebrow">AN ILLUSTRATIVE PREVIEW</p><h2 id="simulation-heading">Visual simulation</h2><p className="section-description">See an AI-generated visualization based on one focus from your analysis.</p></div><span className="section-index">04</span></div>
          {supportedConcerns.length === 0 ? <div className="empty-card"><span aria-hidden="true">✳</span><p>Visual simulation isn’t available for the analyzed focus yet.</p></div> : <>
            {simulation.status === 'idle' && <>
              <FocusChoices concerns={supportedConcerns} selectedCode={selectedConcernCode} onSelect={setSelectedConcernCode} />
              <button className="button button-dark simulation-action" disabled={!selectedConcernCode} onClick={() => onSimulate(selectedConcernCode)}>See Visual Simulation <span aria-hidden="true">→</span></button>
            </>}
            {simulation.status === 'loading' && <div aria-live="polite" className="loading-panel"><LoadingState label="Creating your visual simulation…" /></div>}
            {simulation.status === 'error' && <div className="simulation-error" role="alert"><p>{simulation.message}</p><button className="button button-outline button-small" onClick={() => onSimulate(simulation.concernCode)}>Try again</button></div>}
            {simulation.status === 'loaded' && <>
              <div className="simulation-focus"><span className="concern-mark" aria-hidden="true">✳</span><span>Selected focus: <strong>{simulation.data.focusLabel}</strong></span></div>
              {resultImageUnavailable ? <div className="simulation-expired" role="alert"><p>This preview image is unavailable or its temporary link has expired.</p><button className="button button-outline button-small" onClick={() => onSimulate(simulation.data.concernCode)}>Create a new preview</button></div> : <div className="simulation-image-grid">
                <figure className="simulation-image-card"><div className="simulation-image-frame"><img alt="Original uploaded selfie" src={originalImageUrl} /></div><figcaption>Original</figcaption></figure>
                <figure className="simulation-image-card"><div className="simulation-image-frame"><img alt={simulation.data.mock ? 'Illustrative demo simulation' : 'AI-generated visual simulation'} onError={() => setResultImageUnavailable(true)} src={simulation.data.resultImageUrl} /></div><figcaption>{simulation.data.mock ? 'Demo simulation' : 'Simulation'}</figcaption></figure>
              </div>}
              {!resultImageUnavailable && <FocusComparison
                focusArea={simulationFocusAreas[simulation.data.concernCode]}
                onResultImageError={() => setResultImageUnavailable(true)}
                originalImageUrl={originalImageUrl}
                resultImageUrl={simulation.data.resultImageUrl}
              />}
              {simulation.data.mock && <p className="simulation-mock-note">Mock mode uses fixed demo images and did not process your photo.</p>}
            </>}
          </>}
          <p className="simulation-disclaimer">This visualization is illustrative and does not predict medical or clinical outcomes.</p>
        </section>
        <div className="results-note"><span aria-hidden="true">✳</span><p>These insights and product matches are for demonstration purposes and aren’t medical advice.</p></div>
        <div className="results-cta"><p>Ready to explore a new beginning?</p><button className="button button-dark" onClick={onStartAgain}>Analyze another photo <span aria-hidden="true">→</span></button></div>
      </main>
      <footer className="site-footer"><Brand compact /><span>SkinCart AI · Skincare, thoughtfully personalized.</span><span>Demo catalog</span></footer>
    </div>
  );
}

function FocusComparison({
  focusArea,
  onResultImageError,
  originalImageUrl,
  resultImageUrl,
}: {
  focusArea?: SimulationFocusArea;
  onResultImageError: () => void;
  originalImageUrl: string;
  resultImageUrl: string;
}) {
  const [sourceAspectRatio, setSourceAspectRatio] = useState<number>();
  const cropWidth = focusArea ? Math.min(focusArea.width * focusCropScale, 1) : 1;
  const cropHeight = focusArea ? Math.min(focusArea.height * focusCropScale, 1) : 1;
  const cropImageStyle = focusArea ? {
    width: asPercent(1 / cropWidth),
    left: asPercent(0.5 - focusArea.centerX / cropWidth),
    top: asPercent(0.5 - focusArea.centerY / cropHeight),
  } : undefined;
  const frameAspectRatio = sourceAspectRatio ? sourceAspectRatio * cropWidth / cropHeight : undefined;

  return <section aria-labelledby="focus-comparison-heading" className="focus-comparison">
    <div className="focus-comparison-heading">
      <h3 id="focus-comparison-heading">Focus comparison</h3>
      {focusArea
        ? <p><strong>{focusArea.label}</strong><span>Representative UI focus guide; not an exact YouCam detection mask.</span></p>
        : <p>Focus comparison is unavailable for this concern.</p>}
    </div>
    {focusArea && cropImageStyle && <div className="focus-comparison-grid">
      <figure className="focus-comparison-card">
        <div className="focus-comparison-frame" style={{ aspectRatio: frameAspectRatio }}>
          <img
            alt={'Original ' + focusArea.label + ' crop'}
            onLoad={(event) => {
              const image = event.currentTarget;
              if (image.naturalWidth > 0 && image.naturalHeight > 0) {
                setSourceAspectRatio(image.naturalWidth / image.naturalHeight);
              }
            }}
            src={originalImageUrl}
            style={cropImageStyle}
          />
        </div>
        <figcaption>Original</figcaption>
      </figure>
      <figure className="focus-comparison-card">
        <div className="focus-comparison-frame" style={{ aspectRatio: frameAspectRatio }}>
          <img
            alt={'AI Simulation ' + focusArea.label + ' crop'}
            onError={onResultImageError}
            src={resultImageUrl}
            style={cropImageStyle}
          />
        </div>
        <figcaption>AI Simulation</figcaption>
      </figure>
    </div>}
  </section>;
}

function FocusChoices({ concerns, selectedCode, onSelect }: {
  concerns: SkinAnalysis['concerns'];
  selectedCode: string;
  onSelect: (code: string) => void;
}) {
  return <div aria-label="Choose a skincare focus for the visual simulation" className="simulation-focus-choices" role="group">
    {concerns.map((concern) => <button aria-pressed={concern.code === selectedCode} className={concern.code === selectedCode ? 'simulation-focus-choice selected' : 'simulation-focus-choice'} key={concern.code} onClick={() => onSelect(concern.code)} type="button">{concern.displayName}</button>)}
  </div>;
}

function RoutineSteps({ steps }: { steps: RoutineStepResponse[] }) {
  return <div className="routine-list">{steps.map((step) => <RoutineCard key={step.step} number={getStepNumber(step.step)} step={step} />)}</div>;
}

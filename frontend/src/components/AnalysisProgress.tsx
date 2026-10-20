import type { AnalysisStatus } from '../types/skinAnalysis';

interface AnalysisProgressProps {
  fileName: string;
  phase: 'uploading' | 'polling';
  status?: AnalysisStatus;
}

export default function AnalysisProgress({ fileName, phase, status }: AnalysisProgressProps) {
  const uploaded = phase === 'polling';
  const completed = status === 'COMPLETED';
  const lines = [
    { label: 'Photo uploaded', done: uploaded },
    { label: 'Analyzing your skin', done: completed, active: !completed },
    { label: 'Preparing your results', done: completed },
  ];

  return (
    <section className="progress-card" aria-live="polite">
      <div className="progress-visual" aria-hidden="true"><div className="progress-orbit orbit-one" /><div className="progress-orbit orbit-two" /><span className="progress-leaf">✳</span><span className="spinner progress-spinner" /></div>
      <p className="eyebrow">A little moment for you</p>
      <h1>{phase === 'uploading' ? 'Sending your photo…' : 'Getting to know your skin'}</h1>
      <p className="progress-copy">{phase === 'uploading' ? 'Your photo is on its way for analysis.' : 'Your analysis is in progress. This usually takes a short moment.'}</p>
      <div className="progress-file"><span aria-hidden="true">▧</span><span>{fileName}</span></div>
      <ol className="progress-steps">
        {lines.map((line) => (
          <li className={line.done ? 'step-done' : line.active ? 'step-active' : ''} key={line.label}>
            <span className="step-icon">{line.done ? '✓' : line.active ? <span className="step-dot" /> : '·'}</span>
            <span>{line.label}</span>
            {line.active && <span className="step-status">{status === 'PROCESSING' ? 'In progress' : 'Waiting'}</span>}
          </li>
        ))}
      </ol>
      <p className="privacy-note"><span aria-hidden="true">⌑</span> Image bytes aren’t saved in the SkinCart database.</p>
    </section>
  );
}

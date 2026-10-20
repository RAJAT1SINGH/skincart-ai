import type { SkinConcern } from '../types/skinAnalysis';

export default function ConcernCard({ concern }: { concern: SkinConcern }) {
  return (
    <article className="concern-card">
      <div className="concern-heading"><span className="concern-mark" aria-hidden="true">✳</span><h3>{concern.displayName}</h3></div>
      {concern.uiScore !== null && <div className="concern-score-row"><span>Analysis score</span><strong>{concern.uiScore}</strong></div>}
      {concern.uiScore !== null && <div aria-label={`${concern.displayName} analysis score ${concern.uiScore}`} className="score-track" role="img"><span style={{ width: `${Math.max(0, Math.min(100, concern.uiScore))}%` }} /></div>}
    </article>
  );
}

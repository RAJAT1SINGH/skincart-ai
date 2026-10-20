import type { RoutineStep, RoutineStepResponse } from '../types/routine';
import { formatToken } from './ProductCard';

const STEP_ORDER: RoutineStep[] = ['CLEANSER', 'TREATMENT', 'MOISTURIZER', 'SUNSCREEN'];

export default function RoutineCard({ step, number }: { step: RoutineStepResponse; number: number }) {
  return (
    <article className="routine-step">
      <span className="routine-step-number">{number.toString().padStart(2, '0')}</span>
      <div className="routine-step-content"><span className="category-label">{formatToken(step.step)} · {formatToken(step.category)}</span><h3>{step.productName}</h3><p>{step.reason}</p></div>
      <span className="routine-check" aria-hidden="true">✓</span>
    </article>
  );
}

export function getStepNumber(step: RoutineStep): number {
  return STEP_ORDER.indexOf(step) + 1;
}

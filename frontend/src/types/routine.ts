import type { ProductCategory } from './recommendation';

export type RoutineStep = 'CLEANSER' | 'TREATMENT' | 'MOISTURIZER' | 'SUNSCREEN';

export interface RoutineStepResponse {
  step: RoutineStep;
  productId: string;
  productName: string;
  category: ProductCategory;
  reason: string;
}

export interface RoutineResponse {
  analysisId: string;
  morning: RoutineStepResponse[];
  evening: RoutineStepResponse[];
}

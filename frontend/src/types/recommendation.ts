export type ProductCategory =
  | 'CLEANSER'
  | 'SERUM'
  | 'MOISTURIZER'
  | 'SUNSCREEN'
  | 'ACNE_TREATMENT'
  | 'TREATMENT';

export interface ProductRecommendation {
  productId: string;
  productName: string;
  brand: string;
  category: ProductCategory;
  description: string;
  score: number;
  matchedConcerns: string[];
  matchedIngredients: string[];
  reason: string;
}

export interface RecommendationResponse {
  analysisId: string;
  recommendations: ProductRecommendation[];
}

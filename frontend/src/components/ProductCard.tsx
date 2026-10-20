import type { ProductRecommendation } from '../types/recommendation';

export default function ProductCard({ product, index }: { product: ProductRecommendation; index: number }) {
  return (
    <article className="product-card">
      <div className={`product-art product-art-${index % 4}`} aria-hidden="true"><span className="product-orb" /><span className="product-bottle"><span /></span></div>
      <div className="product-details">
        <div className="product-meta"><span>{product.brand}</span><span className="catalog-label">Demo catalog</span></div>
        <h3>{product.productName}</h3>
        <p className="category-label">{formatToken(product.category)}</p>
        <p className="product-description">{product.description}</p>
        <p className="product-reason">{product.reason}</p>
        <div className="product-tags" aria-label="Matched concerns">
          {product.matchedConcerns.map((concern) => <span className="tag tag-green" key={concern}>{formatToken(concern)}</span>)}
          {product.matchedIngredients.map((ingredient) => <span className="tag" key={ingredient}>{ingredient}</span>)}
        </div>
        <div className="match-score"><span>Match score</span><strong>{product.score}<small>/100</small></strong></div>
      </div>
    </article>
  );
}

export function formatToken(value: string): string {
  return value.toLowerCase().replace(/_/g, ' ').replace(/\b\w/g, (letter) => letter.toUpperCase());
}

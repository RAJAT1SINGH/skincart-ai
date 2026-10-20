interface BrandProps {
  compact?: boolean;
}

export default function Brand({ compact = false }: BrandProps) {
  return (
    <a aria-label="SkinCart AI home" className="brand-mark" href="/">
      <span aria-hidden="true" className="brand-symbol">s</span>
      {!compact && <span className="brand-name">skincart<span>AI</span></span>}
    </a>
  );
}

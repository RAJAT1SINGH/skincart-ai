interface ErrorStateProps {
  title: string;
  message: string;
  actionLabel?: string;
  onAction?: () => void;
  secondaryLabel?: string;
  onSecondary?: () => void;
}

export default function ErrorState({ title, message, actionLabel, onAction, secondaryLabel, onSecondary }: ErrorStateProps) {
  return (
    <section className="error-card" role="alert">
      <span aria-hidden="true" className="error-icon">!</span>
      <div>
        <h2>{title}</h2>
        <p>{message}</p>
        <div className="button-row">
          {actionLabel && onAction && <button className="button button-dark button-small" onClick={onAction}>{actionLabel}</button>}
          {secondaryLabel && onSecondary && <button className="button button-quiet button-small" onClick={onSecondary}>{secondaryLabel}</button>}
        </div>
      </div>
    </section>
  );
}

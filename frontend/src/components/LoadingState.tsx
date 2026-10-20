interface LoadingStateProps {
  label: string;
}

export default function LoadingState({ label }: LoadingStateProps) {
  return (
    <div aria-label={label} className="inline-loading" role="status">
      <span aria-hidden="true" className="spinner spinner-small" />
      <span>{label}</span>
    </div>
  );
}

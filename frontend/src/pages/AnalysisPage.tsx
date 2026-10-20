import AnalysisProgress from '../components/AnalysisProgress';
import Brand from '../components/Brand';
import ErrorState from '../components/ErrorState';
import type { AnalysisStatus } from '../types/skinAnalysis';

interface AnalysisPageProps {
  fileName: string;
  phase: 'uploading' | 'polling' | 'error';
  status?: AnalysisStatus;
  errorMessage?: string;
  errorTitle?: string;
  retryLabel?: string;
  onRetry?: () => void;
  onStartAgain: () => void;
}

export default function AnalysisPage({ fileName, phase, status, errorMessage, errorTitle, retryLabel, onRetry, onStartAgain }: AnalysisPageProps) {
  return (
    <div className="page-shell flow-shell">
      <header className="site-header"><Brand /><button className="text-button" onClick={onStartAgain}>Start again</button></header>
      <main className="flow-main">
        {phase === 'error' ? <ErrorState actionLabel={retryLabel} message={errorMessage ?? 'Something went wrong while processing your photo.'} onAction={onRetry} onSecondary={onStartAgain} secondaryLabel="Choose another photo" title={errorTitle ?? 'We couldn’t finish your analysis'} /> : <AnalysisProgress fileName={fileName} phase={phase} status={status} />}
      </main>
      <footer className="site-footer"><Brand compact /><span>Your photo stays private.</span></footer>
    </div>
  );
}

export type AnalysisStatus = 'CREATED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';

export interface SkinAnalysisCreatedResponse {
  analysisId: string;
  status: AnalysisStatus;
}

export interface SkinConcern {
  code: string;
  displayName: string;
  severity: string | null;
  uiScore: number | null;
  rawScore: number | null;
  maskUrls: string[];
}

export interface SkinAnalysis {
  analysisId: string;
  createdAt: string;
  status: AnalysisStatus;
  overallScore: number | null;
  concerns: SkinConcern[];
  errorCode: string | null;
  errorMessage: string | null;
}

export interface ApiErrorResponse {
  timestamp: string;
  status: number;
  code: string;
  message: string;
  path: string;
}

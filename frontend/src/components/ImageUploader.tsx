import { useCallback, useEffect, useRef, useState } from 'react';
import { validateImage } from '../utils/imageValidation';

interface ImageUploaderProps {
  onAnalyze: (file: File, previewUrl: string) => void;
  busy?: boolean;
}

export default function ImageUploader({ onAnalyze, busy = false }: ImageUploaderProps) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const ownedPreviewUrlRef = useRef<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  const releaseOwnedPreview = useCallback(() => {
    const url = ownedPreviewUrlRef.current;
    if (!url) return;
    URL.revokeObjectURL(url);
    ownedPreviewUrlRef.current = null;
  }, []);

  useEffect(() => () => releaseOwnedPreview(), [releaseOwnedPreview]);

  const chooseFile = (nextFile?: File) => {
    if (!nextFile) return;
    releaseOwnedPreview();
    const validationError = validateImage(nextFile);
    setError(validationError);
    if (validationError) {
      setFile(null);
      setPreviewUrl(null);
    } else {
      const url = URL.createObjectURL(nextFile);
      ownedPreviewUrlRef.current = url;
      setFile(nextFile);
      setPreviewUrl(url);
    }
    if (inputRef.current) inputRef.current.value = '';
  };

  const removeFile = () => {
    releaseOwnedPreview();
    setFile(null);
    setPreviewUrl(null);
    setError(null);
    if (inputRef.current) inputRef.current.value = '';
  };

  const analyze = () => {
    if (!file || !previewUrl) return;
    // Ownership transfers to the app so the same object URL survives this component unmounting.
    ownedPreviewUrlRef.current = null;
    onAnalyze(file, previewUrl);
  };

  return (
    <div className="upload-widget">
      <input ref={inputRef} accept="image/jpeg,image/png,.jpg,.jpeg,.png" className="visually-hidden" data-testid="image-input" onChange={(event) => chooseFile(event.currentTarget.files?.[0])} type="file" />
      {file && previewUrl ? (
        <div className="image-preview-card">
          <img alt="Selected selfie preview" className="image-preview" src={previewUrl} />
          <div className="image-preview-footer">
            <div className="image-file-details">
              <span className="file-check" aria-hidden="true">✓</span>
              <span><strong>Photo ready</strong><small>{file.name}</small></span>
            </div>
            <div className="image-actions">
              <button className="change-photo-button" disabled={busy} onClick={() => inputRef.current?.click()} type="button">Change</button>
              <button aria-label="Remove selected photo" className="icon-button" disabled={busy} onClick={removeFile} type="button">×</button>
            </div>
          </div>
        </div>
      ) : (
        <button
          className={`upload-dropzone${dragging ? ' upload-dropzone-active' : ''}`}
          disabled={busy}
          onClick={() => inputRef.current?.click()}
          onDragEnter={(event) => { event.preventDefault(); setDragging(true); }}
          onDragLeave={(event) => { event.preventDefault(); setDragging(false); }}
          onDragOver={(event) => event.preventDefault()}
          onDrop={(event) => { event.preventDefault(); setDragging(false); chooseFile(event.dataTransfer.files[0]); }}
          type="button"
        >
          <span className="upload-icon" aria-hidden="true">
            <svg viewBox="0 0 32 32" fill="none"><path d="M16 21V6m0 0-6 6m6-6 6 6M6 19v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6" stroke="currentColor" strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.7" /></svg>
          </span>
          <strong>Drop your selfie here</strong>
          <span className="upload-separator">or</span>
          <span className="button button-outline button-small">Choose a photo</span>
          <small>JPG or PNG · under 10 MB</small>
        </button>
      )}
      {error && <p className="field-error" role="alert">{error}</p>}
      {file && (
        <button className="button button-dark button-wide" disabled={busy} onClick={analyze} type="button">
          {busy ? <><span className="spinner spinner-small spinner-light" /> Preparing…</> : <>Analyze my skin <span aria-hidden="true">→</span></>}
        </button>
      )}
      <p className="privacy-note"><span aria-hidden="true">⌑</span> Your photo is sent to the analysis backend; image bytes aren’t saved in the SkinCart database.</p>
    </div>
  );
}

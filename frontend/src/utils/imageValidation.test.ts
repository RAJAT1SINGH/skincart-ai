import { describe, expect, it } from 'vitest';
import { MAX_IMAGE_SIZE_BYTES, validateImage } from './imageValidation';

describe('validateImage', () => {
  it('accepts JPEG and PNG images below the backend size limit', () => {
    expect(validateImage(new File(['image'], 'selfie.jpg', { type: 'image/jpeg' }))).toBeNull();
    expect(validateImage(new File(['image'], 'selfie.png', { type: 'image/png' }))).toBeNull();
  });

  it('rejects unsupported, empty, and oversized files', () => {
    expect(validateImage(new File(['text'], 'notes.txt', { type: 'text/plain' }))).toMatch(/JPEG or PNG/);
    expect(validateImage(new File([], 'empty.png', { type: 'image/png' }))).toMatch(/empty/);
    const oversized = new File(['x'.repeat(MAX_IMAGE_SIZE_BYTES)], 'large.png', { type: 'image/png' });
    expect(validateImage(oversized)).toMatch(/smaller than 10 MB/);
  });
});

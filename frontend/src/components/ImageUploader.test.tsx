import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ImageUploader from './ImageUploader';

describe('ImageUploader', () => {
  it('previews a selected image and submits it when asked to analyze', async () => {
    const user = userEvent.setup();
    const onAnalyze = vi.fn();
    render(<ImageUploader onAnalyze={onAnalyze} />);
    const image = new File(['selfie'], 'portrait.png', { type: 'image/png' });

    await user.upload(screen.getByTestId('image-input'), image);

    expect(await screen.findByAltText('Selected selfie preview')).toHaveAttribute('src', 'blob:skincart-preview');
    await user.click(screen.getByRole('button', { name: /analyze my skin/i }));
    expect(onAnalyze).toHaveBeenCalledWith(image, 'blob:skincart-preview');
  });

  it('shows a validation message for unsupported images', () => {
    render(<ImageUploader onAnalyze={vi.fn()} />);
    fireEvent.change(screen.getByTestId('image-input'), {
      target: { files: [new File(['text'], 'notes.txt', { type: 'text/plain' })] },
    });
    expect(screen.getByRole('alert')).toHaveTextContent('Choose a JPEG or PNG image');
    expect(screen.queryByRole('button', { name: /analyze my skin/i })).not.toBeInTheDocument();
  });
});

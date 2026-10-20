export const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

const SUPPORTED_TYPES = new Set(['image/jpeg', 'image/jpg', 'image/png']);
const SUPPORTED_EXTENSIONS = /\.(jpe?g|png)$/i;

export function validateImage(file: File): string | null {
  const typeIsSupported = file.type
    ? SUPPORTED_TYPES.has(file.type.toLowerCase())
    : SUPPORTED_EXTENSIONS.test(file.name);
  if (!typeIsSupported) return 'Choose a JPEG or PNG image to continue.';
  if (file.size === 0) return 'This file is empty. Choose another image.';
  if (file.size >= MAX_IMAGE_SIZE_BYTES) return 'Choose an image smaller than 10 MB.';
  return null;
}

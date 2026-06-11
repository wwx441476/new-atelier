const MAX_IMAGE_BYTES = 4 * 1024 * 1024;
export const MAX_COPILOT_IMAGES = 4;
const ACCEPTED_TYPES = new Set(['image/png', 'image/jpeg', 'image/gif', 'image/webp']);

export async function readImageAsDataUrl(file: File): Promise<string> {
  if (!ACCEPTED_TYPES.has(file.type)) {
    throw new Error('仅支持 PNG、JPEG、GIF、WebP 图片');
  }
  if (file.size > MAX_IMAGE_BYTES) {
    throw new Error('单张图片不能超过 4MB');
  }
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        resolve(reader.result);
        return;
      }
      reject(new Error('读取图片失败'));
    };
    reader.onerror = () => reject(new Error('读取图片失败'));
    reader.readAsDataURL(file);
  });
}

export function isAcceptedImageFile(file: File) {
  return ACCEPTED_TYPES.has(file.type);
}

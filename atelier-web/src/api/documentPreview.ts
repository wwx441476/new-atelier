import client from './client';
import type { ApiResponse } from './types';
import { formatFileSize } from './documentCompare';

export type PreviewJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

export type PreviewBlockType =
  | 'SECTION'
  | 'HEADING'
  | 'PARAGRAPH'
  | 'LIST_ITEM'
  | 'TABLE'
  | 'IMAGE'
  | 'IMAGE_CAPTION'
  | 'CODE'
  | 'SLIDE'
  | 'SHEET';

export type PreviewInlineMark = 'BOLD' | 'ITALIC';

export interface PreviewRun {
  text?: string;
  marks?: PreviewInlineMark[];
}

export interface PreviewBlockAnchor {
  page?: number;
  textHash?: string;
  occurrence?: number;
  sourceStart?: number;
  sourceEnd?: number;
}

export interface PreviewBlockMeta {
  page?: number;
  sheet?: string;
  slideIndex?: number;
  ocrConfidence?: number;
  styleHints?: string;
}

export interface PreviewTableData {
  sheetName?: string;
  rows?: string[][];
}

export interface PreviewBlock {
  id?: string;
  type: PreviewBlockType;
  level?: number;
  text?: string;
  runs?: PreviewRun[];
  anchor?: PreviewBlockAnchor;
  table?: PreviewTableData;
  imageDataUrl?: string;
  meta?: PreviewBlockMeta;
}

export interface PreviewDocument {
  fileName?: string;
  sourceType?: string;
  layoutMode?: string;
  styleMode?: string;
  ocrUsed?: boolean;
  llmStyleUsed?: boolean;
  warnings?: string[];
  blocks?: PreviewBlock[];
}

export interface PreviewJob {
  id: string;
  status: PreviewJobStatus;
  progress?: string;
  progressPercent?: number;
  fileName?: string;
  result?: PreviewDocument;
  error?: string;
  createdAt?: number;
  updatedAt?: number;
}

export interface PreviewOptions {
  enableLlmStyle?: boolean;
  enableLlmRefine?: boolean;
  llmProfileId?: string;
}

/** 与后端 atelier.document-preview.max-file-bytes 保持一致（200MB） */
export const DOCUMENT_PREVIEW_MAX_FILE_BYTES = 200 * 1024 * 1024;

export { formatFileSize };

export const documentPreviewApi = {
  async createJob(file: File, options: PreviewOptions = {}): Promise<PreviewJob> {
    const form = new FormData();
    form.append('file', file);
    form.append('enableLlmStyle', String(options.enableLlmStyle !== false));
    form.append('enableLlmRefine', String(options.enableLlmRefine !== false));
    if (options.llmProfileId) {
      form.append('llmProfileId', options.llmProfileId);
    }
    const res = await client.post<ApiResponse<PreviewJob>>('/document-preview/jobs', form, {
      timeout: 600000,
      transformRequest: [
        (data, headers) => {
          if (headers && typeof headers === 'object') {
            delete (headers as Record<string, unknown>)['Content-Type'];
          }
          return data;
        },
      ],
    });
    return res.data.data;
  },

  async getJob(id: string): Promise<PreviewJob> {
    const res = await client.get<ApiResponse<PreviewJob>>(`/document-preview/jobs/${id}`, {
      timeout: 60000,
    });
    if (res.data.code !== 0) {
      throw new Error(res.data.message || '获取任务失败');
    }
    return res.data.data;
  },

  async cancelJob(id: string): Promise<PreviewJob> {
    const res = await client.post<ApiResponse<PreviewJob>>(`/document-preview/jobs/${id}/cancel`, null, {
      timeout: 30000,
    });
    if (res.data.code !== 0) {
      throw new Error(res.data.message || '停止任务失败');
    }
    return res.data.data;
  },

  async deleteJob(id: string): Promise<void> {
    await client.delete(`/document-preview/jobs/${id}`);
  },
};

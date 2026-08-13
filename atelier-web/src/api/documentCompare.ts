import client from './client';
import type { ApiResponse } from './types';
import type { PreviewDocument } from './documentPreview';

export type DiffOpType = 'ADDED' | 'REMOVED' | 'MODIFIED' | 'MOVED' | 'EQUAL';
export type CompareJobStatus = 'PENDING' | 'RUNNING' | 'SUCCEEDED' | 'FAILED';

export interface CompareOptions {
  ignoreWhitespace?: boolean;
  excelKeyColumn?: boolean;
  enableLlm?: boolean;
  enableLlmStyle?: boolean;
  enableLlmRefine?: boolean;
  llmProfileId?: string;
}

export interface TextHunk {
  type: DiffOpType;
  oldStart: number;
  newStart: number;
  oldLines: string[];
  newLines: string[];
  blockIdsA?: string[];
  blockIdsB?: string[];
}

export interface ParagraphOp {
  type: DiffOpType;
  oldIndex?: number;
  newIndex?: number;
  movedTo?: number;
  oldText?: string;
  newText?: string;
  blockType?: string;
  blockIdsA?: string[];
  blockIdsB?: string[];
}

export interface StructureOp {
  type: DiffOpType;
  path?: string;
  blockType?: string;
  oldText?: string;
  newText?: string;
  detail?: string;
  blockIdsA?: string[];
  blockIdsB?: string[];
}

export interface CompareStats {
  added: number;
  removed: number;
  modified: number;
  moved: number;
}

export interface CompareQuality {
  ocrUsed: boolean;
  warnings: string[];
}

export interface LlmInterpretation {
  available: boolean;
  summary?: string;
  impactPoints?: string[];
  reviewChecklist?: string[];
  error?: string;
}

export interface CompareResult {
  fileNameA?: string;
  fileNameB?: string;
  textHunks: TextHunk[];
  paragraphOps: ParagraphOp[];
  structureOps: StructureOp[];
  stats?: CompareStats;
  quality?: CompareQuality;
  interpretation?: LlmInterpretation;
  plainTextA?: string;
  plainTextB?: string;
  previewA?: PreviewDocument;
  previewB?: PreviewDocument;
}

export interface CompareJob {
  id: string;
  status: CompareJobStatus;
  progress?: string;
  progressPercent?: number;
  fileNameA?: string;
  fileNameB?: string;
  options?: CompareOptions;
  result?: CompareResult;
  error?: string;
  createdAt?: number;
  updatedAt?: number;
}

/** 与后端 atelier.document-compare.max-file-bytes 保持一致（200MB） */
export const DOCUMENT_COMPARE_MAX_FILE_BYTES = 200 * 1024 * 1024;

export function formatFileSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}

export const documentCompareApi = {
  async createJob(fileA: File, fileB: File, options: CompareOptions): Promise<CompareJob> {
    const form = new FormData();
    form.append('fileA', fileA);
    form.append('fileB', fileB);
    form.append('options', JSON.stringify(options));
    const res = await client.post<ApiResponse<CompareJob>>('/document-compare/jobs', form, {
      timeout: 600000,
      transformRequest: [
        (data, headers) => {
          // Drop default application/json so browser can set multipart boundary
          if (headers && typeof headers === 'object') {
            delete (headers as Record<string, unknown>)['Content-Type'];
          }
          return data;
        },
      ],
    });
    return res.data.data;
  },

  async getJob(id: string): Promise<CompareJob> {
    const res = await client.get<ApiResponse<CompareJob>>(`/document-compare/jobs/${id}`, {
      timeout: 60000,
    });
    if (res.data.code !== 0) {
      throw new Error(res.data.message || '获取任务失败');
    }
    return res.data.data;
  },

  async deleteJob(id: string): Promise<void> {
    await client.delete(`/document-compare/jobs/${id}`);
  },
};

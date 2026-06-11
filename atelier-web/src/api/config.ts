import { postData } from './client';
import type {
  AtelierConfigBundle,
  ConfigExportRequest,
  ConfigImportOptions,
  ConfigImportResult,
} from './types';

export const configApi = {
  exportBundle: (request: ConfigExportRequest = {}) =>
    postData<AtelierConfigBundle>('/config/export', {
      includeSecrets: request.includeSecrets !== false,
      options: request.options,
    }),
  importBundle: (bundle: AtelierConfigBundle, options?: ConfigImportOptions) =>
    postData<ConfigImportResult>('/config/import', { bundle, options }),
};

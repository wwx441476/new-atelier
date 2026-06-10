import { getData, postData } from './client';
import type {
  AtelierConfigBundle,
  ConfigImportOptions,
  ConfigImportResult,
} from './types';

export const configApi = {
  exportBundle: (includeSecrets = true) =>
    getData<AtelierConfigBundle>('/config/export', { includeSecrets }),
  importBundle: (bundle: AtelierConfigBundle, options?: ConfigImportOptions) =>
    postData<ConfigImportResult>('/config/import', { bundle, options }),
};

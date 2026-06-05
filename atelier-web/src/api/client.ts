import axios, { AxiosError } from 'axios';
import { message } from 'antd';
import type { ApiResponse } from './types';

const client = axios.create({
  baseURL: '/api/v2',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' },
});

client.interceptors.response.use(
  (response) => {
    const body = response.data as ApiResponse<unknown>;
    if (body && typeof body.code === 'number' && body.code !== 0) {
      message.error(body.message || '请求失败');
      return Promise.reject(new Error(body.message || '请求失败'));
    }
    return response;
  },
  (error: AxiosError<ApiResponse<unknown>>) => {
    const msg =
      error.response?.data?.message ||
      error.message ||
      '网络错误，请检查后端服务是否启动';
    message.error(msg);
    return Promise.reject(error);
  },
);

export async function getData<T>(url: string, params?: Record<string, unknown>): Promise<T> {
  const res = await client.get<ApiResponse<T>>(url, { params });
  return res.data.data;
}

export async function postData<T>(url: string, data?: unknown): Promise<T> {
  const res = await client.post<ApiResponse<T>>(url, data);
  return res.data.data;
}

export async function deleteData<T>(url: string): Promise<T> {
  const res = await client.delete<ApiResponse<T>>(url);
  return res.data.data;
}

export default client;

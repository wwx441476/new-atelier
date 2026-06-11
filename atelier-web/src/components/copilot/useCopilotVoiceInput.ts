import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import { copilotApi } from '../../api/copilot';
import {
  blobToDataUrl,
  isVoiceInputSupported,
  pickAudioMimeType,
} from '../../utils/copilotVoiceInput';

interface UseCopilotVoiceInputOptions {
  enabled: boolean;
  value: string;
  onChange: (next: string) => void;
  llmProfileId?: string;
}

export function useCopilotVoiceInput({
  enabled,
  value,
  onChange,
  llmProfileId,
}: UseCopilotVoiceInputOptions) {
  const [listening, setListening] = useState(false);
  const [transcribing, setTranscribing] = useState(false);
  const [audioStream, setAudioStream] = useState<MediaStream | null>(null);
  const [supported] = useState(isVoiceInputSupported);
  const mediaRecorderRef = useRef<MediaRecorder | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const chunksRef = useRef<Blob[]>([]);
  const voiceBaseRef = useRef('');
  const valueRef = useRef(value);
  const llmProfileIdRef = useRef(llmProfileId);

  useEffect(() => {
    valueRef.current = value;
  }, [value]);

  useEffect(() => {
    llmProfileIdRef.current = llmProfileId;
  }, [llmProfileId]);

  const cleanupStream = useCallback(() => {
    streamRef.current?.getTracks().forEach((track) => track.stop());
    streamRef.current = null;
    mediaRecorderRef.current = null;
    setAudioStream(null);
  }, []);

  const transcribeRecording = useCallback(async (blob: Blob) => {
    if (blob.size < 100) {
      message.warning('未录到有效音频，请重试');
      return;
    }
    setTranscribing(true);
    try {
      const audioDataUrl = await blobToDataUrl(blob);
      const result = await copilotApi.transcribe({
        audioDataUrl,
        llmProfileId: llmProfileIdRef.current,
      });
      const text = result.text?.trim();
      if (!text) {
        message.warning('未识别到语音内容');
        return;
      }
      const base = voiceBaseRef.current;
      const spacer = base.length > 0 && !base.endsWith(' ') && !base.endsWith('\n') ? ' ' : '';
      const next = base + spacer + text;
      onChange(next);
      voiceBaseRef.current = next;
    } catch {
      // axios 拦截器已提示错误
    } finally {
      setTranscribing(false);
    }
  }, [onChange]);

  const stop = useCallback(async (options?: { transcribe?: boolean }) => {
    const shouldTranscribe = options?.transcribe !== false;
    const recorder = mediaRecorderRef.current;
    if (!recorder || recorder.state === 'inactive') {
      cleanupStream();
      setListening(false);
      return;
    }

    setListening(false);
    await new Promise<void>((resolve) => {
      recorder.onstop = () => resolve();
      recorder.stop();
    });
    cleanupStream();

    if (!shouldTranscribe) {
      chunksRef.current = [];
      return;
    }

    const mimeType = recorder.mimeType || pickAudioMimeType() || 'audio/webm';
    const blob = new Blob(chunksRef.current, { type: mimeType });
    chunksRef.current = [];
    await transcribeRecording(blob);
  }, [cleanupStream, transcribeRecording]);

  const start = useCallback(async () => {
    if (!supported || !enabled || transcribing) {
      return;
    }
    if (mediaRecorderRef.current) {
      await stop();
      return;
    }

    try {
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true });
      streamRef.current = stream;
      const mimeType = pickAudioMimeType();
      const recorder = mimeType
        ? new MediaRecorder(stream, { mimeType })
        : new MediaRecorder(stream);
      chunksRef.current = [];
      recorder.ondataavailable = (event) => {
        if (event.data.size > 0) {
          chunksRef.current.push(event.data);
        }
      };
      recorder.start(250);
      mediaRecorderRef.current = recorder;
      voiceBaseRef.current = valueRef.current;
      setAudioStream(stream);
      setListening(true);
    } catch {
      cleanupStream();
      message.error('无法访问麦克风，请检查浏览器权限');
    }
  }, [cleanupStream, enabled, stop, supported, transcribing]);

  const toggle = useCallback(async () => {
    if (listening || transcribing) {
      await stop();
      return;
    }
    await start();
  }, [listening, start, stop, transcribing]);

  useEffect(() => {
    if (!enabled && listening) {
      void stop({ transcribe: false });
    }
  }, [enabled, listening, stop]);

  useEffect(() => () => {
    void stop({ transcribe: false });
  }, [stop]);

  return {
    supported,
    listening,
    transcribing,
    audioStream,
    start,
    stop,
    toggle,
  };
}

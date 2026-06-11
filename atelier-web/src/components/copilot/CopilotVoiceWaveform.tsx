import { useEffect, useRef, useState } from 'react';

const BAR_COUNT = 6;

interface CopilotVoiceWaveformProps {
  stream: MediaStream | null;
  active: boolean;
  transcribing?: boolean;
}

export default function CopilotVoiceWaveform({
  stream,
  active,
  transcribing = false,
}: CopilotVoiceWaveformProps) {
  const [levels, setLevels] = useState<number[]>(() => Array(BAR_COUNT).fill(0.18));
  const frameRef = useRef<number>();
  const analyserRef = useRef<AnalyserNode | null>(null);
  const contextRef = useRef<AudioContext | null>(null);

  useEffect(() => {
    if (!stream || !active) {
      analyserRef.current = null;
      if (contextRef.current) {
        void contextRef.current.close();
        contextRef.current = null;
      }
      return undefined;
    }

    const context = new AudioContext();
    const analyser = context.createAnalyser();
    analyser.fftSize = 32;
    analyser.smoothingTimeConstant = 0.72;
    const source = context.createMediaStreamSource(stream);
    source.connect(analyser);
    contextRef.current = context;
    analyserRef.current = analyser;

    const data = new Uint8Array(analyser.frequencyBinCount);
    const step = Math.max(1, Math.floor(data.length / BAR_COUNT));

    const tick = () => {
      if (!analyserRef.current) {
        return;
      }
      analyserRef.current.getByteFrequencyData(data);
      const nextLevels = Array.from({ length: BAR_COUNT }, (_, index) => {
        const start = index * step;
        const slice = data.slice(start, start + step);
        const avg = slice.reduce((sum, value) => sum + value, 0) / slice.length;
        const normalized = Math.max(0.14, Math.min(1, avg / 110));
        return normalized;
      });
      setLevels(nextLevels);
      frameRef.current = requestAnimationFrame(tick);
    };

    frameRef.current = requestAnimationFrame(tick);

    return () => {
      if (frameRef.current) {
        cancelAnimationFrame(frameRef.current);
      }
      source.disconnect();
      void context.close();
      contextRef.current = null;
      analyserRef.current = null;
    };
  }, [stream, active]);

  useEffect(() => {
    if (!transcribing || active) {
      return undefined;
    }

    let phase = 0;
    const interval = window.setInterval(() => {
      phase += 0.22;
      setLevels(
        Array.from({ length: BAR_COUNT }, (_, index) => (
          0.2 + Math.sin(phase + index * 0.75) * 0.14 + 0.12
        )),
      );
    }, 90);

    return () => window.clearInterval(interval);
  }, [transcribing, active]);

  return (
    <div
      className={`copilot-voice-waveform${transcribing && !active ? ' transcribing' : ''}`}
      aria-hidden
    >
      {levels.map((level, index) => (
        <span
          key={index}
          className="copilot-voice-waveform-bar"
          style={{ transform: `scaleY(${level})` }}
        />
      ))}
    </div>
  );
}

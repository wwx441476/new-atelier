import { useMemo } from 'react';

function renderInline(text: string) {
  const parts = text.split(/(\*\*[^*]+\*\*|`[^`]+`)/g);
  return parts.map((part, index) => {
    if (part.startsWith('**') && part.endsWith('**')) {
      return <strong key={index}>{part.slice(2, -2)}</strong>;
    }
    if (part.startsWith('`') && part.endsWith('`')) {
      return (
        <code key={index} className="copilot-inline-code">
          {part.slice(1, -1)}
        </code>
      );
    }
    return <span key={index}>{part}</span>;
  });
}

function isTableSeparator(line: string) {
  return /^\|?[\s:-]+\|[\s|:-]+$/.test(line.trim());
}

function parseTableRow(line: string) {
  return line
    .trim()
    .replace(/^\|/, '')
    .replace(/\|$/, '')
    .split('|')
    .map((cell) => cell.trim());
}

export default function CopilotMessageContent({ content }: { content: string }) {
  const blocks = useMemo(() => {
    const result: Array<{ type: 'text' | 'code' | 'table'; value: string | string[][] }> = [];
    const segments = content.split(/```/);

    segments.forEach((segment, index) => {
      if (!segment) {
        return;
      }
      if (index % 2 === 1) {
        const code = segment.replace(/^[a-z]+\n/i, '').trimEnd();
        result.push({ type: 'code', value: code });
        return;
      }

      const lines = segment.split('\n');
      let buffer: string[] = [];

      const flushText = () => {
        if (buffer.length > 0) {
          result.push({ type: 'text', value: buffer.join('\n') });
          buffer = [];
        }
      };

      for (let i = 0; i < lines.length; i += 1) {
        const line = lines[i];
        const next = lines[i + 1];
        if (line.includes('|') && next && isTableSeparator(next)) {
          flushText();
          const headers = parseTableRow(line);
          const rows: string[][] = [];
          i += 2;
          while (i < lines.length && lines[i].includes('|')) {
            rows.push(parseTableRow(lines[i]));
            i += 1;
          }
          i -= 1;
          result.push({ type: 'table', value: [headers, ...rows] });
          continue;
        }
        buffer.push(line);
      }
      flushText();
    });

    return result;
  }, [content]);

  return (
    <div className="copilot-message-content">
      {blocks.map((block, index) => {
        if (block.type === 'code') {
          return (
            <pre key={index} className="copilot-code-block">
              <code>{block.value as string}</code>
            </pre>
          );
        }
        if (block.type === 'table') {
          const rows = block.value as string[][];
          const [header, ...body] = rows;
          return (
            <div key={index} className="copilot-table-wrap">
              <table className="copilot-table">
                <thead>
                  <tr>
                    {header.map((cell) => (
                      <th key={cell}>{cell}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {body.map((row, rowIndex) => (
                    <tr key={rowIndex}>
                      {row.map((cell, cellIndex) => (
                        <td key={`${rowIndex}-${cellIndex}`}>{cell}</td>
                      ))}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        }
        return (
          <p key={index} className="copilot-text-block">
            {renderInline(block.value as string)}
          </p>
        );
      })}
    </div>
  );
}

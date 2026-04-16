'use client';

import { useEffect, useRef } from 'react';
import katex from 'katex';
import 'katex/dist/katex.min.css';

interface BoardStep {
  latex: string;
  note?: string;
  highlight?: boolean;
}

interface BoardContent {
  title: string;
  steps: BoardStep[];
}

interface MathBoardProps {
  board: BoardContent | null;
  isThinking: boolean;
}

function KaTeXStep({ latex, note, highlight, index }: BoardStep & { index: number }) {
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (ref.current) {
      try {
        katex.render(latex, ref.current, {
          throwOnError: false,
          displayMode: true,
          output: 'html',
        });
      } catch {
        if (ref.current) ref.current.innerText = latex;
      }
    }
  }, [latex]);

  return (
    <div
      className={`flex items-start gap-4 py-3 px-4 rounded-xl transition-all duration-500 animate-fadeIn
        ${highlight ? 'bg-amber-50 border-l-4 border-amber-400' : 'hover:bg-gray-50'}`}
      style={{ animationDelay: `${index * 150}ms`, animationFillMode: 'both' }}
    >
      <div className="flex-1 overflow-x-auto">
        <div ref={ref} className={`text-center ${highlight ? 'text-amber-700' : 'text-gray-800'}`} />
      </div>
      {note && (
        <div className={`text-xs px-2 py-1 rounded-full whitespace-nowrap flex-shrink-0 mt-2
          ${highlight ? 'bg-amber-200 text-amber-800 font-medium' : 'bg-gray-100 text-gray-500'}`}>
          {note}
        </div>
      )}
    </div>
  );
}

export default function MathBoard({ board, isThinking }: MathBoardProps) {
  return (
    <div className="flex flex-col h-full bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
      <div className="px-6 py-4 border-b border-gray-100 bg-gray-50">
        <div className="flex items-center gap-2">
          <div className="w-3 h-3 rounded-full bg-red-400" />
          <div className="w-3 h-3 rounded-full bg-yellow-400" />
          <div className="w-3 h-3 rounded-full bg-green-400" />
          <span className="ml-3 text-sm font-medium text-gray-500">
            {board?.title || '板书'}
          </span>
        </div>
      </div>

      <div className="flex-1 overflow-y-auto p-6">
        {isThinking && !board && (
          <div className="flex items-center justify-center h-full">
            <div className="flex gap-2">
              <div className="w-2 h-2 bg-indigo-300 rounded-full animate-bounce" style={{ animationDelay: '0ms' }} />
              <div className="w-2 h-2 bg-indigo-300 rounded-full animate-bounce" style={{ animationDelay: '150ms' }} />
              <div className="w-2 h-2 bg-indigo-300 rounded-full animate-bounce" style={{ animationDelay: '300ms' }} />
            </div>
          </div>
        )}

        {!board && !isThinking && (
          <div className="flex flex-col items-center justify-center h-full text-gray-300 gap-3">
            <svg className="w-16 h-16" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1}
                d="M9 12h6m-6 4h6m2 5H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z" />
            </svg>
            <p className="text-sm">板书将在老师讲解时出现</p>
          </div>
        )}

        {board && (
          <div className="space-y-1">
            {board.steps.map((step, i) => (
              <KaTeXStep
                key={`${board.title}-${i}`}
                latex={step.latex}
                note={step.note}
                highlight={step.highlight}
                index={i}
              />
            ))}
          </div>
        )}
      </div>
    </div>
  );
}

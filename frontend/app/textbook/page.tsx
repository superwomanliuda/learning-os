"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import dynamic from "next/dynamic";
import { API_BASE, api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

const MathBoard = dynamic(() => import("@/components/MathBoard"), { ssr: false });

type Asset = { id: string; title: string; sourceType?: string; sourceUrl: string; status: string };
type LessonPhase = { phase: string; name: string; goal: string; minutes: number; teacherSpeech: string; boardSteps: string[] };
type LessonPayload = {
  lessonTitle: string;
  durationMin: number;
  opening: string;
  teachingPhases: LessonPhase[];
  keyPoints: string[];
  interactiveQuestions: string[];
  endingTask: string;
};
type TutorMessage = { role: "user" | "assistant"; content: string };
type BoardContent = { title: string; steps: { latex: string; note?: string; highlight?: boolean }[] };

export default function TextbookPage() {
  const router = useRouter();
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const [assets, setAssets] = useState<Asset[]>([]);
  const [textbookId, setTextbookId] = useState("");
  const [uploadTitle, setUploadTitle] = useState("我的电子教材");
  const [uploading, setUploading] = useState(false);
  const [hasStarted, setHasStarted] = useState(false);
  const [targetMinutes, setTargetMinutes] = useState(4);
  const [focusQuestion, setFocusQuestion] = useState("我最容易在哪一步卡住？");
  const [lesson, setLesson] = useState<LessonPayload | null>(null);
  const [currentPhase, setCurrentPhase] = useState(0);
  const [messages, setMessages] = useState<TutorMessage[]>([]);
  const [input, setInput] = useState("");
  const [isThinking, setIsThinking] = useState(false);
  const [board, setBoard] = useState<BoardContent | null>(null);
  const [msg, setMsg] = useState("");
  const [playing, setPlaying] = useState(false);

  const textbookAssets = assets.filter((v) => (v.sourceType || "").toLowerCase() === "textbook" || v.sourceUrl.startsWith("upload://"));

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: "smooth" });
  }, [messages]);

  const loadAssets = useCallback(async () => {
    try {
      const list = await api<Asset[]>("/api/videos");
      setAssets(list);
      const textbooks = list.filter((v) => (v.sourceType || "").toLowerCase() === "textbook" || v.sourceUrl.startsWith("upload://"));
      if (!textbookId && textbooks.length) setTextbookId(textbooks[0].id);
    } catch {
      /* ignore */
    }
  }, [textbookId]);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    else loadAssets();
  }, [router, loadAssets]);

  async function uploadTextbookByFile(file: File) {
    setMsg("");
    setUploading(true);
    try {
      const form = new FormData();
      form.append("file", file);
      form.append("title", uploadTitle);
      const created = await api<Asset>("/api/videos/textbook/upload", { method: "POST", body: form });
      setTextbookId(created.id);
      setMsg("教材上传成功：" + created.title);
      await loadAssets();
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "教材上传失败");
    } finally {
      setUploading(false);
    }
  }

  async function startLesson() {
    if (!textbookId) {
      setMsg("请先上传或选择教材");
      return;
    }
    setMsg("");
    setIsThinking(true);
    try {
      const plan = await api<LessonPayload>("/api/video/textbook/lesson", {
        method: "POST",
        body: JSON.stringify({ textbookId, targetMinutes, focusQuestion }),
      });
      setLesson(plan);
      setHasStarted(true);
      setCurrentPhase(0);
      const openMessages: TutorMessage[] = [{ role: "assistant", content: plan.opening || "我们开始今天的教材微课。" }];
      const p0 = plan.teachingPhases?.[0];
      if (p0?.teacherSpeech) {
        openMessages.push({ role: "assistant", content: p0.teacherSpeech });
      }
      setMessages(openMessages);
      if (p0) {
        setBoard({
          title: `${p0.phase} · ${p0.name}`,
          steps: (p0.boardSteps || []).map((s, i) => ({ latex: s, note: i === 0 ? p0.goal : "", highlight: i === 0 })),
        });
      } else {
        setBoard(null);
      }
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "微课生成失败");
    } finally {
      setIsThinking(false);
    }
  }

  async function sendMessage() {
    if (!input.trim() || isThinking || !textbookId) return;
    const newMessages = [...messages, { role: "user" as const, content: input.trim() }];
    setMessages(newMessages);
    setInput("");
    setIsThinking(true);
    try {
      const res = await api<{ speech: string; board: BoardContent | null }>("/api/video/textbook/tutor", {
        method: "POST",
        body: JSON.stringify({ textbookId, messages: newMessages }),
      });
      setMessages((prev) => [...prev, { role: "assistant", content: res.speech || "我们继续。" }]);
      if (res.board) setBoard(res.board);
    } catch {
      setMessages((prev) => [...prev, { role: "assistant", content: "网络有点问题，请稍后再试。" }]);
    } finally {
      setIsThinking(false);
    }
  }

  function jumpPhase(idx: number) {
    if (!lesson || !lesson.teachingPhases[idx]) return;
    const p = lesson.teachingPhases[idx];
    setCurrentPhase(idx);
    setBoard({
      title: `${p.phase} · ${p.name}`,
      steps: (p.boardSteps || []).map((s, i) => ({ latex: s, note: i === 0 ? p.goal : "", highlight: i === 0 })),
    });
    if (p.teacherSpeech) {
      setMessages((prev) => [...prev, { role: "assistant", content: p.teacherSpeech }]);
    }
  }

  async function playLatestSpeech() {
    const text = [...messages].reverse().find((m) => m.role === "assistant")?.content || "";
    if (!text.trim()) return;
    setPlaying(true);
    setMsg("");
    try {
      const token = getToken();
      const res = await fetch(`${API_BASE}/api/video/tts`, {
        method: "POST",
        headers: { "Content-Type": "application/json", ...(token ? { Authorization: `Bearer ${token}` } : {}) },
        body: JSON.stringify({ text }),
      });
      if (!res.ok) {
        const body = await res.json().catch(() => ({}));
        throw new Error(body?.error || "语音播放失败");
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const audio = new Audio(url);
      audio.onended = () => URL.revokeObjectURL(url);
      await audio.play();
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "语音播放失败");
    } finally {
      setPlaying(false);
    }
  }

  if (!getToken()) return null;

  return (
    <div className="flex flex-col h-[calc(100vh-56px)] bg-gradient-to-br from-slate-50 to-indigo-50">
      <header className="flex items-center justify-between px-6 py-4 bg-white border-b border-gray-100 shadow-sm">
        <div className="flex items-center gap-3">
          <div className="w-9 h-9 rounded-xl bg-indigo-600 flex items-center justify-center text-white font-bold text-lg">
            教
          </div>
          <div>
            <h1 className="font-semibold text-gray-800 text-sm">AI微课</h1>
            <p className="text-xs text-gray-400">入口：生成微课教案</p>
          </div>
        </div>
      </header>

      <div className="flex flex-1 gap-4 p-4 overflow-hidden">
        <div className="flex flex-col w-1/2 bg-white rounded-2xl shadow-sm border border-gray-100 overflow-hidden">
          <div className="flex-1 overflow-y-auto p-4 space-y-3">
            {!hasStarted && (
              <div className="space-y-4">
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1.5">教材标题（可选）</label>
                  <input
                    value={uploadTitle}
                    onChange={(e) => setUploadTitle(e.target.value)}
                    className="w-full bg-gray-100 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
                  />
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1.5">教材文件（txt / md / pdf）</label>
                  <input ref={fileInputRef} type="file" accept=".txt,.md,.pdf,text/plain,text/markdown,application/pdf" className="hidden"
                    onChange={(e) => {
                      const f = e.target.files?.[0];
                      if (f) uploadTextbookByFile(f);
                      e.target.value = "";
                    }} />
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    disabled={uploading}
                    className="w-full border-2 border-dashed border-gray-200 rounded-xl py-3 text-sm text-gray-500 hover:border-indigo-300 hover:text-indigo-500 transition-colors"
                  >
                    {uploading ? "上传中..." : "点击上传教材"}
                  </button>
                </div>
                <div>
                  <label className="block text-xs font-medium text-gray-600 mb-1.5">选择教材</label>
                  <select
                    value={textbookId}
                    onChange={(e) => setTextbookId(e.target.value)}
                    className="w-full bg-gray-100 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
                  >
                    <option value="">（请选择教材）</option>
                    {textbookAssets.map((v) => <option key={v.id} value={v.id}>{v.title}</option>)}
                  </select>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1.5">时长（分钟）</label>
                    <input
                      type="number"
                      min={2}
                      max={10}
                      value={targetMinutes}
                      onChange={(e) => setTargetMinutes(Number(e.target.value))}
                      className="w-full bg-gray-100 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-medium text-gray-600 mb-1.5">学生卡点（可选）</label>
                    <input
                      value={focusQuestion}
                      onChange={(e) => setFocusQuestion(e.target.value)}
                      className="w-full bg-gray-100 rounded-lg px-3 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
                    />
                  </div>
                </div>
                <button
                  type="button"
                  onClick={startLesson}
                  disabled={isThinking || !textbookId}
                  className="w-full py-3 bg-indigo-600 text-white rounded-xl text-sm font-medium hover:bg-indigo-700 disabled:opacity-40 disabled:cursor-not-allowed transition-colors"
                >
                  {isThinking ? "生成中..." : "生成微课教案并进入教室"}
                </button>
              </div>
            )}

            {hasStarted && messages.map((m, i) => (
              <div key={i} className={`flex gap-3 ${m.role === "user" ? "flex-row-reverse" : "flex-row"}`}>
                {m.role === "assistant" && (
                  <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-sm flex-shrink-0">👩‍🏫</div>
                )}
                <div className={`max-w-[80%] px-4 py-3 rounded-2xl text-sm leading-relaxed
                  ${m.role === "user" ? "bg-indigo-600 text-white rounded-tr-sm" : "bg-gray-100 text-gray-800 rounded-tl-sm"}`}>
                  {m.content}
                </div>
              </div>
            ))}

            {isThinking && hasStarted && (
              <div className="flex gap-3">
                <div className="w-8 h-8 rounded-full bg-indigo-100 flex items-center justify-center text-sm flex-shrink-0">👩‍🏫</div>
                <div className="bg-gray-100 px-4 py-3 rounded-2xl rounded-tl-sm flex gap-1 items-center">
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "0ms" }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "150ms" }} />
                  <div className="w-2 h-2 bg-gray-400 rounded-full animate-bounce" style={{ animationDelay: "300ms" }} />
                </div>
              </div>
            )}
            <div ref={messagesEndRef} />
          </div>

          {hasStarted && (
            <div className="px-4 py-3 border-t border-gray-100 flex gap-2">
              <input
                className="flex-1 bg-gray-100 rounded-xl px-4 py-2 text-sm outline-none focus:ring-2 focus:ring-indigo-200"
                placeholder="输入你的问题..."
                value={input}
                onChange={(e) => setInput(e.target.value)}
                onKeyDown={(e) => e.key === "Enter" && sendMessage()}
                disabled={isThinking}
              />
              <button
                onClick={sendMessage}
                disabled={!input.trim() || isThinking}
                className="p-3 rounded-xl bg-indigo-600 text-white hover:bg-indigo-700 disabled:opacity-40"
              >
                发送
              </button>
            </div>
          )}
        </div>

        <div className="w-1/2 flex flex-col gap-3">
          <div className="h-[68%]">
            <MathBoard board={board} isThinking={isThinking} />
          </div>
          <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-3 overflow-y-auto">
            <div className="flex items-center justify-between">
              <p className="text-sm text-gray-500">教案阶段</p>
              <button type="button" onClick={playLatestSpeech} disabled={playing || !hasStarted}
                className="px-3 py-1.5 rounded-lg bg-gray-100 text-gray-700 text-xs hover:bg-gray-200 disabled:opacity-40">
                {playing ? "播放中..." : "播放老师话术"}
              </button>
            </div>
            <div className="flex flex-wrap gap-2 mt-2">
              {(lesson?.teachingPhases || []).map((p, idx) => (
                <button
                  key={idx}
                  type="button"
                  onClick={() => jumpPhase(idx)}
                  className={`px-2.5 py-1 rounded-md text-xs ${idx === currentPhase ? "bg-indigo-600 text-white" : "bg-gray-100 text-gray-700"}`}
                >
                  {p.phase}
                </button>
              ))}
            </div>
          </div>
        </div>
      </div>

      {msg && <p style={{ color: "#ef4444", padding: "0 1rem 0.8rem" }}>{msg}</p>}
    </div>
  );
}

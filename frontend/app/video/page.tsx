"use client";

import { useCallback, useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

type Video = { id: string; title: string; sourceUrl: string; sourceType?: string; status: string };
type PracticeQuestion = { question: string; type: string; hint: string };

export default function VideoPage() {
  const router = useRouter();
  const [url, setUrl] = useState("https://www.bilibili.com/video/BV1xx411c7mD");
  const [videos, setVideos] = useState<Video[]>([]);
  const [vid, setVid] = useState("");
  const [transcript, setTranscript] = useState("");
  const [segA, setSegA] = useState(0);
  const [segB, setSegB] = useState(30);
  const [q, setQ] = useState("这段在讲什么？");
  const [reply, setReply] = useState("");
  const [level, setLevel] = useState("基础");
  const [questionCount, setQuestionCount] = useState(3);
  const [practiceSummary, setPracticeSummary] = useState("");
  const [practiceQuestions, setPracticeQuestions] = useState<PracticeQuestion[]>([]);
  const [msg, setMsg] = useState("");
  const [importing, setImporting] = useState(false);

  const loadVideos = useCallback(async () => {
    try {
      const list = await api<Video[]>("/api/videos");
      setVideos(list);
      if (list.length && !vid) setVid(list[0].id);
    } catch {
      /* ignore */
    }
  }, [vid]);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    else loadVideos();
  }, [router, loadVideos]);

  function normalizeBilibiliInput(input: string) {
    const text = (input || "").trim();
    if (!text) return "";
    const bvMatch = text.match(/BV[a-zA-Z0-9]{10}/);
    if (bvMatch) return `https://www.bilibili.com/video/${bvMatch[0]}/`;
    const urlMatch = text.match(/https?:\/\/[^\s]+/);
    return urlMatch ? urlMatch[0] : text;
  }

  async function importVideo() {
    setMsg("");
    const cleaned = normalizeBilibiliInput(url);
    if (!cleaned) {
      setMsg("请先粘贴 B 站链接或 BV 号");
      return;
    }
    setImporting(true);
    try {
      const v = await api<Video>("/api/videos/import", {
        method: "POST",
        body: JSON.stringify({ sourceType: "bilibili", sourceUrl: cleaned }),
      });
      setVid(v.id);
      setUrl(cleaned);
      setMsg("已导入：" + v.title + "（字幕可下方粘贴兜底）");
      loadVideos();
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "导入失败");
    } finally {
      setImporting(false);
    }
  }

  function extractPageTag(sourceUrl: string) {
    const m = (sourceUrl || "").match(/[?&]p=(\d+)/);
    return m ? `P${m[1]}` : null;
  }

  function formatVideoOption(v: Video) {
    const page = extractPageTag(v.sourceUrl);
    return page ? `${v.title} [${page}]` : v.title;
  }

  async function saveTranscript() {
    if (!vid) return;
    setMsg("");
    try {
      await api("/api/videos/" + vid + "/transcript", {
        method: "PATCH",
        body: JSON.stringify({ transcript }),
      });
      setMsg("字幕/转写已保存");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  async function ask() {
    if (!vid) return setMsg("请先导入或选择视频");
    setMsg("");
    try {
      const r = await api<{ reply: string }>("/api/video/ask", {
        method: "POST",
        body: JSON.stringify({
          videoId: vid,
          segmentStart: segA,
          segmentEnd: segB,
          question: q,
        }),
      });
      setReply(r.reply);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  async function generatePractice() {
    if (!vid) return setMsg("请先导入或选择视频");
    setMsg("");
    try {
      const r = await api<{
        summary: string;
        questions: PracticeQuestion[];
      }>("/api/video/practice", {
        method: "POST",
        body: JSON.stringify({
          videoId: vid,
          segmentStart: segA,
          segmentEnd: segB,
          level,
          questionCount,
        }),
      });
      setPracticeSummary(r.summary || "");
      setPracticeQuestions(r.questions || []);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  if (!getToken()) return null;

  return (
    <main>
      <h1>校内课堂巩固</h1>
      <p style={{ color: "var(--muted)" }}>
        本页用于校内课堂视频巩固复习。教材上传、教材问答、教材微课请前往「自学教材」页面。
      </p>

      <div className="card">
        <label>B 站视频链接</label>
        <input value={url} onChange={(e) => setUrl(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={importVideo} disabled={importing}>
            {importing ? "导入中..." : "导入"}
          </button>
        </div>
        <p style={{ marginTop: "0.5rem", color: "var(--muted)" }}>
          支持粘贴「标题 + 链接」或仅 BV 号，系统会自动提取。
        </p>
      </div>

      <div className="card">
        <h2>我的视频</h2>
        <select
          value={vid}
          onChange={(e) => setVid(e.target.value)}
          style={{
            width: "100%",
            padding: "0.5rem",
            background: "#121a26",
            color: "var(--text)",
            border: "1px solid var(--border)",
            borderRadius: 6,
          }}
        >
          {videos.length === 0 && <option value="">（请先导入）</option>}
          {videos.map((v) => (
            <option key={v.id} value={v.id}>
              {formatVideoOption(v)}
            </option>
          ))}
        </select>
      </div>

      <div className="card">
        <h2>手动字幕 / 转写（兜底）</h2>
        <textarea
          placeholder="无字幕时粘贴讲义或转写文本…"
          value={transcript}
          onChange={(e) => setTranscript(e.target.value)}
        />
        <button type="button" className="secondary" onClick={saveTranscript} style={{ marginTop: "0.5rem" }}>
          保存到该视频
        </button>
      </div>

      <div className="card">
        <h2>按片段提问</h2>
        <div className="row">
          <div style={{ flex: 1 }}>
            <label>片段开始（秒）</label>
            <input type="number" value={segA} onChange={(e) => setSegA(Number(e.target.value))} />
          </div>
          <div style={{ flex: 1 }}>
            <label>片段结束（秒）</label>
            <input type="number" value={segB} onChange={(e) => setSegB(Number(e.target.value))} />
          </div>
        </div>
        <label style={{ marginTop: "0.5rem" }}>问题</label>
        <input value={q} onChange={(e) => setQ(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={ask}>
            问 AI（绑定片段）
          </button>
        </div>
        {reply && <p className="ok" style={{ marginTop: "0.75rem" }}>{reply}</p>}
      </div>

      <div className="card">
        <h2>基于片段生成练习题</h2>
        <div className="row">
          <div>
            <label>难度</label>
            <select
              value={level}
              onChange={(e) => setLevel(e.target.value)}
              style={{ padding: "0.4rem", background: "#121a26", color: "var(--text)", border: "1px solid var(--border)", borderRadius: 6 }}
            >
              <option value="基础">基础</option>
              <option value="进阶">进阶</option>
            </select>
          </div>
          <div>
            <label>题目数量</label>
            <input
              type="number"
              min={1}
              max={6}
              value={questionCount}
              onChange={(e) => setQuestionCount(Number(e.target.value))}
              style={{ width: 100 }}
            />
          </div>
        </div>
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" className="secondary" onClick={generatePractice}>
            生成练习题（不含答案）
          </button>
        </div>
        {practiceSummary && <p className="ok" style={{ marginTop: "0.75rem" }}>{practiceSummary}</p>}
        {practiceQuestions.length > 0 && (
          <ul className="compact" style={{ marginTop: "0.5rem" }}>
            {practiceQuestions.map((q, i) => (
              <li key={i}>
                [{q.type}] {q.question}（提示：{q.hint}）
              </li>
            ))}
          </ul>
        )}
      </div>

      {msg && <p className="err">{msg}</p>}
    </main>
  );
}

"use client";

import { useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

type Video = { id: string; title: string; sourceUrl: string; status: string };
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

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    else loadVideos();
  }, [router]);

  async function loadVideos() {
    try {
      const list = await api<Video[]>("/api/videos");
      setVideos(list);
      if (list.length && !vid) setVid(list[0].id);
    } catch {
      /* ignore */
    }
  }

  async function importVideo() {
    setMsg("");
    try {
      const v = await api<Video>("/api/videos/import", {
        method: "POST",
        body: JSON.stringify({ sourceType: "bilibili", sourceUrl: url }),
      });
      setVid(v.id);
      setMsg("已导入：" + v.title + "（字幕可下方粘贴兜底）");
      loadVideos();
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "导入失败");
    }
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
      <h1>视频学习（B 站 URL）</h1>
      <p style={{ color: "var(--muted)" }}>
        首版解析 BV 号与占位元数据；无字幕时可粘贴转写。提问会写入 video_question 事件。
      </p>

      <div className="card">
        <label>B 站视频链接</label>
        <input value={url} onChange={(e) => setUrl(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={importVideo}>
            导入
          </button>
        </div>
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
              {v.title}
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

"use client";

import { useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

export default function ClassPage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState("");
  const [ts, setTs] = useState(0);
  const [ctx, setCtx] = useState("");
  const [note, setNote] = useState("");
  const [review, setReview] = useState("");
  const [msg, setMsg] = useState("");

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  async function startClass() {
    setMsg("");
    try {
      const s = await api<{ id: string }>("/api/sessions", {
        method: "POST",
        body: JSON.stringify({ type: "CLASS", title: "1对1 课堂" }),
      });
      setSessionId(s.id);
      setMsg("已开始课堂会话：" + s.id);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  async function markConfusion() {
    if (!sessionId) return setMsg("请先开始课堂");
    setMsg("");
    try {
      await api("/api/class/sessions/" + sessionId + "/confusion", {
        method: "POST",
        body: JSON.stringify({ timestampSec: ts, context: ctx }),
      });
      setMsg("已记录「不懂」标记（带时间戳）");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  async function saveNote() {
    if (!sessionId) return setMsg("请先开始课堂");
    setMsg("");
    try {
      await api("/api/class/sessions/" + sessionId + "/notes", {
        method: "POST",
        body: JSON.stringify({ text: note }),
      });
      setMsg("笔记已保存为事件");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  async function loadReview() {
    if (!sessionId) return setMsg("请先开始课堂");
    setMsg("");
    try {
      const r = await api<{ summary: string }>(
        "/api/class/sessions/" + sessionId + "/review"
      );
      setReview(r.summary);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  if (!getToken()) return null;

  return (
    <main>
      <h1>1 对 1 上课留痕</h1>
      <p style={{ color: "var(--muted)" }}>
        录音/视频可后续接入；此处演示「不懂」标记（带时间戳）与轻量笔记，并写入 learning_event。
      </p>

      <div className="card">
        <button type="button" onClick={startClass}>
          开始本节课堂会话
        </button>
        {sessionId && (
          <p style={{ marginTop: "0.75rem", fontSize: "0.9rem" }}>
            当前 sessionId：<code>{sessionId}</code>
          </p>
        )}
      </div>

      <div className="card">
        <h2>这里没懂</h2>
        <label>时间戳（秒，相对本节课）</label>
        <input
          type="number"
          value={ts}
          onChange={(e) => setTs(Number(e.target.value))}
        />
        <label style={{ marginTop: "0.5rem" }}>上下文（如：老师讲分式通分）</label>
        <input value={ctx} onChange={(e) => setCtx(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={markConfusion}>
            标记不懂
          </button>
        </div>
      </div>

      <div className="card">
        <h2>轻量笔记</h2>
        <textarea value={note} onChange={(e) => setNote(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={saveNote}>
            保存笔记
          </button>
        </div>
      </div>

      <div className="card">
        <h2>课后复盘（学生）</h2>
        <button type="button" className="secondary" onClick={loadReview}>
          生成复盘摘要
        </button>
        {review && <p className="ok" style={{ marginTop: "0.75rem" }}>{review}</p>}
      </div>

      {msg && <p className={msg.includes("失败") || msg.includes("无权") ? "err" : "ok"}>{msg}</p>}
    </main>
  );
}

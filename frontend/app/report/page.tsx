"use client";

import { useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

export default function ReportPage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState("");
  const [summary, setSummary] = useState("");
  const [role, setRole] = useState("");
  const [msg, setMsg] = useState("");

  useEffect(() => {
    if (!getToken()) router.replace("/login");
    else {
      api<{ role: string }>("/api/me")
        .then((m) => setRole(String(m.role)))
        .catch(() => {});
    }
  }, [router]);

  async function loadReport() {
    if (!sessionId.trim()) return setMsg("请填写 sessionId");
    setMsg("");
    try {
      const r = await api<{ summary: string }>("/api/reports/session/" + sessionId.trim());
      setSummary(r.summary);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  if (!getToken()) return null;

  return (
    <main>
      <h1>学情总结 / 老师复盘</h1>
      <p style={{ color: "var(--muted)" }}>
        输入任意学习会话 ID（课堂 / 视频自动会话 / 作业会话均可）。学生仅可看自己会话；老师账号可看全部。
        当前角色：<strong>{role || "…"}</strong>
      </p>

      <div className="card">
        <label>sessionId</label>
        <input
          placeholder="从「上课」或作业/视频页复制"
          value={sessionId}
          onChange={(e) => setSessionId(e.target.value)}
        />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={loadReport}>
            生成报告（聚合事件 + 作业 + 视频摘要）
          </button>
        </div>
        {summary && <p className="ok" style={{ marginTop: "0.75rem" }}>{summary}</p>}
      </div>

      {msg && <p className="err">{msg}</p>}
    </main>
  );
}

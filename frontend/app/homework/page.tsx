"use client";

import { useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

export default function HomeworkPage() {
  const router = useRouter();
  const [question, setQuestion] = useState("解方程：2x + 3 = 11");
  const [steps, setSteps] = useState("我还没写步骤");
  const [attemptId, setAttemptId] = useState<number | null>(null);
  const [reply, setReply] = useState("");
  const [level, setLevel] = useState(0);
  const [total, setTotal] = useState(0);
  const [msg, setMsg] = useState("");

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  async function hint() {
    setMsg("");
    try {
      const body: Record<string, unknown> = {
        questionText: question,
        studentAnswer: steps,
      };
      if (attemptId != null) body.homeworkAttemptId = attemptId;
      const r = await api<{
        homeworkAttemptId: number;
        hintLevel: number;
        hintsUsedTotal: number;
        reply: string;
      }>("/api/homework/hint", {
        method: "POST",
        body: JSON.stringify(body),
      });
      setAttemptId(r.homeworkAttemptId);
      setLevel(r.hintLevel);
      setTotal(r.hintsUsedTotal);
      setReply(r.reply);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "失败");
    }
  }

  if (!getToken()) return null;

  return (
    <main>
      <h1>作业辅导</h1>
      <p style={{ color: "var(--muted)" }}>
        服务端按次数决定提示等级（1–4），不直接给最终答案；每次请求写入 homework_hint_used 事件。
      </p>

      <div className="card">
        <label>题目</label>
        <textarea value={question} onChange={(e) => setQuestion(e.target.value)} />
        <label style={{ marginTop: "0.5rem" }}>我的思路 / 步骤</label>
        <textarea value={steps} onChange={(e) => setSteps(e.target.value)} />
        <div style={{ marginTop: "0.75rem" }}>
          <button type="button" onClick={hint}>
            求提示（下一层）
          </button>
        </div>
        {(level > 0 || total > 0) && (
          <p style={{ marginTop: "0.75rem", fontSize: "0.85rem", color: "var(--muted)" }}>
            本轮等级：{level}；累计提示：{total}；attemptId：{attemptId}
          </p>
        )}
        {reply && <p className="ok" style={{ marginTop: "0.75rem" }}>{reply}</p>}
      </div>

      <div className="card">
        <h2>拒答话术（文档 10.6）</h2>
        <button
          type="button"
          className="secondary"
          onClick={async () => {
            const r = await api<{ message: string }>("/api/ai/refuse-full-answer");
            setReply(r.message);
          }}
        >
          查看服务端固定拒答文案
        </button>
      </div>

      {msg && <p className="err">{msg}</p>}
    </main>
  );
}

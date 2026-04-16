"use client";

import { useEffect, useState } from "react";
import { api, getRole, getToken, getUserName } from "@/lib/api";
import { useRouter } from "next/navigation";

type Observation = {
  id: number;
  studentId: number;
  type: string;
  category: string;
  content: string;
  evidence?: string;
  createdAt: string;
};

type PatternItem = {
  id: number;
  studentId: number;
  type: string;
  confidenceScore: number;
  evidenceSummary: string;
  confirmed: boolean;
  createdAt: string;
};

type StrategyItem = {
  id: number;
  studentId: number;
  patternId: number;
  suggestion: string;
  priority: number;
  active: boolean;
  createdAt: string;
};

export default function ClassPage() {
  const router = useRouter();
  const [role, setRole] = useState<string | null>(null);
  const [userName, setUserName] = useState<string>("");

  // student view states
  const [sessionId, setSessionId] = useState("");
  const [ts, setTs] = useState(0);
  const [ctx, setCtx] = useState("");
  const [note, setNote] = useState("");
  const [review, setReview] = useState("");

  // teacher view states
  const [targetStudentId, setTargetStudentId] = useState(1);
  const [observations, setObservations] = useState<Observation[]>([]);
  const [patterns, setPatterns] = useState<PatternItem[]>([]);
  const [strategies, setStrategies] = useState<StrategyItem[]>([]);
  const [teacherLoading, setTeacherLoading] = useState(false);
  const [seedLoading, setSeedLoading] = useState(false);
  const [seededStudents, setSeededStudents] = useState<{name: string; id: number}[]>([]);

  const [msg, setMsg] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    setRole(getRole());
    setUserName(getUserName() || "");
  }, [router]);

  useEffect(() => {
    if (role === "TEACHER" && targetStudentId > 0) {
      loadTeacherData();
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [role, targetStudentId]);

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

  async function loadTeacherData() {
    setTeacherLoading(true);
    setMsg("");
    try {
      const [obs, ps, sts] = await Promise.all([
        api<Observation[]>(`/api/observation/student/${targetStudentId}?limit=20`),
        api<PatternItem[]>(`/api/pattern/student/${targetStudentId}?confirmed=false`),
        api<StrategyItem[]>(`/api/strategy/student/${targetStudentId}?activeOnly=true`),
      ]);
      setObservations(obs);
      setPatterns(ps);
      setStrategies(sts);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "加载老师驾驶舱失败");
    } finally {
      setTeacherLoading(false);
    }
  }

  async function seedDemo() {
    setSeedLoading(true);
    setMsg("");
    setSeededStudents([]);
    try {
      const r = await api<{ message: string; students: {name: string; id: number}[] }>("/api/demo/seed", { method: "POST" });
      setSeededStudents(r.students || []);
      setMsg("✅ " + r.message);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "演示数据生成失败");
    } finally {
      setSeedLoading(false);
    }
  }

  async function generatePattern() {
    setMsg("");
    try {
      await api<PatternItem[]>("/api/pattern/generate", {
        method: "POST",
        body: JSON.stringify({ studentId: targetStudentId }),
      });
      await loadTeacherData();
      setMsg("已完成模式生成");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "模式生成失败");
    }
  }

  async function confirmPattern(patternId: number, confirmed: boolean) {
    setMsg("");
    try {
      await api<PatternItem>("/api/pattern/confirm", {
        method: "POST",
        body: JSON.stringify({ patternId, confirmed }),
      });
      await loadTeacherData();
      setMsg(confirmed ? "已确认该模式" : "已忽略该模式");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "操作失败");
    }
  }

  async function generateStrategy(patternId?: number) {
    setMsg("");
    try {
      await api<StrategyItem[]>("/api/strategy/generate", {
        method: "POST",
        body: JSON.stringify({
          studentId: targetStudentId,
          patternId: patternId || null,
        }),
      });
      await loadTeacherData();
      setMsg("已生成教学策略");
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "策略生成失败");
    }
  }

  if (!getToken()) return null;

  if (role === "TEACHER") {
    return (
      <main style={{ maxWidth: 1200 }}>
        <h1>1对1 上课（老师驾驶舱）</h1>
        <p style={{ color: "var(--muted)" }}>
          欢迎你，{userName || "老师"}。认知层链路：Observation → Pattern → Strategy（最终由老师决策）。
        </p>

        <div className="card">
          <div className="row">
            <label style={{ marginBottom: 0 }}>目标学生 ID</label>
            <input
              style={{ width: 180 }}
              type="number"
              min={1}
              value={targetStudentId}
              onChange={(e) => setTargetStudentId(Number(e.target.value || 1))}
            />
            <button type="button" onClick={loadTeacherData} disabled={teacherLoading}>
              刷新看板
            </button>
            <button type="button" className="secondary" onClick={generatePattern}>
              生成模式
            </button>
            <button type="button" className="secondary" onClick={() => generateStrategy()}>
              生成策略
            </button>
          </div>
          <div className="row" style={{ marginTop: "0.75rem", borderTop: "1px solid var(--border)", paddingTop: "0.75rem" }}>
            <span style={{ color: "var(--muted)", fontSize: "0.85rem" }}>演示模式</span>
            <button type="button" onClick={seedDemo} disabled={seedLoading}
              style={{ background: "#f59e0b", color: "#fff", border: "none" }}>
              {seedLoading ? "生成中..." : "一键加载演示数据"}
            </button>
            <span style={{ color: "var(--muted)", fontSize: "0.8rem" }}>
              生成小明（依赖提示）、小红（步骤卡壳）、小华（迁移困难）3个模拟学生
            </span>
          </div>
        </div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "1rem" }}>
          <div className="card" style={{ marginBottom: 0 }}>
            <h2>观察区（系统自动）</h2>
            {observations.length === 0 ? (
              <p style={{ color: "var(--muted)" }}>暂无 Observation 数据</p>
            ) : (
              <ul className="compact">
                {observations.slice(0, 12).map((o) => (
                  <li key={o.id}>
                    [{o.category}] {o.content}
                  </li>
                ))}
              </ul>
            )}
          </div>

          <div className="card" style={{ marginBottom: 0 }}>
            <h2>模式区（需确认）</h2>
            {patterns.length === 0 ? (
              <p style={{ color: "var(--muted)" }}>暂无待确认 Pattern</p>
            ) : (
              <div>
                {patterns.map((p) => (
                  <div
                    key={p.id}
                    style={{
                      border: "1px solid var(--border)",
                      borderRadius: 8,
                      padding: "0.6rem",
                      marginBottom: "0.6rem",
                    }}
                  >
                    <p style={{ margin: 0 }}>
                      ⚠️ 可能存在【{p.type}】（置信度 {Number(p.confidenceScore).toFixed(2)}）
                    </p>
                    <p style={{ margin: "0.3rem 0", color: "var(--muted)", fontSize: "0.85rem" }}>
                      {p.evidenceSummary}
                    </p>
                    <div className="row">
                      <button type="button" onClick={() => confirmPattern(p.id, true)}>
                        确认
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => confirmPattern(p.id, false)}
                      >
                        忽略
                      </button>
                      <button
                        type="button"
                        className="secondary"
                        onClick={() => generateStrategy(p.id)}
                      >
                        基于该模式生成策略
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>

          <div className="card" style={{ marginBottom: 0 }}>
            <h2>策略区（核心）</h2>
            {strategies.length === 0 ? (
              <p style={{ color: "var(--muted)" }}>暂无策略，先确认 Pattern 再生成</p>
            ) : (
              <ul className="compact">
                {strategies.map((s) => (
                  <li key={s.id}>
                    [P{s.priority}] {s.suggestion}
                  </li>
                ))}
              </ul>
            )}
          </div>
        </div>

        {msg && <p className={msg.includes("失败") || msg.includes("无权") ? "err" : "ok"}>{msg}</p>}
        {seededStudents.length > 0 && (
          <div style={{ marginTop: "0.5rem", fontSize: "0.9rem", color: "var(--muted)" }}>
            {seededStudents.map(s => (
              <span key={s.id} style={{ marginRight: "1.5rem" }}>
                {s.name}（学生 ID：<strong style={{ color: "var(--fg)" }}>{s.id}</strong>）
              </span>
            ))}
          </div>
        )}
      </main>
    );
  }

  return (
    <main>
      <h1>1 对 1 上课（学生）</h1>
      <p style={{ color: "var(--muted)" }}>
        你可以在课堂中标记“没懂”、记录笔记并在课后生成复盘。
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

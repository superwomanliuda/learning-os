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
  status?: string;
  severity?: string;
  trend?: string;
  evidenceIds?: string;
  falsePositiveRisks?: string;
  createdAt: string;
};

type InsightItem = {
  id: number;
  patternId: number;
  insightText: string;
  possibleCauses?: string;
  teachingImplications?: string;
  confidence: number;
  status: string;
};

type DecisionItem = {
  id: number;
  lessonFocus: string;
  practicePolicy: string;
  interactionPolicy: string;
  materialPolicy: string;
  difficultyPolicy: string;
  status: string;
};

type PipelineResult = {
  observationsCount?: number;
  newPatterns?: PatternItem[];
  pendingPatterns?: PatternItem[];
  newInsights?: InsightItem[];
  confirmedPatterns?: PatternItem[];
  insights?: InsightItem[];
  decision?: DecisionItem;
  stage?: string;
  nextAction?: string;
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
  const [speakText, setSpeakText] = useState("");
  const [explainText, setExplainText] = useState("");

  // teacher view states
  const [targetStudentId, setTargetStudentId] = useState<number | "">("");
  const [observations, setObservations] = useState<Observation[]>([]);
  const [patterns, setPatterns] = useState<PatternItem[]>([]);
  const [strategies, setStrategies] = useState<StrategyItem[]>([]);
  const [teacherLoading, setTeacherLoading] = useState(false);
  const [seedLoading, setSeedLoading] = useState(false);
  const [seededStudents, setSeededStudents] = useState<{name: string; id: number}[]>([]);

  // v2 pipeline states
  const [pipelineLoading, setPipelineLoading] = useState(false);
  const [pipelineResult, setPipelineResult] = useState<PipelineResult | null>(null);
  const [reviewLoading, setReviewLoading] = useState(false);

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
    if (role === "TEACHER" && targetStudentId && targetStudentId > 0) {
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

  async function reportStudentEvent(eventType: string, payload: Record<string, unknown>) {
    if (!sessionId) {
      setMsg("请先开始课堂");
      return false;
    }
    try {
      await api("/api/events", {
        method: "POST",
        body: JSON.stringify({
          sessionId,
          eventType,
          timestampSec: ts,
          payload,
        }),
      });
      return true;
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "事件上报失败");
      return false;
    }
  }

  async function quickSpeak(message: string) {
    const ok = await reportStudentEvent("student_speak", { message });
    if (ok) setMsg("已记录：" + message);
  }

  async function submitFreeSpeak() {
    if (!speakText.trim()) {
      setMsg("先写一句你想说的");
      return;
    }
    const ok = await reportStudentEvent("student_speak", { message: speakText.trim() });
    if (ok) {
      setMsg("已记录：" + speakText.trim());
      setSpeakText("");
    }
  }

  async function submitExplanation() {
    if (!explainText.trim()) {
      setMsg("先写一句解释");
      return;
    }
    const text = explainText.trim();
    const isVague =
      text.length < 8 || /反正|差不多|就是这样|大概|可能吧/.test(text);
    const ok = await reportStudentEvent("explanation_response", {
      content: text,
      quality: isVague ? "vague" : "clear",
    });
    if (ok) {
      setMsg(isVague ? "已提交解释（系统判定含混）" : "已提交解释");
      setExplainText("");
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

  async function seedNumberLineCase() {
    setSeedLoading(true);
    setMsg("");
    setSeededStudents([]);
    setPipelineResult(null);
    try {
      const r = await api<{ message: string; students: {name: string; id: number}[]; lessonTitle?: string }>(
        "/api/demo/seed-numberline", { method: "POST" }
      );
      setSeededStudents(r.students || []);
      setMsg("✅ " + r.message + (r.lessonTitle ? ` —— ${r.lessonTitle}` : ""));
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "数轴案例加载失败");
    } finally {
      setSeedLoading(false);
    }
  }

  async function runPipeline() {
    if (!targetStudentId) {
      setMsg("请先选择目标学生 ID");
      return;
    }
    setPipelineLoading(true);
    setMsg("");
    try {
      const r = await api<PipelineResult>("/api/pipeline/run", {
        method: "POST",
        body: JSON.stringify({ studentId: targetStudentId }),
      });
      setPipelineResult(r);
      await loadTeacherData();
      setMsg(`✅ 诊断完成：观察 ${r.observationsCount} 条，新增 ${r.newPatterns?.length ?? 0} 个 pattern 候选`);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "诊断失败");
    } finally {
      setPipelineLoading(false);
    }
  }

  async function runDecision() {
    if (!targetStudentId) {
      setMsg("请先选择目标学生 ID");
      return;
    }
    setPipelineLoading(true);
    setMsg("");
    try {
      const r = await api<PipelineResult>("/api/pipeline/decision", {
        method: "POST",
        body: JSON.stringify({ studentId: targetStudentId }),
      });
      setPipelineResult(r);
      if (r.stage === "no_confirmed_pattern") {
        setMsg("⚠️ 暂无已确认的 Pattern，请先在下方 review 卡片确认或修改至少一个");
      } else {
        setMsg("✅ 教学方向已生成");
      }
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "教学方向生成失败");
    } finally {
      setPipelineLoading(false);
    }
  }

  async function reviewPattern(patternId: number, feedbackType: "confirm" | "reject" | "modify" | "uncertain", correctedType?: string, teacherText?: string) {
    setReviewLoading(true);
    setMsg("");
    try {
      await api("/api/teacher-review", {
        method: "POST",
        body: JSON.stringify({
          studentId: targetStudentId,
          targetType: "pattern",
          targetId: patternId,
          feedbackType,
          correctedPatternType: correctedType,
          teacherText,
        }),
      });
      await loadTeacherData();
      setMsg(`✅ 已记录反馈：${feedbackType}${correctedType ? ` → ${correctedType}` : ""}`);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "反馈失败");
    } finally {
      setReviewLoading(false);
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
              type="text"
              inputMode="numeric"
              pattern="[0-9]*"
              placeholder="输入学生 ID"
              value={targetStudentId}
              onChange={(e) => {
                const v = e.target.value.replace(/[^0-9]/g, "");
                setTargetStudentId(v === "" ? "" : Number(v));
              }}
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
              {seedLoading ? "生成中..." : "加载方程作业演示"}
            </button>
            <button type="button" onClick={seedNumberLineCase} disabled={seedLoading}
              style={{ background: "#7c3aed", color: "#fff", border: "none" }}>
              {seedLoading ? "生成中..." : "📺 加载数轴课案例（视频实录）"}
            </button>
          </div>
          <div className="row" style={{ marginTop: "0.75rem", borderTop: "1px solid var(--border)", paddingTop: "0.75rem" }}>
            <span style={{ color: "var(--muted)", fontSize: "0.85rem" }}>v2 诊断流水线</span>
            <button type="button" onClick={runPipeline} disabled={pipelineLoading}
              style={{ background: "#0ea5e9", color: "#fff", border: "none" }}>
              {pipelineLoading ? "诊断中..." : "🔍 运行诊断（observation→pattern→insight）"}
            </button>
            <button type="button" onClick={runDecision} disabled={pipelineLoading} className="secondary">
              📋 生成教学方向（需先 review）
            </button>
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

        {pipelineResult && (
          <div className="card" style={{ background: "#f8fafc", border: "1px solid #cbd5e1" }}>
            <h2>v2 诊断结果</h2>
            <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>
              阶段：<strong>{pipelineResult.stage}</strong>
              {pipelineResult.observationsCount !== undefined && (
                <> · observation 总数：{pipelineResult.observationsCount}</>
              )}
            </p>

            {pipelineResult.newPatterns && pipelineResult.newPatterns.length > 0 && (
              <div style={{ marginTop: "0.5rem" }}>
                <h3 style={{ fontSize: "0.95rem" }}>本次新增 Pattern 候选（{pipelineResult.newPatterns.length}）</h3>
                {pipelineResult.newPatterns.map((p) => {
                  const insight = pipelineResult.newInsights?.find((i) => i.patternId === p.id);
                  return (
                    <div key={p.id} style={{
                      border: "1px solid #cbd5e1", borderRadius: 8, padding: "0.7rem",
                      marginBottom: "0.6rem", background: "#fff"
                    }}>
                      <div style={{ display: "flex", justifyContent: "space-between" }}>
                        <strong>⚠️ {p.type}</strong>
                        <span style={{ color: "var(--muted)", fontSize: "0.85rem" }}>
                          置信度 {Number(p.confidenceScore).toFixed(2)}
                          {p.severity && ` · 严重度 ${p.severity}`}
                          {p.trend && ` · 趋势 ${p.trend}`}
                        </span>
                      </div>
                      <p style={{ margin: "0.3rem 0", color: "var(--muted)", fontSize: "0.85rem" }}>
                        {p.evidenceSummary}
                      </p>
                      {p.falsePositiveRisks && (
                        <p style={{ margin: "0.3rem 0", color: "#b45309", fontSize: "0.78rem" }}>
                          可能误判：{(JSON.parse(p.falsePositiveRisks) as string[]).join("、")}
                        </p>
                      )}
                      {insight && (
                        <div style={{
                          background: "#f1f5f9", borderRadius: 6, padding: "0.5rem",
                          margin: "0.5rem 0", fontSize: "0.88rem"
                        }}>
                          💡 <strong>AI 解读：</strong>{insight.insightText}
                        </div>
                      )}
                      <div className="row" style={{ marginTop: "0.5rem", flexWrap: "wrap", gap: "0.4rem" }}>
                        <button type="button" disabled={reviewLoading}
                          onClick={() => reviewPattern(p.id, "confirm")}>
                          ✓ 确认
                        </button>
                        <button type="button" className="secondary" disabled={reviewLoading}
                          onClick={() => reviewPattern(p.id, "reject")}>
                          ✗ 不准确
                        </button>
                        <button type="button" className="secondary" disabled={reviewLoading}
                          onClick={() => reviewPattern(p.id, "uncertain")}>
                          ⏳ 继续观察
                        </button>
                        <button type="button" className="secondary" disabled={reviewLoading}
                          onClick={() => {
                            const v = prompt("修改为新的 Pattern 类型（如 modeling_entry_difficulty）：", p.type);
                            if (v && v.trim() && v.trim() !== p.type) {
                              const reason = prompt("用一句话说明为什么修改（可选）：", "") || undefined;
                              reviewPattern(p.id, "modify", v.trim(), reason);
                            }
                          }}>
                          ✎ 修改类型
                        </button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {pipelineResult.decision && (
              <div style={{ marginTop: "1rem" }}>
                <h3 style={{ fontSize: "0.95rem" }}>📋 下节课教学方向</h3>
                <div style={{ background: "#fff", border: "1px solid #cbd5e1", borderRadius: 8, padding: "0.8rem" }}>
                  <p><strong>教学重点：</strong>{pipelineResult.decision.lessonFocus}</p>
                  <p><strong>练习策略：</strong>{pipelineResult.decision.practicePolicy}</p>
                  <p><strong>互动策略：</strong>{pipelineResult.decision.interactionPolicy}</p>
                  <p><strong>材料策略：</strong>{pipelineResult.decision.materialPolicy}</p>
                  <p><strong>难度策略：</strong>{pipelineResult.decision.difficultyPolicy}</p>
                </div>
              </div>
            )}

            {pipelineResult.nextAction && (
              <p style={{ marginTop: "0.6rem", color: "#0369a1", fontSize: "0.85rem" }}>
                👉 {pipelineResult.nextAction}
              </p>
            )}
          </div>
        )}

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
        <h2>我的反应（系统会记录作为诊断证据）</h2>
        <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>
          课堂中随时可点。每次都会带上当前时间戳（{ts} 秒）。
        </p>
        <div className="row" style={{ flexWrap: "wrap", gap: "0.5rem" }}>
          <button type="button" className="secondary" onClick={() => quickSpeak("我卡住了")}>
            🚧 我卡住了
          </button>
          <button type="button" className="secondary" onClick={() => quickSpeak("我有想法")}>
            💡 我有想法
          </button>
          <button type="button" className="secondary" onClick={() => quickSpeak("我不懂这一步")}>
            ❓ 我不懂这一步
          </button>
          <button type="button" className="secondary" onClick={() => quickSpeak("老师这样对吗")}>
            🙋 老师这样对吗
          </button>
          <button type="button" className="secondary" onClick={() => quickSpeak("我不知道从哪开始")}>
            😶 不知道从哪开始
          </button>
        </div>

        <label style={{ marginTop: "0.9rem" }}>自由表达（一句话即可）</label>
        <textarea
          value={speakText}
          onChange={(e) => setSpeakText(e.target.value)}
          placeholder="如：'反正就是这样移过去'、'我想试试自己写第一步'..."
          style={{ minHeight: 50 }}
        />
        <div style={{ marginTop: "0.5rem" }}>
          <button type="button" onClick={submitFreeSpeak}>
            提交我的表达
          </button>
        </div>

        <label style={{ marginTop: "0.9rem" }}>解释一下你刚才那一步为什么这样做</label>
        <textarea
          value={explainText}
          onChange={(e) => setExplainText(e.target.value)}
          placeholder="老师让你解释时使用。系统会判断你的解释是清晰还是含混。"
          style={{ minHeight: 50 }}
        />
        <div style={{ marginTop: "0.5rem" }}>
          <button type="button" className="secondary" onClick={submitExplanation}>
            提交解释
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

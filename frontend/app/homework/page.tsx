"use client";

import { useEffect, useRef, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";

type Step = {
  index: number;
  content: string;
  durationSec: number;
  submittedAt: number; // ms timestamp
  revised: boolean;
};

const HESITATION_THRESHOLD_SEC = 30;

export default function HomeworkPage() {
  const router = useRouter();
  const [sessionId, setSessionId] = useState<string>("");
  const [question, setQuestion] = useState("解方程：2x + 3 = 11");
  const [knowledgePoint, setKnowledgePoint] = useState("一元一次方程");
  const [started, setStarted] = useState(false);

  const [steps, setSteps] = useState<Step[]>([]);
  const [currentStepInput, setCurrentStepInput] = useState("");
  const [stepStartedAt, setStepStartedAt] = useState<number | null>(null);

  const [explanation, setExplanation] = useState("");

  const [attemptId, setAttemptId] = useState<number | null>(null);
  const [hintReply, setHintReply] = useState("");
  const [hintLevel, setHintLevel] = useState(0);
  const [hintsUsed, setHintsUsed] = useState(0);

  const [submitted, setSubmitted] = useState(false);
  const [msg, setMsg] = useState("");

  const lastInputAtRef = useRef<number | null>(null);
  const hesitationFiredRef = useRef<boolean>(false);

  useEffect(() => {
    if (!getToken()) router.replace("/login");
  }, [router]);

  // 自动停顿检测：每 5 秒检查一次"距离上次输入"
  useEffect(() => {
    if (!started || submitted) return;
    const timer = setInterval(() => {
      if (!stepStartedAt || hesitationFiredRef.current) return;
      const lastTouch = lastInputAtRef.current ?? stepStartedAt;
      const idleSec = Math.floor((Date.now() - lastTouch) / 1000);
      if (idleSec >= HESITATION_THRESHOLD_SEC && currentStepInput.trim().length === 0) {
        hesitationFiredRef.current = true;
        reportEvent("hesitation", {
          pause_sec: idleSec,
          step: steps.length + 1,
        });
      }
    }, 5000);
    return () => clearInterval(timer);
  }, [started, submitted, stepStartedAt, currentStepInput, steps.length]);

  async function reportEvent(eventType: string, payload: Record<string, unknown>) {
    if (!sessionId) return;
    try {
      await api("/api/events", {
        method: "POST",
        body: JSON.stringify({
          sessionId,
          eventType,
          timestampSec: Math.floor((Date.now() - (stepStartedAt ?? Date.now())) / 1000),
          payload,
        }),
      });
    } catch (e) {
      console.warn("event report failed", eventType, e);
    }
  }

  async function startAttempt() {
    setMsg("");
    try {
      const s = await api<{ id: string }>("/api/sessions", {
        method: "POST",
        body: JSON.stringify({ type: "HOMEWORK", title: "作业辅导（分步答题）" }),
      });
      setSessionId(s.id);
      setStarted(true);
      const now = Date.now();
      setStepStartedAt(now);
      lastInputAtRef.current = now;
      hesitationFiredRef.current = false;
      // 标记题目开始
      await api("/api/events", {
        method: "POST",
        body: JSON.stringify({
          sessionId: s.id,
          eventType: "question_start",
          timestampSec: 0,
          payload: { question, knowledge_point: knowledgePoint },
        }),
      });
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "启动答题失败");
    }
  }

  function handleInputChange(v: string) {
    setCurrentStepInput(v);
    lastInputAtRef.current = Date.now();
    hesitationFiredRef.current = false;
  }

  async function commitStep() {
    if (!currentStepInput.trim()) {
      setMsg("先写下你这一步的内容");
      return;
    }
    setMsg("");
    const idx = steps.length + 1;
    const now = Date.now();
    const durationSec = stepStartedAt ? Math.floor((now - stepStartedAt) / 1000) : 0;
    const newStep: Step = {
      index: idx,
      content: currentStepInput.trim(),
      durationSec,
      submittedAt: now,
      revised: false,
    };
    setSteps((prev) => [...prev, newStep]);

    await reportEvent("step_submit", {
      step: idx,
      content: newStep.content,
      duration_sec: durationSec,
      correct: null, // 学生侧不自评对错
    });

    setCurrentStepInput("");
    setStepStartedAt(now);
    lastInputAtRef.current = now;
    hesitationFiredRef.current = false;
  }

  async function reviseLastStep(newContent: string) {
    if (steps.length === 0) return;
    const last = steps[steps.length - 1];
    const updated: Step = { ...last, content: newContent.trim(), revised: true };
    setSteps((prev) => [...prev.slice(0, -1), updated]);
    await reportEvent("step_revision", {
      step: last.index,
      previous: last.content,
      new_content: newContent.trim(),
    });
  }

  async function requestHint() {
    setMsg("");
    if (!sessionId) {
      setMsg("请先开始答题");
      return;
    }
    const stepsText = steps.map((s) => `第${s.index}步：${s.content}`).join("\n") +
      (currentStepInput.trim() ? `\n当前正在写：${currentStepInput.trim()}` : "");

    // 先打求助事件
    await reportEvent("help_request", {
      within_sec: stepStartedAt ? Math.floor((Date.now() - stepStartedAt) / 1000) : 0,
      message: "求提示",
      step: steps.length + 1,
      hints_used_so_far: hintsUsed,
    });

    try {
      const r = await api<{
        homeworkAttemptId: number;
        hintLevel: number;
        hintsUsedTotal: number;
        reply: string;
      }>("/api/homework/hint", {
        method: "POST",
        body: JSON.stringify({
          questionText: question,
          studentAnswer: stepsText || "（学生还没写步骤）",
          homeworkAttemptId: attemptId,
          sessionId,
        }),
      });
      setAttemptId(r.homeworkAttemptId);
      setHintLevel(r.hintLevel);
      setHintsUsed(r.hintsUsedTotal);
      setHintReply(r.reply);
    } catch (e: unknown) {
      setMsg(e instanceof Error ? e.message : "提示获取失败");
    }
  }

  async function submitExplanation() {
    if (!explanation.trim()) {
      setMsg("先写一句解释");
      return;
    }
    // 简单本地启发判断 quality（vague: <8 字 / 含"反正"/"差不多"/"就是这样"）
    const text = explanation.trim();
    const isVague =
      text.length < 8 ||
      /反正|差不多|就是这样|大概|可能吧/.test(text);
    await reportEvent("explanation_response", {
      content: text,
      quality: isVague ? "vague" : "clear",
    });
    setMsg(isVague ? "已提交解释（系统判定含混，会作为观察证据）" : "已提交解释");
    setExplanation("");
  }

  async function submitAnswer(correct: boolean) {
    setMsg("");
    if (steps.length === 0) {
      setMsg("还没写步骤，先写至少一步");
      return;
    }
    await reportEvent("answer_submit", {
      correct,
      total_steps: steps.length,
      total_time_sec: Math.floor(
        (Date.now() - (steps[0]?.submittedAt ?? Date.now())) / 1000
      ),
      hints_used: hintsUsed,
    });
    setSubmitted(true);
    setMsg(correct ? "已提交（自报正确）" : "已提交（自报错误，系统会作为重点证据）");
  }

  async function markStuck() {
    await reportEvent("student_speak", {
      message: "我卡住了",
      step: steps.length + 1,
    });
    setMsg("已记录'卡住了'信号");
  }

  if (!getToken()) return null;

  return (
    <main style={{ maxWidth: 900 }}>
      <h1>作业辅导（分步答题）</h1>
      <p style={{ color: "var(--muted)" }}>
        每写完一步点'完成这一步'。系统会记录每步耗时、停顿、求助和解释行为，作为后续诊断证据。
      </p>

      {!started && (
        <div className="card">
          <label>题目</label>
          <textarea value={question} onChange={(e) => setQuestion(e.target.value)} />
          <label style={{ marginTop: "0.5rem" }}>知识点（可选）</label>
          <input
            value={knowledgePoint}
            onChange={(e) => setKnowledgePoint(e.target.value)}
            placeholder="如：一元一次方程"
          />
          <div style={{ marginTop: "0.75rem" }}>
            <button type="button" onClick={startAttempt}>
              开始答题
            </button>
          </div>
        </div>
      )}

      {started && (
        <>
          <div className="card">
            <h2>题目</h2>
            <p style={{ whiteSpace: "pre-wrap" }}>{question}</p>
            {knowledgePoint && (
              <p style={{ color: "var(--muted)", fontSize: "0.85rem" }}>
                知识点：{knowledgePoint}
              </p>
            )}
          </div>

          <div className="card">
            <h2>我的解题步骤</h2>
            {steps.length === 0 ? (
              <p style={{ color: "var(--muted)" }}>还没写第一步，下面开始 ↓</p>
            ) : (
              <ol style={{ paddingLeft: "1.5rem" }}>
                {steps.map((s) => (
                  <li
                    key={s.index}
                    style={{ marginBottom: "0.6rem" }}
                  >
                    <div style={{ whiteSpace: "pre-wrap" }}>
                      {s.content}
                      {s.revised && (
                        <span style={{ color: "#f59e0b", marginLeft: 8, fontSize: "0.8rem" }}>
                          已修改
                        </span>
                      )}
                    </div>
                    <div style={{ color: "var(--muted)", fontSize: "0.78rem" }}>
                      用时 {s.durationSec} 秒
                      {s.index === steps.length && !submitted && (
                        <button
                          type="button"
                          className="secondary"
                          style={{ marginLeft: "0.6rem", fontSize: "0.78rem", padding: "0.15rem 0.5rem" }}
                          onClick={() => {
                            const v = prompt("修改这一步内容：", s.content);
                            if (v && v.trim() && v.trim() !== s.content) {
                              reviseLastStep(v);
                            }
                          }}
                        >
                          修改这一步
                        </button>
                      )}
                    </div>
                  </li>
                ))}
              </ol>
            )}

            {!submitted && (
              <>
                <label style={{ marginTop: "0.5rem" }}>
                  第 {steps.length + 1} 步
                </label>
                <textarea
                  value={currentStepInput}
                  onChange={(e) => handleInputChange(e.target.value)}
                  placeholder="写下你接下来要做的一步..."
                  style={{ minHeight: 70 }}
                />
                <div className="row" style={{ marginTop: "0.5rem" }}>
                  <button type="button" onClick={commitStep}>
                    完成这一步
                  </button>
                  <button type="button" className="secondary" onClick={requestHint}>
                    求提示
                  </button>
                  <button type="button" className="secondary" onClick={markStuck}>
                    我卡住了
                  </button>
                </div>
              </>
            )}
          </div>

          {hintReply && (
            <div className="card">
              <h2>AI 分层提示（等级 {hintLevel}）</h2>
              <p style={{ whiteSpace: "pre-wrap" }}>{hintReply}</p>
              <p style={{ color: "var(--muted)", fontSize: "0.8rem" }}>
                累计求助 {hintsUsed} 次
              </p>
            </div>
          )}

          {!submitted && steps.length > 0 && (
            <div className="card">
              <h2>用一句话解释你刚才为什么这样做（可选）</h2>
              <textarea
                value={explanation}
                onChange={(e) => setExplanation(e.target.value)}
                placeholder="如：我先把 3 移到右边因为..."
                style={{ minHeight: 60 }}
              />
              <div style={{ marginTop: "0.5rem" }}>
                <button type="button" className="secondary" onClick={submitExplanation}>
                  提交解释
                </button>
              </div>
              <p style={{ color: "var(--muted)", fontSize: "0.78rem", marginTop: "0.4rem" }}>
                提示：写得越具体，系统越能判断你是真理解还是凭印象。
              </p>
            </div>
          )}

          {!submitted && (
            <div className="card">
              <h2>完成提交</h2>
              <div className="row">
                <button type="button" onClick={() => submitAnswer(true)}>
                  提交答案（我做对了）
                </button>
                <button type="button" className="secondary" onClick={() => submitAnswer(false)}>
                  提交答案（我做错了）
                </button>
              </div>
            </div>
          )}

          {submitted && (
            <div className="card">
              <p className="ok">
                ✓ 本题已结束。系统已记录：{steps.length} 步、累计求助 {hintsUsed} 次、停顿
                自动监测中。
              </p>
              <button
                type="button"
                onClick={() => {
                  setStarted(false);
                  setSubmitted(false);
                  setSteps([]);
                  setCurrentStepInput("");
                  setExplanation("");
                  setHintReply("");
                  setHintLevel(0);
                  setHintsUsed(0);
                  setAttemptId(null);
                  setSessionId("");
                  setStepStartedAt(null);
                }}
              >
                做下一题
              </button>
            </div>
          )}
        </>
      )}

      {msg && <p className={msg.startsWith("✓") ? "ok" : "err"}>{msg}</p>}

      <p style={{ color: "var(--muted)", fontSize: "0.78rem", marginTop: "1.5rem" }}>
        会话 ID：{sessionId || "（未开始）"}
      </p>
    </main>
  );
}

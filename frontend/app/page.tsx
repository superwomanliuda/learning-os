"use client";

import { useEffect, useState } from "react";
import { api, getToken } from "@/lib/api";
import { useRouter } from "next/navigation";
import Link from "next/link";

type Dashboard = {
  todaySessions: { id: string; title: string; type: string; status: string }[];
  recentVideos: { id: string; title: string; status: string }[];
  ongoingHomework: { id: number; questionExcerpt: string; hintCount: number }[];
  confusionMarks: string[];
  metrics: { independentCorrectCount: number; avgHintCount: number };
};

export default function HomePage() {
  const router = useRouter();
  const [data, setData] = useState<Dashboard | null>(null);
  const [err, setErr] = useState("");

  useEffect(() => {
    if (!getToken()) {
      router.replace("/login");
      return;
    }
    api<Dashboard>("/api/dashboard/today")
      .then(setData)
      .catch((e) => setErr(e instanceof Error ? e.message : "加载失败"));
  }, [router]);

  if (!getToken()) return null;
  if (err) return <main><p className="err">{err}</p></main>;
  if (!data) return <main><p style={{ color: "var(--muted)" }}>加载中…</p></main>;

  return (
    <main>
      <h1>今日学习</h1>
      <p style={{ color: "var(--muted)", marginBottom: "1.25rem" }}>
        统一入口：课程 / 视频 / 作业与「没搞懂的点」
      </p>

      <div className="card">
        <h2>概览</h2>
        <p style={{ fontSize: "0.9rem", color: "var(--muted)" }}>
          独立完成（无提示做对）次数：{String(data.metrics.independentCorrectCount)}；平均提示次数：{" "}
          {data.metrics.avgHintCount.toFixed(2)}
        </p>
      </div>

      <div className="card">
        <h2>今日会话</h2>
        {data.todaySessions.length === 0 ? (
          <p style={{ color: "var(--muted)" }}>今日暂无会话，可从下方模块开始。</p>
        ) : (
          <ul className="compact">
            {data.todaySessions.map((s) => (
              <li key={s.id}>
                {s.title}（{s.type}）— {s.status}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>最近导入的视频</h2>
        {data.recentVideos.length === 0 ? (
          <p style={{ color: "var(--muted)" }}>
            暂无 — <Link href="/video">去导入 B 站链接</Link>
          </p>
        ) : (
          <ul className="compact">
            {data.recentVideos.map((v) => (
              <li key={v.id}>
                {v.title} — {v.status}
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>进行中的作业</h2>
        {data.ongoingHomework.length === 0 ? (
          <p style={{ color: "var(--muted)" }}>
            暂无 — <Link href="/homework">去作业辅导</Link>
          </p>
        ) : (
          <ul className="compact">
            {data.ongoingHomework.map((h) => (
              <li key={h.id}>
                {h.questionExcerpt}（已提示 {h.hintCount} 次）
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="card">
        <h2>我没搞懂的点（课堂标记）</h2>
        {data.confusionMarks.length === 0 ? (
          <p style={{ color: "var(--muted)" }}>
            暂无 — <Link href="/class">上课时点「这里没懂」</Link>
          </p>
        ) : (
          <ul className="compact">
            {data.confusionMarks.map((c, i) => (
              <li key={i}>{c}</li>
            ))}
          </ul>
        )}
      </div>

      <div className="row" style={{ marginTop: "1rem" }}>
        <Link href="/class" className="btn">
          1对1 上课
        </Link>
        <Link href="/video" className="btn">
          视频学习
        </Link>
        <Link href="/homework" className="btn">
          作业辅导
        </Link>
      </div>
    </main>
  );
}

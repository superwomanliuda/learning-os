"use client";

import { useState } from "react";
import { api, setToken } from "@/lib/api";
import { useRouter } from "next/navigation";

export default function LoginPage() {
  const router = useRouter();
  const [username, setUsername] = useState("student");
  const [password, setPassword] = useState("demo123");
  const [err, setErr] = useState("");

  async function login(e: React.FormEvent) {
    e.preventDefault();
    setErr("");
    try {
      const res = await api<{ token: string; role: string; name: string; userId: number }>("/api/auth/login", {
        method: "POST",
        body: JSON.stringify({ username, password }),
      });
      setToken(res.token);
      if (typeof window !== "undefined") {
        localStorage.setItem("role", res.role);
        localStorage.setItem("userName", res.name);
      }
      router.push("/");
    } catch (e: unknown) {
      setErr(e instanceof Error ? e.message : "登录失败");
    }
  }

  return (
    <main>
      <h1>Learning OS Demo</h1>
      <p style={{ color: "var(--muted)", marginBottom: "1.5rem" }}>
        演示账号：<code>student</code> / <code>demo123</code>；老师端：<code>teacher</code> /{" "}
        <code>demo123</code>
      </p>
      <div className="card" style={{ maxWidth: 360 }}>
        <form onSubmit={login}>
          <label>用户名</label>
          <input value={username} onChange={(e) => setUsername(e.target.value)} />
          <label style={{ marginTop: "0.75rem" }}>密码</label>
          <input
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
          />
          {err && <p className="err">{err}</p>}
          <div style={{ marginTop: "1rem" }}>
            <button type="submit">登录</button>
          </div>
        </form>
      </div>
    </main>
  );
}

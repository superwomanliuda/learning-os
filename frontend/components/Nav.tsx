"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { clearToken, getToken } from "@/lib/api";

export default function Nav() {
  const pathname = usePathname();
  if (pathname === "/login") return null;
  if (typeof window !== "undefined" && !getToken()) return null;

  return (
    <nav className="nav">
      <Link href="/">今日学习</Link>
      <Link href="/class">1对1 上课</Link>
      <Link href="/video">视频学习</Link>
      <Link href="/homework">作业辅导</Link>
      <Link href="/report">学情 / 复盘</Link>
      <span style={{ flex: 1 }} />
      <button
        type="button"
        className="secondary"
        onClick={() => {
          clearToken();
          window.location.href = "/login";
        }}
      >
        退出
      </button>
    </nav>
  );
}

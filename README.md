# Learning OS Demo v0.1

前后端分离的学习过程记录 + 分层 AI 辅导演示项目（对应交付文档中的路线 B MVP）。

## 结构

- `backend/`：Spring Boot 3.2 + JPA + MySQL，JWT 鉴权，统一 `AiService` 调用 OpenAI 兼容接口（无 Key 时走本地兜底文案）。
- `frontend/`：Next.js 14（App Router）+ TypeScript。

## 后端

1. 安装 JDK 17+、Maven，本机安装 MySQL 并创建库：

   ```sql
   CREATE DATABASE learning_os CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
   ```

2. 修改 `backend/src/main/resources/application.yml` 中的 `spring.datasource.username` / `password`。

3. 可选：设置环境变量启用真实模型：

   ```bash
   export OPENAI_API_KEY=sk-...
   ```

4. 启动：

   ```bash
   cd backend
   mvn spring-boot:run
   ```

5. 演示账号（首次启动自动写入）：
   - 学生：`student` / `demo123`
   - 老师：`teacher` / `demo123`

## 前端

```bash
cd frontend
npm install
npm run dev
```

浏览器访问 <http://localhost:3000>。若后端不在本机 8080，可设置：

```bash
export NEXT_PUBLIC_API_URL=http://127.0.0.1:8080
```

## 主要 API（均需 Bearer Token，除 `/api/auth/**`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/register` | 注册 |
| GET | `/api/me` | 当前用户 |
| GET | `/api/dashboard/today` | 今日学习入口数据 |
| POST | `/api/sessions` | 创建学习会话 |
| POST | `/api/events` | 上报 `learning_event` |
| POST | `/api/videos/import` | 导入 B 站 URL（占位元数据） |
| PATCH | `/api/videos/{id}/transcript` | 手动粘贴字幕/转写 |
| POST | `/api/video/ask` | 按片段提问（写 `video_question` 事件） |
| POST | `/api/homework/hint` | 分层提示（服务端定级，写 `homework_hint_used`） |
| GET | `/api/reports/session/{sessionId}` | 学情总结（老师可看全部，学生仅自己） |
| POST | `/api/class/sessions/{id}/confusion` | 课堂「不懂」标记 |
| GET | `/api/class/sessions/{id}/review` | 学生课堂复盘文案 |
| GET | `/api/ai/refuse-full-answer` | 拒答固定话术 |

## 说明

- 所有学习行为以 `learning_event` 为主轴；作业提示等级由**服务端**根据已用次数计算（1–4），不依赖前端自报等级。
- B 站真实标题/字幕抓取未接第三方服务，导入失败或元数据不全时，请用「手动转写」兜底（与文档一致）。

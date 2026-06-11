package com.learningos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learningos.dto.TextbookTutorRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private static final String SYSTEM_GLOBAL = """
            你是一个面向中小学生的 AI 学习辅导老师。
            你的目标不是替学生完成学习任务，而是帮助学生真正理解。
            你必须遵守以下规则：
            1. 不直接给最终答案。
            2. 不输出可直接抄写的完整标准解。
            3. 优先基于当前上下文回答：课堂片段、视频片段、题目内容、学生已有步骤。
            4. 先判断学生卡在哪，再决定提示强度。
            5. 你的回答要尽量短、清楚、像老师，不要像百科全书。
            6. 在适当时候反问学生，让学生自己说出理解。
            7. 如果学生连续多次求完整答案，你要温和拒绝，并继续给分层提示。
            8. 当上下文不足时，先提出一个最小澄清问题，而不是胡乱补全。
            """;

    private final WebClient webClient;
    private final WebClient groqClient;
    private final WebClient diagnosticClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final String apiKey;
    private final String groqApiKey;
    private final String groqModel;
    private final String diagnosticApiKey;
    private final String diagnosticModel;
    private final int diagnosticMaxTokens;

    public AiService(ObjectMapper objectMapper,
                     @Value("${ai.openai.base-url}") String baseUrl,
                     @Value("${ai.openai.api-key}") String apiKey,
                     @Value("${ai.openai.model}") String model,
                     @Value("${ai.openai.max-tokens:1024}") int maxTokens,
                     @Value("${ai.groq.base-url}") String groqBaseUrl,
                     @Value("${ai.groq.api-key}") String groqApiKey,
                     @Value("${ai.groq.model}") String groqModel,
                     @Value("${ai.diagnostic.base-url}") String diagnosticBaseUrl,
                     @Value("${ai.diagnostic.api-key}") String diagnosticApiKey,
                     @Value("${ai.diagnostic.model}") String diagnosticModel,
                     @Value("${ai.diagnostic.max-tokens:2048}") int diagnosticMaxTokens) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.groqApiKey = groqApiKey;
        this.groqModel = groqModel;
        this.diagnosticApiKey = diagnosticApiKey;
        this.diagnosticModel = diagnosticModel;
        this.diagnosticMaxTokens = diagnosticMaxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
                .build();
        this.groqClient = WebClient.builder()
                .baseUrl(groqBaseUrl.endsWith("/") ? groqBaseUrl.substring(0, groqBaseUrl.length() - 1) : groqBaseUrl)
                .build();
        this.diagnosticClient = WebClient.builder()
                .baseUrl(diagnosticBaseUrl.endsWith("/") ? diagnosticBaseUrl.substring(0, diagnosticBaseUrl.length() - 1) : diagnosticBaseUrl)
                .build();
    }

    public String homeworkHint(int hintLevel, String question, String studentAnswer, int hintsUsedSoFar,
                               List<String> errorTypes, String extraContext) {
        String levelRule = switch (hintLevel) {
            case 1 -> "这是学生第一次求助：只重述题意或提示考查知识点。";
            case 2 -> "第二次求助：提示第一步方向或关键思路。";
            case 3, 4 -> "第三次及以上：指出错误位置或给解题结构（框架），绝不写完整解答步骤。";
            default -> "给适度提示，绝不给最终答案。";
        };
        String errors = errorTypes == null || errorTypes.isEmpty()
                ? "无"
                : String.join("、", errorTypes);
        String user = """
                任务：基于题目、学生当前步骤、历史提示次数，生成当前轮次的辅导回复。
                题目：%s
                学生当前答案/步骤：%s
                已使用提示次数（不含本轮）：%d
                历史错误类型：%s
                补充上下文：%s

                本轮提示等级：%d。%s

                输出要求：
                - 绝不输出最终答案或可直接抄写的完整标准解。
                - 结尾必须附带一个让学生继续思考的问题。
                语气：冷静、鼓励、具体。
                """.formatted(question, studentAnswer, hintsUsedSoFar, errors,
                extraContext == null ? "" : extraContext, hintLevel, levelRule);
        return chat(SYSTEM_GLOBAL, user, fallbackHomework(hintLevel, question));
    }

    public String videoSegmentExplain(String title, int segStart, int segEnd, String transcriptOrStub,
                                      String question) {
        String user = """
                任务：学生针对某个视频时间片段提问，请你只围绕该片段进行解释。
                视频标题：%s
                片段：%d 秒 ~ %d 秒
                该片段字幕或转写（可能为空）：%s
                学生问题：%s

                输出要求：
                1. 先用一句话概括这段在讲什么
                2. 再用更简单的话解释
                3. 如有必要，举一个与原片段一致的小例子
                4. 最后追问：你现在最不明白的是哪一步或哪个词？

                禁止：跳出片段讲太远；复述一大段未加工的原文；片段里没说的不要装作说过。
                """.formatted(title, segStart, segEnd,
                transcriptOrStub == null || transcriptOrStub.isBlank() ? "（暂无字幕，请基于标题与时间段做最小范围推断，并先让学生补充片段内容）" : transcriptOrStub,
                question);
        return chat(SYSTEM_GLOBAL, user, fallbackVideo(title, segStart, segEnd, question));
    }

    public String classReview(String transcriptStub, String confusionSummary, String notes) {
        String user = """
                任务：基于课堂录音转写、学生「不懂」标记、课堂笔记，帮助学生复盘。
                转写摘要：%s
                不懂标记汇总：%s
                笔记：%s

                输出要求：
                - 先列出本次课堂最值得复盘的 1-3 个点
                - 每个点用「老师原来在讲什么 -> 你可能卡在哪里 -> 你现在该怎么想」三段式
                - 尽量不要超过 200 字
                - 结尾问：你想先复盘哪一个点？
                """.formatted(
                transcriptStub == null ? "（暂无）" : transcriptStub,
                confusionSummary == null ? "（暂无）" : confusionSummary,
                notes == null ? "（暂无）" : notes);
        return chat(SYSTEM_GLOBAL, user, "本节课可先回顾：概念引入、例题关键一步、易错点。你想先复盘哪一个点？");
    }

    public String teacherSessionReport(String eventsSummary, String homeworkSummary, String videoSummary) {
        String user = """
                任务：基于课堂事件、视频学习记录、作业尝试记录，生成老师可直接使用的课后总结。
                课堂与事件摘要：%s
                作业摘要：%s
                视频学习摘要：%s

                输出格式：
                1. 本次最主要的卡点（最多 3 条）
                2. 提示依赖情况
                3. 可能的错误模式
                4. 下次课建议

                要求：基于证据说话，不要空泛评价；不要用夸张词汇；每条尽量引用具体行为（如「课堂中标记不懂 2 次」）。
                """.formatted(eventsSummary, homeworkSummary, videoSummary);
        return chatDiagnostic(SYSTEM_GLOBAL, user, "1. 卡点：待补充学习过程数据。\n2. 提示依赖：数据不足。\n3. 错误模式：待观察。\n4. 建议：继续收集课堂标记与作业提示记录。");
    }

    public String refuseFullAnswer() {
        return """
                我不能直接给这道题的最终答案，但我可以陪你一步一步想出来。
                我们先不急着看结果，你先告诉我：这道题最先应该用到哪个知识点？
                如果你还是没思路，我可以给你下一层提示。""";
    }

    public Map<String, Object> videoPractice(String title, int segStart, int segEnd,
                                             String transcriptOrStub, String level, int questionCount) {
        int count = Math.max(1, Math.min(questionCount, 6));
        String lv = (level == null || level.isBlank()) ? "基础" : level;
        String user = """
                任务：基于视频片段内容，为学生生成练习题，不给答案。
                视频标题：%s
                片段：%d 秒 ~ %d 秒
                片段文本：%s
                难度：%s
                题目数量：%d

                输出必须是 JSON，格式：
                {
                  "summary": "一句话总结本段核心",
                  "questions": [
                    {"question":"...", "type":"概念理解/应用/思辨", "hint":"一句提示，不给答案"}
                  ]
                }

                约束：
                - 不能输出标准答案。
                - 题目必须贴合片段文本，不要跳出片段。
                - hint 只能给方向。
                """.formatted(
                title, segStart, segEnd,
                transcriptOrStub == null || transcriptOrStub.isBlank() ? "（无字幕，尽量基于标题与片段范围）" : transcriptOrStub,
                lv, count);
        Map<String, Object> fallback = fallbackVideoPractice(title, segStart, segEnd, transcriptOrStub, lv, count);
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            return fallback;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_GLOBAL),
                            Map.of("role", "user", "content", user)
                    )
            );
            String raw = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.warn("练习题生成失败: {}", e.getMessage());
                        return Mono.just("");
                    })
                    .block();
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode content = root.path("choices").isArray() && !root.path("choices").isEmpty()
                    ? root.path("choices").get(0).path("message").path("content")
                    : null;
            if (content == null || content.asText("").isBlank()) {
                return fallback;
            }
            JsonNode json = tryExtractJson(content.asText(""));
            if (json == null || !json.isObject()) {
                return fallback;
            }
            String summary = json.path("summary").asText(fallback.get("summary").toString());
            List<Map<String, String>> qs = new ArrayList<>();
            JsonNode arr = json.path("questions");
            if (arr.isArray()) {
                for (JsonNode q : arr) {
                    if (qs.size() >= count) {
                        break;
                    }
                    String qq = q.path("question").asText("").trim();
                    if (qq.isBlank()) {
                        continue;
                    }
                    qs.add(Map.of(
                            "question", qq,
                            "type", q.path("type").asText("概念理解"),
                            "hint", q.path("hint").asText("先回看该片段中的关键词，再尝试自己组织答案。")
                    ));
                }
            }
            if (qs.isEmpty()) {
                return fallback;
            }
            Map<String, Object> out = new HashMap<>();
            out.put("summary", summary);
            out.put("questions", qs);
            return out;
        } catch (Exception e) {
            log.warn("练习题 JSON 解析失败: {}", e.getMessage());
            return fallback;
        }
    }

    public byte[] textToSpeech(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("文本不能为空");
        }
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            throw new IllegalArgumentException("未配置 OPENAI_API_KEY，无法生成语音");
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", "tts-1",
                    "voice", "nova",
                    "input", text,
                    "speed", 0.95
            );
            byte[] audio = webClient.post()
                    .uri("/audio/speech")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_OCTET_STREAM)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.warn("TTS 调用失败: {}", e.getMessage());
                        return Mono.just(new byte[0]);
                    })
                    .block();
            if (audio == null || audio.length == 0) {
                throw new IllegalArgumentException("语音生成失败，请稍后重试");
            }
            return audio;
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception e) {
            throw new IllegalArgumentException("语音生成失败，请稍后重试");
        }
    }

    public String textbookAnswer(String title, String textbookContent, String question) {
        String content = textbookContent == null ? "" : textbookContent;
        if (content.length() > 3000) {
            content = content.substring(0, 3000);
        }
        String user = """
                任务：基于教材内容回答学生问题，目标是帮助理解，不给可直接抄写的完整答案。
                教材标题：%s
                教材摘录：%s
                学生问题：%s

                输出要求：
                1. 先说结论方向（1 句）
                2. 再分 2-3 步解释
                3. 最后给一个反问，确认学生是否理解
                禁止脱离教材瞎编。
                """.formatted(
                title == null ? "教材" : title,
                content.isBlank() ? "（暂无教材内容）" : content,
                question == null ? "" : question);
        return chat(
                SYSTEM_GLOBAL,
                user,
                "这道问题可以先回到教材里的定义和例子：先说清概念，再对照一个小例子。你愿意先用自己的话复述这个概念吗？"
        );
    }

    public Map<String, Object> textbookLessonPlan(String title, String textbookContent, int targetMinutes, String focusQuestion) {
        int minutes = Math.max(2, Math.min(10, targetMinutes));
        String content = textbookContent == null ? "" : textbookContent;
        if (content.length() > 3500) {
            content = content.substring(0, 3500);
        }
        String user = """
                任务：基于教材内容生成一个 %d 分钟的 1 对 1 微课脚本，面向中小学生。
                你是主导课堂节奏的家教老师，要按阶段推进，不等学生追问。
                教材标题：%s
                教材摘录：%s
                学生关注问题：%s

                输出必须是 JSON：
                {
                  "lessonTitle": "课程题目（简短）",
                  "durationMin": %d,
                  "opening": "开场 1-2 句",
                  "teachingPhases": [
                    {
                      "phase": "Phase 0",
                      "name": "破冰与目标确认",
                      "goal": "用一句话描述本阶段目标",
                      "minutes": 1,
                      "teacherSpeech": "老师对学生说的话（口语化）",
                      "boardSteps": ["板书或步骤1","板书或步骤2"]
                    }
                  ],
                  "keyPoints": ["要点1","要点2","要点3"],
                  "interactiveQuestions": ["互动提问1","互动提问2"],
                  "endingTask": "课后 1 个小任务（不给标准答案）"
                }

                约束：
                - 课程题目必须具体，不要空泛。
                - 教学阶段必须遵循：Phase 0 破冰与目标确认 -> Phase 1 快速摸底 -> Phase 2 统一讲解流程 -> Phase 3 引导练习 -> Phase 4 小结。
                - 每个阶段只做一件事，teacherSpeech 要像真实老师说话。
                - keyPoints 要贴合教材内容。
                - interactiveQuestions 要能检查学生是否理解。
                - 不输出标准答案。
                """.formatted(
                minutes,
                title == null ? "教材" : title,
                content.isBlank() ? "（暂无教材内容）" : content,
                focusQuestion == null || focusQuestion.isBlank() ? "（无）" : focusQuestion,
                minutes
        );
        Map<String, Object> fallback = fallbackTextbookLesson(title, content, minutes);
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            return fallback;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_GLOBAL),
                            Map.of("role", "user", "content", user)
                    )
            );
            String raw = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.warn("教材微课生成失败: {}", e.getMessage());
                        return Mono.just("");
                    })
                    .block();
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode contentNode = root.path("choices").isArray() && !root.path("choices").isEmpty()
                    ? root.path("choices").get(0).path("message").path("content")
                    : null;
            if (contentNode == null || contentNode.asText("").isBlank()) {
                return fallback;
            }
            JsonNode json = tryExtractJson(contentNode.asText(""));
            if (json == null || !json.isObject()) {
                return fallback;
            }
            List<String> keyPoints = readStringArray(json.path("keyPoints"), 5);
            List<String> questions = readStringArray(json.path("interactiveQuestions"), 5);
            List<Map<String, Object>> phases = readPhases(json.path("teachingPhases"), 6);
            if (keyPoints.isEmpty()) {
                keyPoints = (List<String>) fallback.get("keyPoints");
            }
            if (questions.isEmpty()) {
                questions = (List<String>) fallback.get("interactiveQuestions");
            }
            if (phases.isEmpty()) {
                phases = (List<Map<String, Object>>) fallback.get("teachingPhases");
            }
            Map<String, Object> out = new HashMap<>();
            out.put("lessonTitle", json.path("lessonTitle").asText((String) fallback.get("lessonTitle")));
            out.put("durationMin", json.path("durationMin").asInt(minutes));
            out.put("opening", json.path("opening").asText((String) fallback.get("opening")));
            out.put("teachingPhases", phases);
            out.put("keyPoints", keyPoints);
            out.put("interactiveQuestions", questions);
            out.put("endingTask", json.path("endingTask").asText((String) fallback.get("endingTask")));
            return out;
        } catch (Exception e) {
            log.warn("教材微课 JSON 解析失败: {}", e.getMessage());
            return fallback;
        }
    }

    public Map<String, Object> textbookTutorTurn(String title, String textbookContent, List<TextbookTutorRequest.TutorMessage> messages) {
        String content = textbookContent == null ? "" : textbookContent;
        if (content.length() > 2800) {
            content = content.substring(0, 2800);
        }
        String history = formatTutorMessages(messages);
        String user = """
                任务：你是1对1教材辅导老师。请基于教材内容，给出下一轮辅导回复，并给出板书步骤。
                教材标题：%s
                教材摘录：%s
                对话历史：%s

                输出必须是 JSON：
                {
                  "speech": "老师对学生说的话，简洁口语化",
                  "board": {
                    "title": "板书标题",
                    "steps": [
                      {"latex":"步骤1","note":"一句解释","highlight":false},
                      {"latex":"步骤2","note":"关键点","highlight":true}
                    ]
                  }
                }

                约束：
                - 回答要紧扣教材，不要泛泛而谈。
                - speech 不超过 120 字。
                - 没有合适板书时，board 也要给一个最小可用结构（至少 1 个步骤）。
                - 不输出标准答案。
                """.formatted(
                title == null ? "教材" : title,
                content.isBlank() ? "（暂无教材内容）" : content,
                history.isBlank() ? "（暂无）" : history
        );
        Map<String, Object> fallback = fallbackTutorTurn(title, content, messages);
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            return fallback;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_GLOBAL),
                            Map.of("role", "user", "content", user)
                    )
            );
            String raw = webClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.warn("教材导师回复生成失败: {}", e.getMessage());
                        return Mono.just("");
                    })
                    .block();
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode contentNode = root.path("choices").isArray() && !root.path("choices").isEmpty()
                    ? root.path("choices").get(0).path("message").path("content")
                    : null;
            if (contentNode == null || contentNode.asText("").isBlank()) {
                return fallback;
            }
            JsonNode json = tryExtractJson(contentNode.asText(""));
            if (json == null || !json.isObject()) {
                return fallback;
            }
            String speech = json.path("speech").asText("").trim();
            JsonNode boardNode = json.path("board");
            Map<String, Object> board = toBoardMap(boardNode);
            if (speech.isBlank() || board == null) {
                return fallback;
            }
            return Map.of("speech", speech, "board", board);
        } catch (Exception e) {
            log.warn("教材导师 JSON 解析失败: {}", e.getMessage());
            return fallback;
        }
    }

    private String chat(String system, String user, String fallback) {
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            log.warn("未配置有效 OPENAI_API_KEY，使用本地兜底回复");
            return fallback;
        }
        return callChat(webClient, apiKey, model, maxTokens, system, user, fallback);
    }

    // 第二层：Groq + Llama 3.1 8B，用于 observation 结构化
    private String chatGroq(String system, String user, String fallback) {
        if (groqApiKey == null || groqApiKey.isBlank() || "your-groq-api-key".equals(groqApiKey)) {
            log.warn("未配置有效 GROQ_API_KEY，使用本地兜底回复");
            return fallback;
        }
        return callChat(groqClient, groqApiKey, groqModel, 1024, system, user, fallback);
    }

    // 第四层：GPT-5.4，用于 pattern 分析和 strategy 生成
    private String chatDiagnostic(String system, String user, String fallback) {
        if (diagnosticApiKey == null || diagnosticApiKey.isBlank() || "your-diagnostic-api-key".equals(diagnosticApiKey)) {
            log.warn("未配置有效 DIAGNOSTIC_API_KEY，使用本地兜底回复");
            return fallback;
        }
        return callChat(diagnosticClient, diagnosticApiKey, diagnosticModel, diagnosticMaxTokens, system, user, fallback);
    }

    private String callChat(WebClient client, String key, String mdl, int tokens,
                            String system, String user, String fallback) {
        try {
            // GPT-5.4 使用 max_completion_tokens，其他模型使用 max_tokens
            String tokenKey = mdl.startsWith("gpt-5") ? "max_completion_tokens" : "max_tokens";
            Map<String, Object> body = Map.of(
                    "model", mdl,
                    tokenKey, tokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
                            Map.of("role", "user", "content", user)
                    )
            );
            String raw = client.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body)
                    .retrieve()
                    .bodyToMono(String.class)
                    .timeout(Duration.ofSeconds(60))
                    .onErrorResume(e -> {
                        log.warn("LLM 调用失败: {}", e.getMessage());
                        if (e instanceof org.springframework.web.reactive.function.client.WebClientResponseException ex) {
                            log.warn("LLM 错误详情: {}", ex.getResponseBodyAsString());
                        }
                        return Mono.just("");
                    })
                    .block();
            if (raw == null || raw.isBlank()) return fallback;
            JsonNode root = objectMapper.readTree(raw);
            // 打印实际使用的模型名，方便验证
            String actualModel = root.path("model").asText("unknown");
            log.info("[AI调用] 请求模型: {} | 实际返回模型: {}", mdl, actualModel);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) return fallback;
            String content = choices.get(0).path("message").path("content").asText("");
            return content.isBlank() ? fallback : content.trim();
        } catch (Exception e) {
            log.warn("解析 LLM 响应失败: {}", e.getMessage());
            return fallback;
        }
    }

    private static String fallbackHomework(int level, String question) {
        return switch (level) {
            case 1 -> "我们先看清题目在问什么：它要你在已知条件里找到哪些量之间的关系？"
                    + " 试着用一句话说说题目的目标。接下来：你认为这道题属于哪一类知识点？";
            case 2 -> "可以试着先写出与条件直接相关的一个等式或关系式，不用算到底。"
                    + " 如果你要写第一步，你会选哪一个已知量入手？";
            case 3 -> "对照你写的步骤，看看从哪一行开始结论跟条件可能对不上。"
                    + " 你愿意指出你最不确定的是哪一行吗？";
            default -> "可以用「设未知数—列关系—检验」这样的骨架自己填，但不要抄答案。"
                    + " 如果只允许写提纲，你的提纲里会包含哪三步？";
        };
    }

    private static String fallbackVideo(String title, int a, int b, String q) {
        return "这段（" + a + "-" + b + " 秒）在「" + title + "」里应围绕一个小概念展开。"
                + " 关于你的问题：「" + q + "」，建议先对照画面里出现的定义或例子。"
                + " 你现在最不明白的是哪一个词或哪一步？";
    }

    private Map<String, Object> fallbackTextbookLesson(String title, String content, int minutes) {
        List<String> keywords = extractKeywords(content, 3);
        if (keywords.isEmpty()) {
            keywords = List.of("核心概念", "关键步骤", "常见误区");
        }
        String lessonTitle = (title == null || title.isBlank())
                ? ("1对1微课：" + keywords.get(0))
                : ("1对1微课：" + title + "中的" + keywords.get(0));
        return Map.of(
                "lessonTitle", lessonTitle,
                "durationMin", minutes,
                "opening", "今天我们用几分钟把「" + keywords.get(0) + "」讲清楚，先从你最容易混淆的地方开始。",
                "teachingPhases", List.of(
                        Map.of(
                                "phase", "Phase 0",
                                "name", "破冰与目标确认",
                                "goal", "确认本节微课目标，降低紧张感",
                                "minutes", 1,
                                "teacherSpeech", "先别急着做题，我们先确认今天只解决一个核心问题：搞清楚「" + keywords.get(0) + "」。",
                                "boardSteps", List.of("本节目标：" + keywords.get(0))
                        ),
                        Map.of(
                                "phase", "Phase 1",
                                "name", "快速摸底",
                                "goal", "判断学生当前理解卡点",
                                "minutes", Math.max(1, minutes / 5),
                                "teacherSpeech", "我先问你一个小问题：你会怎么解释「" + keywords.get(0) + "」？",
                                "boardSteps", List.of("摸底问题：你如何解释关键词？")
                        ),
                        Map.of(
                                "phase", "Phase 2",
                                "name", "统一讲解流程",
                                "goal", "用固定步骤讲清概念与方法",
                                "minutes", Math.max(1, minutes / 3),
                                "teacherSpeech", "我们统一用三步来理解：定义 -> 关系 -> 例子。",
                                "boardSteps", List.of(
                                        "第1步：定义「" + keywords.get(0) + "」",
                                        "第2步：关联「" + keywords.get(1) + "」",
                                        "第3步：识别「" + keywords.get(2) + "」"
                                )
                        ),
                        Map.of(
                                "phase", "Phase 3",
                                "name", "引导练习",
                                "goal", "让学生按流程自己说一遍",
                                "minutes", Math.max(1, minutes / 4),
                                "teacherSpeech", "现在轮到你：按刚才三步，用自己的话讲一遍，我只在你卡住时提醒。",
                                "boardSteps", List.of("学生复述：定义 -> 关系 -> 例子")
                        ),
                        Map.of(
                                "phase", "Phase 4",
                                "name", "小结与任务",
                                "goal", "收束课堂并布置轻量任务",
                                "minutes", 1,
                                "teacherSpeech", "今天你已经建立了清晰顺序，接下来做一个小练习巩固就够了。",
                                "boardSteps", List.of("小结：抓住关键词，按顺序表达")
                        )
                ),
                "keyPoints", List.of(
                        "先理解「" + keywords.get(0) + "」的定义",
                        "再梳理「" + keywords.get(1) + "」和前面知识的关系",
                        "最后用「" + keywords.get(2) + "」检查自己是否真的理解"
                ),
                "interactiveQuestions", List.of(
                        "你能先用自己的话说一下「" + keywords.get(0) + "」吗？",
                        "如果把条件改一点，这个思路还成立吗？为什么？"
                ),
                "endingTask", "请用 3 句话总结今天的核心点，并完成 1 道你自己出的同类型小题。"
        );
    }

    private Map<String, Object> fallbackTutorTurn(String title, String content, List<TextbookTutorRequest.TutorMessage> messages) {
        List<String> keywords = extractKeywords(content, 3);
        if (keywords.isEmpty()) {
            keywords = List.of("核心概念", "关键步骤", "易错点");
        }
        String latestQuestion = "";
        if (messages != null && !messages.isEmpty()) {
            for (int i = messages.size() - 1; i >= 0; i--) {
                TextbookTutorRequest.TutorMessage m = messages.get(i);
                if (m != null && "user".equalsIgnoreCase(m.getRole()) && m.getContent() != null && !m.getContent().isBlank()) {
                    latestQuestion = m.getContent().trim();
                    break;
                }
            }
        }
        String speech = latestQuestion.isBlank()
                ? "我们先从教材这一段的核心概念开始，一步一步来，你跟着我走就行。"
                : "你刚问的「" + latestQuestion + "」很关键。我们先抓住定义，再看它和例子的关系。";
        return Map.of(
                "speech", speech,
                "board", Map.of(
                        "title", (title == null || title.isBlank()) ? "教材板书" : title,
                        "steps", List.of(
                                Map.of("latex", "定义：" + keywords.get(0), "note", "先说清概念", "highlight", false),
                                Map.of("latex", "关系：" + keywords.get(1), "note", "连到例子", "highlight", true),
                                Map.of("latex", "检查：" + keywords.get(2), "note", "避免误解", "highlight", false)
                        )
                )
        );
    }

    private String formatTutorMessages(List<TextbookTutorRequest.TutorMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        return messages.stream()
                .filter(m -> m != null && m.getRole() != null && m.getContent() != null && !m.getContent().isBlank())
                .limit(12)
                .map(m -> m.getRole() + ": " + m.getContent())
                .collect(java.util.stream.Collectors.joining("\n"));
    }

    private Map<String, Object> toBoardMap(JsonNode boardNode) {
        if (boardNode == null || !boardNode.isObject()) {
            return null;
        }
        String title = boardNode.path("title").asText("").trim();
        JsonNode stepsNode = boardNode.path("steps");
        List<Map<String, Object>> steps = new ArrayList<>();
        if (stepsNode.isArray()) {
            for (JsonNode s : stepsNode) {
                String latex = s.path("latex").asText("").trim();
                if (latex.isBlank()) {
                    latex = s.path("text").asText("").trim(); // backward compatible
                }
                if (latex.isBlank()) {
                    continue;
                }
                steps.add(Map.of(
                        "latex", latex,
                        "note", s.path("note").asText(""),
                        "highlight", s.path("highlight").asBoolean(false)
                ));
                if (steps.size() >= 8) {
                    break;
                }
            }
        }
        if (steps.isEmpty()) {
            return null;
        }
        return Map.of(
                "title", title.isBlank() ? "教材板书" : title,
                "steps", steps
        );
    }

    private List<String> readStringArray(JsonNode node, int limit) {
        List<String> out = new ArrayList<>();
        if (!node.isArray()) {
            return out;
        }
        for (JsonNode item : node) {
            String s = item.asText("").trim();
            if (!s.isBlank() && out.size() < limit) {
                out.add(s);
            }
        }
        return out;
    }

    private List<Map<String, Object>> readPhases(JsonNode node, int limit) {
        List<Map<String, Object>> phases = new ArrayList<>();
        if (!node.isArray()) {
            return phases;
        }
        for (JsonNode p : node) {
            if (!p.isObject() || phases.size() >= limit) {
                continue;
            }
            String phase = p.path("phase").asText("").trim();
            String name = p.path("name").asText("").trim();
            String goal = p.path("goal").asText("").trim();
            String teacherSpeech = p.path("teacherSpeech").asText("").trim();
            if (name.isBlank()) {
                continue;
            }
            List<String> boardSteps = readStringArray(p.path("boardSteps"), 6);
            Map<String, Object> item = new HashMap<>();
            item.put("phase", phase.isBlank() ? ("Phase " + phases.size()) : phase);
            item.put("name", name);
            item.put("goal", goal);
            item.put("minutes", p.path("minutes").asInt(1));
            item.put("teacherSpeech", teacherSpeech);
            item.put("boardSteps", boardSteps);
            phases.add(item);
        }
        return phases;
    }

    private JsonNode tryExtractJson(String text) {
        String t = text.trim();
        try {
            return objectMapper.readTree(t);
        } catch (Exception ignored) {
        }
        int l = t.indexOf('{');
        int r = t.lastIndexOf('}');
        if (l >= 0 && r > l) {
            try {
                return objectMapper.readTree(t.substring(l, r + 1));
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private Map<String, Object> fallbackVideoPractice(String title, int a, int b, String transcript, String level, int count) {
        String text = transcript == null ? "" : transcript;
        List<String> keywords = extractKeywords(text, 4);
        if (keywords.isEmpty()) {
            keywords = List.of("核心概念", "关键步骤", "易错点");
        }
        List<Map<String, String>> qs = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String k1 = keywords.get(i % keywords.size());
            String k2 = keywords.get((i + 1) % keywords.size());
            Map<String, String> q;
            if ("进阶".equals(level) || i % 3 == 2) {
                q = Map.of(
                        "question", "如果把片段中关于「" + k1 + "」的条件改变一点，你会如何调整思路？",
                        "type", "应用",
                        "hint", "先复述片段原始条件，再只改一个变量进行比较。"
                );
            } else if (i % 3 == 1) {
                q = Map.of(
                        "question", "为什么这段会先讲「" + k1 + "」再讲「" + k2 + "」？你能说出因果关系吗？",
                        "type", "思辨",
                        "hint", "试着用“因为…所以…”连接两者。"
                );
            } else {
                q = Map.of(
                        "question", "请用你自己的话解释「" + k1 + "」在这段里的含义。",
                        "type", "概念理解",
                        "hint", "不要背原文，结合片段里的例子说。"
                );
            }
            qs.add(q);
        }
        String summary = text.isBlank()
                ? "本段围绕「" + title + "」的一个核心知识点展开，建议先抓住关键词再做练习。"
                : "本段主要涉及：" + String.join("、", keywords) + "。";
        return Map.of(
                "summary", summary,
                "questions", qs,
                "segment", "%02d:%02d-%02d:%02d".formatted(a / 60, a % 60, b / 60, b % 60)
        );
    }

    /**
     * 给老师生成 AI 辅助建议：追问建议 + 教学策略建议 + 风险提醒
     * 返回结构化 JSON 字符串，包含 followups(list) / strategy(list) / riskAlert(string)
     */
    public Map<String, Object> teacherAdvice(String currentQuestion, String studentAnswer,
                                              List<String> teacherTags, int hintCount, String notes) {
        String tagsStr = (teacherTags == null || teacherTags.isEmpty()) ? "暂无" : String.join("、", teacherTags);
        String prompt = """
                你是一个初中数学 1 对 1 家教系统里的 AI 助教。课堂控制权在老师手里，你只提供建议。
                
                当前题目：%s
                学生当前作答/思路：%s
                老师已打标签：%s
                本题已用提示次数：%d
                老师备注：%s
                
                请输出 JSON，格式严格如下（不要输出任何 JSON 以外的内容）：
                {
                  "riskAlert": "一句话风险提醒，如「该学生可能存在假理解」，若无风险写空字符串",
                  "followups": ["追问1（≤20字）", "追问2（≤20字）"],
                  "strategy": ["教学建议1（≤20字）", "教学建议2（≤20字）"]
                }
                
                要求：
                - followups 给 2 条口语化追问，有诊断价值，不直接给答案
                - strategy 给 1-2 条教学策略，可执行，不超过 20 字
                - riskAlert 只在确实有明显风险时才写，否则空字符串
                """.formatted(
                currentQuestion == null ? "（未设置）" : currentQuestion,
                studentAnswer == null || studentAnswer.isBlank() ? "（暂无）" : studentAnswer,
                tagsStr, hintCount,
                notes == null || notes.isBlank() ? "（无）" : notes);

        String raw = chat(SYSTEM_GLOBAL, prompt,
                "{\"riskAlert\":\"\",\"followups\":[\"你是怎么想到这一步的？\"],\"strategy\":[\"先让学生口述思路\"]}");
        try {
            JsonNode node = objectMapper.readTree(raw.trim()
                    .replaceFirst("(?s)^```json\\s*", "").replaceFirst("(?s)\\s*```$", ""));
            Map<String, Object> result = new HashMap<>();
            result.put("riskAlert", node.path("riskAlert").asText(""));
            List<String> fus = new ArrayList<>();
            node.path("followups").forEach(n -> fus.add(n.asText()));
            result.put("followups", fus);
            List<String> strats = new ArrayList<>();
            node.path("strategy").forEach(n -> strats.add(n.asText()));
            result.put("strategy", strats);
            return result;
        } catch (Exception e) {
            log.warn("teacherAdvice JSON parse failed: {}", raw);
            return Map.of("riskAlert", "", "followups", List.of(raw), "strategy", List.of());
        }
    }

    /**
     * 课后总结生成：给老师一份可直接使用的课后复盘
     */
    public Map<String, String> teacherLessonSummary(String question, List<String> tags,
                                                      int hintCount, String notes) {
        String tagsStr = (tags == null || tags.isEmpty()) ? "暂无" : String.join("、", tags);
        String prompt = """
                你是一个初中数学家教课后总结助手。
                
                本节课题目：%s
                老师打过的标签：%s
                学生本题用提示次数：%d
                老师备注：%s
                
                请输出 JSON，格式严格如下（不要输出任何 JSON 以外的内容）：
                {
                  "mainIssue": "本节课主要问题（1-2句）",
                  "evidence": "证据描述（1-2句）",
                  "nextLesson": "下节课建议（1-2句）",
                  "parentFeedback": "给家长的简短反馈（1-2句，积极但真实）"
                }
                """.formatted(
                question == null ? "（未记录）" : question,
                tagsStr, hintCount,
                notes == null || notes.isBlank() ? "（无）" : notes);

        String raw = chat(SYSTEM_GLOBAL, prompt,
                "{\"mainIssue\":\"需要更多课堂数据\",\"evidence\":\"当前课次证据不足\",\"nextLesson\":\"先补充步骤行为记录\",\"parentFeedback\":\"孩子在课堂中有参与，后续会继续精细诊断\"}");
        try {
            JsonNode node = objectMapper.readTree(raw.trim()
                    .replaceFirst("(?s)^```json\\s*", "").replaceFirst("(?s)\\s*```$", ""));
            Map<String, String> result = new HashMap<>();
            result.put("mainIssue", node.path("mainIssue").asText(""));
            result.put("evidence", node.path("evidence").asText(""));
            result.put("nextLesson", node.path("nextLesson").asText(""));
            result.put("parentFeedback", node.path("parentFeedback").asText(""));
            return result;
        } catch (Exception e) {
            log.warn("teacherLessonSummary JSON parse failed: {}", raw);
            return Map.of("mainIssue", raw, "evidence", "", "nextLesson", "", "parentFeedback", "");
        }
    }

    /**
     * 基于观察列表判断是否形成稳定 Pattern（AI 增强，不替代规则）。
     * 返回：stable / type / confidence / evidence
     */
    public Map<String, Object> evaluatePatternWithAi(String rulePatternType, List<String> observationContents, String ruleEvidence) {
        String obs = (observationContents == null || observationContents.isEmpty())
                ? "（无）"
                : String.join("\n- ", observationContents.stream().limit(10).toList());
        String prompt = """
                你是初中数学1对1课堂诊断助手。
                你的任务是判断：这些观察是否已形成稳定模式趋势。
                
                规则初判类型：%s
                规则证据：%s
                观察列表：
                - %s
                
                请输出 JSON（严格只输出 JSON）：
                {
                  "stable": true,
                  "type": "step_break|transfer_issue|dependency",
                  "confidence": 0.78,
                  "evidence": "一句证据总结"
                }
                
                要求：
                - stable 为 true 表示形成稳定模式
                - confidence 范围 0~1
                - type 只能是 step_break / transfer_issue / dependency
                - evidence 要简短、可解释
                """.formatted(
                rulePatternType == null ? "dependency" : rulePatternType,
                ruleEvidence == null ? "（无）" : ruleEvidence,
                obs
        );

        String fallback = """
                {
                  "stable": true,
                  "type": "%s",
                  "confidence": 0.72,
                  "evidence": "%s"
                }
                """.formatted(
                rulePatternType == null ? "dependency" : rulePatternType,
                ruleEvidence == null ? "规则证据支持形成趋势" : ruleEvidence.replace("\"", "'")
        );

        String raw = chatDiagnostic(SYSTEM_GLOBAL, prompt, fallback);
        try {
            JsonNode node = objectMapper.readTree(raw.trim()
                    .replaceFirst("(?s)^```json\\s*", "").replaceFirst("(?s)\\s*```$", ""));
            String type = node.path("type").asText("dependency");
            if (!Set.of("step_break", "transfer_issue", "dependency").contains(type)) {
                type = "dependency";
            }
            double confidence = Math.max(0, Math.min(1, node.path("confidence").asDouble(0.7)));
            return Map.of(
                    "stable", node.path("stable").asBoolean(true),
                    "type", type,
                    "confidence", confidence,
                    "evidence", node.path("evidence").asText(ruleEvidence == null ? "" : ruleEvidence)
            );
        } catch (Exception e) {
            log.warn("evaluatePatternWithAi parse failed: {}", raw);
            return Map.of(
                    "stable", true,
                    "type", rulePatternType == null ? "dependency" : rulePatternType,
                    "confidence", 0.72,
                    "evidence", ruleEvidence == null ? "规则证据支持形成趋势" : ruleEvidence
            );
        }
    }

    /**
     * 基于 Pattern 生成可执行策略（AI 增强表达，不替老师决策）
     */
    public List<Map<String, Object>> strategySuggestionsWithAi(String patternType, String evidenceSummary) {
        String prompt = """
                你是初中数学1对1课堂助教。请根据 pattern 输出老师可执行策略。
                
                patternType: %s
                evidence: %s
                
                输出 JSON（严格只输出 JSON）：
                {
                  "strategies": [
                    {"suggestion":"可执行策略1（<=20字）","priority":1},
                    {"suggestion":"可执行策略2（<=20字）","priority":1},
                    {"suggestion":"可执行策略3（<=20字）","priority":2}
                  ]
                }
                
                要求：
                - 仅给老师动作，不要替老师做决策
                - 策略必须可执行、可落地
                - priority 仅允许 1 或 2
                """.formatted(
                patternType == null ? "dependency" : patternType,
                evidenceSummary == null ? "（无）" : evidenceSummary
        );

        String fallback = """
                {
                  "strategies": [
                    {"suggestion":"每题先让学生口述思路","priority":1},
                    {"suggestion":"提示前先独立思考60秒","priority":1},
                    {"suggestion":"结束前做一次无提示复现","priority":2}
                  ]
                }
                """;

        String raw = chatDiagnostic(SYSTEM_GLOBAL, prompt, fallback);
        try {
            JsonNode node = objectMapper.readTree(raw.trim()
                    .replaceFirst("(?s)^```json\\s*", "").replaceFirst("(?s)\\s*```$", ""));
            JsonNode arr = node.path("strategies");
            if (!arr.isArray()) {
                return List.of();
            }
            List<Map<String, Object>> out = new ArrayList<>();
            for (JsonNode item : arr) {
                String suggestion = item.path("suggestion").asText("").trim();
                if (suggestion.isBlank()) {
                    continue;
                }
                int p = item.path("priority").asInt(1);
                p = (p <= 1) ? 1 : 2;
                out.add(Map.of("suggestion", suggestion, "priority", p));
                if (out.size() >= 5) {
                    break;
                }
            }
            return out;
        } catch (Exception e) {
            log.warn("strategySuggestionsWithAi parse failed: {}", raw);
            return List.of();
        }
    }

    private List<String> extractKeywords(String text, int max) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Pattern p = Pattern.compile("[\\p{IsHan}A-Za-z0-9]{2,}");
        Matcher m = p.matcher(text);
        Map<String, Integer> freq = new HashMap<>();
        Set<String> stop = new HashSet<>(List.of("这个", "那个", "我们", "他们", "然后", "就是", "因为", "所以", "如果", "但是", "视频", "片段", "以及", "一个", "没有", "可以", "进行"));
        while (m.find()) {
            String w = m.group().trim();
            if (w.length() < 2 || stop.contains(w)) {
                continue;
            }
            freq.put(w, freq.getOrDefault(w, 0) + 1);
        }
        return freq.entrySet().stream()
                .sorted(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue).reversed())
                .limit(max)
                .map(Map.Entry::getKey)
                .toList();
    }

    // ===================== v2 新方法 =====================

    /**
     * v2: 基于 Pattern 生成教育解释 Insight。
     * 输出 JSON: {insight_text, possible_causes:[], teaching_implications:[], confidence}
     */
    public Map<String, Object> generateInsight(String patternType, String evidenceSummary, List<String> observationSamples) {
        String samples = (observationSamples == null || observationSamples.isEmpty())
                ? "（无）"
                : String.join("\n- ", observationSamples.stream().limit(8).toList());
        String prompt = """
                你是懂初中数学教学和青少年学习心理的教学分析助理。
                请根据候选 Pattern 和证据，生成教育解释 Insight。

                Pattern 类型：%s
                证据摘要：%s
                观察样本：
                - %s

                要求：
                - 不要停留在表面现象，解释问题本质可能是什么
                - 说明如果继续原教学方式可能有什么风险
                - 必须用"可能"、"更像是"等谨慎措辞，输出需要老师确认
                - 不超过 150 字

                输出 JSON（严格只输出 JSON）：
                {
                  "insight_text": "...",
                  "possible_causes": ["原因1","原因2"],
                  "teaching_implications": ["教学影响1","教学影响2"],
                  "confidence": 0.7
                }
                """.formatted(
                patternType == null ? "unknown" : patternType,
                evidenceSummary == null ? "（无）" : evidenceSummary,
                samples);

        String fallback = """
                {
                  "insight_text": "学生在 %s 维度反复出现信号，建议老师确认是真实问题还是阶段性现象。",
                  "possible_causes": ["阶段性反应","知识点不熟"],
                  "teaching_implications": ["建议先确认再调整教学策略"],
                  "confidence": 0.6
                }
                """.formatted(patternType == null ? "未知" : patternType);

        String raw = chatDiagnostic(SYSTEM_GLOBAL, prompt, fallback);
        try {
            JsonNode node = objectMapper.readTree(stripJsonFence(raw));
            Map<String, Object> out = new HashMap<>();
            out.put("insight_text", node.path("insight_text").asText(""));
            List<String> causes = new ArrayList<>();
            node.path("possible_causes").forEach(n -> causes.add(n.asText()));
            out.put("possible_causes", causes);
            List<String> impls = new ArrayList<>();
            node.path("teaching_implications").forEach(n -> impls.add(n.asText()));
            out.put("teaching_implications", impls);
            out.put("confidence", Math.max(0, Math.min(1, node.path("confidence").asDouble(0.6))));
            return out;
        } catch (Exception e) {
            log.warn("generateInsight parse failed: {}", raw);
            return Map.of(
                    "insight_text", raw,
                    "possible_causes", List.of(),
                    "teaching_implications", List.of(),
                    "confidence", 0.5
            );
        }
    }

    /**
     * v2: 基于 confirmed Pattern + Insight 生成下节课教学决策。
     * 输出 JSON: {lesson_focus, practice_policy, interaction_policy, material_policy, difficulty_policy}
     */
    public Map<String, Object> generateTeachingDecision(
            List<String> patternTypes,
            List<String> insightTexts,
            String teacherFeedbackHint,
            String studentProfileHint) {
        String patterns = (patternTypes == null || patternTypes.isEmpty()) ? "（暂无）" : String.join("、", patternTypes);
        String insights = (insightTexts == null || insightTexts.isEmpty())
                ? "（暂无）"
                : String.join("\n- ", insightTexts.stream().limit(5).toList());
        String prompt = """
                你是初中数学一对一教学设计专家。
                请根据已确认或部分确认的 Pattern、Insight、老师反馈、学生画像，生成下一节课教学决策。
                不要直接生成题目，只输出教学方向。

                已确认 Pattern：%s
                Insight：
                - %s
                老师历史偏好/反馈：%s
                学生画像提示：%s

                输出 JSON（严格只输出 JSON）：
                {
                  "lesson_focus": "本节课要解决的真问题（一句话）",
                  "practice_policy": "练习策略（少量分步题/变式题/半成品题等）",
                  "interaction_policy": "互动策略（每步追问/复述/自检等）",
                  "material_policy": "材料策略（路线图/天平图示/对比表等）",
                  "difficulty_policy": "难度策略（降低起步/保持/提升）",
                  "reason": "为什么这样设计"
                }
                """.formatted(
                patterns,
                insights,
                teacherFeedbackHint == null || teacherFeedbackHint.isBlank() ? "（无）" : teacherFeedbackHint,
                studentProfileHint == null || studentProfileHint.isBlank() ? "（无）" : studentProfileHint);

        String fallback = """
                {
                  "lesson_focus": "围绕已确认的学生问题做精准训练",
                  "practice_policy": "少量分步题，先建立框架再加难度",
                  "interaction_policy": "每步追问为什么，避免直接给步骤",
                  "material_policy": "用路线图或对比图示外化思考",
                  "difficulty_policy": "降低起步难度，逐步提升",
                  "reason": "保守起步，先稳住学生节奏"
                }
                """;
        String raw = chatDiagnostic(SYSTEM_GLOBAL, prompt, fallback);
        try {
            JsonNode node = objectMapper.readTree(stripJsonFence(raw));
            Map<String, Object> out = new HashMap<>();
            out.put("lesson_focus", node.path("lesson_focus").asText(""));
            out.put("practice_policy", node.path("practice_policy").asText(""));
            out.put("interaction_policy", node.path("interaction_policy").asText(""));
            out.put("material_policy", node.path("material_policy").asText(""));
            out.put("difficulty_policy", node.path("difficulty_policy").asText(""));
            out.put("reason", node.path("reason").asText(""));
            return out;
        } catch (Exception e) {
            log.warn("generateTeachingDecision parse failed: {}", raw);
            return Map.of(
                    "lesson_focus", "需要更多数据",
                    "practice_policy", "维持现有节奏",
                    "interaction_policy", "保持开放追问",
                    "material_policy", "暂不变更",
                    "difficulty_policy", "保持",
                    "reason", "数据不足，保守输出"
            );
        }
    }

    /**
     * v2: 把老师自然语言备注抽成多条 observation 证据。
     * 用主档（GPT-4o-mini）即可，不需要重档。
     * 输出 JSON: {observations: [{observation_type, content, confidence}]}
     */
    public List<Map<String, Object>> extractTeacherNoteEvidence(String teacherNote) {
        if (teacherNote == null || teacherNote.isBlank()) {
            return List.of();
        }
        String prompt = """
                你是结构化教学观察抽取工具。从老师的一段自然语言备注里，抽取若干条事实性观察。
                只输出 JSON，不要解释。

                老师备注："%s"

                允许的 observation_type：
                hesitation / step_error / repeated_error / hint_dependency_signal /
                confirmation_dependency_signal / concept_explanation_failure /
                vague_understanding_signal / expression_difficulty_signal /
                start_difficulty_signal / transfer_signal / skip_steps_signal /
                high_load_signal / frustration_signal / modeling_entry_difficulty_signal /
                surface_understanding_signal

                规则：
                - 不要输出 pattern 或诊断结论
                - 只能说"观察到什么"
                - 每条 confidence 0.0-1.0
                - 最多输出 4 条
                - 老师如果说"他不是X"，是否定信号，type 用对应否定标签或忽略

                输出 JSON 格式：
                {
                  "observations": [
                    {"observation_type":"...","content":"基于老师原话的简短描述","confidence":0.85}
                  ]
                }
                """.formatted(teacherNote.replace("\"", "'"));

        String raw = chat(SYSTEM_GLOBAL, prompt,
                "{\"observations\":[]}");
        try {
            JsonNode node = objectMapper.readTree(stripJsonFence(raw));
            List<Map<String, Object>> out = new ArrayList<>();
            JsonNode arr = node.path("observations");
            if (!arr.isArray()) return List.of();
            for (JsonNode item : arr) {
                String type = item.path("observation_type").asText("").trim();
                String content = item.path("content").asText("").trim();
                if (type.isBlank() || content.isBlank()) continue;
                double conf = Math.max(0, Math.min(1, item.path("confidence").asDouble(0.7)));
                out.add(Map.of(
                        "observation_type", type,
                        "content", content,
                        "confidence", conf
                ));
                if (out.size() >= 4) break;
            }
            return out;
        } catch (Exception e) {
            log.warn("extractTeacherNoteEvidence parse failed: {}", raw);
            return List.of();
        }
    }

    private String stripJsonFence(String raw) {
        if (raw == null) return "{}";
        return raw.trim()
                .replaceFirst("(?s)^```json\\s*", "")
                .replaceFirst("(?s)^```\\s*", "")
                .replaceFirst("(?s)\\s*```$", "");
    }
}

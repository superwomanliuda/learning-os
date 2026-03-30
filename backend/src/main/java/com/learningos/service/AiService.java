package com.learningos.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;
    private final String model;
    private final int maxTokens;
    private final String apiKey;

    public AiService(ObjectMapper objectMapper,
                     @Value("${ai.openai.base-url}") String baseUrl,
                     @Value("${ai.openai.api-key}") String apiKey,
                     @Value("${ai.openai.model}") String model,
                     @Value("${ai.openai.max-tokens:1024}") int maxTokens) {
        this.objectMapper = objectMapper;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl)
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
        return chat(SYSTEM_GLOBAL, user, "1. 卡点：待补充学习过程数据。\n2. 提示依赖：数据不足。\n3. 错误模式：待观察。\n4. 建议：继续收集课堂标记与作业提示记录。");
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

    private String chat(String system, String user, String fallback) {
        if (apiKey == null || apiKey.isBlank() || "your-openai-api-key".equals(apiKey)) {
            log.warn("未配置有效 OPENAI_API_KEY，使用本地兜底回复");
            return fallback;
        }
        try {
            Map<String, Object> body = Map.of(
                    "model", model,
                    "max_tokens", maxTokens,
                    "messages", List.of(
                            Map.of("role", "system", "content", system),
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
                        log.warn("LLM 调用失败: {}", e.getMessage());
                        return Mono.just("");
                    })
                    .block();
            if (raw == null || raw.isBlank()) {
                return fallback;
            }
            JsonNode root = objectMapper.readTree(raw);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return fallback;
            }
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
}

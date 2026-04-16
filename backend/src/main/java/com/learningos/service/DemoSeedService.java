package com.learningos.service;

import com.learningos.model.*;
import com.learningos.repository.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class DemoSeedService {

    private final UserRepository userRepository;
    private final LearningSessionRepository sessionRepository;
    private final HomeworkAttemptRepository homeworkAttemptRepository;
    private final LearningEventRepository learningEventRepository;
    private final ObservationRepository observationRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoSeedService(UserRepository userRepository,
                           LearningSessionRepository sessionRepository,
                           HomeworkAttemptRepository homeworkAttemptRepository,
                           LearningEventRepository learningEventRepository,
                           ObservationRepository observationRepository,
                           PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.homeworkAttemptRepository = homeworkAttemptRepository;
        this.learningEventRepository = learningEventRepository;
        this.observationRepository = observationRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public Map<String, Object> seed() {
        cleanDemoData();

        User xiaoming = createStudent("demo_xiaoming", "小明（演示）", "初一");
        User xiaohong = createStudent("demo_xiaohong", "小红（演示）", "初一");
        User xiaohua = createStudent("demo_xiaohua", "小华（演示）", "初一");

        seedXiaoming(xiaoming);
        seedXiaohong(xiaohong);
        seedXiaohua(xiaohua);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "演示数据生成成功");
        result.put("students", List.of(
            Map.of("name", "小明（依赖提示型）", "id", xiaoming.getId()),
            Map.of("name", "小红（步骤卡壳型）", "id", xiaohong.getId()),
            Map.of("name", "小华（迁移困难型）", "id", xiaohua.getId())
        ));
        return result;
    }

    // ─── 小明：依赖提示型 ───────────────────────────────────────────────────────
    // 特征：遇到题目不先思考，平均10秒就求助，提示依赖度高
    private void seedXiaoming(User student) {
        String sessionId = createSession(student.getId(), "一元一次方程 第1次课后作业");

        // 作业1：2x + 3 = 7（基础题，求助1次）
        HomeworkAttempt a1 = attempt(student.getId(), sessionId,
                "解方程：2x + 3 = 7",
                "x = 5", 1, 1, HomeworkAttempt.AttemptResult.CORRECT, 10);
        event(student.getId(), sessionId, "homework_hint_used", 120,
                payload(a1.getId(), 1, "移项", "基础题", null, 10,
                        "x = 5", "2x + 3 = 7"));

        // 作业2：3x - 5 = 10（基础题，求助2次）
        HomeworkAttempt a2 = attempt(student.getId(), sessionId,
                "解方程：3x - 5 = 10",
                "x = 5", 2, 2, HomeworkAttempt.AttemptResult.CORRECT, 8);
        event(student.getId(), sessionId, "homework_hint_used", 300,
                payload(a2.getId(), 1, "移项", "基础题", null, 8,
                        "x = ?", "3x - 5 = 10"));
        event(student.getId(), sessionId, "homework_hint_used", 360,
                payload(a2.getId(), 2, "移项", "基础题", null, 8,
                        "3x = 5", "3x - 5 = 10"));

        // 作业3：x/2 + 1 = 3（去分母，求助3次）
        HomeworkAttempt a3 = attempt(student.getId(), sessionId,
                "解方程：x/2 + 1 = 3",
                "x = 4", 3, 3, HomeworkAttempt.AttemptResult.CORRECT, 12);
        event(student.getId(), sessionId, "homework_hint_used", 500,
                payload(a3.getId(), 1, "去分母", "基础题", null, 12,
                        "x = ?", "x/2 + 1 = 3"));
        event(student.getId(), sessionId, "homework_hint_used", 560,
                payload(a3.getId(), 2, "去分母", "基础题", null, 12,
                        "x/2 = 2", "x/2 + 1 = 3"));
        event(student.getId(), sessionId, "homework_hint_used", 620,
                payload(a3.getId(), 3, "去分母", "基础题", null, 12,
                        "x = 2", "x/2 + 1 = 3"));

        // 作业4：2(x - 1) = 6（去括号，求助4次）
        HomeworkAttempt a4 = attempt(student.getId(), sessionId,
                "解方程：2(x - 1) = 6",
                "x = 4", 4, 4, HomeworkAttempt.AttemptResult.CORRECT, 9);
        event(student.getId(), sessionId, "homework_hint_used", 700,
                payload(a4.getId(), 1, "去括号", "基础题", null, 9,
                        "x = ?", "2(x - 1) = 6"));
        event(student.getId(), sessionId, "homework_hint_used", 760,
                payload(a4.getId(), 2, "去括号", "基础题", null, 9,
                        "2x - 1 = 6", "2(x - 1) = 6"));
        event(student.getId(), sessionId, "homework_hint_used", 820,
                payload(a4.getId(), 3, "去括号", "基础题", null, 9,
                        "2x - 2 = 6", "2(x - 1) = 6"));
        event(student.getId(), sessionId, "homework_hint_used", 880,
                payload(a4.getId(), 4, "去括号", "基础题", null, 9,
                        "2x = 6", "2(x - 1) = 6"));

        // 课堂不懂标记（3次）
        event(student.getId(), sessionId, "class_confusion_mark", 200,
                "{\"topic\":\"去括号的分配律\",\"note\":\"学生举手说看不懂\"}");
        event(student.getId(), sessionId, "class_confusion_mark", 450,
                "{\"topic\":\"移项变号规则\",\"note\":\"说不知道为什么要变号\"}");
        event(student.getId(), sessionId, "class_confusion_mark", 650,
                "{\"topic\":\"去分母通分步骤\",\"note\":\"没有尝试就举手了\"}");

        // 预置观察记录（3条 behavior 类，足以触发 pattern 生成）
        observation(student.getId(), "system", "behavior",
                "小明在4道作业题中全部使用了提示，平均求助时间仅10秒，未见独立思考尝试。",
                "{\"hint_count_total\":10,\"avg_time_to_hint_sec\":10,\"knowledge_point\":\"一元一次方程综合\"}");
        observation(student.getId(), "system", "behavior",
                "小明在去括号题（2(x-1)=6）使用了4次提示（最高等级），显示对该类题型严重依赖外部支持。",
                "{\"hint_count\":4,\"hint_level\":4,\"knowledge_point\":\"去括号\"}");
        observation(student.getId(), "system", "behavior",
                "小明在3次课堂不懂标记中，教师描述均为【没有尝试就举手】，行为模式一致。",
                "{\"confusion_count\":3,\"pattern\":\"immediate_help_seeking\"}");
        observation(student.getId(), "teacher", "hesitation",
                "课堂观察：小明遇到新题型时眼神立刻看向老师，不会先自己读题，需要干预培养独立思考习惯。",
                "{\"source\":\"teacher_note\"}");
    }

    // ─── 小红：步骤卡壳型 ──────────────────────────────────────────────────────
    // 特征：理解题意，但每次卡在"移项变号"这一步，错误高度规律
    private void seedXiaohong(User student) {
        String sessionId = createSession(student.getId(), "一元一次方程 第2次课后作业");

        // 作业1：2x - 3 = x + 4，移项未变号写成 x = 1（应为 x = 7）
        HomeworkAttempt a1 = attempt(student.getId(), sessionId,
                "解方程：2x - 3 = x + 4",
                "x = 7", 1, 1, HomeworkAttempt.AttemptResult.CORRECT, 45);
        event(student.getId(), sessionId, "homework_hint_used", 180,
                payload(a1.getId(), 1, "移项", "基础题", "移项未变号：将 -3 移项后仍写作 -3，得 x = 1", 45,
                        "x = 1", "2x - 3 = x + 4"));

        // 作业2：5x - 2 = 3x + 6，移项未变号写成 2x = 4（应为 2x = 8）
        HomeworkAttempt a2 = attempt(student.getId(), sessionId,
                "解方程：5x - 2 = 3x + 6",
                "x = 4", 1, 1, HomeworkAttempt.AttemptResult.CORRECT, 52);
        event(student.getId(), sessionId, "homework_hint_used", 400,
                payload(a2.getId(), 1, "移项", "基础题", "移项未变号：-2 移到右边后写成 -2，得 2x = 4", 52,
                        "2x = 4", "5x - 2 = 3x + 6"));

        // 作业3：3(x + 2) = 15，忘记分配律，写成 3x + 2 = 15
        HomeworkAttempt a3 = attempt(student.getId(), sessionId,
                "解方程：3(x + 2) = 15",
                "x = 3", 2, 2, HomeworkAttempt.AttemptResult.CORRECT, 60);
        event(student.getId(), sessionId, "homework_hint_used", 600,
                payload(a3.getId(), 1, "去括号", "基础题", "去括号时未将括号外系数3分配给括号内每一项，写成 3x + 2 = 15", 60,
                        "3x + 2 = 15", "3(x + 2) = 15"));
        event(student.getId(), sessionId, "homework_hint_used", 660,
                payload(a3.getId(), 2, "去括号", "基础题", "提示后理解分配律，但计算 3×2 时写成 5", 60,
                        "3x + 5 = 15", "3(x + 2) = 15"));

        // 作业4：4x + 7 = 2x + 15，再次移项未变号
        HomeworkAttempt a4 = attempt(student.getId(), sessionId,
                "解方程：4x + 7 = 2x + 15",
                "x = 4", 1, 1, HomeworkAttempt.AttemptResult.CORRECT, 48);
        event(student.getId(), sessionId, "homework_hint_used", 800,
                payload(a4.getId(), 1, "移项", "基础题", "移项未变号：7 移到右边写作 +7，得 2x = 22", 48,
                        "2x = 22", "4x + 7 = 2x + 15"));

        // 预置观察记录（3条 step_error 类）
        observation(student.getId(), "system", "step_error",
                "小红在【解方程：2x-3=x+4】中，移项时将 -3 移到右边未变号，写出 x=1（正确答案 x=7）。提示1次后纠正。",
                "{\"error_step\":\"移项未变号\",\"knowledge_point\":\"移项\",\"wrong_answer\":\"x=1\",\"correct_answer\":\"x=7\"}");
        observation(student.getId(), "system", "step_error",
                "小红在【解方程：5x-2=3x+6】中，再次移项未变号，-2 移项后仍为负，得 2x=4（正确 2x=8）。",
                "{\"error_step\":\"移项未变号\",\"knowledge_point\":\"移项\",\"wrong_answer\":\"2x=4\",\"correct_answer\":\"2x=8\"}");
        observation(student.getId(), "system", "step_error",
                "小红在【解方程：4x+7=2x+15】中，第三次出现移项未变号错误，模式稳定，非偶发性笔误。",
                "{\"error_step\":\"移项未变号\",\"knowledge_point\":\"移项\",\"occurrence_count\":3}");
        observation(student.getId(), "teacher", "step_error",
                "课堂观察：小红做题时移项步骤总是口头背【移项变号】但实际书写时仍写错，说明规则记住了但应用未自动化。",
                "{\"source\":\"teacher_note\",\"insight\":\"规则记忆与应用脱节\"}");
    }

    // ─── 小华：迁移困难型 ──────────────────────────────────────────────────────
    // 特征：基础题全对无提示，一遇到应用题或变式题就崩
    private void seedXiaohua(User student) {
        String sessionId = createSession(student.getId(), "一元一次方程 第3次课后作业");

        // 基础题1：全对，无提示（正常）
        attempt(student.getId(), sessionId,
                "解方程：2x + 3 = 7",
                "x = 2", 0, 0, HomeworkAttempt.AttemptResult.CORRECT, 0);

        // 基础题2：全对，无提示（正常）
        attempt(student.getId(), sessionId,
                "解方程：3x - 5 = 10",
                "x = 5", 0, 0, HomeworkAttempt.AttemptResult.CORRECT, 0);

        // 基础题3：全对，无提示（正常）
        attempt(student.getId(), sessionId,
                "解方程：4x + 1 = 2x + 9",
                "x = 4", 0, 0, HomeworkAttempt.AttemptResult.CORRECT, 0);

        // 应用题1：租船问题（崩了，提示4次，最终做错）
        HomeworkAttempt a4 = attempt(student.getId(), sessionId,
                "某班同学去春游，租船若干条。每条坐6人多4人，若每条坐7人则少用1条船。设船有x条，列方程求解。",
                "6x + 4 = 7(x - 1)，解得 x = 11", 4, 4, HomeworkAttempt.AttemptResult.INCORRECT, 120);
        event(student.getId(), sessionId, "homework_hint_used", 300,
                payload(a4.getId(), 1, "应用题建模", "应用题", "不知道如何设未知数，直接写出了 6x=7x 的错误等式", 120,
                        "6x = 7x - 1", "租船问题"));
        event(student.getId(), sessionId, "homework_hint_used", 420,
                payload(a4.getId(), 2, "应用题建模", "应用题", "提示设 x 为船数后，等量关系理解有误", 120,
                        "6x + 4 = 7x", "租船问题"));
        event(student.getId(), sessionId, "homework_hint_used", 540,
                payload(a4.getId(), 3, "应用题建模", "应用题", "等量关系建立后方程写错：右边少了括号", 120,
                        "6x + 4 = 7x - 1", "租船问题"));
        event(student.getId(), sessionId, "homework_hint_used", 640,
                payload(a4.getId(), 4, "应用题建模", "应用题", "提示最高等级后仍解出 x = 5（正确答案 x = 11）", 120,
                        "6x + 4 = 7x - 1 → x = 5", "租船问题"));
        event(student.getId(), sessionId, "class_confusion_mark", 350,
                "{\"topic\":\"应用题中等量关系的建立\",\"note\":\"学生说不知道哪两个量相等\"}");

        // 应用题2：年龄问题（崩了，提示3次）
        HomeworkAttempt a5 = attempt(student.getId(), sessionId,
                "小明今年比他爸爸小26岁，4年后爸爸的年龄是小明的3倍，求小明今年的年龄。",
                "x = 9", 3, 3, HomeworkAttempt.AttemptResult.CORRECT, 90);
        event(student.getId(), sessionId, "homework_hint_used", 750,
                payload(a5.getId(), 1, "应用题建模", "应用题", "将【4年后】漏掉，直接列了 x + 26 = 3x", 90,
                        "x + 26 = 3x", "年龄问题"));
        event(student.getId(), sessionId, "homework_hint_used", 840,
                payload(a5.getId(), 2, "应用题建模", "应用题", "加入时间条件后等量关系写成 (x+4) + 26 = 3x", 90,
                        "(x+4) + 26 = 3x", "年龄问题"));
        event(student.getId(), sessionId, "homework_hint_used", 920,
                payload(a5.getId(), 3, "应用题建模", "应用题", "提示等量关系后正确列出 (x+26+4) = 3(x+4)", 90,
                        "(x+30) = 3(x+4)", "年龄问题"));
        event(student.getId(), sessionId, "class_confusion_mark", 800,
                "{\"topic\":\"含时间变化的等量关系\",\"note\":\"学生说搞不清楚谁加4谁不加4\"}");

        // 分式变式题（崩了，提示3次）
        HomeworkAttempt a6 = attempt(student.getId(), sessionId,
                "解方程：(x + 1)/2 - x/3 = 1",
                "x = 4", 3, 3, HomeworkAttempt.AttemptResult.CORRECT, 80);
        event(student.getId(), sessionId, "homework_hint_used", 1000,
                payload(a6.getId(), 1, "去分母", "变式题", "不知道如何通分，直接将左边两项相减得 (x+1-x)/5 = 1", 80,
                        "(x+1-x)/5 = 1", "(x+1)/2 - x/3 = 1"));
        event(student.getId(), sessionId, "homework_hint_used", 1080,
                payload(a6.getId(), 2, "去分母", "变式题", "提示最小公倍数为6后，展开时系数算错：2×(x+1)写成 2x+1", 80,
                        "2x + 1 - 3x = 6", "(x+1)/2 - x/3 = 1"));
        event(student.getId(), sessionId, "homework_hint_used", 1140,
                payload(a6.getId(), 3, "去分母", "变式题", "纠正分配律错误后正确完成", 80,
                        "2(x+1) - 3x = 6 → x = 4", "(x+1)/2 - x/3 = 1"));
        event(student.getId(), sessionId, "class_confusion_mark", 1050,
                "{\"topic\":\"异分母通分后展开系数的处理\",\"note\":\"学生说和之前做的题感觉不一样\"}");

        // 预置观察记录（3条，触发 transfer_issue 模式）
        observation(student.getId(), "system", "behavior",
                "小华在3道基础题（标准形式方程）中全部做对且无需提示，但在2道应用题中均需3次以上提示，正确率明显下降。",
                "{\"base_accuracy\":\"3/3\",\"application_accuracy\":\"1/2\",\"knowledge_point\":\"应用题建模\",\"problem_type_gap\":true}");
        observation(student.getId(), "system", "behavior",
                "小华在分式变式题 (x+1)/2 - x/3 = 1 中，将去分母后的分配律处理错误（2×(x+1) 写成 2x+1），而基础去分母题无此错误，说明迁移时遗漏步骤。",
                "{\"error_step\":\"去分母后展开时系数未分配\",\"knowledge_point\":\"去分母\",\"problem_type\":\"变式题\"}");
        observation(student.getId(), "system", "behavior",
                "小华在3次课堂不懂标记中，2次与应用题等量关系有关，1次与通分有关，均发生在题型变化时（非标准形式），表现出明显的迁移困难。",
                "{\"confusion_count\":3,\"trigger\":\"题型变化\",\"pattern\":\"transfer_difficulty\"}");
        observation(student.getId(), "teacher", "behavior",
                "课堂观察：小华解标准形式方程时非常流畅，几乎不需要引导。但一旦题目套上情景或变式，就会呆住，说【这道题和上面的不一样】。",
                "{\"source\":\"teacher_note\",\"insight\":\"缺乏题型识别和算法迁移能力\"}");
    }

    // ─── 工具方法 ──────────────────────────────────────────────────────────────

    private User createStudent(String username, String name, String grade) {
        if (userRepository.existsByUsername(username)) {
            return userRepository.findByUsername(username).get();
        }
        User u = new User();
        u.setUsername(username);
        u.setName(name);
        u.setPassword(passwordEncoder.encode("demo123"));
        u.setRole(User.Role.STUDENT);
        u.setGrade(grade);
        return userRepository.save(u);
    }

    private String createSession(Long studentId, String title) {
        LearningSession s = new LearningSession();
        s.setId(UUID.randomUUID().toString());
        s.setStudentId(studentId);
        s.setType(LearningSession.SessionType.HOMEWORK);
        s.setTitle(title);
        s.setStartedAt(LocalDateTime.now().minusHours(2));
        s.setEndedAt(LocalDateTime.now().minusMinutes(30));
        s.setDurationSec(5400);
        s.setStatus(LearningSession.Status.COMPLETED);
        return sessionRepository.save(s).getId();
    }

    private HomeworkAttempt attempt(Long studentId, String sessionId,
                                     String question, String answer,
                                     int hintCount, int lastHintLevel,
                                     HomeworkAttempt.AttemptResult result,
                                     int stuckDurationSec) {
        HomeworkAttempt a = new HomeworkAttempt();
        a.setStudentId(studentId);
        a.setSessionId(sessionId);
        a.setQuestionText(question);
        a.setStudentAnswer(answer);
        a.setHintCount(hintCount);
        a.setLastHintLevel(lastHintLevel);
        a.setResult(result);
        a.setStuckDurationSec(stuckDurationSec);
        return homeworkAttemptRepository.save(a);
    }

    private void event(Long studentId, String sessionId,
                       String eventType, int timestampSec, String payload) {
        LearningEvent e = new LearningEvent();
        e.setStudentId(studentId);
        e.setSessionId(sessionId);
        e.setEventType(eventType);
        e.setTimestampSec(timestampSec);
        e.setPayload(payload);
        learningEventRepository.save(e);
    }

    private void observation(Long studentId, String type, String category,
                              String content, String evidence) {
        Observation o = new Observation();
        o.setStudentId(studentId);
        o.setType(type);
        o.setCategory(category);
        o.setContent(content);
        o.setEvidence(evidence);
        observationRepository.save(o);
    }

    private String payload(Long attemptId, int hintLevel,
                            String knowledgePoint, String problemType,
                            String errorStep, int timeToFirstHintSec,
                            String studentAnswerBeforeHint, String questionExcerpt) {
        String errorStepJson = errorStep != null
                ? "\"error_step\":\"" + errorStep + "\","
                : "";
        return String.format(
                "{\"homework_attempt_id\":%d,\"hint_level\":%d," +
                "\"knowledge_point\":\"%s\",\"problem_type\":\"%s\"," +
                "%s\"time_to_first_hint_sec\":%d," +
                "\"student_answer_before_hint\":\"%s\"," +
                "\"question_excerpt\":\"%s\"}",
                attemptId, hintLevel,
                knowledgePoint, problemType,
                errorStepJson, timeToFirstHintSec,
                studentAnswerBeforeHint.replace("\"", "'"),
                questionExcerpt.replace("\"", "'"));
    }

    private void cleanDemoData() {
        List<String> demoUsernames = List.of("demo_xiaoming", "demo_xiaohong", "demo_xiaohua");
        for (String username : demoUsernames) {
            userRepository.findByUsername(username).ifPresent(user -> {
                Long uid = user.getId();
                observationRepository.deleteAll(observationRepository.findByStudentIdOrderByCreatedAtDesc(uid));
                learningEventRepository.deleteAll(learningEventRepository.findByStudentIdOrderByCreatedAtDesc(uid));
                homeworkAttemptRepository.deleteAll(homeworkAttemptRepository.findByStudentIdOrderByCreatedAtDesc(uid));
                sessionRepository.deleteAll(sessionRepository.findByStudentIdOrderByStartedAtDesc(uid));
                userRepository.delete(user);
            });
        }
    }
}

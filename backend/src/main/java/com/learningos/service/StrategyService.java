package com.learningos.service;

import com.learningos.model.Pattern;
import com.learningos.model.Strategy;
import com.learningos.repository.PatternRepository;
import com.learningos.repository.StrategyRepository;
import com.learningos.util.SecurityUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StrategyService {

    private final PatternRepository patternRepository;
    private final StrategyRepository strategyRepository;
    private final AiService aiService;

    public StrategyService(PatternRepository patternRepository,
                           StrategyRepository strategyRepository,
                           AiService aiService) {
        this.patternRepository = patternRepository;
        this.strategyRepository = strategyRepository;
        this.aiService = aiService;
    }

    /**
     * 只基于老师已确认的 pattern 生成策略。
     * Strategy 必须是可执行动作（课堂动作、提问动作、练习动作）。
     */
    @Transactional
    public List<Strategy> generateStrategy(Long requestedStudentId, Long patternId) {
        Long studentId = resolveStudentId(requestedStudentId);
        List<Pattern> sources = resolvePatternSources(studentId, patternId);
        if (sources.isEmpty()) {
            throw new IllegalArgumentException("暂无可生成策略的已确认 Pattern");
        }

        List<Strategy> created = new ArrayList<>();
        for (Pattern p : sources) {
            Strategy latest = strategyRepository
                    .findTopByStudentIdAndPatternIdAndActiveOrderByCreatedAtDesc(studentId, p.getId(), true);
            if (latest != null) {
                continue; // 避免重复堆叠同一 Pattern 的活跃策略
            }
            for (Strategy seed : buildStrategiesByPattern(p)) {
                created.add(strategyRepository.save(seed));
            }
        }
        return created;
    }

    public List<Strategy> listByStudent(Long requestedStudentId, Boolean activeOnly) {
        Long studentId = resolveStudentId(requestedStudentId);
        if (Boolean.TRUE.equals(activeOnly)) {
            return strategyRepository.findByStudentIdAndActiveOrderByPriorityAscCreatedAtDesc(studentId, true);
        }
        return strategyRepository.findByStudentIdOrderByPriorityAscCreatedAtDesc(studentId);
    }

    private List<Pattern> resolvePatternSources(Long studentId, Long patternId) {
        if (patternId != null) {
            Pattern p = patternRepository.findById(patternId)
                    .orElseThrow(() -> new IllegalArgumentException("Pattern 不存在"));
            if (!p.getStudentId().equals(studentId)) {
                throw new IllegalArgumentException("pattern 与 studentId 不匹配");
            }
            if (!Boolean.TRUE.equals(p.getConfirmed())) {
                throw new IllegalArgumentException("Pattern 尚未被老师确认，不能生成策略");
            }
            return List.of(p);
        }
        return patternRepository.findByStudentIdAndConfirmedOrderByConfidenceScoreDescCreatedAtDesc(studentId, true)
                .stream()
                .limit(3)
                .toList();
    }

    private Long resolveStudentId(Long requestedStudentId) {
        String role = SecurityUtils.currentRole();
        if ("TEACHER".equalsIgnoreCase(role)) {
            if (requestedStudentId == null) {
                throw new IllegalArgumentException("老师侧生成策略需要 studentId");
            }
            return requestedStudentId;
        }
        return SecurityUtils.currentUserId();
    }

    private List<Strategy> buildStrategiesByPattern(Pattern pattern) {
        List<Seed> ruleSeeds = switch (pattern.getType()) {
            case "step_break" -> List.of(
                    new Seed("每题先口述第一步再动笔", 1),
                    new Seed("把卡点步骤拆成2个小问题", 1),
                    new Seed("先做同结构微题再回原题", 2)
            );
            case "transfer_issue" -> List.of(
                    new Seed("先做1道轻微变式题验证迁移", 1),
                    new Seed("要求学生总结识别信号", 1),
                    new Seed("禁止重复原题，改做同结构题", 2)
            );
            case "dependency" -> List.of(
                    new Seed("提示前先让学生独立想60秒", 1),
                    new Seed("每次提示只给方向不给步骤", 1),
                    new Seed("结束前复现一次无提示解题", 2)
            );
            default -> List.of(
                    new Seed("先让学生复述当前思路再推进", 1),
                    new Seed("优先追问原因而不是直接提示", 2)
            );
        };

        List<Map<String, Object>> aiSeeds = aiService.strategySuggestionsWithAi(
                pattern.getType(),
                pattern.getEvidenceSummary()
        );

        // 去重并保序：先 AI，后规则兜底
        Map<String, Integer> merged = new LinkedHashMap<>();
        for (Map<String, Object> ai : aiSeeds) {
            String suggestion = String.valueOf(ai.getOrDefault("suggestion", "")).trim();
            if (suggestion.isBlank()) {
                continue;
            }
            int priority = ((Number) ai.getOrDefault("priority", 1)).intValue();
            merged.putIfAbsent(suggestion, priority <= 1 ? 1 : 2);
            if (merged.size() >= 4) {
                break;
            }
        }
        for (Seed s : ruleSeeds) {
            merged.putIfAbsent(s.suggestion, s.priority);
            if (merged.size() >= 4) {
                break;
            }
        }

        return merged.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry::getValue))
                .map(e -> {
                    Strategy s = new Strategy();
                    s.setStudentId(pattern.getStudentId());
                    s.setPatternId(pattern.getId());
                    s.setSuggestion(e.getKey());
                    s.setPriority(e.getValue());
                    s.setActive(true);
                    return s;
                })
                .toList();
    }

    private record Seed(String suggestion, int priority) {}
}

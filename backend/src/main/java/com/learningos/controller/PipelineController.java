package com.learningos.controller;

import com.learningos.dto.PipelineRunRequest;
import com.learningos.service.TeachingPipelineService;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/pipeline")
public class PipelineController {

    private final TeachingPipelineService pipelineService;

    public PipelineController(TeachingPipelineService pipelineService) {
        this.pipelineService = pipelineService;
    }

    /**
     * Stage 1：诊断（observation → pattern + 同步 pending insight）
     */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody PipelineRunRequest req) {
        return pipelineService.runDiagnose(req.getStudentId(), req.getSessionId());
    }

    /**
     * Stage 2：老师 review 完后生成 teaching decision
     */
    @PostMapping("/decision")
    public Map<String, Object> runDecision(@RequestBody PipelineRunRequest req) {
        return pipelineService.runDecisionAfterReview(req.getStudentId(), req.getSessionId());
    }
}

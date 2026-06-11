package com.learningos.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * v2: 课件里的具体内容块。一个 LessonKit 包含多个 Artifact。
 *
 * module 类型：
 * - lesson_flow         课堂流程（整体）
 * - phase               单个 Phase（含话术/板书/互动/提问/留白/小贴士）
 * - practice_set        练习题包（独立模块）
 * - homework            作业
 * - ppt_slide           PPT 页面大纲
 * - visual_explanation  图示提示词 + 占位图
 */
@Data
@Entity
@Table(name = "lesson_kit_artifact")
public class LessonKitArtifact {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lesson_kit_id", nullable = false)
    private Long lessonKitId;

    /** module 大类：见类注释 */
    @Column(nullable = false, length = 60)
    private String module;

    /** 细类型，如 phase 内的 opening/practice/recap 等 */
    @Column(name = "artifact_type", length = 60)
    private String artifactType;

    @Column(length = 255)
    private String title;

    /** 实际内容 JSON：含话术、板书、互动任务、提问脚本、留白点等 */
    @Column(columnDefinition = "JSON")
    private String content;

    /** 老师小贴士：怎么用这块、学生可能有什么反应、关键提示 */
    @Column(name = "teacher_tip", columnDefinition = "TEXT")
    private String teacherTip;

    /** 在课件里的顺序（同 module 内） */
    @Column
    private Integer position = 0;

    /** 生成依据：依赖了哪些 pattern/insight/decision/feedback ids */
    @Column(name = "trace_context", columnDefinition = "JSON")
    private String traceContext;

    /** 老师是否编辑过 */
    @Column(name = "teacher_edited")
    private Boolean teacherEdited = false;

    @Column(name = "teacher_edit_reason", columnDefinition = "TEXT")
    private String teacherEditReason;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();
}

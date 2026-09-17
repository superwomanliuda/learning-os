# 第三章 Context 生成与 Lesson Kit 生成

## 1. 模块目标

本模块负责完成以下流程：

```text
Classroom / Homework Events
↓
Event Pool
↓
Context
↓
Planning Intent
↓
参考内容检索
↓
Planning
↓
老师审核并确认
↓
Lesson Kit
↓
课堂 / 作业执行
↓
新的 Events
```

主要职责：

1. 接收各学习场景产生的标准化 Event；
2. 从 Event Pool 中识别对当前备课有价值的信息；
3. 聚合多个相关 Event 形成 Context；
4. 基于 Context、本节教学目标和课程约束形成 Planning Intent；
5. 检索老师历史资料、Teaching Unit 和题库；
6. 生成本节课 Planning；
7. 支持老师通过自然语言修改 Planning；
8. 老师确认 Planning 后生成 Lesson Kit；
9. 保存 Lesson Kit 与 Planning、Context、Event、Reference Unit 的来源关系。

---

# 2. 模块输入与输出

## 2.1 输入一：标准化 Event

当前支持：

```text
Classroom Event
Homework Event
```

未来其他学习场景只需按照统一 Event Schema 输出 Event，即可进入 Event Pool。

Context 模块不重新读取课堂录音、作业提交等底层业务数据。

---

## 2.2 输入二：本节课信息

包括：

- 学生；
- 年级；
- 教材；
- 当前教学章节；
- 本节课程主题；
- 教学目标；
- 必须涵盖内容；
- 课程时长；
- 老师其他要求。

例如：

```json
{
  "lessonTopic": "勾股定理的应用",
  "durationMinutes": 60,
  "objectives": ["能够判断勾股定理的适用条件", "能够解决简单长度计算问题"],
  "constraints": ["本节需要完成教材第18-20页"]
}
```

---

## 2.3 输入三：参考资料

老师可以在 Planning 前添加：

- 历史 Lesson Kit；
- 教案；
- PPT；
- 讲义；
- 题目；
- 学生历史作业；
- 板书；
- 作业设计；
- 课程大纲；
- 其他教学资料。

参考资料用于回答：

> 当前教学设计可以参考或复用哪些已有内容。

Context 与 Reference Material 不同：

```text
Context
= 学生此前发生了什么

Reference Material
= 当前课程可以参考什么
```

---

## 2.4 输出

```text
Context
↓
Planning
↓
Confirmed Planning
↓
Lesson Kit
```

---

# 3. Event 输入规范

所有场景 Event 使用统一基础结构。

```json
{
  "eventId": "event_001",
  "eventSource": "classroom",
  "eventType": "student_revised_after_support",

  "studentIds": ["student_001"],
  "courseId": "course_001",
  "lessonId": "lesson_001",

  "lessonStepIds": ["lesson_step_003"],

  "knowledgePointIds": ["pythagorean_theorem"],

  "capabilityIds": ["identify_application_condition"],

  "questionIds": ["question_012"],

  "observableSummary": "学生首次判断错误，在老师提示检查直角条件后修正答案",

  "sourceRefs": [
    {
      "sourceType": "classroom_answer",
      "sourceId": "answer_021"
    }
  ],

  "occurredAt": "datetime",
  "confidence": 0.94
}
```

公共字段允许为空。

---

## 3.1 lessonStepIds

`lessonStepIds` 为可选字段。

用于表示：

> 当前 Event 可以明确关联到学生实际经历过的某个历史 Lesson Step。

例如：

```text
lesson_step_003

展示三个三角形，
让学生判断哪些可以使用勾股定理。
```

对应课堂 Event 可以记录：

```json
{
  "lessonStepIds": ["lesson_step_003"]
}
```

该关联只表示课堂上下文关系。

不得根据该关联自动推断：

- 某教学方法有效；
- 某教学方法无效；
- 某 Lesson Step 导致某种学生表现；
- 老师是否严格执行 Lesson Kit。

无法可靠关联时：

```json
{
  "lessonStepIds": []
}
```

Event 仍然有效。

---

# 4. Event 来源与追踪

每个 Event 使用：

```text
sourceRefs
```

保存其原始业务来源。

完整追踪链：

```text
Lesson Kit Content
↓
sourcePlanningItemIds
↓
Planning Item
├── sourceContextIds
└── referenceUnitIds
       ↓
Context          Reference Unit
↓                ↓
sourceEventIds   Reference Material
↓
Event
↓
sourceRefs
↓
原始业务记录
```

不建立独立 Evidence 实体。

---

# 5. Event Pool

Event Pool 保存当前可以参与 Context 分析的标准化 Event。

```text
Event Pool
├── Classroom Events
├── Homework Events
└── future events...
```

不是所有 Event 都生成 Context。

普通行为记录可以长期存在于 Event Pool 中，但不进入本次 Context。

---

# 6. Context 定义

Context 是对多个相关 Event 的聚合结果。

它回答：

> 当前备课时，关于这个学生有哪些事实、变化或历史教学上下文值得关注？

处理流程：

```text
Event Pool
↓
确定当前课程关注范围
↓
筛选相关 Events
↓
关联相关 Events
↓
识别重复表现 / 变化 / 场景差异
↓
合并重复事实
↓
Context
```

Context 不直接决定教学方案。

---

# 7. Context Event 筛选

优先根据以下维度筛选 Event：

```text
student
course
knowledgePoint
capability
question / questionFamily
lessonStep
time
```

例如本节目标：

> 独立判断勾股定理适用条件。

优先检索：

```text
knowledgePoint = pythagorean_theorem
capability = identify_application_condition
```

核心筛选标准：

> 该信息是否可能影响本节 Planning？

不得仅因为 Event 较新或数量较多而进入 Context。

---

# 8. Context 生成情况

## 8.1 重复表现

例如：

```text
课堂出现
+
作业重复出现
```

Context：

> 学生在课堂和作业首次作答中多次未主动确认勾股定理适用条件。

---

## 8.2 前后变化

例如：

```text
首次错误
↓
提示后修正
↓
后续独立作答再次出现
```

Context：

> 学生在明确提示后能够关注直角条件，但在独立作答中仍多次没有主动检查该条件。

---

## 8.3 不同场景表现不一致

例如：

```text
课堂独立正确
+
作业首次错误
```

Context：

> 学生课堂中能够完成相关任务，但后续作业首次提交中没有保持相同表现。

不得自动选择某个场景作为“真实水平”。

---

## 8.4 稳定表现

例如：

```text
课堂多次独立正确
+
作业首次提交多次正确
```

Context：

> 学生在课堂和作业多个独立任务中均能够完成该目标。

稳定表现可以用于避免 Planning 机械重复基础内容。

---

## 8.5 已教学但缺少独立表现

Context：

> 该内容已经完成教学，但目前缺少学生独立作答数据，尚无法判断后续应用情况。

---

## 8.6 通常不形成 Context

包括：

- 单次普通正确；
- 单次轻微错误；
- 格式问题；
- 与当前课程无明显关系；
- 被更完整 Context 覆盖；
- 低置信度孤立事件；
- 不会改变当前 Planning 的信息。

---

# 9. Context 与历史 Lesson Step

Context 可以保存：

```json
{
  "relatedLessonStepIds": ["lesson_step_003"]
}
```

例如：

```text
历史教学：
概念说明 + 静态图

之后：
课堂独立判断仍存在问题
+
作业中再次出现
```

系统可以据此在 Planning 时避免机械重复同样的设计，并检索其他教学方案。

但不得形成：

> 静态图对该学生无效。

正确表达是：

> 此前已经使用静态图，之后相关表现仍值得继续处理。

---

# 10. Context Scope

第一版数据模型支持：

```text
class
group
student
```

当前教师端第一版为 1 对 1 时，主要使用：

```text
student
```

保留 class / group 用于未来扩展。

---

# 11. Context 数据结构

```json
{
  "contextId": "context_001",

  "target": {
    "knowledgePointIds": ["pythagorean_theorem"],
    "capabilityIds": ["identify_application_condition"]
  },

  "scope": {
    "type": "student",
    "studentIds": ["student_001"]
  },

  "summary": "学生课堂中需要提示才能关注直角条件；作业首次提交中多次未确认直角条件便使用勾股定理；收到批改后能够完成原题订正。",

  "sourceEventIds": ["classroom_event_021", "homework_event_031"],

  "relatedLessonStepIds": ["lesson_step_003"],

  "createdAt": "datetime"
}
```

---

# 12. Context 边界

Context 不保存：

- Planning Action；
- 教学方法好坏；
- 教学因果判断；
- AI 内部推理；
- Event 原始全文；
- Lesson Kit 修改方案；
- 学生心理、性格或态度推断；
- 固定“学习风格”标签。

Context 只保存：

> 学生表现、变化和相关历史教学上下文。

---

# 13. Reference Material 与 Reference Unit

## 13.1 Reference Material

老师添加的每项教学资料建立：

```json
{
  "referenceMaterialId": "reference_001",
  "referenceType": "lesson_kit",
  "sourceId": "lesson_kit_0725",
  "title": "7月25日 勾股定理基础",
  "selectedByTeacher": true
}
```

第一版支持：

```text
lesson_kit
lesson_plan
ppt
handout
question_set
student_work
board_note
homework_design
curriculum
```

---

## 13.2 Reference Unit

Reference Material 不整份发送给 AI。

处理流程：

```text
Reference Material
↓
解析结构
↓
按自然结构拆分
↓
Reference Unit
↓
建立检索索引
```

例如 Lesson Kit 可以拆为：

- 教学环节；
- PPT 页面；
- 板书；
- 练习；
- 作业。

题目通常一题一个 Unit。

---

## 13.3 Reference Unit 数据结构

```json
{
  "referenceUnitId": "unit_001",
  "referenceMaterialId": "reference_001",
  "sourceType": "lesson_kit",

  "sourceLocation": {
    "lessonStepId": "lesson_step_003"
  },

  "content": "展示三个不同三角形，让学生判断哪些可以使用勾股定理。",

  "summary": "通过多个图形判断勾股定理适用条件",

  "knowledgePointIds": ["pythagorean_theorem"],

  "capabilityIds": ["identify_application_condition"]
}
```

---

# 14. Teaching Unit

Teaching Unit 是可以脱离原完整 Lesson Kit，被其他老师独立理解和采用的教学设计单元。

例如：

- 一个讲解方式；
- 一个课堂活动；
- 一个正反例活动；
- 一个可视化演示；
- 一组提问；
- 一组练习；
- 一个学生操作任务；
- 一个独立检查活动。

---

## 14.1 Teaching Unit 数据结构

```json
{
  "teachingUnitId": "teaching_unit_001",
  "title": "勾股定理适用条件正反例判断",

  "sourceType": "internal_seed",

  "target": {
    "knowledgePointIds": ["pythagorean_theorem"],
    "capabilityIds": ["identify_application_condition"]
  },

  "intendedContext": ["学生知道公式，但独立作答时经常直接使用公式而不检查条件"],

  "objective": "让学生在计算前主动判断是否满足勾股定理适用条件",

  "teacherActions": ["依次展示正例和反例", "要求学生先判断", "追问判断依据"],

  "studentActions": ["独立判断", "说明依据", "比较不同案例"],

  "materialRefs": []
}
```

---

## 14.2 来源

第一版：

```text
internal_seed
teacher_shared
```

`teacher_shared` 必须保留创建老师。

不得包含具体学生私有数据。

---

## 14.3 Teaching Unit 与 Reference Unit

```text
Teaching Unit
= 可复用教学设计

Reference Unit
= AI 检索时使用的检索单位
```

第一版一个 Teaching Unit 可以映射成一个 Reference Unit，参与统一检索。

---

## 14.4 采纳

只有 Teaching Unit 实际进入 confirmed Planning，才记录：

```text
adopted
```

仅检索、查看或进入候选不计为采纳。

---

# 15. Planning Intent 与参考内容检索

## 15.1 Planning Intent

Context 生成完成后，系统先生成 Planning Intent。

输入：

```text
Final Context
+
本节教学目标
+
教学进度
+
课程约束
+
老师要求
```

Planning Intent 回答：

1. 本节需要处理哪些教学问题；
2. 为这些问题需要寻找哪些参考内容。

例如：

```text
需要处理：
让学生在没有提示时主动判断勾股定理适用条件。

需要寻找：
- 相关历史教学内容
- 可复用判断活动
- 独立检查任务
- 适合当前目标的表现形式
```

Planning Intent 不直接确定最终教学方法。

---

## 15.2 RAG 检索范围

系统从三类内容检索：

```text
老师自己的 Reference Units
+
internal_seed Teaching Units
+
teacher_shared Teaching Units
```

题目使用独立 Question Retrieval。

---

## 15.3 检索方式

第一版使用 Hybrid Retrieval：

```text
Metadata Filter
+
Keyword / Full-text Search
+
Vector Semantic Search
+
Rerank
```

Metadata 至少包括：

- subject；
- grade；
- knowledgePointIds；
- capabilityIds；
- sourceType；
- teacherId；
- current textbook / progress；
- resource requirement。

---

## 15.4 检索 Query

Query 由：

```text
Planning Intent
+
教学目标
+
相关 Context
```

生成。

不只使用知识点名称。

例如：

> 寻找能够让学生在无提示条件下主动判断勾股定理适用条件的教学活动。

---

## 15.5 候选排序

优先考虑：

1. 教学目标相关性；
2. Context 相关性；
3. 当前教学进度；
4. 当前课堂时间与设备约束；
5. 与老师历史内容的互补性；
6. Teaching Unit 内容完整性。

使用次数只能作为弱排序信号。

---

## 15.6 老师限制

老师可以明确：

- 不使用某份资料；
- 只参考指定资料；
- 不使用共享 Teaching Unit；
- 采用某个 Teaching Unit；
- 不采用某个 Teaching Unit。

后续 Planning 必须遵守。

---

# 16. Planning

## 16.1 Planning 定义

Planning 是本节 Lesson Kit 生成前，由老师确认的**完整教学方案**。

Planning 需要确定：

```text
本节重点处理什么
↓
整节课分成哪些教学环节
↓
每个环节达到什么目的
↓
老师如何组织这个环节
↓
学生需要做什么
↓
采用什么教学形式
↓
是否需要板书 / PPT / 动态图 / 交互 / 练习等资源
↓
每个环节安排多长时间
```

因此，以下内容属于 Planning：

- 教学重点；
- 教学环节；
- 每个环节的教学目的；
- 环节时长；
- 教学方式；
- Teacher Action 的方向；
- Student Action；
- 是否进行独立检查；
- 使用哪类教学资源；
- 是否复用 Teaching Unit / Reference Unit；
- 复杂资源是否必要。

Planning **不生成最终成品**：

- 不生成完整 PPT 文件；
- 不生成最终板书内容；
- 不生成完整题目正文；
- 不生成动画文件；
- 不生成完整教师讲稿。

---

## 16.2 Planning 输入

```text
Final Context
+
本节课程信息
+
Planning Intent
+
检索得到的 Reference Units
+
Teaching Units
+
课堂执行约束
```

---

## 16.3 Planning 总体结构

Planning 第一版输出：

```text
A. 本节教学目标
B. 本节教学重点
C. Planning Items
D. 教学流程
```

---

## 16.4 Planning Item

每个 Planning Item 表示一个明确教学决策。

```json
{
  "planningItemId": "planning_item_001",

  "target": {
    "knowledgePointIds": ["pythagorean_theorem"],
    "capabilityIds": ["identify_application_condition"]
  },

  "action": "optimize",

  "plan": "减少重复概念解释，增加无提示条件判断活动。",

  "sourceContextIds": ["context_001"],

  "referenceUnitIds": ["unit_021"]
}
```

---

## 16.5 Planning Action

第一版支持：

```text
preserve
reinforce
optimize
replace
add
verify
```

### preserve

继续使用已有内容。

### reinforce

保留已有内容，并增加巩固。

### optimize

保留目标，但调整教学形式、活动或资源。

### replace

本节采用新的主要教学路径。

### add

新增历史资料中不存在的内容。

### verify

增加学生独立检查任务。

---

# 16.6 教学流程

Planning 必须输出完整 `lessonFlow`。

例如：

```json
{
  "lessonFlow": [
    {
      "lessonStepPlanId": "step_plan_001",

      "title": "旧知识快速检查",

      "objective": "确认学生是否能独立识别勾股定理的使用条件",

      "durationMinutes": 5,

      "teachingMode": "oral_questioning",

      "teacherAction": "展示一个标准图形并要求学生先判断是否可以使用勾股定理",

      "studentAction": "独立判断并说明理由",

      "resourceStrategy": {
        "type": "simple_board",
        "complexityLevel": 0
      },

      "planningItemIds": ["planning_item_001"]
    }
  ]
}
```

---

# 16.7 teachingMode

每个 Lesson Step 在 Planning 阶段就必须确定主要教学形式。

第一版可支持：

```text
teacher_explanation
oral_questioning
board_explanation
worked_example
student_independent_practice
guided_practice
comparison_activity
discussion
visual_demonstration
dynamic_demonstration
interactive_exploration
assessment
```

允许一个 Step 有一个主要形式和若干辅助形式。

例如：

```json
{
  "primaryTeachingMode": "interactive_exploration",
  "secondaryTeachingModes": ["oral_questioning"]
}
```

---

# 16.8 Resource Strategy

教学形式确定后，Planning 同时确定当前 Step 需要什么类型的资源。

例如：

```json
{
  "resourceStrategy": {
    "type": "interactive_graph",
    "complexityLevel": 3,
    "reason": "representation_change",
    "sourceContextIds": ["context_001"]
  }
}
```

`resourceStrategy` 只决定：

> 需要什么形式的资源。

不在 Planning 阶段制作最终内容。

---

## 16.9 Resource Complexity

第一版资源复杂度：

```text
Level 0
口头 / 板书 / 纸笔

Level 1
静态 PPT / 图片 / 图表

Level 2
分步 PPT / 动态图示 / 简单动画

Level 3
可操作图形 / 互动工具

Level 4
复杂游戏 / AI 对话 / 情境模拟
```

Planning 默认选择：

> 能够完成当前教学目标的最低复杂度方案。

---

## 16.10 复杂资源升级条件

满足以下任一条件时，可以升级 Resource Complexity：

### A. 老师明确要求

例如：

> 这部分我想用 GeoGebra。

老师当前 Lesson 要求优先级最高。

---

### B. Context + 历史教学上下文表明需要改变表现形式

例如：

```text
历史 Lesson：
文字解释 + 静态图
↓
后续多次独立表现仍存在相关问题
```

Planning 可以：

> 本次改用动态操作方式。

这里只表示：

> 尝试不同 representation。

不表示历史方式被证明无效。

---

### C. 当前教学目标本身需要动态表达

例如：

> 理解参数变化如何影响函数图像。

可使用动态图或交互图。

如果目标只是：

> 根据公式计算。

则无需升级。

---

### D. Student Action 本身需要对应能力

例如 Planning 希望：

```text
预测
↓
拖动参数
↓
观察
↓
解释
```

则 Resource 必须支持：

```text
manipulation
```

静态图片无法满足，需要 interactive resource。

---

## 16.11 Execution Constraints

选择教学形式时必须考虑：

- Lesson 总时长；
- 当前 Step 时长；
- 老师设备；
- 学生设备；
- 网络条件；
- 课堂操作成本；
- 老师明确要求。

例如：

```text
互动任务预计 12 分钟
当前 Step 只有 5 分钟
```

则不应采用。

---

## 16.12 Teaching Unit 使用

如果检索到合适 Teaching Unit，Planning 可以直接采用。

例如：

```json
{
  "teachingUnitIds": ["teaching_unit_031"]
}
```

Teaching Unit 的：

- objective；
- teacherActions；
- studentActions；
- materialRefs；

可以参与 Lesson Step Planning。

---

# 16.13 Planning 来源

每个 Planning Item 保存：

```text
sourceContextIds
referenceUnitIds
teachingUnitIds
```

用于回答：

```text
为什么需要处理这个问题
→ Context

参考了什么历史教学内容
→ Reference Unit

用了什么可复用教学方案
→ Teaching Unit
```

---

# 16.14 Planning AI 生成流程

Planning 生成采用以下 Workflow：

```text
Final Context
+
Lesson Input
↓
Planning Intent Generation
↓
RAG Retrieval
↓
Candidate Rerank
↓
Planning Generation
↓
Rule Validation
↓
Planning Semantic Validation
↓
Teacher Review
```

---

## 16.15 Planning Generation Context

LLM 不接收所有历史内容全文。

发送给 Planning Generator 的内容包括：

```text
当前 Lesson Input
Final Context
Planning Intent
Top Reference Units
Top Teaching Units
老师限制
```

所有输入为结构化 Context。

---

## 16.16 Structured Output

Planning Generator 必须按照固定 JSON Schema 输出。

后端不得依赖从自然语言文本中解析 Planning。

---

## 16.17 Planning Validation

### 代码校验

检查：

- 所有 Step 总时长是否超过 Lesson Duration；
- 必须涵盖内容是否进入 Planning；
- referenceUnitId 是否存在；
- contextId 是否属于当前学生；
- teachingUnitId 是否存在；
- resourceStrategy 是否合法。

### AI Semantic Validator

检查：

- 是否遗漏重要 Context；
- 是否违反老师要求；
- 教学流程是否实现目标；
- Step 之间是否重复；
- 复杂资源是否有明确理由；
- 教学方式是否能在当前时间内执行。

Validator 返回问题，不直接修改 Planning。

有问题时重新生成指定 Planning Item 或 Step。

---

# 16.18 老师审核与修改

AI Planning 生成后进入：

```text
teacher_review
```

老师可以：

```text
确认
与 AI 对话修改
```

不提供：

```text
重新生成 Planning
```

---

## 16.19 对话修改

例如：

> 练习时间再增加 10 分钟，把讲解压缩一点。

AI 返回 Change Set：

```json
{
  "changes": [
    {
      "type": "duration_change",
      "target": "step_plan_002",
      "from": 15,
      "to": 10
    }
  ]
}
```

老师点击：

```text
应用到 Planning
```

后生成新版本。

---

## 16.20 Planning Version

保存：

```text
Planning v1
Planning v2
Planning v3
...
```

最终老师确认后：

```text
status = confirmed
```

生成：

```text
Confirmed Planning Snapshot
```

Lesson Kit 只能使用该 Snapshot。

---

# 17. Lesson Kit

## 17.1 Lesson Kit 定义

Lesson Kit 将老师确认后的 Planning 转换为**可以直接用于课堂执行的具体内容和材料**。

Planning 已决定：

```text
什么时候教
教什么
怎么教
学生做什么
采用什么教学形式
需要什么资源
```

Lesson Kit 负责：

```text
具体说什么
具体展示什么
具体写什么
具体做哪道题
具体生成哪些 PPT 页面
具体板书如何形成
具体交互资源是什么
具体作业是什么
```

Lesson Kit 不重新决定教学策略。

---

# 17.2 Lesson Kit 输入

```text
Confirmed Planning Snapshot
+
Planning 实际引用的 Reference Units
+
Teaching Units
+
Question Bank
+
Resource Tools
```

Lesson Kit 阶段不重新独立分析 Event Pool 或 Context。

---

# 17.3 Lesson Kit 总体结构

第一版 Lesson Kit 包括：

```text
Lesson Overview
Lesson Steps
Classroom Practice
Board Design
Homework
PPT / Visual（按需）
Interactive Resource（按需）
```

---

# 17.4 Lesson Overview

保存：

- 课程主题；
- 教材 / 章节；
- 教学目标；
- 教学重点；
- 时长。

这些数据直接来自 confirmed Planning。

---

# 17.5 Lesson Step

每个 Planning Step 转换为一个可执行 Lesson Step。

```json
{
  "lessonStepId": "lesson_step_003",

  "sourcePlanningItemIds": ["planning_item_001"],

  "title": "无提示适用条件判断",

  "objective": "检查学生能否主动判断勾股定理适用条件",

  "durationMinutes": 10,

  "teacherActions": [
    "依次展示三个图形",
    "要求学生先判断是否可以使用勾股定理",
    "暂时不允许开始计算",
    "追问判断依据"
  ],

  "studentActions": ["独立判断", "说明判断依据"],

  "teacherPrompts": ["你准备使用哪个定理？", "使用之前还需要确认什么？"],

  "observation": {
    "enabled": true,
    "focus": "观察学生是否在没有提示情况下主动检查直角条件"
  },

  "resourceIds": []
}
```

---

# 17.6 Teacher Actions

Lesson Kit 将 Planning 中较高层的 Teacher Action 转成老师可以直接执行的步骤。

例如 Planning：

> 通过正反例活动让学生独立判断适用条件。

Lesson Kit：

```text
1. 展示标准直角三角形；
2. 要求学生判断；
3. 展示旋转后的直角三角形；
4. 再次判断；
5. 展示非直角三角形；
6. 要求学生说明为什么不能使用；
7. 最后归纳条件。
```

---

# 17.7 Student Actions

具体描述学生在该 Step 中要做什么。

例如：

```text
观察
预测
独立计算
拖动参数
比较
解释
回答
归纳
```

---

# 17.8 Teacher Prompts

系统可以生成老师可直接使用的提问。

例如：

> “你为什么认为这里可以使用勾股定理？”

> “如果这个角不是直角会发生什么？”

老师可以直接编辑。

---

# 17.9 Observation

Planning Action = `verify` 或 Step 明确要求观察学生独立行为时生成。

例如：

> 不提示“直角条件”，观察学生是否会主动检查。

Observation 描述：

> 需要观察什么。

不得提前判断学生是否掌握。

---

# 17.10 Classroom Practice

根据 Planning 中的：

- Step Objective；
- target；
- difficulty；
- verify / reinforce 等要求；

从题库中检索。

流程：

```text
Question Retrieval
↓
Question Selection
↓
找到合适题目？
├─ Yes → 使用已有 questionId
└─ No
    ↓
    AI Question Generation
    ↓
    Answer / Explanation
    ↓
    Question Validation
    ↓
    questionId
```

没有正式 `questionId` 的新题不得进入 Lesson Kit。

---

# 17.11 板书设计

Planning 已经决定：

```text
该 Step 是否需要板书
+
板书在教学中的作用
```

Lesson Kit 阶段生成具体 Board Plan。

输入：

```text
Confirmed Step Plan
Teacher Actions
Student Actions
Teacher Prompts
Visual Summary（如存在）
```

输出：

```json
{
  "boardPlanId": "board_001",

  "lessonStepId": "lesson_step_003",

  "operations": [
    {
      "sequence": 1,
      "trigger": "student_identifies_unknown",
      "operation": "write",
      "content": "未知量：x"
    },

    {
      "sequence": 2,
      "trigger": "student_states_relationship",
      "operation": "append",
      "content": "数量关系：..."
    }
  ]
}
```

板书重点包含：

```text
什么时候写
+
写什么
```

板书不直接由 PPT 生成。

如果当前 Step 同时存在 PPT / Visual，可以读取 Visual Summary，避免重复展示相同内容。

---

# 17.12 PPT / Visual

只有 Planning 的 Resource Strategy 要求时才生成。

流程：

```text
Planning Resource Strategy
↓
Visual Specification
↓
Renderer
↓
Visual Resource
```

Visual Specification 例如：

```json
{
  "type": "progressive_diagram",

  "lessonStepId": "lesson_step_003",

  "purpose": "逐步展示数量关系",

  "sequence": ["先展示情境", "学生回答后显示已知量", "最后显示关系"]
}
```

再由：

- PPT Renderer；
- Diagram Renderer；
- Image Generator；

生成最终内容。

不直接让 LLM 一次生成整份 PPT。

---

# 17.13 Interactive Resource

只有 confirmed Planning 明确选择：

```text
interactive_exploration
```

或其他需要操作能力的教学形式时生成。

生成流程：

```text
Planning Resource Strategy
↓
检索已有 Interactive Teaching Unit
↓
有合适内容？
├─ Yes → 复用
└─ No
    ↓
    Interactive Specification
    ↓
    对应 Tool / Renderer
```

Interactive Specification 至少包含：

- 教学目的；
- 操作对象；
- 学生操作；
- 状态变化；
- 反馈方式；
- 使用时间；
- 对应 Lesson Step。

---

# 17.14 Homework

输入：

```text
Lesson Objectives
+
Planning Targets
+
Classroom Practice Summary
+
Homework Duration
```

优先从题库检索。

课堂已经使用过的题目默认不原样作为作业重复。

输出：

```json
{
  "objective": "独立完成从实际情境建立一元一次方程",

  "estimatedMinutes": 20,

  "questionIds": ["question_201", "question_202"]
}
```

生成 Homework 不代表自动发送。

---

# 17.15 Reference Unit 的处理

根据 Planning Action：

### preserve

复用相关 Reference Unit。

### reinforce

复用原内容并增加巩固。

### optimize

按照 Planning 已确认的新教学形式重新制作具体内容。

### replace

旧内容仅作为来源背景，不进入当前主要 Lesson Step。

### add

新增具体内容。

### verify

生成独立检查活动或题目。

---

# 17.16 Lesson Kit 生成 Workflow

```text
Confirmed Planning Snapshot
↓
读取 lessonFlow
↓
逐 Step 生成可执行内容
↓
生成 Resource Requests
↓
Practice Retrieval / Generation
↓
Board Generation
↓
Visual / Interactive Generation（按需）
↓
Homework Generation
↓
Lesson Kit Assembly
↓
Rule Validation
↓
Semantic Validation
↓
AI Generated Version
```

---

# 17.17 AI Context 构建

不同生成任务只接收当前任务需要的信息。

例如 Blackboard 只接收：

```text
当前 Step
source Planning Item
Teacher Actions
Student Actions
Teacher Prompts
Visual Summary
```

不读取：

- 全部 Event；
- 全部 Context；
- 全部历史 Lesson Kit。

---

# 17.18 AI Task 类型

第一版可以使用同一个基础 LLM，通过不同结构化 Task 完成：

```text
PlanningIntentTask
PlanningGenerationTask
PlanningValidationTask
PlanningRevisionTask

LessonStepGenerationTask
QuestionGenerationTask
BoardGenerationTask
VisualSpecificationTask
HomeworkGenerationTask
LessonKitValidationTask
```

不要求每个 Task 都实现成独立自治 Agent。

---

# 17.19 Tool Calling

需要访问真实业务能力时通过 Tool。

例如：

```text
reference.search
teachingUnit.search
question.search
question.create
question.validate
visual.render
ppt.render
interactive.render
```

LLM 不直接访问数据库。

---

# 17.20 Structured Output

核心生成任务必须返回结构化 Schema。

至少包括：

```text
PlanningIntentSchema
PlanningSchema
LessonStepSchema
QuestionSchema
BoardPlanSchema
VisualSpecSchema
HomeworkSchema
ValidationResultSchema
```

---

# 17.21 Lesson Kit Validation

## Rule Validation

代码检查：

- Step 总时间；
- questionId；
- resourceId；
- sourcePlanningItemIds；
- 必须涵盖内容；
- verify 是否有对应独立任务。

## Semantic Validation

AI 检查：

- 是否遗漏 confirmed Planning；
- 是否出现与 Planning 冲突的教学活动；
- Teacher Action 与 Student Action 是否一致；
- 是否提前暴露学生需要自己发现的答案；
- PPT / Board 是否严重重复；
- 练习是否真正对应目标；
- 复杂资源是否超出执行条件。

发现问题后，只重新生成对应资源或 Step。

---

# 17.22 局部修改

Lesson Kit 不提供：

```text
重新生成整份 Lesson Kit
```

老师修改时，系统先识别目标：

```text
Lesson Step
Practice
Board
Visual
Homework
```

例如：

> “这个板书太多了，只保留建模步骤。”

只重新生成：

```text
Board Plan
```

---

# 17.23 Planning Impact

Lesson Kit 修改前判断：

```text
planningImpact = true / false
```

例如：

> 把静态图改成动态分步图。

如果不改变教学活动：

```text
planningImpact = false
```

可以直接修改 Lesson Kit。

但：

> 删除学生独立练习，全部改成老师讲解。

会改变教学策略：

```text
planningImpact = true
```

需要返回 Planning 修改。

---

# 17.24 Lesson Kit Version

保存：

```text
AI Generated Version

Teacher Final Version
```

Teacher Final Version 不覆盖 AI Generated Version。

---

# 18. 老师修改与 Context

老师修改：

```text
Planning
或
Lesson Kit
```

不自动修改 Context。

如果老师认为 Context 事实本身错误，应进入 Context 来源检查与反馈流程。

---

# 19. 完整示例

本节教学目标：

> 学生能够独立判断什么时候可以使用勾股定理。

历史 Lesson：

```text
概念说明
+
公式解释
+
静态三角形
```

后续 Event：

```text
课堂：
提示“检查直角条件”后可以修正

作业：
三道题首次均直接使用勾股定理

批改后：
能够补充直角条件并订正
```

形成 Context：

> 学生此前已经接受适用条件的概念和公式说明；课堂和作业首次独立作答中仍多次没有主动检查直角条件，在明确提示后能够完成修正。

Planning Intent：

```text
需要处理：
无提示情况下主动检查适用条件。

需要寻找：
历史相关教学内容；
适用条件判断活动；
独立检查练习。
```

RAG 检索：

```text
历史 Reference Unit：
定义 + 公式 + 静态图

Teaching Unit A：
正反例快速判断

Teaching Unit B：
学生分类后归纳适用条件
```

Planning：

```text
教学重点：
先判断条件，再进行计算。

Step 1｜快速检查｜5分钟
形式：口头提问
学生独立判断一个标准图形。

Step 2｜正反例判断｜10分钟
形式：comparison_activity
采用 Teaching Unit A。
连续展示正例和反例。
学生先判断，再说明依据。

Step 3｜归纳适用条件｜5分钟
形式：board_explanation
老师根据学生回答逐步形成板书。

Step 4｜无提示独立检查｜10分钟
形式：student_independent_practice
两道新图形判断题。

Resource Strategy：
静态图 + 板书即可完成目标，
不需要动画。
```

老师反馈：

> 第二个环节我希望图形可以拖动，让学生观察变形之后条件是否变化。

AI 修改 Step 2：

```text
teachingMode:
comparison_activity
+
interactive_exploration

resourceStrategy:
interactive_geometry
Level 3
```

重新计算时间后仍符合课程约束。

老师确认 Planning。

Lesson Kit 生成：

```text
Step 1
→ 具体 Teacher Prompt

Step 2
→ 具体 Interactive Geometry
→ 操作说明
→ Teacher Actions
→ Student Actions

Step 3
→ Board Plan

Step 4
→ 两道具有 questionId 的独立判断题

Homework
→ 相关巩固题
```

课堂和作业继续产生 Event，进入下一轮 Context。

---

# 20. 验收标准

1. Context 只能通过标准化 Event 获取学生表现。
2. Context 必须保存 `sourceEventIds`。
3. Event 应尽可能保存 `sourceRefs`。
4. Event 可以关联 `lessonStepIds`。
5. Lesson Step 与 Event 的关联不得自动用于教学因果判断。
6. Context 应支持重复表现、变化、跨场景差异、稳定表现和缺少独立表现。
7. Context 不保存 Planning Action。
8. Context 不生成固定学习风格、心理或性格标签。
9. Reference Material 必须拆分为可检索 Reference Unit。
10. Reference Unit 必须保存原始来源位置。
11. Teaching Unit 必须能够独立理解和复用。
12. Teaching Unit 不得包含具体学生私有信息。
13. Teaching Unit 可以作为 Reference Unit 参与统一检索。
14. Planning Intent 必须描述“需要处理什么”和“需要检索什么”。
15. Reference Retrieval 必须支持老师自己的 Reference Unit 和 Teaching Unit。
16. 检索应结合 Metadata、文本/语义检索和排序。
17. 只有实际进入 confirmed Planning 的 Teaching Unit 才计为 adoption。
18. Planning 必须基于 Final Context、本节课程输入和实际检索结果。
19. Planning 必须输出完整教学流程。
20. 每个 Planning Step 必须至少包含教学目的、时长、教学形式和 Student Action。
21. Planning 阶段必须决定每个 Step 使用何种教学形式。
22. Planning 阶段必须决定是否需要 PPT、板书、动态或交互资源。
23. Planning 不生成最终 PPT、板书、题目或互动资源成品。
24. 资源复杂度默认选择能够完成教学目标的最低等级。
25. 复杂资源必须来源于老师要求、Context/历史教学上下文、教学目标或 Student Action 的明确需要。
26. 资源选择必须考虑课程时长和设备等执行约束。
27. 老师必须能够查看并通过自然语言修改 Planning。
28. 不提供无目的的 Planning 整体重新生成。
29. 每次 Planning 修改必须生成新版本。
30. 老师确认后形成 Confirmed Planning Snapshot。
31. Lesson Kit 只能基于 Confirmed Planning Snapshot 生成。
32. Lesson Kit 不重新直接分析 Event Pool 或重新决定教学策略。
33. Lesson Kit 必须将 Planning Step 转换为可执行 Lesson Step。
34. Lesson Step 必须至少包含 Teacher Actions、Student Actions 和对应资源。
35. 需要老师提问时可生成 Teacher Prompts。
36. `verify` Step 必须生成明确 Observation 或独立任务。
37. 板书必须关联具体 Lesson Step。
38. 板书设计需要定义内容及出现时机。
39. PPT / Visual 仅在 Planning 要求时生成。
40. Interactive Resource 仅在 Planning 已确认需要交互能力时生成。
41. AI 新题必须获得正式 `questionId` 并完成校验。
42. Homework 必须基于教学目标和课堂已有练习生成。
43. Lesson Kit 中的重要内容必须保存 `sourcePlanningItemIds`。
44. AI 生成结果必须经过 Rule Validation。
45. AI 生成结果必须支持 Semantic Validation。
46. 验证失败优先局部重新生成，不重新生成整份 Lesson Kit。
47. 老师可以直接编辑 Lesson Kit。
48. 老师可以通过自然语言定向修改具体 Step 或资源。
49. Lesson Kit 修改如果改变教学策略，必须返回 Planning 调整。
50. AI Generated Version 与 Teacher Final Version 必须分别保存。
51. 系统必须支持从 Lesson Kit 回溯至 Planning、Context、Event 和 Reference Source。

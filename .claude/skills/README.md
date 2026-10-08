# .claude/skills（仓库内技能集）

仓库自带的技能目录，作为仓库操作技能的一部分随代码分发。整体策略：**通用技能以用户级注册表为主源（当前运行时唯一加载源），本目录是镜像副本 + 项目专属技能的唯一载体**。

## 内容清单（21 个）

### 项目专属（本仓库原生，其他地方拿不到）

| 技能                           | 用途                             |
| ------------------------------ | -------------------------------- |
| `legado-android-crash-debug`   | Android 崩溃日志分析             |
| `legado-chinese-documentation` | 中文文档/规范写作                |
| `legado-chinese-git-workflow`  | 中文 Conventional Commits 工作流 |
| `legado-compose-scrollbar`     | Compose 滚动条相关               |
| `legado-git-local-discipline`  | 本地 Git 纪律 / 提交习惯         |
| `legado-localization-sync`     | 本地化字符串同步                 |
| `legado-ui-architecture`       | UI 架构约定                      |

### 通用技能（从用户级 `.agents/skills` 镜像，仅镜像时点快照）

| 技能                            | 用途                                                              |
| ------------------------------- | ----------------------------------------------------------------- |
| `grill-with-docs`               | 需求梳理并同步产出 ADR/词汇表                                     |
| `to-spec`                       | 把讨论合成 spec 并发布到 issue 追踪                               |
| `to-tickets`                    | 把计划拆成 tracer-bullet 工单                                     |
| `research`                      | 高可信一手资料调研并落 Markdown                                   |
| `tdd`                           | 测试驱动开发流程                                                  |
| `diagnosing-bugs`               | 疑难 bug / 性能回归诊断                                           |
| `code-review`                   | Standards + Spec 双维度 diff 审查                                 |
| `domain-modeling`               | 统一术语并产出 ADR / 词汇表                                       |
| `improve-codebase-architecture` | 扫描架构深化点并产出 HTML 报告                                    |
| `resolving-merge-conflicts`     | git 合并 / 变基冲突逐个解析                                       |
| `teach`                         | 面向场景组织项目知识教学                                          |
| `writing-for-agents`            | 撰写/重构智能体消费的文档（skill、AGENTS.md/CLAUDE.md、指针体系） |
| `show-me`                       | 用图表/代码草图/HTML 可视化讲解当前话题                           |
| `wait-what`                     | 消息没看懂时抛开术语用通俗语言重新讲一遍                          |

## 更新策略

- **项目专属技能**：直接在本目录维护，新项目技能统一用 `legado-` 前缀命名。
- **通用技能镜像**：以用户级 `.agents/skills` 为权威源；上游（Matt Pocock 的 skills 仓库）更新时，手动同步回来（`cp -r <用户级>/<技能> .claude/skills/<技能>`）。不在此直接改通用技能内容，改也应以回传上游为准。
- 归档的历史技能在 `docs/archive/skills/claude-superpowers/`，如需取用先评估是否与上述镜像重复。

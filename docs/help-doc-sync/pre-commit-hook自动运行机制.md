# pre-commit hook 自动运行机制

**问题**：意思是以后 git commit 的提交的时候会自动运行这个 hook？

**回答**：

对，每次 `git commit` 都会自动触发。

原理很简单：`core.hooksPath = .husky` 把 Git 的钩子目录指向了 `.husky/` 文件夹，所以 `.husky/pre-commit` 就是 Git 的 pre-commit 钩子。你执行 `git commit` 时，Git 会在提交前自动运行它。

流程是：

```
git commit -m "feat: xxx"
  → 1. 运行 .husky/pre-commit
       → npx lint-staged          （格式化）
       → node scripts/help-doc-sync.mjs   （文档同步检查）
  → 2. 两个都通过 → 继续写 commit message
  → 3. 运行 .husky/commit-msg   （commitlint 校验提交格式）
  → 4. 全部通过 → 提交成功
```

任意一步失败，commit 就会被中断，不会产生提交。

所以以后你只要正常 `git commit`，不用额外做什么，这个门禁自己就会跑。只有两种情况需要你主动干预：

- **被拦住了** → 按提示要么补文档，要么加 `SKIP_DOC_SYNC=1`
- **想临时关掉** → `git commit --no-verify`（会同时跳过 lint-staged 和 commitlint，不推荐）

---

## AI 能否读到拦截原因

**问题**：被 hook 拦截了，AI 能读到为啥被拦截吗？

**回答**：

能。只要 hook 的输出走了 stderr，而且 commit 是通过 Bash 工具跑的，我就能在工具结果里看到完整的拦截信息。

比如刚才测试时，hook 输出了：

```
[help-doc-sync] ❌ 拦截提交：以下代码改动未同步对应帮助文档。

  📄 源文件：app/src/main/java/io/legado/app/model/analyzeRule/test_e2e.kt
     应同步文档：ruleHelp.md、xpathHelp.md、regexHelp.md、jsHelp.md、jsVariableHelp.md、书源字段规则类型.md、订阅源字段规则类型.md

  处理方式（二选一）：
  1) 修改对应的帮助文档后重新提交
  2) 确认本次改动不涉及文档内容，提交时加环境变量跳过：
     SKIP_DOC_SYNC=1 git commit ...
```

我直接读到了，而且知道是哪个源文件、缺了哪些文档、该怎么处理。

不过有个前提：**得是通过 Bash 工具跑的 commit**。如果你在终端手动敲 `git commit`，我看不到终端输出，那就得你把报错贴给我，我才能帮你分析。

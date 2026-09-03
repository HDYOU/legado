# Husky + Commitlint 配置指南

> 面向团队新人的一份完整说明：装了什么、为什么装、怎么用。

---

## 1. 这套东西是干嘛的

团队里经常出现这种情况：

```
git commit -m "修了个 bug"
git commit -m "更新"
git commit -m "改"
```

消息写得乱七八糟，过两个月回头看 git log 完全不知道当时改了什么。

**Husky + Commitlint** 的解决方案：在 `git commit` 的瞬间拦下消息，不符合规范就不让提交。

```
git commit -m "修了个 bug"
↓
husky 检测到 commit-msg 钩子
↓
commitlint 校验消息格式
↓
❌ type may not be empty — 拒绝提交
```

---

## 2. 组件分工

| 组件                       | 角色                                                                   |
| -------------------------- | ---------------------------------------------------------------------- |
| **husky**                  | git 钩子管理器。在 `.git/hooks/` 里注册脚本，每次 git 操作触发对应钩子 |
| **commitlint**             | 提交消息校验器。按照 Conventional Commits 规则检查格式                 |
| **cz / commitizen**        | 交互式提交引导。运行 `npm run commit` 时弹出问答，自动拼出合规消息     |
| **conventional-changelog** | 从 git 历史自动生成 CHANGELOG.md                                       |

---

## 3. 安装

```bash
# 一次性安装所有依赖
npm install -D husky commitlint @commitlint/config-conventional \
  conventional-changelog-cli conventional-changelog-conventionalcommits \
  standard-version

# 初始化 husky（创建 .husky/ 目录和 pre-commit 钩子）
npx husky init
```

`npm install` 会把依赖写进 `package.json` 的 `devDependencies`，并下载到 `node_modules/`（已配置在 `.gitignore` 中，不会被提交）。

---

## 4. 钩子文件

### `.husky/commit-msg`（核心）

```bash
npx --no -- commitlint --edit "$1"
```

- `$1` 是 git 传来的 commit 消息文件路径（`.git/COMMIT_EDITMSG`）
- `--edit` 告诉 commitlint 去读那个文件并校验
- `--no` 跳过 npm 日志，避免噪音

**触发时机**：`git commit` 消息写完后、写入磁盘前。

### `.husky/pre-commit`

```bash
npx lint-staged
```

**触发时机**：`git commit` 之前，暂存区有变动时。

用途：对暂存的代码跑格式化/检查（当前配置了 prettier，后续可加 eslint 等）。

---

## 5. commitlint 规则配置（`.commitlintrc.js`）

### 5.1 合法 type 枚举

```javascript
'type-enum': [2, 'always', [
  'feat', 'fix', 'docs', 'style', 'refactor',
  'perf', 'test', 'chore', 'ci', 'revert'
]]
```

| type       | 含义      | 示例                              |
| ---------- | --------- | --------------------------------- |
| `feat`     | 新功能    | `feat(用户模块): 添加手机号登录`  |
| `fix`      | 缺陷修复  | `fix(支付): 修复微信回调签名失败` |
| `docs`     | 文档变更  | `docs(api): 更新接口说明`         |
| `style`    | 代码格式  | `style: 调整缩进为2空格`          |
| `refactor` | 重构      | `refactor(网关): 拆分过长服务类`  |
| `perf`     | 性能优化  | `perf(列表): 优化虚拟滚动渲染`    |
| `test`     | 测试      | `test: 补充用户模块单测`          |
| `chore`    | 构建/依赖 | `chore: 升级 Gradle 到 8.5`       |
| `ci`       | 持续集成  | `ci: 修改 GitHub Actions 流程`    |
| `revert`   | 回滚      | `revert: 回滚 v1.2.0 的登录重构`  |

### 5.2 关键规则说明

```javascript
'type-case': [2, 'always', 'lower-case'],        // type 必须全小写
'type-empty': [2, 'never'],                       // type 必填
'subject-empty': [2, 'never'],                    // subject 必填
'subject-max-length': [2, 'always', 100],         // subject ≤100 字符
'subject-case': [0],                              // 关闭大小写限制（允许中文）
'header-max-length': [2, 'always', 120],          // 整行 ≤120 字符
'body-max-line-length': [1, 'always', 200],       // body 每行 ≤200 字符（警告级）
'footer-max-line-length': [1, 'always', 200],     // footer 每行 ≤200 字符（警告级）
```

> `[2, ...]` = error 级别，违反即拒绝提交  
> `[1, ...]` = warning 级别，违反仍可提交但会提示  
> `[0]` = 关闭该规则

### 5.3 交互式提示（commitizen）

配置了中文提示词，运行 `npm run commit` 时看到的界面：

```
? 选择提交类型: feat
? 输入影响范围（可选）: 用户模块
? 填写简短描述: 添加手机号一键登录功能
? 填写详细描述（可选，使用 "|" 换行）: 接入运营商一键登录 SDK...
? 列出不兼容变更（可选）:
? 关联的 Issue（可选，例如 #123）: Closes #128
? 确认提交以上信息？ Yes
```

---

## 6. 提交消息格式

### 6.1 标准模板

```
<type>(<scope>): <subject>

<body>

<footer>
```

### 6.2 各部分说明

**type** — 必填，10 个合法值之一，全小写。

**scope** — 选填，影响范围，用中文模块名。

```
feat(用户模块): ...
fix(支付): ...
perf(列表页): ...
```

**subject** — 必填，动宾短语，≤100 字符，不加句号。

```
✅ feat(用户模块): 添加手机号一键登录功能
✅ fix(支付): 修复微信支付回调签名验证失败的问题
✅ perf(列表页): 优化大数据量表格的虚拟滚动渲染

❌ feat: 更新代码          （无意义）
❌ fix: 修了一个 bug        （太笼统）
❌ fix(支付): 修复了问题。   （句号结尾）
```

**body** — 选填，说明背景/方案/影响范围，每行 ≤200 字符，与 subject 之间空一行。

```
在高并发场景下，原有的库存扣减逻辑存在竞态条件。
改用 Redis 分布式锁 + 数据库乐观锁双重保障。

影响范围：订单服务、库存服务
测试确认：已通过 500 并发压测验证
```

**footer** — 选填，关联 Issue 或标注 Breaking Change。

```
Closes #128
```

### 6.3 Breaking Change 标注

涉及不兼容变更时，两种标注方式：

**方式一：footer 标注**

```
feat(接口): 重构用户信息返回结构

将用户接口返回的扁平结构改为嵌套结构，前端需同步调整字段取值路径。

BREAKING CHANGE: /api/user/info 返回结构变更
- avatar 字段移入 profile 对象
- 移除已废弃的 nickname 字段
```

**方式二：type 后加 `!`**

```
feat(接口)!: 重构用户信息返回结构
```

---

## 7. CHANGELOG 自动生成

### 7.1 生成命令

```bash
# 最近一次版本的 changelog（日常用这个）
npm run changelog

# 所有历史版本的完整 changelog
npm run changelog:all

# 发版：自动更新版本号 + 生成 changelog + 打 git tag
npm run release
```

### 7.2 配置

`conventional-changelog` 从 git remote 自动提取仓库地址，从 commit 消息中按 type 分组：

- `feat` → 新功能
- `fix` → 缺陷修复
- `perf` → 性能优化
- `refactor` → 代码重构
- `docs` → 文档更新
- `test` → 测试
- `chore` / `ci` / `style` → 隐藏（不显示在 changelog 中）

---

## 8. 日常使用

### 8.1 推荐：交互式提交

```bash
npm run commit
```

弹出问答引导，自动拼出合规消息，不容易出错。

### 8.2 手动提交

```bash
git commit -m "feat(支付): 修复微信支付回调签名验证失败的问题"
```

### 8.3 amend 上次提交

```bash
git commit --amend -m "fix(登录): 修复白屏问题"
```

amend 后的消息同样会经过 commitlint 校验。

### 8.4 修复历史提交消息

如果历史提交消息不规范，需要用 `git rebase` 改写：

```bash
# 从最近 3 次提交开始交互式 rebase
git rebase -i HEAD~3
```

然后把 `pick` 改成 `edit` / `reword`，按提示修改消息。改完后 `npm run changelog` 重新生成 CHANGELOG。

---

## 9. 常见问题

**Q: 中英文混排时空格怎么处理？**

A: 中文与英文/数字之间加一个空格，如「添加 Redis 缓存」「升级 Gradle 到 8.5」。

**Q: scope 用中文还是英文？**

A: 推荐中文（可读性好）。commitlint 中已关闭 `subject-case` 检查，不会因为中文报错。

**Q: 多人协作时如何保证规范一致？**

A: 靠工具而非靠自觉。husky + commitlint 配置在仓库中，每个 clone 下来的仓库都会自动生效，不符合规范的提交直接被拦。

**Q: 想临时跳过校验怎么办？**

A: `git commit --no-verify -m "..."` 可以跳过 husky 钩子。不推荐日常使用，仅在紧急修复时临时 bypass。

**Q: node_modules 没有会被提交吗？**

A: 不会。`.gitignore` 里已经配置了 `node_modules/`。

---

## 10. 相关文件索引

```
docs/git-hook/
├── setup-guide.md          # 本文件 — 配置说明
├── invalid-commit-examples.md  # 非法提交实例
└── change-log.md           # 改动清单与验证结果

项目根目录：
├── .husky/                 # git 钩子脚本
│   ├── commit-msg          # commitlint 校验
│   └── pre-commit          # lint-staged
├── .commitlintrc.js        # commitlint 规则配置
├── package.json            # 依赖 + 脚本 + lint-staged 配置
└── CHANGELOG.md            # 自动生成的变更日志
```

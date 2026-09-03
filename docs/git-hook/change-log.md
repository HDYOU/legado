# Git Commit 规范配置 — 改动清单与验证结果

> 配置日期：2026-09-02  
> 依据：`.claude/skills/chinese-commit-conventions/SKILL.md`

---

## 一、改动文件清单

| 文件                | 操作   | 作用                                                                          |
| ------------------- | ------ | ----------------------------------------------------------------------------- |
| `.husky/commit-msg` | 新建   | 提交消息校验钩子，调用 `commitlint --edit`                                    |
| `.husky/pre-commit` | 修改   | pre-commit 钩子，调用 `lint-staged`                                           |
| `.commitlintrc.js`  | 新建   | commitlint 规则配置（中文适配）                                               |
| `package.json`      | 修改   | 新增 `commit`/`changelog`/`changelog:all`/`release` 脚本 + `lint-staged` 配置 |
| `CHANGELOG.md`      | 新生成 | 从 git 历史提交自动生成的变更日志（206KB）                                    |

## 二、依赖变更

`package.json` 的 `devDependencies` 新增：

| 包                                           | 用途                          |
| -------------------------------------------- | ----------------------------- |
| `husky`                                      | git 钩子管理                  |
| `commitlint`                                 | 提交消息校验                  |
| `@commitlint/config-conventional`            | Conventional Commits 规则基座 |
| `conventional-changelog-cli`                 | CHANGELOG 生成                |
| `conventional-changelog-conventionalcommits` | changelog 中文适配 preset     |
| `standard-version`                           | 发版自动化                    |

已存在的 `cz-conventional-changelog` + `commitizen` 配置保持不变。

---

## 三、配置要点

### commitlint 规则（`.commitlintrc.js`）

| 规则                     | 值                            | 说明                                 |
| ------------------------ | ----------------------------- | ------------------------------------ |
| `type-enum`              | `[2, 'always', [...]]`        | type 必须在 10 个合法值中            |
| `type-case`              | `[2, 'always', 'lower-case']` | type 必须全小写                      |
| `type-empty`             | `[2, 'never']`                | type 必填                            |
| `subject-empty`          | `[2, 'never']`                | subject 必填                         |
| `subject-max-length`     | `[2, 'always', 100]`          | subject 不超过 100 字符              |
| `subject-case`           | `[0]`                         | 关闭大小写限制（允许中文）           |
| `header-max-length`      | `[2, 'always', 120]`          | header 总长不超过 120 字符           |
| `body-max-line-length`   | `[1, 'always', 200]`          | body 每行不超过 200 字符（警告级）   |
| `footer-max-line-length` | `[1, 'always', 200]`          | footer 每行不超过 200 字符（警告级） |

### husky 钩子

| 钩子         | 触发时机                | 执行内容                     |
| ------------ | ----------------------- | ---------------------------- |
| `pre-commit` | `git commit` 前         | `npx lint-staged`            |
| `commit-msg` | `git commit` 消息写完后 | `npx commitlint --edit "$1"` |

### package.json 脚本

| 脚本                    | 等价命令                          | 用途                             |
| ----------------------- | --------------------------------- | -------------------------------- |
| `npm run commit`        | `npx cz`                          | 交互式提交引导                   |
| `npm run changelog`     | `conventional-changelog ...`      | 重新生成 CHANGELOG.md            |
| `npm run changelog:all` | `conventional-changelog ... -r 0` | 生成包含所有历史版本的 CHANGELOG |
| `npm run release`       | `standard-version`                | 自动发版（版本号 + changelog）   |

---

## 四、验证结果

### 4.1 合法提交

```
feat(用户模块): 添加手机号一键登录功能
fix(订单): 修复并发下单导致库存超卖的问题
perf(列表页): 优化大数据量表格的虚拟滚动渲染
```

> ✅ 均通过 commitlint 校验，退出码 0。

### 4.2 非法提交（已被拦截）

| #   | 提交消息                                 | 被拒原因                                   |
| --- | ---------------------------------------- | ------------------------------------------ |
| 1   | `修复了登录页白屏问题`                   | 缺 type，`type-empty` + `subject-empty`    |
| 2   | `bugfix(订单): 修复了并发下单问题`       | type 不在白名单，`type-enum`               |
| 3   | `fix():`                                 | subject 为空，`subject-empty`              |
| 4   | `Feat(用户模块): 添加手机号一键登录功能` | type 大小写错误，`type-case`               |
| 5   | `feat 用户模块 添加手机号登录`           | 缺冒号分隔符，解析失败                     |
| 6   | `fix(用户模块): [超长描述...]`           | subject 超 100 字符，`subject-max-length`  |
| 7   | `chore: 改了点东西`                      | ⚠️ 格式对但语义空洞（需 Code Review 兜底） |

> 前 6 条均以退出码 1 被 commitlint 拦截。第 7 条能通过校验，但内容无意义，规范执行依赖人工审查。

### 4.3 CHANGELOG 生成

`npm run changelog` 成功生成 `CHANGELOG.md`（211,612 字节），包含从项目初始到当前的所有历史提交记录，按版本号分组，每条提交附带 commit hash 链接。

---

## 五、日常使用

```bash
# 交互式提交（推荐，自动引导填写各字段）
npm run commit

# 手动提交（需符合 Conventional Commits 格式）
git commit -m "feat(支付): 修复微信支付回调签名验证失败的问题"

# 发版时自动生成 changelog + 版本号
npm run release

# 重新生成 CHANGELOG（比如修复了历史提交消息后）
npm run changelog
```

---

## 六、文件索引

```
docs/git-hook/
├── invalid-commit-examples.md   # 非法提交实例（被拦原因 + 正确写法）
└── change-log.md               # 完整改动清单与验证结果（本文件）
```

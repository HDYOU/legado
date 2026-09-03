# Git Commit 规范 — 详细参考

> AI 在需要写 commit message 或回答 commit 规范相关问题时读此文件。
> 日常开发只需记住 CLAUDE.md 里那段摘要即可。

---

## 1. 格式

```
<type>(<scope>): <subject>

<body>

<footer>
```

---

## 2. type 类型表

| type       | 含义                     | 示例场景                 |
| ---------- | ------------------------ | ------------------------ |
| `feat`     | 新功能                   | 添加用户注册模块         |
| `fix`      | 缺陷修复                 | 修复登录页白屏问题       |
| `docs`     | 文档变更                 | 更新 API 接口文档        |
| `style`    | 代码格式（不影响逻辑）   | 调整缩进、补充分号       |
| `refactor` | 重构（非新功能、非修复） | 拆分过长的服务类         |
| `perf`     | 性能优化                 | 优化首页列表查询速度     |
| `test`     | 测试相关                 | 补充用户模块单元测试     |
| `chore`    | 构建/工具/依赖变更       | 升级 Gradle 到 8.5       |
| `ci`       | 持续集成配置             | 修改 GitHub Actions 流程 |
| `revert`   | 回滚提交                 | 回滚 v1.2.0 的登录重构   |

---

## 3. 各字段规则

### type

- 必填，从上表中选取
- 全小写（`Feat` 会被拦截）

### scope

- 选填，表示影响范围，用中文模块名
- 示例：`用户模块`、`订单`、`支付`、`列表页`

### subject

- 必填，中文简述，不超过 100 字符
- 使用动宾短语：「添加 xxx」「修复 xxx」「优化 xxx」
- 不加句号结尾
- 不要写「修改了代码」这种无意义描述

### body

- 选填，说明变更动机、技术方案、影响范围
- 每行不超过 200 字符（中文约 100 字）
- 正文与标题之间空一行

### footer

- 关联 Issue：`Closes #128`、`Refs #129`
- Breaking Change：`BREAKING CHANGE: <描述>`

---

## 4. Breaking Change 标注

两种方式：

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

必须标注的场景：

- 数据库表结构变更
- 公共 API 参数/返回值变更
- 配置文件格式变更

---

## 5. 好示例

```
feat(用户模块): 添加手机号一键登录功能
fix(支付): 修复微信支付回调签名验证失败的问题
perf(列表页): 优化大数据量表格的虚拟滚动渲染
refactor(网关): 将单体网关拆分为独立微服务
docs(api): 更新用户接口说明
chore(构建): 升级 Gradle 到 8.5
ci: 修改 GitHub Actions matrix strategy
```

---

## 6. 避免的写法

| 错误写法                                    | 问题                        |
| ------------------------------------------- | --------------------------- |
| `fix: 修了一个 bug`                         | 太笼统，无意义              |
| `feat: 更新代码`                            | 无意义                      |
| `chore: 改了点东西`                         | 无意义                      |
| `bugfix(订单): ...`                         | type 不在白名单，应用 `fix` |
| `Feat(用户模块): ...`                       | 首字母大写，应用 `feat`     |
| `feat 用户模块 xxx`                         | 缺冒号分隔符                |
| `fix():`                                    | subject 为空                |
| `fix(用户模块): [超过100字符的超长描述...]` | subject 超长                |

---

## 7. 中英文混排

中文与英文/数字之间加一个空格：

```
✅ 添加 Redis 缓存
✅ 升级 Gradle 到 8.5
✅ 使用 OkHttp 5.3.2
❌ 添加Redis缓存
❌ 升级Gradle到8.5
```

---

## 8. 交互式提交

```bash
npm run commit
```

等同于 `npx cz`，会弹出问答引导：

```
? 选择提交类型: feat
? 输入影响范围（可选）: 用户模块
? 填写简短描述: 添加手机号一键登录功能
? 填写详细描述（可选，使用 "|" 换行）: ...
? 列出不兼容变更（可选）:
? 关联的 Issue（可选，例如 #123）: Closes #128
? 确认提交以上信息？ Yes
```

---

## 9. Git Hook 拦截机制

### 触发流程

```
git commit -m "..."
  ↓
husky 检测到 commit-msg 钩子
  ↓
commitlint 读取消息文件并校验
  ↓
合规 → 提交成功
不合规 → 报错并拒绝提交
```

### 生效条件

**必须跑过 `npm install`**。`package.json` 中 `"prepare": "husky"` 是 npm 生命周期脚本，`npm install` 结束后自动运行，把 `.husky/` 下的钩子注册到 `.git/hooks/`。

不跑 `npm install` → 钩子文件在但未注册 → 不会拦截。

### 临时跳过

```bash
git commit --no-verify -m "..."
```

仅限紧急修复时使用。

---

## 10. CHANGELOG

```bash
npm run changelog       # 最近一次版本
npm run changelog:all   # 所有历史版本
npm run release          # 发版（版本号 + changelog + tag）
```

---

## 11. 相关文件

```
docs/git-hook/
├── commit-spec.md               # 本文件 — 完整规范参考
├── setup-guide.md               # husky + commitlint 配置说明
├── invalid-commit-examples.md   # 非法提交实例（含被拦原因）
├── change-log.md                # 改动清单与验证结果
└── 新人快速使用git-hook.md       # fork 后钩子是否会生效
```

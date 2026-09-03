# 非法提交实例

> commitlint 实测拦截记录。每个实例包含原始提交消息、被拒原因、以及正确的写法。

---

## 实例 1：缺少 type 关键字

**原始提交：**

```
修复了登录页白屏问题
```

**拦截原因：**

```
subject may not be empty  [subject-empty]
type may not be empty     [type-empty]
```

**问题：** 解析器在 `(` 之前找不到 type，两个字段都判空。必须以 `type(scope):` 开头。

**正确写法：**

```
fix(登录页): 修复白屏问题
```

---

## 实例 2：type 不在白名单

**原始提交：**

```
bugfix(订单): 修复了并发下单问题
```

**拦截原因：**

```
type must be one of [feat, fix, docs, style, refactor, perf, test, chore, ci, revert]  [type-enum]
```

**问题：** `bugfix` 不是合法 type，必须用 `fix`。

**正确写法：**

```
fix(订单): 修复并发下单导致库存超卖的问题
```

---

## 实例 3：subject 为空

**原始提交：**

```
fix():
```

**拦截原因：**

```
subject may not be empty  [subject-empty]
type may not be empty     [type-empty]
```

**问题：** 括号里既没填 scope 也没填描述。

**正确写法：**

```
fix: 修复白屏问题
```

---

## 实例 4：type 首字母大写

**原始提交：**

```
Feat(用户模块): 添加手机号一键登录功能
```

**拦截原因：**

```
type must be lower-case  [type-case]
type must be one of [...] [type-enum]
```

**问题：** `Feat` 首字母大写，必须写全小写 `feat`。

**正确写法：**

```
feat(用户模块): 添加手机号一键登录功能
```

---

## 实例 5：缺少冒号分隔符

**原始提交：**

```
feat 用户模块 添加手机号登录
```

**拦截原因：**

```
subject may not be empty  [subject-empty]
type may not be empty     [type-empty]
```

**问题：** 格式必须是 `type(scope): subject`，那个冒号不能省。没有冒号解析器就找不到 subject 的起始位置。

**正确写法：**

```
feat(用户模块): 添加手机号一键登录功能
```

---

## 实例 6：subject 超长

**原始提交：**

```
fix(用户模块): 这是一个非常非常长的描述超过了commitlint规定的subject-max-length限制长度一百个字符的边界值测试用例为了确保规则真的生效我们写一个超级长的句子来验证commitlint是否会拒绝它
```

**拦截原因：**

```
subject must not be longer than 100 characters  [subject-max-length]
```

**问题：** subject 超过 100 字符上限。

**正确写法：**

```
fix(用户模块): 修复超长subject被commitlint拦截的问题
```

---

## 实例 7：无意义描述（不会被拦，但不合规）

**原始提交：**

```
chore: 改了点东西
```

**结果：** ✅ 通过 commitlint 校验

**问题：** commitlint 只管格式不管语义，这种「改了点东西」需要靠 Code Review 兜底。

**建议写法：**

```
chore(构建): 升级 Gradle 到 8.5
```

---

## 合法 vs 非法速查

| 写法                                        | 结果                   |
| ------------------------------------------- | ---------------------- |
| `fix: 修复白屏问题`                         | ✅                     |
| `feat(用户模块): 添加手机号一键登录功能`    | ✅                     |
| `fix(订单): 修复并发下单导致库存超卖的问题` | ✅                     |
| `修复了登录页白屏问题`                      | ❌ 缺 type             |
| `bugfix(订单): 修复了并发下单问题`          | ❌ type 不在白名单     |
| `fix():`                                    | ❌ subject 为空        |
| `Feat(用户模块): 添加手机号一键登录功能`    | ❌ type 大小写         |
| `feat 用户模块 添加手机号登录`              | ❌ 缺冒号              |
| `fix(用户模块): [超长描述...]`              | ❌ subject 超 100 字符 |
| `chore: 改了点东西`                         | ⚠️ 格式对但语义空洞    |

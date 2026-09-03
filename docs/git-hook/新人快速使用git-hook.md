# 新人快速使用 Git Hook

> 一个新人 clone/fork 仓库后，直接 `git commit` 会不会被拦截？

---

## 结论

**会，但有一个前提：跑过 `npm install`。没错，就是 `npm install` 命令，没打错。 如果没打过npm install命令，就不会被拦截。**

---

## 为什么只打npm install就行？

`package.json` 里有这一行：

```json
"scripts": {
  "prepare": "husky"
}
```

`prepare` 是 npm 的内置生命周期脚本——`npm install` 跑完后自动执行，不用你手动触发。它做的事就是把 `.husky/` 下的钩子接进 git 的钩子目录。

所以流程就是：

```
npm install
  ↓ （自动）
prepare → npx husky
  ↓
.husky/commit-msg → .git/hooks/commit-msg
  ↓
之后 git commit 自动触发
```

---

## 你可以现在验证

在项目根目录跑：

```bash
npm install
```

装完后随便试一条不合规的：

```bash
git commit -m "修了个 bug"
```

会看到报错，提交被拦下。

---

## 流程拆解

```
clone / fork 仓库
  ↓
.gitignore 已排除 node_modules，但 .husky/ 随仓库一起下来了
  ↓
npm install          ← 关键步骤
  ↓
npm 自动运行 "prepare" 脚本（即 npx husky）
  ↓
husky 把 .husky/commit-msg 接到 .git/hooks/commit-msg
  ↓
之后每次 git commit，钩子自动触发
  ↓
commitlint 校验 → 不合规就拦下来
```

---

## 为什么能自动触发

`package.json` 里配了：

```json
"scripts": {
  "prepare": "husky"
}
```

`prepare` 是 npm 的生命周期脚本，**每次 `npm install` 结束后自动运行**，不需要手动敲。它做的事就是把 `.husky/` 下的脚本注册到 `.git/hooks/` 里。

---

## 前提条件

对方必须跑一次 `npm install`。只要跑了，钩子就自动接上，不需要任何额外配置。

如果 clone 之后直接 `git commit`、不装依赖，钩子文件在但没被注册，就不会触发校验。

---

## 可执行权限

`.husky/` 下的脚本需要可执行权限。Windows 上 clone 一般没问题；某些环境（如 SSH 拉取）可能丢可执行位。husky v9 对此做了兼容，即使权限丢了也会自动修复。

---

## 一句话

**只要跑过 `npm install`，钩子就自动生效，不需要额外配置。** 这也是 husky 的设计目的——把钩子跟着仓库走，团队里谁 clone 下来都一样。

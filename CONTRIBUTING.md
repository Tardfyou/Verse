# Verse 协作开发指南

本文档用于统一 `Verse` 仓库的 Git 使用方式、分支规则和日常协作流程。

仓库地址：`https://github.com/Tardfyou/Verse.git`

## 1. 分支约定

- `main`
  - 稳定分支
  - 只保留可发布、可回滚的版本
  - 不直接在本地开发
- `develop`
  - 日常集成分支
  - 默认从这里拉新分支
  - 功能完成后合并回这里
- `feature/<name>`
  - 功能开发分支
  - 示例：`feature/login-page`、`feature/home-feed`
- `fix/<name>`
  - 普通缺陷修复分支
  - 示例：`fix/crash-on-startup`
- `hotfix/<name>`
  - 紧急线上修复分支
  - 一般从 `main` 拉出，修完后同时合并回 `main` 和 `develop`

## 2. 首次配置

### 2.1 克隆仓库

```powershell
git clone https://github.com/Tardfyou/Verse.git
cd Verse
git switch develop
```

### 2.2 配置 Git 身份

如果本机还没配置 Git 用户信息，先执行：

```powershell
git config --global user.name "你的名字"
git config --global user.email "你的邮箱"
```

检查是否配置成功：

```powershell
git config --global user.name
git config --global user.email
```

### 2.3 检查远程仓库

```powershell
git remote -v
```

期望看到：

```text
origin  https://github.com/Tardfyou/Verse.git (fetch)
origin  https://github.com/Tardfyou/Verse.git (push)
```

### 2.4 Android Studio 建议

- 打开项目后确认 Git 已启用
- `Settings` -> `Version Control` 中项目根目录应映射到 Git
- `Settings` -> `Version Control` -> `Git` 中可执行文件应指向本机 Git
- 若使用 GitHub 登录，建议在 Android Studio 或 Git Credential Manager 中完成认证

## 3. 日常开发流程

### 3.1 开始一个新需求

先更新本地 `develop`：

```powershell
git switch develop
git pull --ff-only origin develop
```

基于 `develop` 新建功能分支：

```powershell
git switch -c feature/your-feature-name
```

示例：

```powershell
git switch -c feature/login-page
```

### 3.2 提交本地修改

查看变更：

```powershell
git status
```

添加文件并提交：

```powershell
git add .
git commit -m "feat: add login page UI"
```

建议提交信息格式：

- `feat: 新功能`
- `fix: 修复问题`
- `refactor: 重构`
- `docs: 文档更新`
- `chore: 杂项维护`
- `test: 测试调整`

### 3.3 首次推送当前功能分支

```powershell
git push -u origin feature/your-feature-name
```

后续继续推送：

```powershell
git push
```

### 3.4 发起合并请求

功能完成后，在 GitHub 发起 PR：

- 源分支：`feature/your-feature-name`
- 目标分支：`develop`

PR 标题建议直接描述结果，不写模糊标题。

示例：

- `Add login page UI`
- `Fix crash when opening settings`

## 4. 常用推送与同步命令

### 4.1 更新本地 develop

```powershell
git switch develop
git pull --ff-only origin develop
```

### 4.2 将 develop 最新改动同步到自己的功能分支

```powershell
git switch feature/your-feature-name
git fetch origin
git rebase origin/develop
```

如果你不熟悉 `rebase`，也可以用：

```powershell
git switch feature/your-feature-name
git merge origin/develop
```

### 4.3 推送当前分支

```powershell
git push
```

### 4.4 首次推送新分支

```powershell
git push -u origin feature/your-feature-name
```

### 4.5 拉取 main 最新代码

```powershell
git switch main
git pull --ff-only origin main
```

### 4.6 紧急修复流程

从 `main` 拉出紧急修复分支：

```powershell
git switch main
git pull --ff-only origin main
git switch -c hotfix/your-hotfix-name
```

修复完成后：

- 先 PR 到 `main`
- 再把同样的改动合并回 `develop`

## 5. 协作规则

- 不直接向 `main` 推送日常开发代码
- 日常开发默认从 `develop` 开分支
- 一个需求或一个修复对应一个独立分支
- 提交粒度尽量小，避免把多个无关修改混在一次提交里
- 发 PR 前先同步 `develop`，尽量自己解决冲突
- 不提交本地构建产物、缓存文件和个人配置文件
- 不随意改动别人正在开发的分支
- 不对公共分支执行强推

严格限制：

- 禁止对 `main` 使用 `git push --force`
- 禁止对 `develop` 使用 `git push --force`
- 若必须改写自己功能分支历史，只能使用：

```powershell
git push --force-with-lease
```

并且只允许用于你自己的功能分支。

## 6. 提交前检查

提交或发 PR 之前，至少完成以下检查：

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat test
```

如果后续接入了更多检查，再追加到这里，例如：

- Lint
- 单元测试
- UI 测试
- 静态检查

## 7. 不应提交的内容

以下内容一般不应提交到仓库：

- `build/`
- `.gradle/`
- `local.properties`
- 本地设备相关 `.idea` 文件
- 临时测试文件
- 与当前需求无关的格式化噪音改动

## 8. 推荐的开发节奏

建议按下面的顺序工作：

1. 切到 `develop`
2. 拉取最新代码
3. 新建自己的功能分支
4. 开发并小步提交
5. 推送远程分支
6. 发 PR 合并到 `develop`
7. 版本稳定后再从 `develop` 合并到 `main`

## 9. 当前项目默认规则

- 当前默认协作分支：`develop`
- 当前稳定分支：`main`
- 日常开发目标分支：`develop`
- 发布目标分支：`main`

## 10. 有用的排查命令

查看当前分支：

```powershell
git branch --show-current
```

查看分支跟踪关系：

```powershell
git branch -vv
```

查看当前工作区状态：

```powershell
git status
```

查看提交历史：

```powershell
git log --oneline --graph --decorate -20
```

如果拉取时报冲突，先不要盲目强推，先确认冲突文件和目标分支，再处理。

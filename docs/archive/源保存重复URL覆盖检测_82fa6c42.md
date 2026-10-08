# 源保存重复 URL 覆盖检测

## 需求与结论
保存书源/订阅源时，若待保存 URL 与源列表中**另一条已存在的源**相同，弹出警告"将覆盖 xxx，是否继续"，确认后覆盖保存、取消则中止。所有会触发保存的入口都要检测。

## 关键事实（已核对代码）
- 两个编辑界面都有唯一保存收口 `saveSource(source, onSuccess)`，调试/登录/搜索/源所用API/设置源变量/保存全部经它：
  - [BookSourceEditActivity.saveSource](file:///g:/Project/legado-Max-Suml/Legado_Max/app/src/main/java/io/legado/app/ui/book/source/edit/BookSourceEditActivity.kt#L786-L808)（入口见 `onCompatOptionsItemSelected` L430-L484）
  - [RssSourceEditActivity.saveSource](file:///g:/Project/legado-Max-Suml/Legado_Max/app/src/main/java/io/legado/app/ui/rss/source/edit/RssSourceEditActivity.kt#L160-L174)
  只需在这两个方法前加检测即可覆盖全部路径，无需逐菜单改。
- `bookSourceUrl` / `sourceUrl` 均为 Room `@PrimaryKey`，insert 用 `OnConflictStrategy.REPLACE`（`BookSourceDao.kt` L273、`RssSourceDao.kt` L113），故"相同 URL 保存"必然是覆盖另一条主键相同的记录。
- 按 URL 查已有源：`appDb.bookSourceDao.getBookSource(url)`（BookSourceDao L262）、`appDb.rssSourceDao.getByKey(url)`（RssSourceDao L25），返回可含 `bookSourceName`/`sourceName` 用于提示。
- 现有可复用文案模式：`source_recycle_bin_conflict_msg`、`overwrite`（覆盖）。

## 检测语义（主线程即时判断，避免不必要查询）
- 令 `originalUrl = viewModel.bookSource?.bookSourceUrl`（编辑前加载的原始 URL）。
- URL 为空 → 直接执行原保存逻辑（由 `viewModel.save` 的 blank 校验报错，保持现状）。
- `source.bookSourceUrl == originalUrl`（未改地址，更新同一条记录）→ 无覆盖风险，直接保存，不查询。
- 否则用 `getBookSource(source.bookSourceUrl)` 查询：
  - 命中（返回非 null，即存在另一条同 URL 源）→ 弹警告，确认后保存、取消则中止。
  - 未命中 / 查询异常 → 直接保存（fail-safe，绝不因检测本身阻断保存）。
- 订阅源同理，`originalUrl = viewModel.rssSource?.sourceUrl`，用 `getByKey`。

## 改动清单

### 1. BookSourceEditActivity.kt
- 将现有 `saveSource` 方法体重命名为私有 `performSave(source, onSuccess)`（逻辑不变，含 `pendingSaveCount` 计数与 `settle`）。
- 新增 `saveSource(source, onSuccess)` 作为检测闸门：
  - 命中"需查询"分支时用 `lifecycleScope.launch { withContext(IO) { getBookSource(url) } }`（参照同文件 `alertGroups` L1026-L1035 的写法）。
  - 命中已有源时 `alert(title) { setMessage(格式化 msg, 填入 existing.bookSourceName); positiveButton(R.string.overwrite){ performSave(...) }; negativeButton(R.string.cancel) }`。取消时不调用 performSave（调试/登录/搜索入口因此不跳转）。
  - 未命中或异常分支立即 `performSave(...)`。

### 2. RssSourceEditActivity.kt
- 同样把 `saveSource` 体拆为 `performSave`，新增检测闸门；查询用 `appDb.rssSourceDao.getByKey(source.sourceUrl)`，提示填入 `existing.sourceName`。

### 3. 字符串资源（4 语言同步，遵循 legado-localization-sync）
新增 2 组 key（书名/订阅源各一，措辞自然）：
- `source_url_duplicate_title`（书源地址重复）
- `source_url_duplicate_cover_msg`（已存在相同地址的书源"%1$s"，保存将覆盖它，是否继续？）
- `rss_source_url_duplicate_title` / `rss_source_url_duplicate_cover_msg`（订阅源版）
按钮复用现有 `overwrite` 与 `cancel`。
文件：[values/strings.xml](file:///g:/Project/legado-Max-Suml/Legado_Max/app/src/main/res/values/strings.xml)、values-zh、values-zh-rTW、values-zh-rHK（HK/TW 用繁体）。

## 不在本次范围
- 书源/订阅源列表页的批量导入、检查替换等"多源批量保存"路径（需求限定"源编辑界面"，且批量导入非单条覆盖确认场景）。如需一并处理再单独提出。

## 验证
- `./gradlew assembleDebug` 编译通过。
- 手动/adb 验证（参照项目 adb 免点击方法论）：
  1. 编辑已有书源，把 URL 改成另一条已存在源的 URL → 点保存/调试/登录/搜索均弹覆盖警告；确认后覆盖成功、取消则停留不跳转。
  2. 不改 URL 直接保存 → 无警告（同记录更新）。
  3. 新建源输入已存在的 URL → 弹警告。
  4. 订阅源重复以上场景。
- 完成 code-review（Standards + Spec）。
- 用户可见功能改动，按 update-log-rules 维护 `updateLog.md`。

# ME Building Gadgets

一个 NeoForge 1.21.1 模组，将 **Building Gadgets 2** 与 **Applied Energistics 2** 集成：新增三款"ME 小帮手"，缺料时可直接从 AE 网络下单。

## 新增物品

- **ME复制粘贴小帮手**
- **ME建筑小帮手**
- **ME更替小帮手**

## 功能

- **从AE下单** — 在 BG2 的材料列表界面中添加按钮，自动通过 AE2 合成缺失物品
- **链式下单** — 缺失多种材料时一次性排入队列，确认一个物品后自动弹出到下一个，全部使用 AE2 原生下单界面、由玩家手动确认
- **智能库存检查** — 下单前自动扣除背包、AE 网络库存和已在合成中的数量，避免重复下单
- **无线终端支持** — 检测主手、副手、背包和饰品栏中的无线终端
- **网络扫描** — 绑定到无线访问点后自动搜索网络上的合成终端
- **饰品栏支持** — 无线终端放在 Curios 饰品栏中也能正常使用

## 前置模组

- [NeoForge](https://neoforged.net/) 21.1.230+
- [Building Gadgets 2](https://www.curseforge.com/minecraft/mc-mods/building-gadgets-2) 1.3.9+
- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) 19.2.17+

## 使用方法

1. 使用小帮手的绑定模式，将小帮手绑定到任意 AE2 线缆/子网/控制器（或无线访问点）
2. 打开小帮手的材料列表界面，点击 **"从AE下单"**；或直接右键粘贴触发缺料审计
3. 所有缺失且有样板的材料会依次弹出 AE2 原生的合成数量界面：确认一个订单后自动弹出到下一个，关闭界面即跳过当前材料
4. 材料到位后再次右键即可粘贴建造；Shift+右键可跳过审计强制粘贴

## 开发规范

- **目录结构**：`ae/` AE 集成逻辑 · `items/` 三款小帮手 · `client/` 客户端 GUI 与缓存 · `mixin/` BG2/AE2 注入 · `network/` 网络包
- **命名**：物品 ID 用 kebab-case；玩家可见文案一律放语言文件（`zh_cn` / `en_us`），键前缀 `me_building_gadgets.messages.*`，禁止硬编码字符串
- **客户端隔离**：渲染/GUI 代码按 BG2 模式标注 `@OnlyIn(Dist.CLIENT)` 并做客户端空判，公共类不得引用 `net.minecraft.client.*`
- **诊断日志**：统一走 `Diagnostics.log`（标签 `[GadgetsME/diag]`），由 `debugLog` 配置控制，日志保持英文单语
- **提交信息**：`type: 中文描述`，type ∈ `feat / fix / refactor / docs / chore / ci / i18n`，相关改动分批提交
- **提交前**：`./gradlew build` 通过再提交

## 许可证

GNU AGPL 3.0

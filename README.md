# ME Building Gadgets

一个 NeoForge 1.21.1 模组，将 **Building Gadgets 2** 与 **Applied Energistics 2** 集成：新增三款"ME 小帮手"，缺料时可直接从 AE 网络下单。

> 模组 ID：`me_building_gadgets`（21.1.1 起由 `gadgetsme_network` 更名，旧存档中的旧 ID 物品会失效）

## 新增物品

- **ME复制粘贴小帮手** — 复制粘贴模板，缺料时可自动下单
- **ME建筑小帮手** — 原版建筑小帮手 + AE 集成
- **ME更替小帮手** — 原版更替小帮手 + AE 集成

三款小帮手的统一能力：绑定到 AE 网络后，使用时若材料不足但有样板、且身上携带无线终端，会直接链式拉起 AE2 的下单界面，逐个确认即可补齐材料。

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

## 许可证

GNU AGPL 3.0

# GadgetsME Network

一个 NeoForge 1.21.1 模组，将 **Building Gadgets 2** 与 **Applied Energistics 2** 集成，在材料列表界面添加"从AE下单"功能。

## 功能

- **从AE下单** — 在 BG2 的材料列表界面中添加按钮，自动通过 AE2 合成缺失物品
- **智能库存检查** — 下单前自动扣除背包和 AE 网络中已有的物品
- **依次弹窗** — 多个缺失物品逐个在 AE2 的合成数量界面中打开
- **无线终端支持** — 检测主手、副手、背包和饰品栏中的无线终端
- **网络扫描** — 绑定到无线访问点后自动搜索网络上的合成终端
- **饰品栏支持** — 无线终端放在 Curios 饰品栏中也能正常使用

## 前置模组

- [NeoForge](https://neoforged.net/) 21.1.230+
- [Building Gadgets 2](https://www.curseforge.com/minecraft/mc-mods/building-gadgets-2) 1.3.9+
- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) 19.2.17+

## 使用方法

1. 使用小帮手的绑定模式，将小帮手绑定到任意 AE2 线缆/子网/控制器（或无线访问点）
2. 打开小帮手的材料列表界面
3. 点击 **"从AE下单"** 自动合成所有缺失材料
4. 每个缺失物品会自动弹出 AE2 合成数量界面
5. 确认或跳过每个物品 — 队列会自动推进到下一个

## 许可证

GNU AGPL 3.0

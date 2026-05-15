# GadgetsME Network

A NeoForge 1.21.1 mod that integrates **Building Gadgets 2** with **Applied Energistics 2**, adding an "Order from AE" button to the material list GUI.

## Features

- **Order from AE** — Adds a button in BG2's MaterialListGUI to automatically craft missing items through AE2
- **Smart stock check** — Deducts items already present in your inventory and AE network before queuing crafts
- **Sequential popup** — Multiple missing items open one-by-one in AE2's native CraftAmountScreen
- **Wireless terminal support** — Detects wireless terminals in main hand, offhand, or inventory

## Dependencies

- [NeoForge](https://neoforged.net/) 21.1.230+
- [Building Gadgets 2](https://www.curseforge.com/minecraft/mc-mods/building-gadgets-2) 1.3.9+
- [Applied Energistics 2](https://www.curseforge.com/minecraft/mc-mods/applied-energistics-2) 19.2.17+

## Usage

1. Bind your Building Gadget to any AE2 cable/subnet/controller using the gadget's bind mode
2. Open the Building Gadget's Material List GUI
3. Click **"Order from AE"** to auto-craft all missing materials
4. For each missing item, the AE2 CraftAmountScreen opens automatically
5. Confirm or skip each item — the queue advances to the next

## License

GNU AGPL 3.0

# 维吉尔的椅子

适用于 Minecraft Java 版 1.21.1。最低支持 NeoForge 21.1.1，需要 Java 21，无额外模组依赖。

## 安装与玩法

下载模组 JAR，放到游戏实例的 `mods` 文件夹。联机游玩时，客户端和服务端都需要安装同一版本。[下载最新版](https://github.com/Luccasssz/vergil-chair/releases/latest)

- 在创造物品栏点右上方的 `>` 翻页，打开椅子图标的「维吉尔的椅子」标签页领取。也可以用 6 块白色混凝土在工作台合成。
- 椅子约宽 0.86 格、高 1.35 格，放置时需要一列两格空间。右键椅面或椅背都能坐下，按左 Shift 起身。
- 按 G 触发震惊瘫坐。角色受惊抬手，后仰时叹一口气，随后手臂垂落并保持姿势。再按一次 G 坐直。
- 按 H 触发震惊跌倒。角色先沿椅面滑到扶手前方，再倾倒、落地，然后起身。若前滑路线或周围落点被挡住，会提示并保持原来的坐姿。

G 和 H 可以在「选项 → 控制 → 按键绑定」中修改。按 F5 可以从第三人称观看动作。

合成摆法中，■ 表示白色混凝土，· 表示空格。

```
■··
■■■
■·■
```

## 构建与模型

使用 JDK 21 运行 `./gradlew build`（Windows 为 `gradlew.bat build`）。打包好的 JAR 位于 `build/libs`。

游戏网格与白色纹理由 `Vergil_Chair.blend` 导出。持有源模型时，可以用 Blender 5.1 和下面的命令重新导出。

```text
blender -b <Vergil_Chair.blend 的路径> --python tools/export_chair.py
```

运行 `python tools/generate_slump_sigh.py` 可重新生成叹气音效，需要 NumPy、SciPy 和 FFmpeg。

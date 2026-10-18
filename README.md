# ☁ CloudBeats — SoundCloud Player for Minecraft

<div align="center">

![Minecraft](https://img.shields.io/badge/Minecraft-1.21.11%20|%2026.3-brightgreen.svg?style=for-the-badge&logo=minecraft)
![Fabric](https://img.shields.io/badge/Loader-Fabric%20%E2%89%A50.19.2-blue.svg?style=for-the-badge)
![Version](https://img.shields.io/badge/Release-v0.6.8-ff5500.svg?style=for-the-badge)
![License](https://img.shields.io/badge/License-MIT-purple.svg?style=for-the-badge)

**Listen to SoundCloud, playlists, and local audio files directly inside Minecraft with a modern sleek UI & in-game HUD.**

[Features](#-features) • [Installation](#-installation) • [Keybinds](#-keybindings) • [Building](#-building-from-source)

</div>

---

## ✨ Features

- **No API Key / Account Required** — Works instantly out of the box using public SoundCloud stream resolvers.
- **Search & URL Playback**:
  - Type any track name or artist to get instant search results.
  - Paste any SoundCloud track or playlist link.
  - Paste direct paths to local `.mp3` files on your PC.
- **Modernized Dark Glass UI**:
  - Sleek Spotify/Cyber-minimal design with glowing accent elements.
  - Live animated 4-bar audio equalizer reacting when a track is playing.
  - Interactive track cards with 1-click **Play**, **Like** (Favorite), **Queue**, and **Remove**.
  - Built-in **HUD & Settings** tab right in the player — customize HUD anchor, size, and layout without leaving the menu.
- **Floating Now Playing Dock**:
  - Track artwork and detailed metadata.
  - Interactive scrubbable progress bar with hover time preview.
  - Playback controls: `🔀` Shuffle, `⏮` Previous, `-10s` Rewind, `▶ / ⏸` Play/Pause, `+10s` Forward, `⏭` Next, `🔁` Repeat (Off / All / One).
  - Quick volume slider with 1-click **Mute/Unmute** (`🔊 / 🔇`).
- **In-Game Music HUD**:
  - **Full Mode**: Displays track info, animated equalizer, progress bar, mini volume, and playback buttons.
  - **Compact Mode**: Minimalist banner with just the track name and progress bar.
  - **3 Sizes**: `S` (Small), `M` (Medium), `L` (Large).
  - **9 Anchor Positions**: Top, center, bottom, left, and right screen anchors.
  - **Draggable**: Drag the HUD anywhere on your screen with the mouse in-game.
- **Favorites & Playlist Queue**:
  - Save your favorite SoundCloud tracks and local files with one click (`♥`).
  - Manage track queues on the fly.

---

## 🎮 Keybindings

All hotkeys are customizable in Minecraft's **Controls → Key Binds** menu under the **SoundCloud Player** category:

| Keybind | Default Key | Action |
| :--- | :---: | :--- |
| **Open CloudBeats Player** | `P` | Open or close the main music workstation |
| **Play / Pause** | `.` *(Period)* | Toggle playback |
| **Next Track** | `]` | Skip to the next track in queue |
| **Previous Track** | `[` | Previous track or restart track |
| **Seek Forward (+10s)** | `=` | Jump forward 10 seconds |
| **Seek Backward (-10s)** | `-` | Jump back 10 seconds |
| **Volume Up** | `↑` *(Up Arrow)* | Increase volume (+10%) |
| **Volume Down** | `↓` *(Down Arrow)* | Decrease volume (-10%) |
| **Toggle In-Game HUD** | `H` | Show or hide the player HUD |
| **Cycle HUD Size** | `J` | Cycle through S / M / L size presets |
| **Toggle HUD Style** | `K` | Toggle between Full and Compact HUD modes |

> **In-Menu Shortcuts**: `Space` pauses/resumes playback; `Enter` runs search or starts selected song; `↑ / ↓` navigates the track list.

---

## 📥 Installation

1. Install **[Fabric Loader](https://fabricmc.net/use/)** (version `≥ 0.19.2`) and **Fabric API** for Minecraft `1.21.11` or `26.3`.
2. Download the latest `soundcloud-0.6.8.jar` from **[Releases](https://github.com/solanafl1pper/minecraftcloud/releases)**.
3. Drop the downloaded `.jar` file into your `.minecraft/mods` folder (or via PrismLauncher / CurseForge / Modrinth).
4. Launch the game and press **`P`** to start listening!

*(Optional)* Install **Mod Menu** to access CloudBeats settings directly from the mods list.

---

## 🛠 Building From Source

Requires **JDK 21** or newer:

```bash
# Clone the repository
git clone https://github.com/solanafl1pper/minecraftcloud.git
cd minecraftcloud

# Build the mod jar
./gradlew build
```

The compiled mod will be located in:
```
build/libs/soundcloud-0.6.8.jar
```

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

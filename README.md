# AirLink 🚀

An ultra-lightweight, zero-bloat peer-to-peer Android messaging application built entirely using native Bluetooth APIs, a custom Canvas-rendered UI, and a modular communication architecture. Engineered from scratch using pure Java to explore the absolute limits of Android binary optimization.

## ⚡ Key Highlights

*   **Hyper-Optimized Size:** ~35 KB Signed APK (Successfully broke the sub-40KB barrier!)
*   **100% Offline P2P:** Zero internet, zero external dependencies, total privacy.
*   **Frameworkless Rendering:** No Jetpack Compose, no XML Layout inflation overhead—pure custom Canvas rendering.

---

## 📊 Core Specs & Features

### 📁 Technical Breakdown
*   **Language & Runtime:** Pure Java 11 (Zero Kotlin runtime or AndroidX/KTX overhead strings).
*   **UI Pipeline:** Custom Canvas Rendered UI with Hybrid Input processing (direct EditText bridging).
*   **Data Transport:** Low-level Native Bluetooth Socket APIs with binary data chunking.
*   **Multilingual Engine:** Native UTF-8 parsing engine that flawlessly handles Unicode characters (Full Hindi support).

### 🛠️ Feature Modules
*   **Device Pairing & Discovery:** Efficient local Bluetooth device scanning and MAC connection abstraction.
*   **Real-time P2P Chat:** Seamless packet routing over active RFCOMM channels.
*   **Binary File/Image Transfer:** Custom bit-stream parsing to reliably share files/images between connected local storage paths.
*   **Lightweight Home Widget:** Integrated micro app-widget for monitoring connection status directly from the home screen.

---

## 📐 Architecture Overview

AirLink uses a highly decoupled, modular architectural design. The transport layer is strictly separated from the communication and presentation layers, ensuring the core chat engine remains protocol-agnostic.

```text
       ┌────────────────────────┐
       │        UI Layer        │  (Custom Canvas Lifecycle)
       └───────────┬────────────┘
                   │
                   ▼
       ┌────────────────────────┐
       │      Chat Engine       │  (State Management & Encoding)
       └───────────┬────────────┘
                   │
                   ▼
       ┌────────────────────────┐
       │  Transport Interface   │  (Decoupled Abstraction Layer)
       └───────────┬────────────┘
                   │
                   ▼
       ┌────────────────────────┐
       │    Bluetooth Layer     │  (Native Hardware RFCOMM Sockets)
       └────────────────────────┘
```
This modular layout allows future expansion into alternative protocols (like Wi-Fi Direct or Local LAN) without touching the UI rendering pipeline.

---

## 🧠 Technical Challenges Overcome

Building a rich offline communication experience inside 35 KB required solving unique low-level engineering roadblocks:

*   **Android 13+ Runtime Restrictions:** Redesigned permissions helper and asynchronous Bluetooth discovery adapters to satisfy modern Android target SDK 36 background execution rules.
*   **Multiline Packet Integrity:** Resolved traditional socket stream buffering bugs where packet fragmentation sliced strings mid-sentence, ensuring 100% message packet retention.
*   **Canvas + Native EditText Bridge:** Built a custom focus-routing architecture to link a hidden native Android EditText window loop with manually bounded drawing zones on a single custom Canvas view.
*   **Viewport Scrolling & Keyboard Sync:** Created custom dynamic layout-matrix scaling equations to realign painted chat bubbles instantly when the soft keyboard is toggled.
*   **Multilingual Byte Boundary Alignment:** Refactored byte array slicing methodologies to avoid split-byte corruption during the decoding of multi-byte characters like Hindi text structures.

---

## 🔬 Optimization Blueprint

Modern Android development often defaults to importing heavy external libraries, leading to massive application bloat. AirLink counters this paradigm. By relying purely on low-level system services, direct programmatic layout calculations, and aggressive R8 compiler compression rules (R8 Full Mode), this project proves that utility applications can operate beautifully inside a minimal micro-binary payload.

### Crucial optimizations set in `gradle.properties`

```properties
android.enableR8.fullMode=true
android.buildFeatures.optimize=true
```


---

## 💡 License

This repository is open-source. Feel free to clone, analyze, and experiment with low-level Android compiler optimization!
# 996coin Wallet — Android

<p align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="996coin Wallet" width="96" height="96"/>
</p>

<p align="center">
  <strong>Open-source, non-custodial Android wallet for the 996coin (NNS) network</strong>
</p>

<p align="center">
  <a href="https://github.com/syabiz/996coin-wallet/releases"><img src="https://img.shields.io/github/v/release/syabiz/996coin-wallet?color=FF6B00&label=Latest%20Release" alt="Release"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-MIT-blue.svg" alt="License"/></a>
  <a href="https://github.com/syabiz/996coin-wallet/issues"><img src="https://img.shields.io/github/issues/syabiz/996coin-wallet" alt="Issues"/></a>
  <img src="https://img.shields.io/badge/platform-Android%208.0%2B-green" alt="Platform"/>
  <img src="https://img.shields.io/badge/built%20with-Kotlin-7F52FF" alt="Kotlin"/>
</p>

---

## ⚠️ Transparency Notice

> **This is a cryptocurrency wallet. Your funds are your responsibility.**
>
> This project is intentionally **100% open source** so that anyone — developers, security researchers, and users — can **read, audit, and verify** every line of code that handles your private keys and transactions. We believe that in the context of financial software, transparency is not optional — it is a requirement for trust.
>
> **We strongly encourage you to review the source code before using this wallet with real funds.**

---

## Table of Contents

- [About](#about)
- [Security & Transparency](#security--transparency)
- [Features](#features)
- [Architecture](#architecture)
- [Network Parameters](#network-parameters)
- [Getting Started](#getting-started)
  - [Requirements](#requirements)
  - [Build from Source](#build-from-source)
- [How Keys Are Handled](#how-keys-are-handled)
- [Permissions Explained](#permissions-explained)
- [Known Limitations](#known-limitations)
- [Contributing](#contributing)
- [License](#license)
- [Disclaimer](#disclaimer)

---

## About

**996coin Wallet** is a lightweight, non-custodial SPV (Simplified Payment Verification) Android wallet for the [996coin (NNS)](https://github.com/Imusing/996-Coin) network — a community-driven Proof-of-Stake cryptocurrency by the HCC Community.

This wallet is built on top of [bitcoinj](https://bitcoinj.org/), a well-established Java/Kotlin library for Bitcoin-compatible protocols, customized with 996coin's specific network parameters.

> **Non-custodial** means: **we do not hold your keys, we do not hold your coins.** Everything stays on your device.

---

## Security & Transparency

### Why is this open source?

Wallet software is among the most security-sensitive software a user can install. A closed-source wallet requires you to blindly trust the developer. We reject that model entirely.

By publishing every line of code:

- ✅ **Anyone can audit** how private keys are generated, stored, and used
- ✅ **Anyone can verify** that we do not transmit keys or seed phrases to any server
- ✅ **Anyone can build** the APK themselves from source and compare it to released builds
- ✅ **Anyone can report** vulnerabilities via GitHub Issues or direct contact

### Key security properties

| Property | Implementation |
|---|---|
| **Key storage** | Keys stored in `bitcoinj` wallet protobuf, encrypted with AES-256-GCM using user PIN |
| **Seed phrase** | BIP-39 12-word mnemonic, generated locally using `java.security.SecureRandom` |
| **PIN storage** | Stored in `EncryptedSharedPreferences` (AES-256-GCM, Android Keystore-backed) |
| **No server-side keys** | Private keys never leave the device |
| **No analytics / tracking** | Zero third-party analytics SDKs |
| **No ads** | This app contains no advertising |
| **Backup exclusion** | Wallet file is excluded from Android cloud backup (`backup_rules.xml`) |
| **Network** | HTTPS enforced for all API calls (`network_security_config.xml`) |

### Files to audit

If you want to verify the security of this wallet, these are the most important files to review:

```
app/src/main/java/com/coin996/wallet/
├── core/spv/WalletManager.kt          ← Key generation, signing, broadcasting
├── core/network/Coin996NetworkParams.kt ← Network configuration (no hidden servers)
├── utils/SecurePreferences.kt         ← How PIN is stored
├── utils/Extensions.kt                ← Address validation, QR parsing
├── service/SpvSyncService.kt          ← What the background service does
└── data/repository/Repositories.kt   ← What data is fetched from the internet
```

```
app/src/main/
├── AndroidManifest.xml                ← All permissions declared here
└── res/xml/network_security_config.xml ← Network security policy
```

### What we connect to

This app makes network connections to:

| Endpoint | Purpose | Contains private data? |
|---|---|---|
| 996coin P2P network (port `49969`) | SPV blockchain sync | No — only public blockchain data |
| DNS seeds (`seed*.996coin.com`) | Find peer nodes | No |
| `klingex.io/api` | NNS/USDT price data | No |

**We never send private keys, seed phrases, addresses, or balances to any server.**

---

## Features

| Feature | Status |
|---|---|
| Create new wallet (BIP-39 seed) | ✅ |
| Restore from 12-word seed phrase | ✅ |
| SPV blockchain sync | ✅ |
| Send NNS | ✅ |
| Receive NNS (QR code) | ✅ |
| Transaction history | ✅ |
| Real-time NNS/USDT price (Klingex) | ✅ |
| 24h price chart | ✅ |
| PIN protection | ✅ |
| Biometric login | ✅ |
| Backup seed phrase | ✅ |
| Dark mode | ✅ |
| Deep link `996coin:` URI | ✅ |
| BIP-21 payment request QR | ✅ |
| Background price updates | ✅ |
| Auto-restart sync after reboot | ✅ |
| Cold staking support | 🔜 Planned |
| Hardware wallet support | 🔜 Planned |
| Multi-language | 🔜 Planned |

---

## Architecture

```
996coin-wallet/
├── core/
│   ├── network/   — Coin996NetworkParams (P2P, address prefixes, magic bytes)
│   └── spv/       — WalletManager (bitcoinj wrapper: create, send, sync)
├── data/
│   ├── network/   — Klingex price API (Retrofit)
│   ├── repository/— WalletRepository, PriceRepository
│   └── WalletDatabase.kt (Room: tx cache, price cache)
├── service/
│   ├── SpvSyncService.kt    — Foreground service for blockchain sync
│   ├── PriceUpdateWorker.kt — WorkManager periodic price refresh
│   └── BootReceiver.kt      — Restart sync after device reboot
├── ui/
│   ├── activities/ — Splash, Setup, Main
│   ├── fragments/  — Home, Send, Receive, History, Settings
│   ├── fragments/setup/ — Welcome → Create → Verify → PIN → Done
│   ├── adapters/   — TransactionAdapter
│   └── viewmodels/ — HomeViewModel, SendViewModel, ReceiveViewModel, SetupViewModel
└── utils/
    ├── Extensions.kt        — QR generation, NNS formatting, URI parsing
    └── SecurePreferences.kt — Encrypted PIN/settings storage
```

**Tech stack:**

- Language: **Kotlin**
- UI: **Material Design 3** + View Binding
- Navigation: **Jetpack Navigation Component**
- DI: **Hilt** (Dagger)
- Async: **Kotlin Coroutines + Flow**
- Blockchain: **bitcoinj 0.16.2** (customized for 996coin)
- Network: **Retrofit + OkHttp + Moshi**
- Database: **Room**
- Security: **EncryptedSharedPreferences + Android Keystore**
- Background: **WorkManager**
- Charts: **MPAndroidChart**
- QR: **ZXing + zxing-android-embedded**
- Animation: **Lottie**

---

## Network Parameters

996coin-specific parameters as configured in `Coin996NetworkParams.kt`:

| Parameter | Value |
|---|---|
| Ticker | `NNS` |
| Bech32 HRP | `996` |
| P2PKH prefix | `53` → addresses start with **N** |
| P2SH prefix | `18` |
| WIF prefix | `128` |
| P2P port | `49969` |
| RPC port | `48931` |
| Network magic | `0x99 0x6c 0x01 0x33` |
| Last PoW block | `500` |
| Block spacing | `3 minutes` |
| Cold staking height | `260,000` |
| Max stake weight/UTXO | `125,000 NNS` |

---

## Getting Started

### Requirements

- Android Studio **Panda (2024.2.1)** or newer
- Android SDK **35** (Android 15)
- Min SDK: **26** (Android 8.0)
- JDK **17**
- Internet connection (for Gradle dependency download)

### Build from Source

**We encourage you to build from source rather than trusting pre-built APKs.**

```bash
# 1. Clone the repo
git clone https://github.com/syabiz/996coin-wallet.git
cd 996coin-wallet

# 2. Copy local.properties template
cp local.properties.template local.properties
# Edit local.properties — set your Android SDK path:
# sdk.dir=/path/to/your/Android/sdk

# 3. Add required assets (see notes below)
#    - res/font/nunito_regular.ttf, nunito_semibold.ttf, nunito_bold.ttf
#    - res/mipmap-*/ic_launcher.png and ic_launcher_round.png
#    - res/raw/success_animation.json (Lottie)

# 4. Build debug APK
./gradlew assembleDebug

# 5. Or build release APK (requires signing config)
./gradlew assembleRelease
```

> **Verify your build:** After building, you can compare the APK's SHA-256 hash against the hash published in each [GitHub Release](https://github.com/syabiz/996coin-wallet/releases).

### Assets Not Included in This Repo

The following assets are **not committed** to this repository because they are either proprietary or must be obtained from their original source:

| Asset | Where to get it | Destination |
|---|---|---|
| Nunito font (Regular/SemiBold/Bold) | [fonts.google.com/specimen/Nunito](https://fonts.google.com/specimen/Nunito) | `app/src/main/res/font/` |
| App icon (`ic_launcher.png`) | Your own design | `app/src/main/res/mipmap-*/` |
| Lottie success animation | [lottiefiles.com](https://lottiefiles.com/search?q=success+checkmark) | `app/src/main/res/raw/success_animation.json` |

---

## How Keys Are Handled

This section explains the complete lifecycle of your private key, so you can verify the code yourself.

### 1. Key Generation
```
WalletManager.createNewWallet()
  └── DeterministicSeed(SecureRandom, ...)   ← OS-level cryptographic RNG
        └── Wallet.fromSeed(params, seed)    ← BIP-32 HD wallet derivation
```
The seed is generated **entirely on-device** using `java.security.SecureRandom`. It is never transmitted anywhere.

### 2. Seed Display
```
SetupFragments.kt → CreateWalletFragment
  └── viewModel.generatedWords               ← displayed once on screen
```
After the user confirms they have written down the words, **the raw mnemonic is no longer displayed**. It remains accessible only via `Settings → Show Recovery Phrase` (requires PIN).

### 3. Key Storage
```
WalletManager.saveWallet()
  └── WalletProtobufSerializer().writeWallet(wallet, FileOutputStream("996coin.wallet"))
```
The wallet file is stored in the app's **private internal storage** (`/data/data/com.coin996.wallet/files/`), which is:
- Not accessible by other apps (Android sandbox)
- Not included in cloud backups (see `backup_rules.xml`)
- Optionally encrypted with your PIN via `wallet.encrypt(password)`

### 4. Signing
```
WalletManager.sendCoins()
  └── wallet.sendCoins(peerGroup, SendRequest)
        └── bitcoinj signs the transaction locally
              └── broadcast to P2P network
```
Signing happens **locally**. The signed transaction (not the key) is broadcast to the network.

### 5. PIN / Encryption
```
SecurePreferences.savePin()
  └── EncryptedSharedPreferences (AES-256-GCM)
        └── Backed by Android Keystore (hardware-backed on supported devices)
```

---

## Permissions Explained

All permissions are declared in `AndroidManifest.xml`. Here is what each one is for and why it is needed:

| Permission | Why it's needed |
|---|---|
| `INTERNET` | Connect to 996coin P2P network and price API |
| `ACCESS_NETWORK_STATE` | Check connectivity before attempting sync |
| `ACCESS_WIFI_STATE` | Optimize sync behavior on Wi-Fi |
| `CAMERA` | Scan QR codes for send address |
| `USE_BIOMETRIC` | Optional biometric login |
| `USE_FINGERPRINT` | Fingerprint auth (older devices) |
| `VIBRATE` | Haptic feedback on QR scan |
| `FOREGROUND_SERVICE` | Keep SPV sync running in background |
| `FOREGROUND_SERVICE_DATA_SYNC` | Android 14+ foreground service type |
| `WAKE_LOCK` | Prevent device sleep during initial sync |
| `RECEIVE_BOOT_COMPLETED` | Restart sync service after device reboot |

> **We do not request:** `READ_CONTACTS`, `WRITE_EXTERNAL_STORAGE`, `READ_PHONE_STATE`, `ACCESS_FINE_LOCATION`, or any other permission unrelated to wallet functionality.

---

## Known Limitations

- **SPV is not a full node.** SPV wallets trust that the longest chain is valid. For maximum security, run your own 996coin full node.
- **Price data depends on Klingex.** If Klingex is unavailable, price display will fail gracefully. Wallet functions (send/receive) are unaffected.
- **Initial sync takes time.** The first blockchain sync can take several minutes depending on network conditions.
- **PoS staking is not supported** in this wallet. For staking, use the official Qt desktop wallet.
- **DNS seeds must be reachable.** If `seed*.996coin.com` entries are not configured, peer discovery may fail. Add manual peer IPs via settings (planned feature).

---

## Contributing

Contributions are welcome! Please follow these guidelines:

1. **Fork** this repository
2. **Create** a feature branch: `git checkout -b feature/your-feature-name`
3. **Commit** your changes with a clear message
4. **Open a Pull Request** with a description of what changed and why

### Reporting Security Issues

If you discover a security vulnerability, **please do not open a public GitHub Issue.**

Instead, report it privately by:
- Opening a [GitHub Security Advisory](https://github.com/syabiz/996coin-wallet/security/advisories/new)
- Or emailing the maintainer directly (see GitHub profile)

We will acknowledge the report within 48 hours and aim to release a fix within 14 days for critical issues.

### Code Style

- Follow **Kotlin coding conventions**
- Use **ViewBinding** (no `findViewById`)
- All network calls must go through the **Repository layer**
- No private keys or seeds in **logs** (`BuildConfig.DEBUG` guards already in place)

---

## License

```
MIT License

Copyright (c) 2026 syabiz

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in
all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
THE SOFTWARE.
```

This project builds on [bitcoinj](https://github.com/bitcoinj/bitcoinj) (Apache 2.0) and [996-Coin](https://github.com/Imusing/996-Coin) (MIT).

---

## Disclaimer

> This software is provided **"as is"**, without warranty of any kind. The authors are not responsible for any loss of funds resulting from bugs, user error, device compromise, or any other cause.
>
> Cryptocurrency wallets carry inherent risk. **Always:**
> - Back up your 12-word recovery phrase **offline** (paper, metal)
> - Never share your seed phrase or PIN with anyone
> - Never store your seed phrase digitally (photos, cloud notes, email)
> - Test with small amounts before using significant funds
> - Keep your Android device updated and secure

---

<p align="center">
  Built with ❤️ for the 996coin community &nbsp;|&nbsp;
  <a href="https://996coin.com">996coin.com</a> &nbsp;|&nbsp;
  <a href="https://klingex.io/register?ref=B68D1568">Trade NNS on Klingex</a>
</p>

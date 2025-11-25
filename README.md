# PsychoLogger 🧠📊

<img width="512" height="512" alt="psychologger_icon" src="https://github.com/user-attachments/assets/252d71b1-bee1-444d-a859-2c2fabe2cdd8" />

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![Android](https://img.shields.io/badge/Android-7.0%2B-3DDC84?logo=android&logoColor=white)](https://www.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-1.9.24-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Open Source](https://img.shields.io/badge/Open%20Source-%E2%9D%A4-red?logo=open-source-initiative&logoColor=white)](https://opensource.org)
[![Built with Claude Code](https://img.shields.io/badge/Built%20with-Claude%20Code-8A63D2?logo=anthropic&logoColor=white)](https://claude.ai/code)
[![ChatGPT](https://img.shields.io/badge/Assisted%20by-ChatGPT-74aa9c?logo=openai&logoColor=white)](https://chat.openai.com)
[![PBL](https://img.shields.io/badge/Learning-Project%20Based-orange?logo=gradleplay&logoColor=white)](https://en.wikipedia.org/wiki/Project-based_learning)
<img src="https://img.shields.io/badge/Made_with-Love_&_Coffee-ff69b4"/>

[🇪🇸 Versión en español](README.md)

---

## What is PsychoLogger?

PsychoLogger is your personal logbook for psychonaut experiences. An Android app designed for those who want to document and understand their experiences with psychoactive substances in a responsible and safe way.

---

## Why use PsychoLogger?

### 🔒 Total Privacy
- All your data stays **on your device** — nothing is uploaded to the internet  
- Protected with fingerprint or PIN  
- Military-grade encryption for your most sensitive entries  

### 📝 Complete Logging
- Record substance, dose, date, and time  
- Document your **set** (mindset) and **setting** (environment)  
- Add personal notes and/or use it as a complementary “psychonaut diary”  
- Categorize your substances with colors and emojis  

### 📈 Visualize Your Patterns
- Explore your history through an interactive calendar  
- Statistics to better understand your habits  

### 🔗 Attached Harm Reduction Resources  
Links to well-known harm-reduction and information websites:

- **Erowid** – Extensive database on psychoactive substances  
- **TripSit** – Interaction info, dosage guidelines, and real-time assistance  
- **MAPS** – Research on medical, legal, and cultural uses of psychedelics  
- **PsychonautWiki** – Scientific encyclopedia of psychoactive substances  

---

## Main Features

### 🏠 Main Screen  
The app blends native Android elements with a smooth web-based interface. Your security is guaranteed from the moment you open the app.

### 💊 Substance Management  
Includes predefined substances (LSD, Ketamine, Opium), and you can add your own:
- Psychedelics 🍄  
- Stimulants/MDMA ⚡  
- Dissociatives 🌀  
- Depressants 😴  

### 📊 Your Data, Your Control  
- **Export** everything to CSV anytime  
- **Import** data from other formats  
- **Migrate** easily between devices  
- **Backup** your information locally and securely  

### 🛡️ Meaningful Security  
- **Biometric unlock:** fingerprint, face, etc.  
- **Backup PIN**  
- **Auto-lock:** closes itself after inactivity  
- **Secure entry point:** verification required every time  

---

## Installation

### Requirements
- Android 7.0 or higher  
- About 20MB of storage  
- Biometric sensor (recommended but not required)  

### How to Install
1. Download the APK or clone this repo  
2. If building from source: Android Studio + Gradle  
3. Install on your device  
4. Set your PIN and biometrics on first launch  

## How to Use PsychoLogger

### First Time
1. **Set up your security** – Choose a PIN and enable biometrics  
2. **Add your substances** – Customize them with colors and emojis  
3. **Adjust preferences** – Auto-lock time, etc.

### Daily Use
1. **Open the app** – Authenticate with fingerprint or PIN  
2. **Log your experience** – Substance, dose, context, notes  
3. **Review your data** – Calendar, graphs, statistics  
4. **Export** when you need a backup

## Technology

Built using modern Android technologies:
- **Kotlin** as the main language  
- **Jetpack Compose** for smooth native UI  
- **Material 3** for a clean and familiar design  
- **AndroidX Encryption** for maximum security  
- **Hybrid WebView** for the main interface  

## Important: Harm Reduction

PsychoLogger is designed for:
- ✅ **Education** about psychoactive substances  
- ✅ **Harm reduction** through documentation  
- ✅ **Self-knowledge** and understanding patterns  
- ✅ **Responsible research**  

It is NOT:
- ❌ A promotion of recreational use  
- ❌ A replacement for professional medical advice  
- ❌ Intended for minors  

## Technical Details

### Current Version: 1.0
- **Package:** com.d4vram.psychologger  
- **Target:** Android 14 (API 36)  
- **Minimum:** Android 7.0 (API 24)  
- **Size:** ~3MB installed
=

### Data Structure
Your entries are stored in a structured way:

```
📁 Substances (name, color, emoji, date)
📁 Entries (substance, dose, date, set, setting, notes)
📁 Preferences (user configuration)
```


## Privacy & Security

### Your Privacy Is Sacred
- **Zero telemetry** – No data is sent to any server  
- **Local only** – Everything stays on your device  
- **No external connections** – The app works fully offline  
- **Robust encryption** – AES-256 for sensitive data  

### Security Controls
- **Multi-layer authentication** (biometric + PIN)  
- **Secure PIN hashing** (SHA-256)  
- **Smart auto-lock** configurable  
- **Integrity verification** on each startup  

## Contribute

Want to improve PsychoLogger? Contributions are welcome:
1. Fork the repository  
2. Create a branch for your feature  
3. Follow Kotlin/Android conventions  
4. Submit your PR with a detailed description  

## Support

Issues? Suggestions? Bugs?
- Open an issue on GitHub  
- Contact the developer  
- Check the technical documentation  

## 🔓 Decrypting Encrypted Audio Backups

When you export audio from PsychoLogger, it is encrypted using **AES-256-GCM** to protect your privacy.  
Here is how to decrypt it:

### Requirements

```bash
# Install Python 3 if you don't have it
sudo apt install python3 python3-pip  # Linux/Ubuntu
# brew install python3                # macOS

# Install cryptography library
pip3 install cryptography

### Decryption Commands

```bash
# Option 1: With password in the command (less secure)
python3 decrypt_psychologger.py audios_encrypted_2025-01-15.zip myPassword123

# Option 2: Without password (it will ask you for it in a hidden way - RECOMMENDED)
python3 decrypt_psychologger.py audios_encrypted_2025-01-15.zip
🔒 Password: ****
```

### What does the script do?

1. **Reads the encrypted ZIP** with your exported audios
2. **Extracts the metadata** (salt, IV, PBKDF2 iterations)
3. **Derives the AES-256 key** from your password using PBKDF2 with 120,000 iterations
4. **Decrypts** the data with AES-256-GCM
5. **Extracts the audios** to the `decrypted_audios/` folder

### Complete example

```bash
# 1. Download the encrypted ZIP from your phone
adb pull /sdcard/Download/encrypted_audios_2025-01-15.zip .

# 2. Decrypt
python3 decrypt_psychologger.py encrypted_audios_2025-01-15.zip
🔒 Password: ****

# Output:
🔓 Decrypting: audios_encrypted_2025-01-15.zip
📄 Metadata:
   - Algorithm: AES-256-GCM
   - Iterations: 120000
   - Salt: 16 bytes
   - IV: 12 bytes
📦 Encrypted data: 2458930 bytes
🔑 Deriving AES-256 key with PBKDF2 (120000 iterations)...
🔐 Decrypting with AES-256-GCM...
✅ Decryption successful: 2458802 bytes
📂 Extracting audios to: decrypted_audios/
🎵 Audios found: 12
   ✓ audio_2025-01-10_143522.m4a
   ✓ audio_2025-01-11_092311.m4a
   ...
✅ Decryption complete!

# 3. Your audio files are in: decrypted_audios/
ls decrypted_audios/
```

### Encryption Security

- **Algorithm:** AES-256-GCM (military standard)
- **Key derivation:** PBKDF2-HMAC-SHA256 with 120,000 iterations
- **Unique salt:** Randomly generated by backup
- **Unique IV:** Randomly generated (96 bits)
- **Authentication:** GCM includes integrity verification

### Common Errors

**❌ Incorrect password:**
```
❌ ERROR: Incorrect password or corrupted data
```
→ Verify that the password is exactly the one you used when exporting.

**❌ Library not installed:**
```
ModuleNotFoundError: No module named ‘cryptography’
```
→ Run: `pip3 install cryptography`

---

**Remember:** This tool is designed to promote responsible use and harm reduction. Always inform yourself properly and consider the risks before experimenting with any psychoactive substance.

*Developed with ❤️ for the responsible psychonaut community.*


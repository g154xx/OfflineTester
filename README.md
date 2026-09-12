# POS Offline Tester v1.0

**EMV Offline Capability Testing Tool using HCE (Host Card Emulation)**

Test whether a POS terminal supports offline payment transactions by emulating a contactless card with offline mode enabled.

---

## 📋 Overview

This Android application emulates a contactless payment card (Visa, Mastercard, etc.) that supports offline transactions. It communicates with a real POS reader to determine if the terminal:

- **Supports offline mode** (P1=0x80 → TC Cryptogram) ✓
- **Requires online authorization** (P1=0x00 → ARQC) ✗
- **Declines transaction** (P1=0x40 → AAC) ✗
- **Incomplete transaction** (no GENERATE AC received) ?

### Key Features

- ✅ **HCE Emulation** - Acts as a virtual contactless card
- ✅ **20+ Payment AIDs** - Mastercard, Visa, Electron, Diners, AmEx, etc.
- ✅ **Real-time APDU Logging** - See every command/response
- ✅ **Automatic Verdict** - Detects offline capability from P1 value
- ✅ **Log Export** - Save complete transaction history to .txt file
- ✅ **45-second Timeout** - Auto-finalize test after timeout
- ✅ **GitHub Actions CI/CD** - Auto-build APK on push

---

## 🛠️ Installation & Setup

### Prerequisites

- **Android Device**: Samsung A22 or any device with NFC (API 24+)
- **NFC Enabled**: Activate NFC in Settings
- **Git** (optional, for cloning)

### Step 1: Clone Repository

```bash
git clone https://github.com/YOUR_GITHUB_USERNAME/OfflineTester.git
cd OfflineTester
```

### Step 2: Open in Android Studio

1. Open Android Studio
2. File → Open → Select `OfflineTester` folder
3. Wait for Gradle sync to complete

### Step 3: Build APK Locally (Optional)

```bash
./gradlew assembleRelease
```

APK will be at: `app/build/outputs/apk/release/app-release.apk`

### Step 4: Use GitHub Actions (Recommended)

1. Push code to GitHub
2. Go to **Actions** tab
3. Wait for workflow to complete
4. Download APK from **Artifacts**

### Step 5: Install on Device

```bash
adb install app-release.apk
```

Or manually transfer and install via file manager.

---

## 🚀 Usage

### Starting a Test

1. **Open the app** on your NFC-enabled device
2. **Tap "START TEST"** button
3. **Position device near POS reader** (contactless area, ~10cm)
4. **Wait for test to complete** (automatic after 45 seconds)
5. **Check the verdict**:
   - 🟢 **Green** = Supports offline
   - 🔴 **Red** = Requires online
   - 🟡 **Yellow** = Incomplete/Unknown

### Understanding the Log

Each line shows:
```
[TIME] [DIRECTION] APDU_HEX  # DESCRIPTION
```

- `>>` = Card sends (RX by POS)
- `<<` = POS sends (TX by device)
- Color-coded by message type

Example:
```
14:23:45.123 [RX] 00A4040007A0000000041010 # SELECT Mastercard
14:23:45.245 [TX] 6F408407A0000000041010... # FCI Response
14:23:45.367 [RX] 80A8000013 9F1A02 9F0206  # GPO Request
14:23:45.489 [TX] 800630C00 10020100        # AIP 0x3C (offline OK)
14:23:45.612 [RX] 80AE80000E ...            # GENERATE AC P1=0x80 (TC!)
VERDICT: ✓ SUPPORTS OFFLINE
```

### Exporting Logs

1. Tap **EXPORT** button
2. File saved to: `/storage/emulated/0/Android/data/com.gag4.offlinetester/files/logs/`
3. Format: `log_YYYYMMDD_HHMMSS.txt`
4. Contains full transaction timeline

---

## 📊 Supported Payment AIDs

| Card Type | AID | ISO/IEC |
|-----------|-----|---------|
| Mastercard | A0000000041010 | Standard |
| Maestro | A0000000043060 | Mastercard Debit |
| Visa | A0000000031010 | Standard |
| Visa Electron | A0000000032010 | Debit |
| American Express | A0000000025010 | AmEx |
| Diners Club | A0000000036000 | Regional |
| JCB | A0000000651010 | Asia-Pacific |
| UnionPay | A0000000330101 | International |
| PSE | 325041592E5359532E444446303031 | Payment System Environment |

**Adding More AIDs:**
1. Edit `app/src/main/res/xml/apdu_service.xml`
2. Add new `<aid-filter>` entry
3. Rebuild APK

---

## 🔍 Technical Details

### HCE Flow

```
1. Device activates HCE service
2. POS reader detects card (AID scanning)
3. POS sends SELECT [AID]
   → Device responds: FCI with AIP bit 6 = 1 (offline capable)
4. POS sends GPO (Get Processing Options)
   → Device responds: AIP 0x3C00 (offline allowed)
5. POS decides based on floor limit:
   → Sends GENERATE AC with P1=0x80 (TC, offline) = SUPPORTS ✓
   → Sends GENERATE AC with P1=0x00 (ARQC, online) = REQUIRES ONLINE ✗
6. Device analyzes P1 value and renders verdict
```

### AIP Interpretation

- **AIP = 0x3C00** = Offline data authentication + CVM offline possible
- **Bit 6 = 1** signals to POS: "This card can go offline"
- POS decides if it wants to use offline based on:
  - Floor limit settings
  - Available network connectivity
  - Terminal configuration

### P1 Values in GENERATE AC

| P1 | Meaning | Offline? |
|----|---------|----------|
| 0x80 | TC (Transaction Certificate) | ✓ YES |
| 0x00 | ARQC (Auth Request Cryptogram) | ✗ NO |
| 0x40 | AAC (Application Authentication Code) | ✗ DECLINED |

---

## ⚙️ Configuration

### Timeout Setting

Edit in `Constants.java`:
```java
public static final int TEST_TIMEOUT_MS = 45000; // milliseconds
```

### Export Filename Format

Edit in `ApduLogger.java`:
```java
// Current format: log_YYYYMMDD_HHMMSS.txt
SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)
```

### Response Data

Modify APDU responses in `HceCardService.java`:
```java
RESPONSE_MAP.put(
    "00A4040007A000000004101000",  // SELECT Mastercard
    concat(...)  // Response bytes
);
```

---

## 🐛 Troubleshooting

### "NFC not available"
- Device lacks NFC hardware
- Check: Settings → NFC

### "Test doesn't complete"
- Ensure HCE service is activated:
  - Settings → Apps → Offline Tester → Permissions → NFC ✓
- POS reader might not support HCE

### "No logs appearing"
- Check log storage permissions:
  - Settings → Apps → Offline Tester → Permissions → Storage ✓
- Ensure test was started before approaching reader

### "APK won't compile"
- Update Android Studio to latest version
- Run: `./gradlew clean && ./gradlew build`
- Check JDK version: Must be 11+

---

## 📁 Project Structure

```
OfflineTester/
├── .github/workflows/build.yml          # GitHub Actions CI/CD
├── app/
│   ├── src/main/
│   │   ├── java/com/gag4/offlinetester/
│   │   │   ├── MainActivity.java         # UI Controller
│   │   │   ├── TestViewModel.java        # State Management
│   │   │   ├── HceCardService.java       # HCE Implementation
│   │   │   ├── ApduAnalyzer.java         # Verdict Logic
│   │   │   ├── ApduLogger.java           # Logging (Thread-safe)
│   │   │   ├── LogAdapter.java           # RecyclerView Adapter
│   │   │   ├── TlvBuilder.java           # TLV Response Builder
│   │   │   ├── TlvParser.java            # TLV Parser
│   │   │   ├── HexUtils.java             # Hex Utilities
│   │   │   ├── LogEntry.java             # Data Model
│   │   │   └── Constants.java            # EMV Constants & AIDs
│   │   ├── res/
│   │   │   ├── layout/activity_main.xml  # Main Layout
│   │   │   ├── layout/item_log.xml       # Log Item Layout
│   │   │   ├── values/strings.xml
│   │   │   ├── values/colors.xml
│   │   │   └── xml/apdu_service.xml      # HCE Configuration
│   │   └── AndroidManifest.xml
│   └── build.gradle
├── build.gradle
├── settings.gradle
├── gradle.properties
└── README.md
```

---

## 🔐 Security Notes

- This tool uses **standard EMV commands** only
- No card data is stored or transmitted remotely
- All logging is local to device storage
- HCE service requires NFC permission and system approval

---

## 📝 License

Open source. Use freely for pentesting and research.

---

## 👤 Author

**GAG4 Red Team** - Offline EMV Testing  
For questions/issues: Create GitHub issue

---

## 🔄 Version History

**v1.0 (Current)**
- HCE emulation with 20+ AIDs
- Real-time APDU logging
- Automatic offline verdict
- Log export functionality
- 45-second timeout
- GitHub Actions CI/CD

---

## ✅ Verification Checklist

- [ ] Android device with NFC
- [ ] NFC enabled in settings
- [ ] App installed and permissions granted
- [ ] Real POS terminal available for testing
- [ ] GitHub account (for Actions workflow)
- [ ] ADB installed (for manual install)

---

**Ready to test POS offline capabilities!** 🚀

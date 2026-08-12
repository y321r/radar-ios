# Radar iOS — Release preparation (ready when the Apple Developer account arrives)

Prepared 2026-08-13. Everything below that needs NO account is already DONE and verified.
Everything marked [ACCOUNT] waits for the paid Apple Developer Program ($99/yr).

## Status (verified)
- [x] Simulator build green (Codemagic #13) — `Radar.app` 21.74 MB, arm64, com.radar.news
- [x] Device target declared: iosArm64() in composeApp/build.gradle.kts
- [x] All 8 feed URLs are HTTPS — no ATS exception needed (default ATS permits https)
- [x] `ios-device` Codemagic workflow written — builds the real-device .app unsigned
- [x] Archive/TestFlight workflow written (commented in codemagic.yaml, `ios-release`)

## When the account arrives (checklist)
1. [ACCOUNT] Register Apple Developer Program: https://developer.apple.com/programs/enroll/
   - Country: Saudi Arabia. Payment: $99/year. Activation: 24–48h (sometimes instant).
2. [ACCOUNT] Get Team ID → Developer portal → Membership → Team ID (10 chars).
3. [ACCOUNT] Create App ID `com.radar.news` → Certificates, IDs & Profiles → Identifiers.
4. [ACCOUNT] Create the app record in App Store Connect (name «رادار», bundle id, SKU).
5. [CONFIG] Put the Team ID into `iosApp/Configuration/Config.xcconfig` (`TEAM_ID=`).
6. [CONFIG] Uncomment the `ios-release` workflow in codemagic.yaml (it already has
   placeholders). Add the distribution certificate + provisioning profile in
   Codemagic → Settings → Code signing (Apple Developer Portal integration).
7. [CONFIG] Add `iosApp/ExportOptions.plist`:
   ```xml
   <?xml version="1.0" encoding="UTF-8"?>
   <!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
   <plist version="1.0">
   <dict>
     <key>method</key><string>app-store-connect</string>
     <key>teamID</key><string>TEAM_ID</string>
   </dict>
   </plist>
   ```
8. [CI] Run the `ios-release` workflow → produces `Radar.ipa` → Codemagic can upload
   to TestFlight (App Store Connect API key in Codemagic → Integrations).
9. [TEST] Install TestFlight on the iPhone, accept the invite, verify feeds load.

## Free-account alternative (no $99) — sideloading
- Apple ID free tier: sign in Xcode with the Apple ID → personal team (7-day expiry).
- Or AltStore / Sideloadly with the free Apple ID — the unsigned device .app from the
  `ios-device` workflow can be signed locally on a Mac and installed on YOUR iPhone.
- Limits: 7-day re-sign, max 3 apps, no push/background guarantees. Fine for a personal
  smoke test; NOT a distribution path.

## Artifacts
- `ios-artifacts/Radar.app.zip` — simulator build #13 (verified: plist, arm64, resources)
- Device .app will appear as `build-ios-device/Build/Products/Debug-iphoneos/Radar.app`
  in the `ios-device` workflow artifacts once run.

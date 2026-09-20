# AR — Messaging + Audio/Video Calling

Apna aur dosto ke liye personal messenger. WhatsApp jaisa structure — chat list, 1:1 messaging,
audio/video calls — lekin apna alag dark indigo+amber theme.

## Stack
- Kotlin + Jetpack Compose (Material 3)
- Firebase Auth (Phone OTP)
- Firestore — chats, messages, user profiles, call signaling
- Firebase Cloud Messaging — incoming call & message push
- WebRTC (`stream-webrtc-android`) — actual audio/video call media, STUN for NAT traversal
- GitHub Actions — builds debug APK on every push to `main`

## One-time Firebase setup (required before first build)
1. Create a project at https://console.firebase.google.com
2. Add an Android app with package name **`com.ar.messenger`**
3. Enable in the Firebase console:
   - **Authentication → Sign-in method → Phone**
   - **Firestore Database** (start in production mode, then paste `firestore.rules` from this repo)
   - **Cloud Messaging**
4. Download `google-services.json` from Project Settings → Your apps
5. Either:
   - Place it at `app/google-services.json` (gitignored, stays local), **or**
   - Add it as a GitHub Actions secret named `GOOGLE_SERVICES_JSON` (paste the raw file
     contents) — the workflow writes it automatically before building.

## Calling notes
- Signaling (offer/answer/ICE candidates) goes through Firestore (`calls/{callId}`).
- Media (actual audio/video) flows peer-to-peer via WebRTC using Google's public STUN server.
- On real mobile networks (carrier NAT / CGNAT) a STUN-only setup can sometimes fail to
  connect. If calls connect on WiFi but not mobile data, add a TURN server in
  `WebRtcCallManager.kt` → `iceServers` (a free option: a small `coturn` instance on Render,
  or a service like Metered/Twilio TURN).
- For calls to ring when the app is closed, you'll need a small Cloud Function (or your
  existing Flask relay) that sends an FCM data message of type `call` when a `calls/{callId}`
  document is created — `ARMessagingService.kt` already handles receiving it.

## Build
- **Locally**: open in Android Studio, sync, run.
- **GitHub Actions**: push to `main` (or run the workflow manually) — APK artifact appears
  under the workflow run as `AR-debug-apk`.

## Not yet built (future steps)
- Group chats (schema supports it via `participants` array, UI is 1:1 only for now)
- Media/image messages (model has `MessageType.IMAGE` but upload flow isn't wired yet)
- Read receipts / typing indicators
- TURN server for reliable calling on mobile data

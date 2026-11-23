# Privacy Policy for Ann

Effective Date: November 23, 2026
Developer Contact: activex1357@gmail.com
Package Name: com.aistudio.ann.chat

## 1. Overview
Ann ("we", "our", or the "App") is a personal mobile companion and voice assistant designed with strict data minimization. This policy explains what information the App accesses and how it is processed.

## 2. Information We Process
- **Google Account Sign-In**: Used via Google Credential Manager & Firebase Authentication solely to verify your identity (`uid`, display name, email) and sync your personal chat sessions and saved memories to your private Firestore workspace.
- **Minimal Memories & Chat History**: Stored locally on your device in an encrypted-sandbox SQLite (Room) database and synced privately to your Google Firebase account. You may view, edit, or erase all stored data anytime from the Memories tab or Permissions & Privacy screen.
- **Gmail Read-Only (Optional)**: When connected by you, Ann fetches only recent email headers/snippets transiently in memory when you ask inbox questions. Email contents are never saved permanently or shared.
- **Contacts Lookup (Optional)**: Used strictly on-device when you ask Ann to call someone, opening your phone's native dialer without placing calls silently.
- **Microphone & Always-On Local VAD (Optional)**: Local audio energy detection stays on-device; only recognized voice commands when speaking are processed to answer your requests.
- **Mobile Storage Search (Optional)**: File and folder searches scan on-device storage locally to locate files you request.
- **System Settings & Accessibility Control (Optional)**: Used only when explicitly granted by you to inspect hardware info, adjust device settings (brightness, volume, timeout, auto-rotate), and execute requested hand gestures/navigation via the floating QuickBall.

## 3. Data Sharing & Security
- We do **not** sell, rent, or trade your personal data to advertisers or third parties.
- All user data synced to Firebase Firestore is isolated per authenticated user ID (`request.auth.uid == userId`).

## 4. Data Deletion
You can delete individual memories, delete chat sessions, or tap **"Erase All Synced Memories & Chats"** inside Permissions & Privacy at any time. For additional deletion requests, contact `activex1357@gmail.com`.

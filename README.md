# Routinely: AI-Powered Wellness & Health Tracker

**Routinely** is a comprehensive, privacy-focused health and productivity companion for Android. It seamlessly integrates physical activity tracking, nutrition management, and habit reinforcement into a single, cohesive Material 3 experience.

---

## 🚀 Key Features

### 1. Holistic Wellness Score (0–10)
Beyond simple step counts, Routinely calculates a daily wellness score based on a weighted balance of your **Sleep, Workouts, Nutrition, and Steps**. The algorithm is fully customizable, allowing you to prioritize the metrics that matter most to your personal goals.

### 2. AI-Powered Estimation (Gemini API)
*   **Smart Calories:** Describe your meal in plain English (e.g., "Two homemade rotis with a bowl of dal"), and the integrated Gemini AI estimates the caloric intake using specialized prompts for global and Indian cuisines.
*   **MET-Based Burn:** AI analyzes your specific workout details (sets, intensity, target area) to provide high-accuracy MET (Metabolic Equivalent of Task) values for precise calorie-burn calculations.

### 3. Deep Health Integration
*   **Health Connect & Google Fit:** Synchronizes steps, distance, heart points, weight, and sleep data directly from the Android ecosystem.
*   **Incremental Catch-up:** Optimized to identify data gaps and backfill missing history without redundant battery or data usage.

### 4. Resilient Architecture
*   **Self-Healing Background Sync:** Utilizes `WorkManager` for managed periodic syncs that survive device restarts and app closures.
*   **Offline-First:** All health metrics are persisted in a local **Room Database**, ensuring your history is always available even without an internet connection.
*   **Interactive Insights:** Weekly and Monthly views with "snapping" graph interactions for clear trend analysis.

---

## 🛠️ Tech Stack

- **Language:** 100% Kotlin
- **UI Architecture:** Material 3 with XML-based layouts and custom drawables.
- **Database:** Room Persistence Library for high-performance health metric storage.
- **Background Tasks:** WorkManager API (Periodic and One-time tasks).
- **Networking:** OkHttp for secure AI communication.
- **AI Integration:** Google Gemini Pro / Flash models.
- **Health Data:** Health Connect SDK & Google Fit Fitness API.

---

## ⚙️ Setup & Configuration

To showcase Routinely on your own device or test environment, follow these steps:

### 1. Prerequisites
- Android Studio Ladybug (2024.2.1) or newer.
- Physical device or Emulator with **API 26 (Android 8.0)** or higher.
- (Recommended) **Health Connect** installed on the device.

### 2. AI Backend Setup
The Android app does not contain a Gemini API key. It sends short-lived Firebase Auth and App Check tokens to the Cloudflare Worker in `cloudflare-backend/`, where the Gemini key is stored as a Cloudflare secret.

After deploying the Worker, add its HTTPS URL to `local.properties`:
```properties
AI_BACKEND_URL=https://your-worker.your-subdomain.workers.dev
```

Do not add `GEMINI_API_KEY` to the Android project. Configure that key only with Wrangler's secret command from the `cloudflare-backend/` directory.

### 3. Health App Linking
To see live data in the dashboard:
1.  Open **Health Connect** on your phone settings.
2.  Grant Routinely permissions to read Steps, Sleep, and Calories.
3.  Ensure your primary tracking app (like Google Fit) is also linked to Health Connect.

---

## 🧠 Implementation Highlights

*   **Resilience:** The app features a "Self-Healing" logic in `MainActivity` that verifies background worker status every time the app opens, ensuring sync never stays broken.
*   **Performance:** Implements **Transactional Batch Processing** for database backfills, allowing 180 days of health history to be written in milliseconds rather than seconds.
*   **Privacy:** No personal health data ever leaves the device except for the anonymous meal/workout text sent to the Gemini API for estimation.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

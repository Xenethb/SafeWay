# SafeWay 🛡️
**Your Personal Guardian & Emergency Response System**

SafeWay is a high-reliability mobile safety application designed to provide users with immediate assistance during emergencies. Built with **Java** and **Firebase**, it features real-time location sharing, automated SOS triggers, and secure evidence logging to ensure help is always just a shake away.

---

## 📱 Visual Gallery

| Home Screen | Live Map Tracking | Emergency SOS |
| :---: | :---: | :---: |
| <img src="screenshots/home.png" width="200"> | <img src="screenshots/map.png" width="200"> | <img src="screenshots/sos_active.png" width="200"> |

> *Note: Replace the filenames in the table above with the actual names of the photos in your screenshots folder.*

---

## 🚀 Key Features

* **Intelligent SOS Trigger:** Multiple activation methods including a gesture-based "Shake to Alert" (using a Low-Pass Filtered Accelerometer) and a "Volume Key Confirmation" to prevent false alarms.
* **Live Location Tracking:** Continuous background GPS monitoring via **Foreground Services**, optimized for Android 14+ and battery efficiency.
* **Guardian Network:** A mutual "Protector" system where friends can monitor each other's live coordinates during an active SOS event.
* **Evidence Vault:** Automatically records video and metadata (Timestamp/Coordinates) during emergencies, stored locally for legal or personal evidence.
* **Scheduled Activation:** "Safe Mode" scheduling—perfect for late-night travel or high-risk commutes.

---

## 🛠️ Tech Stack

| Category | Technology |
| :--- | :--- |
| **Language** | Java (Android SDK) |
| **Database** | Firebase Realtime Database |
| **Auth** | Firebase Authentication |
| **Location** | Google Play Services (FusedLocationProvider) |
| **Architecture** | Service-Oriented (Foreground Services) |

---

## ⚙️ Setup & Installation

1. **Clone the Repository:**
   ```bash
   git clone [https://github.com/Xenethb/SafeWay.git](https://github.com/Xenethb/SafeWay.git)

2. Firebase Setup:

Create a project in the Firebase Console.

Add an Android App with package name: com.s23010602.safeway.

Download google-services.json and place it in the app/ directory.

3. Build: Open in Android Studio and sync Gradle.



👨‍🎓 University Project
Institution: Open University of Sri Lanka

Course: Human-Computer Interaction (HCI) / Mobile Application Development

Project Goal: To leverage mobile sensors and cloud infrastructure to improve personal safety in urban environments.


---

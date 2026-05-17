# ≡ƒî┐ NammaHasiru

> **α▓¿α▓«α│ìα▓« α▓╣α▓╕α▓┐α▓░α│ü** ΓÇö *Our Greenery* | A smart plant care & garden management Android app

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=for-the-badge&logo=android&logoColor=white"/>
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white"/>
  <img src="https://img.shields.io/badge/Firebase-Firestore-FFCA28?style=for-the-badge&logo=firebase&logoColor=black"/>
  <img src="https://img.shields.io/badge/Min%20SDK-24-informational?style=for-the-badge"/>
</p>

---

## ≡ƒôû About

**NammaHasiru** (meaning *"Our Greenery"* in Kannada) is a feature-rich Android application designed to help gardening enthusiasts manage, track, and care for their plants. With AI-powered plant identification, real-time mapping, and smart notifications, NammaHasiru brings your garden to the digital world.

---

## Γ£¿ Features

| Feature | Description |
|---|---|
| ≡ƒî▒ **Plant Management** | Add, track, and manage all your plants with photos and details |
| ≡ƒù║∩╕Å **Garden Map** | Visualize your garden layout using Google Maps integration |
| ≡ƒñû **AI Plant Identification** | Identify plants and get care tips powered by AI |
| ≡ƒôè **Stats & Analytics** | View growth statistics and plant health insights |
| ≡ƒöö **Smart Notifications** | Timely reminders for watering, fertilizing, and more |
| ≡ƒæñ **User Profiles** | Personalized profiles with photo support |
| ΓÜÖ∩╕Å **Settings** | Customize app preferences and notification schedules |
| ≡ƒöÉ **Authentication** | Secure login, registration & password recovery via Firebase |

---

## ≡ƒÅù∩╕Å Tech Stack

- **Language**: Kotlin
- **Architecture**: MVVM (Model-View-ViewModel)
- **Database**: Firebase Firestore
- **Authentication**: Firebase Auth
- **Maps**: Google Maps SDK for Android
- **AI Integration**: Custom AI module for plant identification
- **Background Tasks**: WorkManager
- **UI**: Material Design 3 with custom animations (floating leaves effect)
- **Navigation**: Navigation Component with Bottom Nav + Navigation Drawer

---

## ≡ƒô▒ Screens

- **Splash Screen** ΓÇö Animated app introduction
- **Onboarding** ΓÇö First-time user walkthrough
- **Login / Register / Forgot Password** ΓÇö Full auth flow
- **Home** ΓÇö Dashboard with plant overview
- **Add Plant** ΓÇö Camera + form to add a new plant
- **Map** ΓÇö Location-based garden mapping
- **Stats** ΓÇö Plant growth & health analytics
- **Notifications** ΓÇö Scheduled care reminders
- **Profile** ΓÇö User profile management
- **Settings** ΓÇö App configuration

---

## ≡ƒÜÇ Getting Started

### Prerequisites

- Android Studio **Hedgehog** or newer
- Android SDK **24+** (min), **34** (target)
- A Firebase project with **Firestore** & **Authentication** enabled
- A **Google Maps API Key**

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/Sumanth724/NammaHasiru.git
   cd NammaHasiru
   ```

2. **Add your `google-services.json`**
   - Download from your Firebase console
   - Place it in `app/`

3. **Configure API Keys**
   - Open (or create) `local.properties` in the project root
   - Add your Google Maps key:
     ```properties
     MAPS_API_KEY=YOUR_GOOGLE_MAPS_API_KEY_HERE
     ```

4. **Build & Run**
   - Open in Android Studio
   - Sync Gradle and run on an emulator or physical device

---

## ≡ƒôé Project Structure

```
NammaHasiru/
Γö£ΓöÇΓöÇ app/
Γöé   ΓööΓöÇΓöÇ src/main/
Γöé       Γö£ΓöÇΓöÇ java/com/nammahasiru/app/
Γöé       Γöé   Γö£ΓöÇΓöÇ ai/            # AI plant identification module
Γöé       Γöé   Γö£ΓöÇΓöÇ data/          # Repositories & data models
Γöé       Γöé   Γö£ΓöÇΓöÇ ui/            # Fragments (Home, Map, Stats, Profile, etc.)
Γöé       Γöé   Γö£ΓöÇΓöÇ viewmodel/     # ViewModels (MVVM)
Γöé       Γöé   Γö£ΓöÇΓöÇ worker/        # WorkManager background tasks
Γöé       Γöé   Γö£ΓöÇΓöÇ MainActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ SplashActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ OnboardingActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ LoginActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ RegisterActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ ForgotPasswordActivity.kt
Γöé       Γöé   Γö£ΓöÇΓöÇ ProfileActivity.kt
Γöé       Γöé   ΓööΓöÇΓöÇ SettingsActivity.kt
Γöé       Γö£ΓöÇΓöÇ res/               # Layouts, drawables, animations, menus
Γöé       ΓööΓöÇΓöÇ AndroidManifest.xml
ΓööΓöÇΓöÇ preview/                   # App preview assets
```

---

## ≡ƒöÆ Permissions Used

| Permission | Purpose |
|---|---|
| `ACCESS_FINE_LOCATION` | Precise garden location for map |
| `ACCESS_COARSE_LOCATION` | Approximate location fallback |
| `CAMERA` | Capture plant photos |
| `READ/WRITE_EXTERNAL_STORAGE` | Save & access plant images |
| `POST_NOTIFICATIONS` | Send care reminder notifications |
| `INTERNET` | Firebase sync, Maps tiles, AI APIs |
| `ACCESS_NETWORK_STATE` | Network connectivity checks |

---

## ≡ƒñ¥ Contributing

Contributions, issues, and feature requests are welcome!

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## ≡ƒæ¿ΓÇì≡ƒÆ╗ Developer

**Sumanth Kumar**
- GitHub: [@Sumanth724](https://github.com/Sumanth724)

---

## ≡ƒôä License

This project is for educational and personal use. All rights reserved ┬⌐ 2026 Sumanth Kumar.

---

<p align="center">Made with Γ¥ñ∩╕Å and ≡ƒî┐ for plant lovers everywhere</p>

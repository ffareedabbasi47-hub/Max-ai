# MAX JARVIS HUD - CI/CD & GitHub Actions Setup

This repository includes an automated GitHub Actions workflow (`.github/workflows/android.yml`) that compiles and generates downloadable Android APK artifacts for every push or pull request.

---

## 🔑 GitHub Repository Secrets Configuration

To securely inject credentials and API keys during CI/CD builds without hardcoding secrets in source code, configure the following **Repository Secrets** in GitHub.

### Step-by-Step Instructions

1. Go to your repository page on **GitHub** (`https://github.com/your-username/your-repo-name`).
2. Click on the **Settings** tab at the top of the repository.
3. In the left navigation menu, scroll down to the **Security** section and click **Secrets and variables** > **Actions**.
4. Click the green **New repository secret** button for each secret listed below.

---

### Required & Optional Secrets

| Secret Name | Description | Required? |
| :--- | :--- | :--- |
| `GEMINI_API_KEY` | Your Google Gemini API Key for AI features. | **Recommended** (Defaults to fallback if missing) |
| `KEYSTORE_PATH` | Base64 encoded debug/release keystore content or path reference. | **Optional** (Debug keystore generated automatically if absent) |
| `STORE_PASSWORD` | Keystore password for release signing configuration. | **Optional** |
| `KEY_PASSWORD` | Key alias password for release signing configuration. | **Optional** |

---

## 🛠️ How it Works

- **Fallback Safe**: If `GEMINI_API_KEY` is not provided in GitHub Secrets, the build configuration automatically falls back to a safe placeholder (`FALLBACK_KEY_VALID`) to prevent compilation errors in `BuildConfig.java`.
- **Automatic Build & Artifact Export**: On every push to any branch or manual workflow dispatch, GitHub Actions builds the debug APK and attaches it as a downloadable artifact named `MAX-JARVIS-Debug-APK`.

---

## 🚀 Triggering a Build Manually

1. Go to the **Actions** tab on GitHub.
2. Select **Build MAX Android APK** from the left workflow menu.
3. Click **Run workflow** > **Run workflow**.

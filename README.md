# Project Athena

Project Athena is a private, local catalog for large PDF and EPUB collections. It
scans ebook folders in place, lets you search by title or author, and reports
exact or possible duplicate files.

This first release is intentionally read-only: Athena does not rename, move,
delete, or upload ebooks.

## Windows features

- Scan multiple ebook folders and their subfolders
- Read PDF and EPUB title, author, and ISBN metadata
- Re-scan incrementally without reprocessing unchanged files
- Search by title or author and filter by file format
- Detect exact copies with SHA-256 file fingerprints
- Flag possible alternate files or editions by normalized title and author
- Keep all catalog data in a local SQLite database

## Run on Windows 11

Install these prerequisites:

- [Python 3.12 or newer](https://www.python.org/downloads/)
- [Node.js 22 or newer](https://nodejs.org/)
- [uv](https://docs.astral.sh/uv/getting-started/installation/)

Open PowerShell in the project folder and run:

```powershell
uv sync
npm install
npm run dev
```

Open [http://127.0.0.1:43181](http://127.0.0.1:43181). The development command
starts both the local API and web interface.

For a production-style local run:

```powershell
npm run build
npm start
```

Then open [http://127.0.0.1:43182](http://127.0.0.1:43182).

## Local data and privacy

The SQLite catalog is stored at:

```text
%USERPROFILE%\.project-athena\athena.db
```

Set `ATHENA_DATA_DIR` before starting Athena to use another location. Ebook
files stay in their original folders. The server binds to `127.0.0.1` by
default, so it is available only on the laptop.

## Duplicate reports

- **Exact copies** have identical file contents, regardless of filename.
- **Possible matches** have matching title and author metadata but different
  file contents. They may be alternate formats or editions and require review.

No file operation is performed from either report.

## Android app

The native app in [`android/`](android/) is the phone-first reading inbox:

Download the current test build:
[`project-athena-v0.2.0-debug.zip`](releases/project-athena-v0.2.0-debug.zip)

- Select a Downloads or ebook folder with Android's system folder picker
- Scan PDF and EPUB files without broad storage permissions
- Search locally by title or author
- Detect exact copies and likely alternate editions
- Edit incorrect title or author metadata
- Open an ebook directly in Moon+ Reader
- Save selected Moon+ passages through **Share → Project Athena**

The catalog and captured passages stay in the app's private SQLite database.
Folder access is read-only, and Athena never deletes or moves an ebook.

### Build and install

Open `android/` in Android Studio, or use JDK 17 or newer and an Android SDK:

```bash
cd android
./gradlew assembleDebug
```

Install the generated APK on a USB-connected phone:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Development checks

```powershell
uv run pytest
npm run lint
npm run build
```

```bash
cd android
./gradlew testDebugUnitTest lintDebug assembleDebug
```

## Planned later

Laptop pairing, Wi-Fi transfer, text extraction, topic search, and
Ollama-powered summaries are intentionally outside this Android test build.

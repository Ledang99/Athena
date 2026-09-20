# Project Athena

Project Athena is a private, local catalog for large PDF and EPUB collections. It
scans ebook folders in place, lets you search by title or author, and reports
exact or possible duplicate files.

This first release is intentionally read-only: Athena does not rename, move,
delete, or upload ebooks.

## Current features

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

## Development checks

```powershell
uv run pytest
npm run lint
npm run build
```

## Planned later

Text extraction, topic search, Ollama-powered summaries, and an Android client
are intentionally outside this first milestone.
# React + TypeScript + Vite

This template provides a minimal setup to get React working in Vite with HMR and some Oxlint rules.

Currently, two official plugins are available:

- [@vitejs/plugin-react](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react) uses [Oxc](https://oxc.rs)
- [@vitejs/plugin-react-swc](https://github.com/vitejs/vite-plugin-react/blob/main/packages/plugin-react-swc) uses [SWC](https://swc.rs/)

## React Compiler

The React Compiler is not enabled on this template because of its impact on dev & build performances. To add it, see [this documentation](https://react.dev/learn/react-compiler/installation).

## Expanding the Oxlint configuration

If you are developing a production application, we recommend enabling type-aware lint rules by installing `oxlint-tsgolint` and editing `.oxlintrc.json`:

```json
{
  "$schema": "./node_modules/oxlint/configuration_schema.json",
  "plugins": ["react", "typescript", "oxc"],
  "options": {
    "typeAware": true
  },
  "rules": {
    "react/rules-of-hooks": "error",
    "react/only-export-components": ["warn", { "allowConstantExport": true }]
  }
}
```

See the [Oxlint rules documentation](https://oxc.rs/docs/guide/usage/linter/rules) for the full list of rules and categories.

# Continue Eclipse Plugin

This module ports Continue's developer experience to Eclipse by combining
components from the existing VS Code/JetBrains extensions with Eclipse UI
patterns from the AssistAI plugin. The result is an SWT-based view that embeds
the React GUI, talks to the Continue core binary, and implements the IDE
protocols that the core expects.

## Architecture

| Piece | Source | Notes |
| --- | --- | --- |
| GUI | `gui/` (same bundle used by VS Code + JetBrains) | Served inside an SWT `Browser` with the JetBrains-compatible `window.postIntellijMessage` bridge. |
| Core runtime | `binary/continue-binary` | The Continue core binary runs as a child process; IPC mirrors the JetBrains `CoreMessenger`. |
| IDE bridge | Eclipse APIs inspired by `eclipse-chatgpt-plugin` | Provides workspace/file operations, git helpers, notifications, etc. |

The `com.continuedev.eclipse.continue.plugin` bundle contains:

- `BrowserBridge` – injects the JS bridge expected by `IdeMessenger.tsx`.
- `CoreMessenger` – reuses the JSON protocol to talk to the core binary.
- `EclipseIde`/`IdeProtocolClient` – Eclipse implementations of the IDE protocol (read/write files, open editors, run git commands, emit toasts, …).
- `ContinueView` – declares the Eclipse view and wires the SWT browser.

## Populating runtime assets

1. **Build the core binary** (all platforms you intend to ship):
   ```bash
   cd binary
   npm install
   npm run build
   ```
   Copy the produced `binary/bin/<target>` folders into
   `extensions/eclipse/plugins/com.continuedev.eclipse.continue.plugin/core/<target>`.

2. **Build the GUI** (or reuse an existing Continue release build):
   ```bash
   cd gui
   npm install
   npm run build
   cp -R dist/* ../extensions/eclipse/plugins/com.continuedev.eclipse.continue.plugin/webview
   ```
   During development you can skip this step and point Eclipse at a running Vite
   server by exporting `CONTINUE_GUI_URL=http://localhost:5173`.

3. **Launch Eclipse for testing**:
   - Import `extensions/eclipse/plugins/com.continuedev.eclipse.continue.plugin`
     as an Eclipse plug-in project.
   - Run an Eclipse Application launch configuration and open *Window →
     Show View → Continue*.

Packaging into an update site follows the same pattern as the
`eclipse-chatgpt-plugin` project: create a feature referencing the plug-in,
generate the `site/` artifacts with PDE, and publish.

## Third-party notices

The plug-in bundles Jackson Core/Databind/Annotations (Apache 2.0) inside
`plugins/com.continuedev.eclipse.continue.plugin/lib`. These jars are the same
versions used by the AssistAI plugin and are required to parse the Continue JSON
protocol.

## Current limitations / next steps

- Only a subset of IDE protocol calls are implemented (file I/O, git metadata,
  and basic commands). Additional handlers (problems, inline completions,
  context providers, etc.) can be ported incrementally.
- The SWT browser does not expose developer tools yet. A future iteration could
  embed the Microsoft Edge WebView2 driver on Windows to unlock devtools and
  clipboard APIs.
- Secrets storage and WorkOS authentication are stubbed. Hook these into Eclipse
  secure storage or reuse Continue's WorkOS flow when available.
- The update site + automated packaging scripts still need to be added so the
  plugin can be produced via CI/CD alongside the VS Code and JetBrains builds.

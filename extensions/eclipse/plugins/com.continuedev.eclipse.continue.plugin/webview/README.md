# Continue GUI Assets

This bundle expects the compiled Continue web client (the same Vite build that
ships with the VS Code and JetBrains extensions) to live inside this folder.

During development you can point the plugin at a running Vite dev server by
setting the `CONTINUE_GUI_URL` environment variable before launching Eclipse:

```
export CONTINUE_GUI_URL=http://localhost:5173
```

For production builds run the GUI build from the repository root and copy the
result into this directory:

```
cd gui
npm install
npm run build
cp -R dist/* ../extensions/eclipse/plugins/com.continuedev.eclipse.continue.plugin/webview
```

Only `index.html` and the generated `assets` directory need to be copied. The
plugin serves files from this folder via the embedded SWT Browser.

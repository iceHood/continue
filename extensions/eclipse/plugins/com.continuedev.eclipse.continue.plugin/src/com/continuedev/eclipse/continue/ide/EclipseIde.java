package com.continuedev.eclipse.continue.ide;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.eclipse.core.filesystem.EFS;
import org.eclipse.core.filesystem.IFileStore;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.swt.dnd.Clipboard;
import org.eclipse.swt.dnd.TextTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.widgets.Display;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.ide.FileStoreEditorInput;
import org.eclipse.ui.ide.IDE;

import com.continuedev.eclipse.continue.service.ContinuePluginService;
import com.continuedev.eclipse.continue.util.Log;
import com.continuedev.eclipse.continue.util.PlatformUtils;

/**
 * Implements a subset of the IDE bridge Continue expects. The methods are
 * intentionally minimal to keep the first Eclipse integration lightweight. Many
 * operations simply rely on standard filesystem calls and shell commands.
 */
public class EclipseIde {
    private final ContinuePluginService service;

    public EclipseIde(ContinuePluginService service) {
        this.service = service;
    }

    public Map<String, Object> getIdeInfo() {
        Map<String, Object> info = new HashMap<>();
        info.put("ideType", "jetbrains"); // Reuse JetBrains channel for now
        info.put("name", "Eclipse");
        info.put("version", PlatformUtils.getEclipseVersion());
        info.put("remoteName", "");
        info.put("extensionVersion", PlatformUtils.getPluginVersion());
        info.put("isPrerelease", false);
        return info;
    }

    public Map<String, Object> getIdeSettings() {
        Map<String, Object> settings = new HashMap<>();
        settings.put("remoteConfigServerUrl", null);
        settings.put("remoteConfigSyncPeriod", 60000);
        settings.put("userToken", "");
        settings.put("continueTestEnvironment", "none");
        settings.put("pauseCodebaseIndexOnStart", false);
        return settings;
    }

    public List<String> getWorkspaceDirs() {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();
        List<String> dirs = new ArrayList<>();
        for (IProject project : projects) {
            if (!project.isOpen()) {
                continue;
            }
            var location = project.getLocation();
            if (location != null) {
                dirs.add(location.toFile().getAbsolutePath());
            }
        }
        return dirs;
    }

    public boolean fileExists(String fileUri) {
        return Files.exists(resolvePath(fileUri));
    }

    public String readFile(String fileUri) throws IOException {
        Path path = resolvePath(fileUri);
        return Files.readString(path);
    }

    public String readRangeInFile(String fileUri, int startLine, int endLine) throws IOException {
        List<String> lines = Files.readAllLines(resolvePath(fileUri), StandardCharsets.UTF_8);
        startLine = Math.max(0, Math.min(lines.size(), startLine));
        endLine = Math.max(startLine, Math.min(lines.size(), endLine));
        return lines.subList(startLine, endLine).stream().collect(Collectors.joining(System.lineSeparator()));
    }

    public void writeFile(String fileUri, String contents) throws IOException {
        Files.writeString(resolvePath(fileUri), contents, StandardCharsets.UTF_8);
    }

    public void openFile(String fileUri) {
        runOnUiThread(
            () -> {
                try {
                    IWorkbenchPage page = getActivePage();
                    if (page == null) {
                        return;
                    }
                    IFileStore store = EFS.getLocalFileSystem().getStore(resolvePath(fileUri).toFile().toURI());
                    IDE.openEditorOnFileStore(page, store);
                } catch (PartInitException e) {
                    Log.error("Failed to open editor for " + fileUri, e);
                }
            });
    }

    public List<String> getOpenFiles() {
        return runOnUiThread(
            () -> {
                IWorkbenchPage page = getActivePage();
                if (page == null) {
                    return Collections.<String>emptyList();
                }
                List<String> open = new ArrayList<>();
                for (IEditorReference ref : page.getEditorReferences()) {
                    try {
                        IEditorInput input = ref.getEditorInput();
                        if (input instanceof FileStoreEditorInput fileStoreInput) {
                            open.add(fileStoreInput.getURI().getPath());
                        }
                    } catch (PartInitException ignored) {
                    }
                }
                return open;
            });
    }

    public Map<String, Object> getCurrentFile() {
        return runOnUiThread(
            () -> {
                IWorkbenchPage page = getActivePage();
                if (page == null) {
                    return null;
                }
                IEditorPart editor = page.getActiveEditor();
                if (editor == null) {
                    return null;
                }
                IEditorInput input = editor.getEditorInput();
                if (!(input instanceof FileStoreEditorInput fileInput)) {
                    return null;
                }
                String contents = "";
                try {
                    contents = readFile(fileInput.getURI().toString());
                } catch (IOException e) {
                    Log.error("Failed to read contents for " + fileInput.getURI(), e);
                }
                Map<String, Object> result = new HashMap<>();
                result.put("isUntitled", false);
                result.put("path", fileInput.getURI().toString());
                result.put("contents", contents);
                return result;
            });
    }

    public String getUniqueId() {
        return PlatformUtils.getMachineId();
    }

    public boolean isTelemetryEnabled() {
        return false;
    }

    public boolean isWorkspaceRemote() {
        return false;
    }

    public String getTerminalContents() {
        return "";
    }

    public void copyToClipboard(String text) {
        runOnUiThread(
            () -> {
                Clipboard clipboard = new Clipboard(Display.getDefault());
                try {
                    clipboard.setContents(new Object[] {text}, new Transfer[] {TextTransfer.getInstance()});
                } finally {
                    clipboard.dispose();
                }
            });
    }

    public List<String> getDiff(boolean includeUnstaged) {
        Path repo = locatePrimaryWorkspace();
        if (repo == null) {
            return Collections.emptyList();
        }
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("diff");
        if (!includeUnstaged) {
            command.add("--staged");
        }
        return runCommand(command, repo);
    }

    public List<String> runCommand(List<String> command, Path cwd) {
        ProcessBuilder builder = new ProcessBuilder(command);
        if (cwd != null) {
            builder.directory(cwd.toFile());
        }
        try {
            Process process = builder.start();
            process.waitFor();
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                .lines()
                .collect(Collectors.toList());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Log.error("Command interrupted " + command, e);
        } catch (IOException e) {
            Log.error("Failed running command " + command, e);
        }
        return Collections.emptyList();
    }

    public List<String> runCommand(String command) {
        boolean windows = PlatformUtils.getOS() == PlatformUtils.OS.WINDOWS;
        List<String> shellCommand = new ArrayList<>();
        if (windows) {
            shellCommand.add("cmd.exe");
            shellCommand.add("/c");
        } else {
            shellCommand.add("bash");
            shellCommand.add("-lc");
        }
        shellCommand.add(command);
        return runCommand(shellCommand, locatePrimaryWorkspace());
    }

    public String getBranch(String dir) {
        List<String> command = List.of("git", "-C", dir, "rev-parse", "--abbrev-ref", "HEAD");
        List<String> output = runCommand(command, null);
        return output.isEmpty() ? "" : output.get(0);
    }

    public String getRepoName(String dir) {
        List<String> command = List.of("git", "-C", dir, "remote", "get-url", "origin");
        List<String> output = runCommand(command, null);
        if (output.isEmpty()) {
            return null;
        }
        String url = output.get(0).replace(".git", "");
        String[] parts = url.split("/");
        if (parts.length >= 2) {
            return parts[parts.length - 2] + "/" + parts[parts.length - 1];
        }
        return url;
    }

    public void showToast(String type, String message) {
        runOnUiThread(
            () -> {
                var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
                if (window == null) {
                    return;
                }
                switch (type) {
                    case "error" -> window.getShell().getDisplay().beep();
                    case "warning", "info" -> {
                    }
                }
                window.getShell().getDisplay().asyncExec(() -> {
                    window.getShell().setMessage(MessageFormat.format("[{0}] {1}", type.toUpperCase(Locale.ROOT), message));
                });
            });
    }

    public Map<String, Object> onLoadPayload() {
        Map<String, Object> payload = new HashMap<>();
        payload.put("windowId", service.getWindowId());
        payload.put("workspacePaths", getWorkspaceDirs());
        payload.put("vscMachineId", getUniqueId());
        payload.put("vscMediaUrl", service.getMediaBaseUrl());
        payload.put("loadedAt", Instant.now().toString());
        return payload;
    }

    private Path resolvePath(String fileUri) {
        if (fileUri == null) {
            return Paths.get("");
        }
        try {
            if (fileUri.startsWith("file:")) {
                return Paths.get(new URI(fileUri));
            }
        } catch (URISyntaxException ignored) {
        }
        return Paths.get(fileUri);
    }

    private IWorkbenchPage getActivePage() {
        var window = PlatformUI.getWorkbench().getActiveWorkbenchWindow();
        return window != null ? window.getActivePage() : null;
    }

    private <T> T runOnUiThread(Callable<T> callable) {
        if (Display.getCurrent() != null) {
            try {
                return callable.call();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> error = new AtomicReference<>();
        Display.getDefault()
            .syncExec(
                () -> {
                    try {
                        result.set(callable.call());
                    } catch (Exception e) {
                        error.set(new RuntimeException(e));
                    }
                });
        if (error.get() != null) {
            throw error.get();
        }
        return result.get();
    }

    private void runOnUiThread(Runnable runnable) {
        if (Display.getCurrent() != null) {
            runnable.run();
            return;
        }
        Display.getDefault().asyncExec(runnable);
    }

    private Path locatePrimaryWorkspace() {
        List<String> dirs = getWorkspaceDirs();
        return dirs.isEmpty() ? null : Paths.get(dirs.get(0));
    }
}

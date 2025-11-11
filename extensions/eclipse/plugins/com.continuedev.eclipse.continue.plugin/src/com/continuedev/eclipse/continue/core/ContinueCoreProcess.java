package com.continuedev.eclipse.continue.core;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.continuedev.eclipse.continue.util.Log;
import com.continuedev.eclipse.continue.util.PlatformUtils;

/**
 * Wraps the Continue core binary. The binary is built via /binary and copied to
 * this plugin's {@code core/<platform>} directory. Communication happens over
 * stdin/stdout using the same JSON protocol as the VS Code and JetBrains
 * extensions.
 */
public class ContinueCoreProcess {
    private final Process process;

    public ContinueCoreProcess() throws IOException {
        File executable = PlatformUtils.resolveCoreBinary().toFile();
        ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath());
        builder.directory(executable.getParentFile());
        this.process = builder.start();
        this.process.onExit().thenRun(() -> Log.warn("Continue core process exited"));
    }

    public InputStream getInputStream() {
        return process.getInputStream();
    }

    public OutputStream getOutputStream() {
        return process.getOutputStream();
    }

    public InputStream getErrorStream() {
        return process.getErrorStream();
    }

    public void destroy() {
        process.destroy();
    }
}

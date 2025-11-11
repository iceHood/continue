package com.continuedev.eclipse.continue.util;

import java.io.File;
import java.io.IOException;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.UUID;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Platform;
import org.osgi.framework.Bundle;

import com.continuedev.eclipse.continue.Activator;

public final class PlatformUtils {

    public enum OS {
        MAC,
        WINDOWS,
        LINUX
    }

    private PlatformUtils() {}

    public static OS getOS() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("mac") || name.contains("darwin")) {
            return OS.MAC;
        } else if (name.contains("win")) {
            return OS.WINDOWS;
        }
        return OS.LINUX;
    }

    public static String getTargetTriple() {
        String osPart = switch (getOS()) {
            case MAC -> "darwin";
            case WINDOWS -> "win32";
            case LINUX -> "linux";
        };

        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        String archPart;
        if (arch.contains("aarch64") || (arch.contains("arm") && arch.contains("64"))) {
            archPart = "arm64";
        } else if (arch.contains("x86") || arch.contains("amd64")) {
            archPart = "x64";
        } else {
            archPart = "x64";
        }

        return osPart + "-" + archPart;
    }

    public static Path resolveCoreBinary() throws IOException {
        Bundle bundle = Activator.getDefault().getBundle();
        String suffix = getOS() == OS.WINDOWS ? ".exe" : "";
        String relative = String.format("core/%s/continue-binary%s", getTargetTriple(), suffix);
        URL entry = bundle.getEntry(relative);
        if (entry == null) {
            throw new IOException(
                "Continue core binary not found. Expected: " + relative + ". " +
                "Run `npm run build` inside /binary and copy the bin output into extensions/eclipse/plugins/com.continuedev.eclipse.continue.plugin/core.");
        }
        URL fileURL = FileLocator.toFileURL(entry);
        return Paths.get(new File(fileURL.getPath()).getAbsolutePath());
    }

    public static String generateWindowId() {
        return UUID.randomUUID().toString();
    }

    public static String getMachineId() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "unknown";
            }
            for (NetworkInterface nif : Collections.list(interfaces)) {
                byte[] mac = nif.getHardwareAddress();
                if (mac == null || mac.length == 0) {
                    continue;
                }
                StringBuilder builder = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    builder.append(String.format(Locale.US, "%02X", mac[i]));
                    if (i < mac.length - 1) {
                        builder.append("-");
                    }
                }
                return builder.toString();
            }
        } catch (SocketException ignored) {
        }
        return "unknown";
    }

    public static String getPluginVersion() {
        Bundle bundle = Activator.getDefault().getBundle();
        return bundle.getVersion().toString();
    }

    public static String getEclipseVersion() {
        return Platform.getBundle("org.eclipse.platform").getVersion().toString();
    }
}

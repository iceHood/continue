package com.continuedev.eclipse.continue.util;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;

import com.continuedev.eclipse.continue.Activator;

public final class Log {

    private Log() {}

    public static void info(String message) {
        log(IStatus.INFO, message, null);
    }

    public static void warn(String message) {
        log(IStatus.WARNING, message, null);
    }

    public static void error(String message, Throwable error) {
        log(IStatus.ERROR, message, error);
    }

    private static void log(int severity, String message, Throwable error) {
        Activator activator = Activator.getDefault();
        if (activator == null) {
            return;
        }
        ILog log = activator.getLog();
        log.log(new Status(severity, Activator.PLUGIN_ID, message, error));
    }
}

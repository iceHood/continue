package com.continuedev.eclipse.continue;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

import java.io.IOException;

import com.continuedev.eclipse.continue.service.ContinuePluginService;
import com.continuedev.eclipse.continue.util.Log;

/**
 * Entry point for the Continue Eclipse bundle. Responsible for wiring the core
 * service that manages the GUI bridge, protocol handlers, and the core binary
 * process.
 */
public class Activator extends AbstractUIPlugin {

    public static final String PLUGIN_ID = "com.continuedev.eclipse.continue.plugin";

    private static Activator instance;
    private ContinuePluginService pluginService;

    public Activator() {}

    @Override
    public void start(BundleContext context) throws Exception {
        super.start(context);
        instance = this;
        try {
            pluginService = new ContinuePluginService(this);
            pluginService.start();
        } catch (IOException e) {
            Log.error("Failed to start Continue core service", e);
        }
    }

    @Override
    public void stop(BundleContext context) throws Exception {
        if (pluginService != null) {
            pluginService.stop();
            pluginService = null;
        }
        instance = null;
        super.stop(context);
    }

    public static Activator getDefault() {
        return instance;
    }

    public ContinuePluginService getContinueService() {
        return pluginService;
    }
}

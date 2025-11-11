package com.continuedev.eclipse.continue.service;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.eclipse.swt.browser.Browser;

import com.continuedev.eclipse.continue.Activator;
import com.continuedev.eclipse.continue.bridge.BrowserBridge;
import com.continuedev.eclipse.continue.bridge.BrowserMessage;
import com.continuedev.eclipse.continue.constants.MessageTypes;
import com.continuedev.eclipse.continue.core.CoreMessenger;
import com.continuedev.eclipse.continue.ide.IdeProtocolClient;
import com.continuedev.eclipse.continue.util.JsonUtils;
import com.continuedev.eclipse.continue.util.Log;
import com.continuedev.eclipse.continue.util.PlatformUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Central service that keeps the browser UI, the core binary, and the Eclipse
 * IDE bridge in sync.
 */
public class ContinuePluginService implements AutoCloseable {
    private final Activator activator;
    private final String windowId;

    private final BrowserBridge browserBridge;
    private final IdeProtocolClient ideProtocolClient;
    private final CoreMessenger coreMessenger;

    private String mediaBaseUrl = "";

    public ContinuePluginService(Activator activator) throws IOException {
        this.activator = activator;
        this.windowId = PlatformUtils.generateWindowId();
        this.browserBridge = new BrowserBridge(this::handleBrowserMessage);
        this.ideProtocolClient = new IdeProtocolClient(this);
        this.coreMessenger = new CoreMessenger(ideProtocolClient, browserBridge);
    }

    public void start() {}

    public void stop() {
        close();
    }

    @Override
    public void close() {
        try {
            coreMessenger.close();
        } catch (Exception ignored) {
        }
        browserBridge.dispose();
    }

    public void registerBrowser(Browser browser) {
        browserBridge.attach(browser);
    }

    public String resolveGuiUrl() throws IOException {
        String url = browserBridge.resolveInitialUrl();
        int idx = url.lastIndexOf('/');
        if (idx > 0) {
            mediaBaseUrl = url.substring(0, idx);
        } else {
            mediaBaseUrl = url;
        }
        return url;
    }

    public String getWindowId() {
        return windowId;
    }

    public String getMediaBaseUrl() {
        return mediaBaseUrl;
    }

    private void handleBrowserMessage(BrowserMessage message) {
        String type = message.messageType();

        if (MessageTypes.PASS_THROUGH_TO_CORE.contains(type)) {
            coreMessenger.sendToCore(type, message.data(), message.messageId(), null);
            return;
        }

        if (MessageTypes.IDE_MESSAGE_TYPES.contains(type) || type.startsWith("jetbrains/")) {
            ideProtocolClient.handleWebviewMessage(
                message,
                response -> {
                    Map<String, Object> envelope = new HashMap<>();
                    envelope.put("status", "success");
                    envelope.put("content", response);
                    envelope.put("done", true);
                    browserBridge.sendToWebview(type, envelope, message.messageId() != null ? message.messageId() : UUID.randomUUID().toString());
                });
            return;
        }

        Log.warn("Unknown message from Continue UI: " + type);
    }
}

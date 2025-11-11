package com.continuedev.eclipse.continue.bridge;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.Path;
import org.eclipse.swt.SWT;
import org.eclipse.swt.browser.Browser;
import org.eclipse.swt.browser.BrowserFunction;
import org.eclipse.swt.browser.ProgressAdapter;
import org.eclipse.swt.browser.ProgressEvent;
import org.eclipse.swt.widgets.Display;
import org.osgi.framework.Bundle;

import com.continuedev.eclipse.continue.Activator;
import com.continuedev.eclipse.continue.util.JsonUtils;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Handles the SWT Browser wiring so the React UI can talk to the Java side
 * using the {@code window.postIntellijMessage} API that already exists for the
 * IntelliJ extension.
 */
public class BrowserBridge {
    private Browser browser;
    private Consumer<BrowserMessage> messageHandler;
    private BrowserFunction bridgeFunction;

    public BrowserBridge(Consumer<BrowserMessage> handler) {
        this.messageHandler = Objects.requireNonNull(handler);
    }

    public void attach(Browser browser) {
        this.browser = browser;
        this.bridgeFunction = new BrowserFunction(browser, "continueBridge") {
            @Override
            public Object function(Object[] arguments) {
                if (arguments.length == 0 || !(arguments[0] instanceof String)) {
                    return null;
                }
                try {
                    JsonNode node = JsonUtils.parseTree((String) arguments[0]);
                    BrowserMessage message =
                        new BrowserMessage(node.path("messageType").asText(), node.path("messageId").asText(null), node.path("data"));
                    messageHandler.accept(message);
                } catch (IOException e) {
                    com.continuedev.eclipse.continue.util.Log.error("Failed to parse message from webview", e);
                }
                return null;
            }
        };

        browser.addProgressListener(new ProgressAdapter() {
            @Override
            public void completed(ProgressEvent event) {
                injectBridgeScript();
            }
        });
    }

    public void dispose() {
        if (bridgeFunction != null) {
            bridgeFunction.dispose();
            bridgeFunction = null;
        }
        browser = null;
    }

    public void sendToWebview(String messageType, Object data, String messageId) {
        if (browser == null || browser.isDisposed()) {
            return;
        }
        Map<String, Object> payload =
            Map.of("messageType", messageType, "messageId", messageId, "data", data);
        String script = MessageFormat.format(
            Locale.ROOT,
            "window.postMessage({0}, '*');",
            JsonUtils.toJson(payload));
        Display.getDefault().asyncExec(() -> browser.execute(script));
    }

    private void injectBridgeScript() {
        if (browser == null || browser.isDisposed()) {
            return;
        }
        String script =
            """
            window.postIntellijMessage = function(messageType, data, messageId) {
                const payload = JSON.stringify({messageType, data, messageId});
                window.continueBridge(payload);
            };
            localStorage.setItem("ide", '"jetbrains"');
            window.ide = "jetbrains";
            """;
        browser.execute(script);
    }

    public String resolveInitialUrl() throws IOException {
        String override = System.getenv("CONTINUE_GUI_URL");
        if (override != null && !override.isBlank()) {
            return override;
        }
        Bundle bundle = Activator.getDefault().getBundle();
        var entry = bundle.getEntry("webview/index.html");
        if (entry == null) {
            throw new IOException("Continue GUI assets were not found. Build the web client and copy it into plugins/com.continuedev.eclipse.continue.plugin/webview.");
        }
        return FileLocator.toFileURL(entry).toExternalForm();
    }
}

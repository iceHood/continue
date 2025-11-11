package com.continuedev.eclipse.continue.core;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import com.continuedev.eclipse.continue.bridge.BrowserBridge;
import com.continuedev.eclipse.continue.constants.MessageTypes;
import com.continuedev.eclipse.continue.ide.IdeProtocolClient;
import com.continuedev.eclipse.continue.util.JsonUtils;
import com.continuedev.eclipse.continue.util.Log;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Shared messenger between the Continue core process and the SWT webview.
 * Mirrors the responsibilities of VsCodeMessenger/IntelliJ CoreMessenger.
 */
public class CoreMessenger implements AutoCloseable {
    private final IdeProtocolClient ideProtocolClient;
    private final BrowserBridge browserBridge;
    private final CoreProcessHandler processHandler;
    private final Map<String, Consumer<JsonNode>> responseListeners = new ConcurrentHashMap<>();

    public CoreMessenger(IdeProtocolClient ideProtocolClient, BrowserBridge browserBridge) throws IOException {
        this.ideProtocolClient = ideProtocolClient;
        this.browserBridge = browserBridge;
        this.processHandler = new CoreProcessHandler(new ContinueCoreProcess(), this::handleCoreMessage);
    }

    public void sendToCore(String messageType, JsonNode payload, String messageId, Consumer<JsonNode> callback) {
        String id = messageId != null ? messageId : UUID.randomUUID().toString();
        if (callback != null) {
            responseListeners.put(id, callback);
        }
        Map<String, Object> envelope =
            Map.of("messageType", messageType, "messageId", id, "data", payload);
        processHandler.send(JsonUtils.toJson(envelope));
    }

    private void handleCoreMessage(String raw) {
        try {
            JsonNode node = JsonUtils.parseTree(raw);
            String messageType = node.path("messageType").asText();
            String messageId = node.path("messageId").asText();
            JsonNode dataNode = node.path("data");

            if (MessageTypes.IDE_MESSAGE_TYPES.contains(messageType)) {
                ideProtocolClient.handleCoreMessage(
                    node,
                    response -> {
                        Map<String, Object> envelope =
                            Map.of("messageType", messageType, "messageId", messageId, "data", response);
                        processHandler.send(JsonUtils.toJson(envelope));
                    });
            }

            if (MessageTypes.PASS_THROUGH_TO_WEBVIEW.contains(messageType)) {
                Object payload = JsonUtils.mapper().treeToValue(dataNode, Object.class);
                browserBridge.sendToWebview(messageType, payload, messageId);
            }

            Consumer<JsonNode> listener = responseListeners.get(messageId);
            if (listener != null) {
                listener.accept(dataNode);
                if (dataNode.path("done").asBoolean(false)) {
                    responseListeners.remove(messageId);
                }
            }
        } catch (Exception e) {
            Log.error("Failed handling core message: " + raw, e);
        }
    }

    @Override
    public void close() {
        processHandler.close();
    }
}

package com.continuedev.eclipse.continue.ide;

import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

import com.continuedev.eclipse.continue.bridge.BrowserMessage;
import com.continuedev.eclipse.continue.service.ContinuePluginService;
import com.continuedev.eclipse.continue.util.JsonUtils;
import com.continuedev.eclipse.continue.util.Log;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.BooleanNode;
import com.fasterxml.jackson.databind.node.NullNode;

/**
 * Routes IDE-specific protocol messages to the Eclipse runtime. The Continue
 * core process communicates using the same payloads regardless of IDE, so this
 * class mainly mirrors the JetBrains implementation.
 */
public class IdeProtocolClient {
    private final EclipseIde ide;

    public IdeProtocolClient(ContinuePluginService service) {
        this.ide = new EclipseIde(service);
    }

    public void handleWebviewMessage(BrowserMessage message, Consumer<JsonNode> respond) {
        handle(message.messageType(), message.data(), respond);
    }

    public void handleCoreMessage(JsonNode message, Consumer<JsonNode> respond) {
        handle(message.path("messageType").asText(), message.path("data"), respond);
    }

    private void handle(String messageType, JsonNode data, Consumer<JsonNode> respond) {
        try {
            switch (messageType) {
                case "jetbrains/onLoad" -> respond.accept(JsonUtils.mapper().valueToTree(ide.onLoadPayload()));
                case "jetbrains/isOSREnabled" -> respond.accept(BooleanNode.TRUE);
                case "jetbrains/getColors" -> respond.accept(JsonUtils.mapper().createObjectNode());
                case "toggleDevTools" -> respondVoid(respond); // Not supported yet
                case "showTutorial" -> respondVoid(respond);
                case "getIdeInfo" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getIdeInfo()));
                case "getIdeSettings" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getIdeSettings()));
                case "getWorkspaceDirs" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getWorkspaceDirs()));
                case "getUniqueId" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getUniqueId()));
                case "isTelemetryEnabled" -> respond.accept(BooleanNode.valueOf(ide.isTelemetryEnabled()));
                case "isWorkspaceRemote" -> respond.accept(BooleanNode.valueOf(ide.isWorkspaceRemote()));
                case "getOpenFiles" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getOpenFiles()));
                case "getCurrentFile" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getCurrentFile()));
                case "fileExists" -> respond.accept(BooleanNode.valueOf(ide.fileExists(data.path("filepath").asText())));
                case "readFile" -> respond.accept(JsonUtils.mapper().valueToTree(ide.readFile(data.path("filepath").asText())));
                case "readRangeInFile" -> respond.accept(
                        JsonUtils.mapper()
                            .valueToTree(
                                ide.readRangeInFile(
                                    data.path("filepath").asText(),
                                    data.path("range").path("start").path("line").asInt(),
                                    data.path("range").path("end").path("line").asInt())));
                case "writeFile" -> {
                    ide.writeFile(data.path("filepath").asText(), data.path("contents").asText());
                    respondVoid(respond);
                }
                case "openFile" -> {
                    ide.openFile(data.path("filepath").asText());
                    respondVoid(respond);
                }
                case "runCommand" -> {
                    List<String> output = ide.runCommand(data.path("command").asText());
                    respond.accept(JsonUtils.mapper().valueToTree(String.join(System.lineSeparator(), output)));
                }
                case "getTerminalContents" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getTerminalContents()));
                case "getDiff" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getDiff(data.path("includeUnstaged").asBoolean(true))));
                case "getBranch" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getBranch(data.path("dir").asText())));
                case "getRepoName" -> respond.accept(JsonUtils.mapper().valueToTree(ide.getRepoName(data.path("dir").asText())));
                case "showToast" -> {
                    ide.showToast(data.path("type").asText(), data.path("message").asText());
                    respondVoid(respond);
                }
                case "copyText" -> {
                    ide.copyToClipboard(data.path("text").asText());
                    respondVoid(respond);
                }
                default -> {
                    Log.warn("Unhandled IDE protocol message: " + messageType);
                    respond.accept(NullNode.getInstance());
                }
            }
        } catch (IOException e) {
            Log.error("Failed handling IDE message " + messageType, e);
            respond.accept(NullNode.getInstance());
        }
    }

    private void respondVoid(Consumer<JsonNode> respond) {
        respond.accept(NullNode.getInstance());
    }
}

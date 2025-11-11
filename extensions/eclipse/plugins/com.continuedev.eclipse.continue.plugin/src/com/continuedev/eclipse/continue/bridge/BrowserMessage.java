package com.continuedev.eclipse.continue.bridge;

import com.fasterxml.jackson.databind.JsonNode;

public record BrowserMessage(String messageType, String messageId, JsonNode data) {}

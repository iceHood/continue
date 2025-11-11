package com.continuedev.eclipse.continue.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;

import com.continuedev.eclipse.continue.util.Log;

public class CoreProcessHandler implements AutoCloseable {
    private final ContinueCoreProcess process;
    private final Consumer<String> onMessage;
    private final BlockingQueue<String> pendingWrites = new LinkedBlockingQueue<>();
    private final ExecutorService executor = Executors.newFixedThreadPool(3);
    private final OutputStreamWriter writer;

    public CoreProcessHandler(ContinueCoreProcess process, Consumer<String> onMessage) {
        this.process = process;
        this.onMessage = onMessage;
        this.writer =
            new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        startReaders();
    }

    private void startReaders() {
        executor.submit(
            () -> {
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (!line.isEmpty()) {
                            onMessage.accept(line);
                        }
                    }
                } catch (IOException e) {
                    Log.error("Failed reading from Continue core", e);
                }
            });

        executor.submit(
            () -> {
                try (BufferedReader reader =
                        new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Log.warn("Continue core stderr: " + line);
                    }
                } catch (IOException e) {
                    Log.error("Failed reading stderr from Continue core", e);
                }
            });

        executor.submit(
            () -> {
                try {
                    while (true) {
                        String payload = pendingWrites.take();
                        writer.write(payload);
                        writer.write("\n");
                        writer.flush();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (IOException e) {
                    Log.error("Failed writing to Continue core process", e);
                }
            });
    }

    public void send(String message) {
        pendingWrites.offer(message);
    }

    @Override
    public void close() {
        executor.shutdownNow();
        process.destroy();
    }
}

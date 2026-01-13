package com.example.chunkmapper;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ChunkMapperMod implements ModInitializer {
    public static final String MOD_ID = "chunkmapper";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private static WebServer webServer;
    private static ChunkDataCollector dataCollector;

    @Override
    public void onInitialize() {
        LOGGER.info("Chunk Mapper Mod initializing...");

        dataCollector = new ChunkDataCollector();

        // サーバー起動時にWebサーバーを開始
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            try {
                webServer = new WebServer(8080, dataCollector);
                webServer.start();
                LOGGER.info("Web server started on http://localhost:8080");
            } catch (Exception e) {
                LOGGER.error("Failed to start web server", e);
            }
        });

        // サーバー停止時にWebサーバーを停止
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (webServer != null) {
                webServer.stop();
                LOGGER.info("Web server stopped");
            }
        });

        // 定期的にチャンクデータを収集
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            dataCollector.collectChunkData(server);
        });

        LOGGER.info("Chunk Mapper Mod initialized!");
    }
}
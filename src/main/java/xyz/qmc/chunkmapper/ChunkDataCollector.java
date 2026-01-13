package xyz.qmc.chunkmapper;

import net.minecraft.block.BlockState;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChunkDataCollector {
    private final Map<String, CompressedChunkData> chunkDataMap = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();
    private int tickCounter = 0;
    private final Set<String> processedChunks = ConcurrentHashMap.newKeySet();
    private static final int MAX_CHUNKS = 1000; // 最大保持チャンク数

    public void collectChunkData(MinecraftServer server) {
        // 40tick(2秒)ごとに更新（負荷軽減）
        tickCounter++;
        if (tickCounter < 40) {
            return;
        }
        tickCounter = 0;

        // メモリ制限チェック
        if (chunkDataMap.size() > MAX_CHUNKS) {
            ChunkMapperMod.LOGGER.warn("Max chunks reached, skipping new chunks");
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getServerWorld();
            ChunkPos playerChunkPos = player.getChunkPos();

            // 視界距離を制限（8チャンクまで）
            int viewDistance = Math.min(8, server.getPlayerManager().getViewDistance());

            // プレイヤーの周囲のチャンクを収集
            for (int x = -viewDistance; x <= viewDistance; x++) {
                for (int z = -viewDistance; z <= viewDistance; z++) {
                    ChunkPos chunkPos = new ChunkPos(playerChunkPos.x + x, playerChunkPos.z + z);

                    String chunkKey = getDimensionName(world) + "_" + chunkPos.x + "_" + chunkPos.z;

                    // 既に処理済みのチャンクはスキップ
                    if (processedChunks.contains(chunkKey)) {
                        continue;
                    }

                    if (world.isChunkLoaded(chunkPos.x, chunkPos.z)) {
                        WorldChunk chunk = world.getChunk(chunkPos.x, chunkPos.z);
                        extractCompressedChunkData(chunk, world, chunkPos);
                        processedChunks.add(chunkKey);
                    }
                }
            }
        }
    }

    private void extractCompressedChunkData(WorldChunk chunk, ServerWorld world, ChunkPos chunkPos) {
        String chunkKey = getDimensionName(world) + "_" + chunkPos.x + "_" + chunkPos.z;

        // チャンク単位で圧縮されたデータを作成
        CompressedChunkData data = new CompressedChunkData();
        data.chunkX = chunkPos.x;
        data.chunkZ = chunkPos.z;
        data.dimension = getDimensionName(world);

        // 16x16のサーフェスマップ
        data.heightMap = new short[256];
        data.blockIds = new byte[256];

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int idx = x * 16 + z;

                // 地表の高さを取得
                int surfaceY = chunk.getHeightmap(net.minecraft.world.Heightmap.Type.WORLD_SURFACE).get(x, z);
                data.heightMap[idx] = (short) surfaceY;

                // 地表のブロックタイプを取得
                BlockPos pos = new BlockPos(chunkPos.getStartX() + x, surfaceY - 1, chunkPos.getStartZ() + z);
                BlockState state = chunk.getBlockState(pos);
                data.blockIds[idx] = getBlockTypeId(state.getBlock().getTranslationKey().replace("block.minecraft.", ""));
            }
        }

        chunkDataMap.put(chunkKey, data);
    }

    // ブロックタイプを数値IDに変換（メモリ節約）
    private byte getBlockTypeId(String blockId) {
        switch (blockId) {
            case "grass_block": return 1;
            case "dirt": return 2;
            case "stone": return 3;
            case "sand": return 4;
            case "water": return 5;
            case "oak_log": return 6;
            case "oak_leaves": return 7;
            case "snow": return 8;
            case "ice": return 9;
            case "netherrack": return 10;
            case "end_stone": return 11;
            case "gravel": return 12;
            case "cobblestone": return 13;
            case "bedrock": return 14;
            case "clay": return 15;
            case "soul_sand": return 16;
            case "mycelium": return 17;
            case "podzol": return 18;
            case "coarse_dirt": return 19;
            case "sandstone": return 20;
            default: return 0;
        }
    }

    private String getDimensionName(ServerWorld world) {
        String id = world.getRegistryKey().getValue().toString();
        if (id.contains("overworld")) return "overworld";
        if (id.contains("nether")) return "nether";
        if (id.contains("end")) return "end";
        return id;
    }

    public String getChunkDataAsJson() {
        return gson.toJson(new ArrayList<>(chunkDataMap.values()));
    }

    public int getChunkCount() {
        return chunkDataMap.size();
    }

    public static class CompressedChunkData {
        public int chunkX;
        public int chunkZ;
        public String dimension;
        public short[] heightMap; // 16x16 = 256 shorts
        public byte[] blockIds;    // 16x16 = 256 bytes
    }
}
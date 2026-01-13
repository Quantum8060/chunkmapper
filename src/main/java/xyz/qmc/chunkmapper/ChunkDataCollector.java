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
    private final Map<String, CompactChunkData> chunkDataMap = new ConcurrentHashMap<>();
    private final Gson gson = new GsonBuilder().create();
    private int tickCounter = 0;
    private final Set<String> processedChunks = ConcurrentHashMap.newKeySet();
    private static final int MAX_CHUNKS = 500; // 最大保持チャンク数

    public void collectChunkData(MinecraftServer server) {
        // 40tick(2秒)ごとに更新
        tickCounter++;
        if (tickCounter < 40) {
            return;
        }
        tickCounter = 0;

        // メモリ制限チェック
        if (chunkDataMap.size() > MAX_CHUNKS) {
            ChunkMapperMod.LOGGER.warn("Max chunks reached ({}), skipping new chunks", MAX_CHUNKS);
            return;
        }

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            ServerWorld world = player.getServerWorld();
            ChunkPos playerChunkPos = player.getChunkPos();

            // 視界距離を制限（5チャンクまで）
            int viewDistance = Math.min(5, server.getPlayerManager().getViewDistance());

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
                        extractCompactChunkData(chunk, world, chunkPos);
                        processedChunks.add(chunkKey);
                    }
                }
            }
        }
    }

    private void extractCompactChunkData(WorldChunk chunk, ServerWorld world, ChunkPos chunkPos) {
        String chunkKey = getDimensionName(world) + "_" + chunkPos.x + "_" + chunkPos.z;

        List<BlockInfo> blocks = new ArrayList<>();

        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int worldX = chunkPos.getStartX() + x;
                int worldZ = chunkPos.getStartZ() + z;

                // 地表の高さを取得
                int surfaceY = chunk.getHeightmap(net.minecraft.world.Heightmap.Type.WORLD_SURFACE).get(x, z);

                // Y 51 から地表+3ブロックまでを収集（範囲を狭める）
                for (int y = 51; y <= Math.min(319, surfaceY + 3); y++) {
                    BlockPos pos = new BlockPos(worldX, y, worldZ);
                    BlockState state = chunk.getBlockState(pos);

                    if (state.isAir()) {
                        continue;
                    }

                    BlockInfo block = new BlockInfo();
                    block.x = (byte) x;  // チャンク内相対座標（0-15）
                    block.y = (short) y;
                    block.z = (byte) z;  // チャンク内相対座標（0-15）
                    block.type = getBlockTypeId(state.getBlock().getTranslationKey().replace("block.minecraft.", ""));

                    blocks.add(block);
                }
            }
        }

        // ブロックが1つもない場合はスキップ
        if (blocks.isEmpty()) {
            return;
        }

        CompactChunkData data = new CompactChunkData();
        data.chunkX = chunkPos.x;
        data.chunkZ = chunkPos.z;
        data.dimension = getDimensionName(world);
        data.blocks = blocks;

        chunkDataMap.put(chunkKey, data);
    }

    // ブロックタイプを数値IDに変換
    private byte getBlockTypeId(String blockId) {
        return switch (blockId) {
            case "grass_block" -> 1;
            case "dirt" -> 2;
            case "stone" -> 3;
            case "sand" -> 4;
            case "water" -> 5;
            case "oak_log" -> 6;
            case "oak_leaves" -> 7;
            case "snow" -> 8;
            case "ice" -> 9;
            case "netherrack" -> 10;
            case "end_stone" -> 11;
            case "gravel" -> 12;
            case "cobblestone" -> 13;
            case "bedrock" -> 14;
            case "clay" -> 15;
            case "soul_sand" -> 16;
            case "oak_planks" -> 21;
            case "spruce_log" -> 22;
            case "birch_log" -> 23;
            case "jungle_log" -> 24;
            case "spruce_leaves" -> 25;
            case "birch_leaves" -> 26;
            case "jungle_leaves" -> 27;
            case "glass" -> 28;
            case "white_wool" -> 29;
            case "terracotta" -> 30;
            default -> 0;
        };
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

    public static class CompactChunkData {
        public int chunkX;
        public int chunkZ;
        public String dimension;
        public List<BlockInfo> blocks;
    }

    public static class BlockInfo {
        public byte x;    // 0-15 (チャンク内相対座標)
        public short y;   // ワールド座標
        public byte z;    // 0-15 (チャンク内相対座標)
        public byte type; // ブロックタイプID
    }
}
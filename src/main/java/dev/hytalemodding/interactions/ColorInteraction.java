package dev.hytalemodding.interactions;

import com.hypixel.hytale.codec.builder.BuilderCodec;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.ComponentAccessor;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.util.ChunkUtil;
import com.hypixel.hytale.protocol.*;
import com.hypixel.hytale.server.core.asset.type.blocksound.config.BlockSoundSet;
import com.hypixel.hytale.server.core.asset.type.blocktype.config.BlockType;
import com.hypixel.hytale.server.core.asset.type.gameplay.GameplayConfig;
import com.hypixel.hytale.server.core.asset.type.gameplay.WorldConfig;
import com.hypixel.hytale.server.core.asset.type.item.config.BlockGroup;
import com.hypixel.hytale.server.core.asset.type.item.config.Item;
import com.hypixel.hytale.server.core.entity.InteractionContext;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.modules.interaction.interaction.CooldownHandler;
import com.hypixel.hytale.server.core.modules.interaction.interaction.config.client.SimpleBlockInteraction;
import com.hypixel.hytale.server.core.universe.world.SoundUtil;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.chunk.BlockChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.WorldChunk;
import com.hypixel.hytale.server.core.universe.world.chunk.section.BlockSection;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import org.checkerframework.checker.nullness.compatqual.NonNullDecl;
import org.checkerframework.checker.nullness.compatqual.NullableDecl;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

public class ColorInteraction extends SimpleBlockInteraction {
    @Nonnull
    public static final BuilderCodec<ColorInteraction> CODEC = BuilderCodec.builder(ColorInteraction.class,
                    ColorInteraction::new, SimpleBlockInteraction.CODEC)
            .documentation("Attempts to set the color of lighting objects")
            .build();

    private String value;
    public static final HytaleLogger LOGGER = HytaleLogger.forEnclosingClass();

    //Modified CycleBlockGroupInteraction for color effects
    @Override
    protected void interactWithBlock(@NonNullDecl World world,
                                     @NonNullDecl CommandBuffer<EntityStore> commandBuffer,
                                     @NonNullDecl InteractionType interactionType,
                                     @NonNullDecl InteractionContext interactionContext,
                                     @NullableDecl ItemStack itemStack,
                                     @NonNullDecl com.hypixel.hytale.math.vector.Vector3i vector3i,
                                     @NonNullDecl CooldownHandler cooldownHandler) {

        Ref<EntityStore> ref = context.getEntity();
        Store<EntityStore> store = ref.getStore();
        Player playerComponent = (Player) commandBuffer.getComponent(ref, Player.getComponentType());

        InteractionSyncData state = context.getState();
        state.state = InteractionState.Failed;

        if (playerComponent == null) {
            (LOGGER.at(Level.INFO)
                    .atMostEvery(5, TimeUnit.MINUTES))
                    .log("ColorInteraction requires a Player but was used for: %s", ref);

            return;
        }
        ChunkStore chunkStore = world.getChunkStore();
        Store<ChunkStore> chunkStoreStore = chunkStore.getStore();

        long chunkIndex = ChunkUtil.indexChunkFromBlock(targetBlock.x, targetBlock.z);
        Ref<ChunkStore> chunkReference = chunkStore.getChunkReference(chunkIndex);
        if (chunkReference == null || !chunkReference.isValid())
            return;
        WorldChunk worldChunkComponent = (WorldChunk) chunkStoreStore.getComponent(chunkReference, WorldChunk.getComponentType());
        assert worldChunkComponent != null;

        BlockChunk blockChunkComponent = (BlockChunk) chunkStoreStore.getComponent(chunkReference, BlockChunk.getComponentType());
        assert blockChunkComponent != null;

        BlockSection blockSection = blockChunkComponent.getSectionAtBlockY(targetBlock.getY());
        GameplayConfig gameplayConfig = world.getGameplayConfig();
        WorldConfig worldConfig = gameplayConfig.getWorldConfig();

        boolean blockBreakingAllowed = worldConfig.isBlockBreakingAllowed();
        if (!blockBreakingAllowed)
            return;
        int blockIndex = blockSection.get(targetBlock.x, targetBlock.y, targetBlock.z);
        BlockType targetBlockType = (BlockType) BlockType.getAssetMap().getAsset(blockIndex);

        if (targetBlockType == null)
            return;
        Item targetBlockItem = targetBlockType.getItem();
        BlockGroup set = BlockGroup.findItemGroup(targetBlockItem);

        if (set == null)
            return;
        int currentIndex = set.getIndex(targetBlockItem);
        if (currentIndex == -1)
            return;
        String nextBlockKey = set.get((currentIndex + 1) % set.size());
        BlockType nextBlockType = (BlockType) BlockType.getAssetMap().getAsset(nextBlockKey);
        if (nextBlockType == null) {
            return;
        }
        ItemStack heldItem = context.getHeldItem();
        if (heldItem != null && playerComponent.canDecreaseItemStackDurability(ref, (ComponentAccessor) store) && !heldItem.isUnbreakable()) {
            playerComponent.updateItemStackDurability(ref, heldItem, playerComponent.getInventory().getHotbar(), context.getHeldItemSlot(), -heldItem.getItem().getDurabilityLossOnHit(), (ComponentAccessor) commandBuffer);
        }

        int newBlockId = BlockType.getAssetMap().getIndex(nextBlockType.getId());
        int rotation = worldChunkComponent.getRotationIndex(targetBlock.x, targetBlock.y, targetBlock.z);
        worldChunkComponent.setBlock(targetBlock.getX(), targetBlock.getY(), targetBlock.getZ(), newBlockId, nextBlockType, rotation, 0, 256);
        state.state = InteractionState.NotFinished;

        BlockSoundSet soundSet = (BlockSoundSet) BlockSoundSet.getAssetMap().getAsset(nextBlockType.getBlockSoundSetIndex());

        if (soundSet != null) {
            int soundEventIndex = soundSet.getSoundEventIndices().getOrDefault(BlockSoundEvent.Hit, 0);
            if (soundEventIndex != 0) {
                SoundUtil.playSoundEvent3d(ref, soundEventIndex, targetBlock.x + 0.5D, targetBlock.y + 0.5D, targetBlock.z + 0.5D, (ComponentAccessor) commandBuffer);
            }
        }
    }

    @Override
    protected void simulateInteractWithBlock(@NonNullDecl InteractionType interactionType, @NonNullDecl InteractionContext interactionContext, @NullableDecl ItemStack itemStack, @NonNullDecl World world, @NonNullDecl com.hypixel.hytale.math.vector.Vector3i vector3i) {
    }

    @Nonnull
    public String toString() {
        return "ColorInteraction{}";
    }
}

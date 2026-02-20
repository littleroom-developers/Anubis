package com.pedro.anubis.mixin;

import com.pedro.anubis.modules.esp.HoleTunnelStairsESP;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.world.ClientChunkManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.Consumer;

@Mixin(ClientChunkManager.class)
public abstract class ClientChunkManagerMixin {
    @Inject(
        method = "loadChunkFromPacket(IILnet/minecraft/network/PacketByteBuf;Lnet/minecraft/nbt/NbtCompound;Ljava/util/function/Consumer;)Lnet/minecraft/world/chunk/WorldChunk;",
        at = @At("RETURN")
    )
    private void onLoadChunkFromPacket(
        int chunkX,
        int chunkZ,
        PacketByteBuf buf,
        NbtCompound nbt,
        Consumer<ChunkData.BlockEntityVisitor> consumer,
        CallbackInfoReturnable<WorldChunk> cir
    ) {
        HoleTunnelStairsESP module = Modules.get().get(HoleTunnelStairsESP.class);
        if (module != null && module.isActive()) module.onChunkLoaded(chunkX, chunkZ);
    }

    @Inject(method = "unload(Lnet/minecraft/util/math/ChunkPos;)V", at = @At("HEAD"))
    private void onUnloadChunk(ChunkPos pos, CallbackInfo ci) {
        HoleTunnelStairsESP module = Modules.get().get(HoleTunnelStairsESP.class);
        if (module != null && module.isActive()) module.onChunkUnloaded(pos);
    }
}

package dev.vergilchair.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.vergilchair.ChairAction;
import dev.vergilchair.ChairSeat;
import dev.vergilchair.VergilChair;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

@EventBusSubscriber(modid = VergilChair.ID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ChairClient {
    private static final String CATEGORY = "key.categories.vergil_chair";
    private static final KeyMapping SLUMP = new KeyMapping("key.vergil_chair.slump",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, CATEGORY);
    private static final KeyMapping FALL = new KeyMapping("key.vergil_chair.fall",
            KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_H, CATEGORY);

    private ChairClient() {}

    @SubscribeEvent
    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(SLUMP);
        event.register(FALL);
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(VergilChair.SEAT.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        NeoForge.EVENT_BUS.addListener(ChairClient::afterTick);
    }

    private static void afterTick(ClientTickEvent.Post event) {
        boolean slump = false;
        boolean fall = false;
        while (SLUMP.consumeClick()) slump = true;
        while (FALL.consumeClick()) fall = true;
        Minecraft minecraft = Minecraft.getInstance();
        if ((slump || fall) && minecraft.screen == null
                && minecraft.player != null && minecraft.player.getVehicle() instanceof ChairSeat) {
            PacketDistributor.sendToServer(new ChairAction(fall));
        }
    }
}

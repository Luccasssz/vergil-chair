package dev.vergilchair;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(VergilChair.ID)
public final class VergilChair {
    public static final String ID = "vergil_chair";

    private static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, ID);
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);

    public static final DeferredHolder<Block, ChairBlock> CHAIR = BLOCKS.register(ID,
            () -> new ChairBlock(BlockBehaviour.Properties.of().noOcclusion().strength(0.8F).pushReaction(PushReaction.BLOCK)
                    .sound(SoundType.BAMBOO_WOOD)));
    public static final DeferredHolder<Item, BlockItem> CHAIR_ITEM = ITEMS.register(ID,
            () -> new BlockItem(CHAIR.get(), new Item.Properties()));
    public static final DeferredHolder<EntityType<?>, EntityType<ChairSeat>> SEAT = ENTITIES.register("seat",
            () -> EntityType.Builder.<ChairSeat>of(ChairSeat::new, MobCategory.MISC)
                    .sized(0.01F, 0.01F).noSave().noSummon().build(id("seat").toString()));
    public static final DeferredHolder<SoundEvent, SoundEvent> SLUMP_SIGH = SOUNDS.register("slump_sigh",
            () -> SoundEvent.createVariableRangeEvent(id("slump_sigh")));

    public VergilChair(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        ENTITIES.register(bus);
        SOUNDS.register(bus);

        DeferredRegister<CreativeModeTab> tabs = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);
        tabs.register(ID, () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.vergil_chair"))
                .icon(() -> CHAIR_ITEM.get().getDefaultInstance())
                .displayItems((parameters, output) -> output.accept(CHAIR_ITEM.get()))
                .build());
        tabs.register(bus);

        bus.addListener(VergilChair::addToCreativeTab);
        bus.addListener(VergilChair::registerPayload);
        NeoForge.EVENT_BUS.addListener(VergilChair::dismountBeforeSaving);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(ID, path);
    }

    private static void addToCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) {
            event.accept(CHAIR_ITEM.get());
        }
    }

    private static void registerPayload(RegisterPayloadHandlersEvent event) {
        event.registrar("2").playToServer(ChairAction.TYPE, ChairAction.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player
                    && player.getVehicle() instanceof ChairSeat seat) {
                seat.onAction(player, payload.fall());
            }
        });
    }

    private static void dismountBeforeSaving(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity().getVehicle() instanceof ChairSeat) {
            event.getEntity().stopRiding();
        }
    }
}

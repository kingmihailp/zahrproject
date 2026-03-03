package com.zahrproject.votingmod;

import com.mojang.logging.LogUtils;
import com.zahrproject.votingmod.client.ChildRenderHandler;
import com.zahrproject.votingmod.client.EventTimerHud;
import com.zahrproject.votingmod.client.GoldenOverlayLayer;
import com.zahrproject.votingmod.client.InventoryLockClientHandler;
import com.zahrproject.votingmod.client.PlayerFlipRenderHandler;
import com.zahrproject.votingmod.client.ScreenFlipHandler;
import com.zahrproject.votingmod.client.SkateboardRenderHandler;
import com.zahrproject.votingmod.command.VotingCommand;
import com.zahrproject.votingmod.enchantments.ModEnchantments;
import com.zahrproject.votingmod.events.ChildEventManager;
import com.zahrproject.votingmod.handler.FlipModelTracker;
import com.zahrproject.votingmod.handler.FlipScreenTracker;
import com.zahrproject.votingmod.handler.ForgeEventHandler;
import com.zahrproject.votingmod.handler.GoldenPlayerHandler;
import com.zahrproject.votingmod.handler.HardcoreModeHandler;
import com.zahrproject.votingmod.handler.HostileVillagersHandler;
import com.zahrproject.votingmod.handler.InventoryLockHandler;
import com.zahrproject.votingmod.handler.MeteorRainHandler;
import com.zahrproject.votingmod.handler.SkateboardHandler;
import com.zahrproject.votingmod.network.ModNetwork;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

@Mod(VotingMod.MOD_ID)
public class VotingMod {

    public static final String MOD_ID = "votingmod";
    private static final Logger LOGGER = LogUtils.getLogger();

    public VotingMod() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::clientSetup);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new FlipScreenTracker());
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
        MinecraftForge.EVENT_BUS.register(new GoldenPlayerHandler());
        MinecraftForge.EVENT_BUS.register(new SkateboardHandler());
        MinecraftForge.EVENT_BUS.register(ChildEventManager.class);
        MinecraftForge.EVENT_BUS.register(HostileVillagersHandler.class);
        MinecraftForge.EVENT_BUS.register(HardcoreModeHandler.class);
        MinecraftForge.EVENT_BUS.register(InventoryLockHandler.class);
        MinecraftForge.EVENT_BUS.register(MeteorRainHandler.class);
        MinecraftForge.EVENT_BUS.register(new FlipModelTracker());
        LOGGER.info("[VotingMod] Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("[VotingMod] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            MinecraftForge.EVENT_BUS.register(ChildRenderHandler.class);
            MinecraftForge.EVENT_BUS.register(SkateboardRenderHandler.class);
            MinecraftForge.EVENT_BUS.register(ScreenFlipHandler.class);
            MinecraftForge.EVENT_BUS.register(EventTimerHud.class);
            MinecraftForge.EVENT_BUS.register(PlayerFlipRenderHandler.class);
            MinecraftForge.EVENT_BUS.register(InventoryLockClientHandler.class);
        });
        FMLJavaModLoadingContext.get().getModEventBus().addListener(GoldenOverlayLayer::onAddLayers);
        LOGGER.info("[VotingMod] Client setup complete.");
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        VotingCommand.register(event.getDispatcher());
        LOGGER.info("[VotingMod] Commands registered.");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        VotingManager.getInstance().start(event.getServer());
        LOGGER.info("[VotingMod] Voting manager started.");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        VotingManager.getInstance().stop();
        LOGGER.info("[VotingMod] Voting manager stopped.");
    }
}

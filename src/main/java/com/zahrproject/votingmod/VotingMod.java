package com.zahrproject.votingmod;

import com.mojang.logging.LogUtils;
import com.zahrproject.votingmod.command.VotingCommand;
import com.zahrproject.votingmod.handler.ForgeEventHandler;
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
        MinecraftForge.EVENT_BUS.register(this);
        MinecraftForge.EVENT_BUS.register(new ForgeEventHandler());
        LOGGER.info("[VotingMod] Mod initialized!");
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
        LOGGER.info("[VotingMod] Common setup complete.");
    }

    private void clientSetup(final FMLClientSetupEvent event) {
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

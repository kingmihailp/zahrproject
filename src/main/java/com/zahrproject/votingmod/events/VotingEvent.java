package com.zahrproject.votingmod.events;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Represents a single voting event.
 * Players vote YES or NO. If YES wins, the action is executed.
 */
public class VotingEvent {

    private final String description;
    private final Consumer<MinecraftServer> action;

    public VotingEvent(String description, Consumer<MinecraftServer> action) {
        this.description = description;
        this.action = action;
    }

    public String getDescription() {
        return description;
    }

    public void execute(MinecraftServer server) {
        action.accept(server);
    }
}

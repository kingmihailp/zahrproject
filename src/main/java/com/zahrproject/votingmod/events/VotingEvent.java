package com.zahrproject.votingmod.events;

import net.minecraft.server.MinecraftServer;

import java.util.function.Consumer;

/**
 * Represents a single voting event with two options.
 * Each option has a display name and an action to execute when it wins.
 */
public class VotingEvent {

    private final String optionA;
    private final String optionB;
    private final Consumer<MinecraftServer> actionA;
    private final Consumer<MinecraftServer> actionB;

    public VotingEvent(String optionA, Consumer<MinecraftServer> actionA,
                       String optionB, Consumer<MinecraftServer> actionB) {
        this.optionA = optionA;
        this.actionA = actionA;
        this.optionB = optionB;
        this.actionB = actionB;
    }

    public String getOptionA() {
        return optionA;
    }

    public String getOptionB() {
        return optionB;
    }

    public void executeA(MinecraftServer server) {
        actionA.accept(server);
    }

    public void executeB(MinecraftServer server) {
        actionB.accept(server);
    }
}

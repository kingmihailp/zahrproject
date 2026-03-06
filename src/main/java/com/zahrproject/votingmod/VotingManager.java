package com.zahrproject.votingmod;

import com.mojang.logging.LogUtils;
import com.zahrproject.votingmod.events.VotingEvent;
import com.zahrproject.votingmod.events.VotingEventList;
import com.zahrproject.votingmod.network.ModNetwork;
import com.zahrproject.votingmod.network.OpenVotingScreenPacket;
import com.zahrproject.votingmod.network.VoteResultPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages the voting cycle on the server side.
 * Picks a random event, sends it to all players, and waits for YES/NO votes.
 * If YES wins the event is executed; if NO wins the event is cancelled.
 */
public class VotingManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final VotingManager INSTANCE = new VotingManager();

    /** Default interval: 3 minutes. Changed at runtime via /votingmod settime. */
    private long voteIntervalSeconds = 180;
    /** How long players have to vote (30 seconds). */
    private static final long VOTE_DURATION_SECONDS = 30;

    private ScheduledExecutorService scheduler;
    private MinecraftServer server;

    private final List<VotingEvent> eventPool = new ArrayList<>();

    // Current voting state — two events are picked per round
    private VotingEvent currentEventA = null;
    private VotingEvent currentEventB = null;
    private final Map<UUID, Integer> playerVotes = new ConcurrentHashMap<>();
    /** 0 = vote for A, 1 = vote for B */
    private final AtomicInteger votesA = new AtomicInteger(0);
    private final AtomicInteger votesB = new AtomicInteger(0);
    private boolean voteActive = false;
    private ScheduledFuture<?> voteEndFuture;

    // Tracks which events were used recently (to avoid repeats)
    private final List<Integer> recentEventIndices = new ArrayList<>();

    private VotingManager() {}

    public static VotingManager getInstance() {
        return INSTANCE;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    public void start(MinecraftServer server) {
        this.server = server;
        this.eventPool.clear();
        this.eventPool.addAll(VotingEventList.buildEventList());
        startScheduler();
        LOGGER.info("[VotingMod] Voting cycle started. First vote in {} sec.", voteIntervalSeconds);
    }

    public void stop() {
        shutdownScheduler();
        voteActive = false;
        LOGGER.info("[VotingMod] Voting cycle stopped.");
    }

    // -------------------------------------------------------------------------
    // Dynamic interval via /votingmod settime
    // -------------------------------------------------------------------------

    /**
     * Changes the interval between votes and immediately reschedules.
     * Safe to call from any thread.
     */
    public synchronized void setInterval(long seconds) {
        this.voteIntervalSeconds = seconds;
        if (server != null) {
            shutdownScheduler();
            startScheduler();
            LOGGER.info("[VotingMod] Vote interval changed to {} seconds.", seconds);
        }
    }

    public long getIntervalSeconds() {
        return voteIntervalSeconds;
    }

    // -------------------------------------------------------------------------
    // Scheduler helpers
    // -------------------------------------------------------------------------

    private void startScheduler() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "VotingMod-Scheduler");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::startNewVote,
                voteIntervalSeconds, voteIntervalSeconds, TimeUnit.SECONDS);
    }

    private void shutdownScheduler() {
        if (scheduler != null && !scheduler.isShutdown()) {
            scheduler.shutdownNow();
            try {
                scheduler.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // -------------------------------------------------------------------------
    // Voting logic
    // -------------------------------------------------------------------------

    private synchronized void startNewVote() {
        if (server == null) return;

        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            LOGGER.info("[VotingMod] No players online, skipping vote.");
            return;
        }

        // Pick two different random events from the pool
        int indexA = pickRandomEventIndex();
        // pickRandomEventIndex adds indexA to recentEventIndices, so the next call won't repeat it
        int indexB = pickRandomEventIndex();

        currentEventA = eventPool.get(indexA);
        currentEventB = eventPool.get(indexB);

        playerVotes.clear();
        votesA.set(0);
        votesB.set(0);
        voteActive = true;

        LOGGER.info("[VotingMod] Starting vote: '{}' vs '{}'",
                currentEventA.getDescription(), currentEventB.getDescription());

        // Send packet to all players to open voting screen
        OpenVotingScreenPacket packet = new OpenVotingScreenPacket(
                currentEventA.getDescription(),
                currentEventB.getDescription(),
                VOTE_DURATION_SECONDS
        );

        for (ServerPlayer player : players) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }

        // Schedule vote end
        voteEndFuture = scheduler.schedule(this::endVote, VOTE_DURATION_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void receiveVote(UUID playerId, int option) {
        if (!voteActive) return;
        if (playerVotes.containsKey(playerId)) return; // Already voted

        playerVotes.put(playerId, option);
        if (option == 0) votesA.incrementAndGet();
        else             votesB.incrementAndGet();

        LOGGER.debug("[VotingMod] Player {} voted for option {}", playerId, option == 0 ? "A" : "B");

        // Check if all online players have voted
        int totalPlayers = server.getPlayerList().getPlayers().size();
        if (playerVotes.size() >= totalPlayers) {
            if (voteEndFuture != null && !voteEndFuture.isDone()) {
                voteEndFuture.cancel(false);
            }
            endVote();
        }
    }

    private synchronized void endVote() {
        if (!voteActive) return;
        voteActive = false;

        int countA = votesA.get();
        int countB = votesB.get();

        // Ties go to A
        boolean aWins = countA >= countB;
        VotingEvent winner = aWins ? currentEventA : currentEventB;

        LOGGER.info("[VotingMod] Vote ended. A={} B={} → winner: '{}'",
                countA, countB, winner.getDescription());

        // Send result packet to all players
        VoteResultPacket resultPacket = new VoteResultPacket(
                aWins ? 0 : 1, countA, countB,
                currentEventA.getDescription(),
                currentEventB.getDescription()
        );
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), resultPacket);
        }

        // Chat broadcast
        String chatMsg = "§aПобедил вариант " + (aWins ? "A" : "B") + ": §e" + winner.getDescription();
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("§6[Голосование] " + chatMsg), false);

        // Execute winning event on main server thread
        server.execute(() -> winner.execute(server));

        currentEventA = null;
        currentEventB = null;
    }

    private int pickRandomEventIndex() {
        if (eventPool.size() == 1) return 0;

        // Build candidate list excluding recent events
        List<Integer> candidates = new ArrayList<>();
        for (int i = 0; i < eventPool.size(); i++) {
            if (!recentEventIndices.contains(i)) {
                candidates.add(i);
            }
        }

        // If all events were recent, reset and use full pool
        if (candidates.isEmpty()) {
            recentEventIndices.clear();
            for (int i = 0; i < eventPool.size(); i++) {
                candidates.add(i);
            }
        }

        int chosen = candidates.get((int) (Math.random() * candidates.size()));
        recentEventIndices.add(chosen);
        // Keep only the last N used indices in memory
        int maxRecent = Math.max(1, eventPool.size() / 2);
        while (recentEventIndices.size() > maxRecent) {
            recentEventIndices.remove(0);
        }
        return chosen;
    }

    public boolean isVoteActive() {
        return voteActive;
    }
}

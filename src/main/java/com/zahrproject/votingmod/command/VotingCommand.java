package com.zahrproject.votingmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.zahrproject.votingmod.VotingManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/**
 * Registers the /votingmod command tree.
 *
 * Usage:
 *   /votingmod settime <hours> <minutes> <seconds>
 *
 * Requires operator permission level 2.
 * Sets the interval between automatic voting rounds.
 */
public class VotingCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            Commands.literal("votingmod")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("settime")
                    .then(Commands.argument("hours", IntegerArgumentType.integer(0))
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(0, 59))
                            .then(Commands.argument("seconds", IntegerArgumentType.integer(0, 59))
                                .executes(ctx -> executeSetTime(ctx,
                                        IntegerArgumentType.getInteger(ctx, "hours"),
                                        IntegerArgumentType.getInteger(ctx, "minutes"),
                                        IntegerArgumentType.getInteger(ctx, "seconds")))))))
        );
    }

    private static int executeSetTime(CommandContext<CommandSourceStack> ctx,
                                      int hours, int minutes, int seconds) {
        long totalSeconds = (long) hours * 3600 + (long) minutes * 60 + seconds;

        if (totalSeconds < 10) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cМинимальный интервал — 10 секунд!"));
            return 0;
        }

        VotingManager.getInstance().setInterval(totalSeconds);

        String formatted = formatTime(hours, minutes, seconds, totalSeconds);
        ctx.getSource().sendSuccess(
                () -> Component.literal("§aИнтервал голосования установлен: §e" + formatted),
                true);
        return 1;
    }

    private static String formatTime(int h, int m, int s, long totalSec) {
        if (h > 0 && m > 0 && s > 0) {
            return h + "ч " + m + "мин " + s + "сек (" + totalSec + " сек.)";
        } else if (h > 0 && m > 0) {
            return h + "ч " + m + "мин (" + totalSec + " сек.)";
        } else if (h > 0 && s > 0) {
            return h + "ч " + s + "сек (" + totalSec + " сек.)";
        } else if (h > 0) {
            return h + "ч (" + totalSec + " сек.)";
        } else if (m > 0 && s > 0) {
            return m + "мин " + s + "сек (" + totalSec + " сек.)";
        } else if (m > 0) {
            return m + "мин (" + totalSec + " сек.)";
        } else {
            return s + "сек.";
        }
    }
}

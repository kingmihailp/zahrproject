package com.zahrproject.votingmod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.RecordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.function.Supplier;

public class VotingDiscItem extends RecordItem {

    public VotingDiscItem(int comparatorOutput, Supplier<SoundEvent> sound,
                          Properties properties, int lengthInTicks) {
        super(comparatorOutput, sound, properties, lengthInTicks);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        tooltip.add(Component.translatable("item.votingmod.voting_disc.desc")
                .withStyle(Style.EMPTY.withItalic(true)
                        .withColor(0x808080)));
    }
}

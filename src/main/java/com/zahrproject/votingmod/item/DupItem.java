package com.zahrproject.votingmod.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;

/**
 * «Дюп» — редкий предмет, выпадающий при ломании любого блока с шансом 0.0001%.
 *
 * <p>Внешний вид: на каждом кадре рендера перебирает все спрайты из игрового
 * атласа текстур, одновременно меняя радужный тинт — эффект «все переливающиеся
 * текстуры из игры» реализован в
 * {@link com.zahrproject.votingmod.client.DupItemRenderer}.
 *
 * <p>Функционал крафта: если положить в сетку крафта с чем угодно, удваивает
 * результат и расходуется — логика в
 * {@link com.zahrproject.votingmod.recipe.DupRecipe}.
 */
public class DupItem extends Item {

    public DupItem(Properties properties) {
        super(properties);
    }

    /**
     * Registers the custom {@link com.zahrproject.votingmod.client.DupItemRenderer}
     * (a {@code BlockEntityWithoutLevelRenderer}) so the item uses it instead of the
     * standard model pipeline. Requires the item model JSON to specify
     * {@code "parent": "minecraft:builtin/entity"}.
     */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return com.zahrproject.votingmod.client.DupItemRenderer.getInstance();
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.votingmod.dup.desc"));
    }
}

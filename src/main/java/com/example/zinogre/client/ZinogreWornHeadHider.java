package com.example.zinogre.client;

import com.example.zinogre.ZinogreMod;
import net.minecraft.client.model.HeadedModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * Hides the wearer's own head (and hat layer) while a Zinogre head is worn, so only the Zinogre head shows. The
 * parts are shown again right after the entity is drawn.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, value = Dist.CLIENT)
public final class ZinogreWornHeadHider {
    private static ModelPart hiddenHead, hiddenHat;
    private static boolean headWasVisible, hatWasVisible;

    static boolean wearing(net.minecraft.world.entity.LivingEntity entity) {
        ItemStack stack = entity.getItemBySlot(EquipmentSlot.HEAD);
        return stack.is(ZinogreMod.HEAD_ITEM.get()) || stack.is(ZinogreMod.STYGIAN_HEAD_ITEM.get());
    }

    @SubscribeEvent
    public static void pre(RenderLivingEvent.Pre<?, ?> event) {
        if (!wearing(event.getEntity()) || !(event.getRenderer().getModel() instanceof HeadedModel headed)) {
            return;
        }
        hiddenHead = headed.getHead();
        headWasVisible = hiddenHead.visible;
        hiddenHead.visible = false;
        if (headed instanceof HumanoidModel<?> humanoid) {
            hiddenHat = humanoid.hat;
            hatWasVisible = hiddenHat.visible;
            hiddenHat.visible = false;
        }
    }

    @SubscribeEvent
    public static void post(RenderLivingEvent.Post<?, ?> event) {
        if (hiddenHead != null) {
            hiddenHead.visible = headWasVisible;
            hiddenHead = null;
        }
        if (hiddenHat != null) {
            hiddenHat.visible = hatWasVisible;
            hiddenHat = null;
        }
    }

    private ZinogreWornHeadHider() {}
}

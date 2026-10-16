package com.example.zinogre;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * /zinogre play <move> [zinogres]: makes Zinogres do one of their moves on the spot, to watch the animations.
 * Moves: roar, bite, leap, flip, charge, death. Without a selector it is the nearest Zinogre within 32 blocks.
 */
@EventBusSubscriber(modid = ZinogreMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public final class ZinogreCommands {
    private static final List<String> MOVES = List.of("roar", "bite", "leap", "flip", "charge", "death");

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("zinogre")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("play")
                        .then(Commands.argument("move", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(MOVES, b))
                                .executes(c -> play(c, nearest(c.getSource())))
                                .then(Commands.argument("zinogres", EntityArgument.entities())
                                        .executes(c -> play(c, EntityArgument.getEntities(c, "zinogres")))))));
    }

    private static Collection<? extends Entity> nearest(CommandSourceStack source) {
        List<ZinogreEntity> found = source.getLevel().getEntitiesOfClass(ZinogreEntity.class,
                new AABB(source.getPosition(), source.getPosition()).inflate(32.0), Entity::isAlive);
        found.sort(Comparator.comparingDouble(z -> z.distanceToSqr(source.getPosition())));
        return found.isEmpty() ? List.of() : List.of(found.get(0));
    }

    private static int play(CommandContext<CommandSourceStack> c, Collection<? extends Entity> entities) throws CommandSyntaxException {
        String move = StringArgumentType.getString(c, "move");
        if (!MOVES.contains(move)) {
            c.getSource().sendFailure(Component.literal("Unknown move '" + move + "'. Moves: " + String.join(", ", MOVES)));
            return 0;
        }
        List<ZinogreEntity> zinogres = new ArrayList<>();
        for (Entity e : entities) {
            if (e instanceof ZinogreEntity z && z.isAlive()) {
                zinogres.add(z);
            }
        }
        if (zinogres.isEmpty()) {
            c.getSource().sendFailure(Component.literal("No Zinogre found (stand within 32 blocks of one, or pick one with a selector)."));
            return 0;
        }
        for (ZinogreEntity z : zinogres) {
            z.playMove(move);
        }
        c.getSource().sendSuccess(() -> Component.literal("Zinogre: " + move + " (" + zinogres.size() + ")"), false);
        return zinogres.size();
    }

    private ZinogreCommands() {}
}

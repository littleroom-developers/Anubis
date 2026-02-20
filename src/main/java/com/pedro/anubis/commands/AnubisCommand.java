package com.pedro.anubis.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;

public class AnubisCommand extends Command {
    public AnubisCommand() {
        super("anubis", "Anubis addon command.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            info("Anubis is active.");
            return SINGLE_SUCCESS;
        });
    }
}

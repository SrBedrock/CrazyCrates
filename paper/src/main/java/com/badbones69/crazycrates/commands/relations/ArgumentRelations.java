package com.badbones69.crazycrates.commands.relations;

import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.utils.MsgUtils;
import com.badbones69.crazycrates.commands.MessageManager;
import dev.triumphteam.cmd.core.message.MessageKey;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

public class ArgumentRelations extends MessageManager {

    private String getContext(String subCommand, String commandOrder) {
        String correctUsage = null;

        switch (subCommand) {
            case "transfer" -> correctUsage = commandOrder + "<crate-name> <player-name> <amount>";
            case "debug", "open", "set" -> correctUsage = commandOrder + "<crate-name>";
            case "tp" -> correctUsage = commandOrder + "<id>";
            case "additem" -> correctUsage = commandOrder + "<crate-name> <prize-number> <chance> [tier]";
            case "preview", "open-others", "forceopen" -> correctUsage = commandOrder + "<crate-name> <player-name>";
            case "mass-open" -> correctUsage = commandOrder + "<crate-name> <key-type> <amount>";
            case "give-random" -> correctUsage = commandOrder + "<key-type> <amount> <player-name>";
            case "give", "take" -> correctUsage = commandOrder + "<key-type> <crate-name> <amount> <player-name>";
            case "giveall" -> correctUsage = commandOrder + "<key-type> <crate-name> <amount>";
        }

        return correctUsage;
    }

    @Override
    public void build() {
        getBukkitCommandManager().registerMessage(MessageKey.TOO_MANY_ARGUMENTS, (sender, context) -> {
            String command = context.getCommand();
            String subCommand = context.getSubCommand();

            String commandOrder = "/" + command + " " + subCommand + " ";

            String correctUsage = getCorrectUsage(command, subCommand, commandOrder);

            if (!correctUsage.isEmpty()) {
                send(sender, Messages.correct_usage.getMessage("%usage%", correctUsage).toString());
            }
        });

        getBukkitCommandManager().registerMessage(MessageKey.NOT_ENOUGH_ARGUMENTS, (sender, context) -> {
            String command = context.getCommand();
            String subCommand = context.getSubCommand();

            String commandOrder = "/" + command + " " + subCommand + " ";

            String correctUsage = getCorrectUsage(command, subCommand, commandOrder);

            if (!correctUsage.isEmpty()) {
                send(sender, Messages.correct_usage.getMessage("%usage%", correctUsage).toString());
            }
        });

        getBukkitCommandManager().registerMessage(MessageKey.UNKNOWN_COMMAND, (sender, context) -> send(sender, Messages.unknown_command.getString()));
    }

    private String getCorrectUsage(@NotNull String command, String subCommand, String commandOrder) {
        String correctUsage = "";

        switch (command) {
            case "crates" -> correctUsage = getContext(subCommand, commandOrder);
            case "chave" -> {
                if (subCommand.equals("ver")) {
                    correctUsage = commandOrder + " [jogador]";
                }
                if (subCommand.equals("transferir")) {
                    correctUsage = commandOrder + " <caixa> <jogador> <quantidade>";
                }
            }
        }

        return correctUsage;
    }

    @Override
    public void send(@NotNull CommandSender sender, @NotNull String component) {
        sender.sendMessage(parse(component));
    }

    @Override
    public String parse(@NotNull String message) {
        return MsgUtils.color(message);
    }
}
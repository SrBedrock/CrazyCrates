package com.badbones69.crazycrates.commands.relations;

import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.utils.MsgUtils;
import com.badbones69.crazycrates.commands.MessageManager;
import dev.triumphteam.cmd.core.message.ContextualKey;
import dev.triumphteam.cmd.core.message.MessageKey;
import dev.triumphteam.cmd.core.message.context.MessageContext;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class ArgumentRelations extends MessageManager {

    private String getContext(String subCommand, String commandOrder) {
        String correctUsage = null;

        switch (subCommand) {
            case "debug", "open", "set" -> correctUsage = commandOrder + "<crate-name>";
            case "tp" -> correctUsage = commandOrder + "<id>";
            case "additem" -> correctUsage = commandOrder + "<crate-name> <prize-number> <chance> [tier]";
            case "preview", "forceopen" -> correctUsage = commandOrder + "<crate-name> <player-name>";
            case "open-others" -> correctUsage = commandOrder + "<crate-name> <player-name> [key-type]";
            case "mass-open" -> correctUsage = commandOrder + "<crate-name> <key-type> <amount>";
            case "give-random" -> correctUsage = commandOrder + "<key-type> <amount> <player-name>";
            case "give", "take" -> correctUsage = commandOrder + "<key-type> <crate-name> <amount> <player-name>";
            case "giveall" -> correctUsage = commandOrder + "<key-type> <crate-name> <amount>";
        }

        return correctUsage;
    }

    @Override
    public void build() {
        for (var messageKey : ContextualKey.getRegisteredKeys()) {
            getBukkitCommandManager().registerMessage(messageKey, this::sendCorrectUsage);
        }
        getBukkitCommandManager().registerMessage(MessageKey.UNKNOWN_COMMAND, (sender, context) -> sendUnknownCommand(sender));
    }

    private void sendCorrectUsage(CommandSender sender, MessageContext context) {
        String command = context.getCommand();
        String subCommand = context.getSubCommand();

        String commandOrder = "/" + command + " " + subCommand + " ";

        String correctUsage = getCorrectUsage(command, subCommand, commandOrder);

        if (sender instanceof Player player) {
            this.send(sender, Messages.correct_usage.getMessage("%usage%", correctUsage, player));
        } else {
            this.send(sender, Messages.correct_usage.getMessage("%usage%", correctUsage));
        }
    }

    private void sendUnknownCommand(@NotNull CommandSender sender) {
        if (sender instanceof Player player) {
            this.send(sender, Messages.unknown_command.getMessage(player));
        } else {
            this.send(sender, Messages.unknown_command.getMessage());
        }
    }

    private String getCorrectUsage(@NotNull String command, String subCommand, String commandOrder) {
        String correctUsage = "";

        switch (command) {
            case "crates" -> {
                return getContext(subCommand, commandOrder);
            }
            case "chave" -> {
                if (subCommand.equals("ver")) {
                    return commandOrder + "[jogador]";
                }
                if (subCommand.equals("transferir")) {
                    return commandOrder + "<chave> <jogador> <quantidade>";
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
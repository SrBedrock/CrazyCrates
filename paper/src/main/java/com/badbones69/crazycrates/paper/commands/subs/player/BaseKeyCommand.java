package com.badbones69.crazycrates.paper.commands.subs.player;

import com.badbones69.crazycrates.api.enums.types.KeyType;
import com.badbones69.crazycrates.paper.CrazyCrates;
import com.badbones69.crazycrates.paper.api.CrazyManager;
import com.badbones69.crazycrates.paper.api.EventLogger;
import com.badbones69.crazycrates.paper.api.FileManager;
import com.badbones69.crazycrates.paper.api.enums.settings.Messages;
import com.badbones69.crazycrates.paper.api.events.PlayerReceiveKeyEvent;
import com.badbones69.crazycrates.paper.api.objects.Crate;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionDefault;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(value = "chaves", alias = {"chave"})
public class BaseKeyCommand extends BaseCommand {

    private final CrazyCrates plugin = CrazyCrates.getPlugin();

    private final CrazyManager crazyManager = plugin.getStarter().getCrazyManager();

    private final EventLogger eventLogger = plugin.getStarter().getEventLogger();

    @Default
    @Permission("crazycrates.command.player.key")
    public void viewPersonal(Player player) {
        String header = Messages.PERSONAL_HEADER.getMessageNoPrefix();

        String noKeys = Messages.PERSONAL_NO_VIRTUAL_KEYS.getMessage();

        getKeys(player, player, header, noKeys);
    }

    @SubCommand("transferir")
    @Permission(value = "crazycrates.command.player.transfer", def = PermissionDefault.OP)
    public void onPlayerTransferKeys(Player sender, @Suggestion("crates") String crateName, @Suggestion("online-players") Player player, @Suggestion("numbers") int amount) {
        Crate crate = crazyManager.getCrateFromName(crateName);

        if (crate != null) {
            if (!player.getName().equalsIgnoreCase(sender.getName())) {

                if (crazyManager.getVirtualKeys(sender, crate) >= amount) {
                    PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(player, crate, PlayerReceiveKeyEvent.KeyReceiveReason.TRANSFER, amount);
                    plugin.getServer().getPluginManager().callEvent(event);

                    if (!event.isCancelled()) {
                        crazyManager.takeKeys(amount, sender, crate, KeyType.VIRTUAL_KEY, false);
                        crazyManager.addKeys(amount, player, crate, KeyType.VIRTUAL_KEY);

                        HashMap<String, String> placeholders = new HashMap<>();

                        placeholders.put("%Crate%", crate.getName());
                        placeholders.put("%Key%", crate.getKey().getItemMeta().getDisplayName());
                        placeholders.put("%Amount%", amount + "");
                        placeholders.put("%Player%", player.getName());

                        sender.sendMessage(Messages.TRANSFERRED_KEYS.getMessage(placeholders));

                        placeholders.put("%Player%", sender.getName());

                        player.sendMessage(Messages.RECEIVED_TRANSFERRED_KEYS.getMessage(placeholders));

                        boolean logFile = FileManager.Files.CONFIG.getFile().getBoolean("Settings.Crate-Actions.Log-File");
                        boolean logConsole = FileManager.Files.CONFIG.getFile().getBoolean("Settings.Crate-Actions.Log-Console");

                        eventLogger.logKeyEvent(player, sender, crate, KeyType.VIRTUAL_KEY, EventLogger.KeyEventType.KEY_EVENT_RECEIVED, logFile, logConsole);
                    }
                } else {
                    sender.sendMessage(Messages.NOT_ENOUGH_KEYS.getMessage("%Crate%", crate.getName()));
                }
            } else {
                sender.sendMessage(Messages.SAME_PLAYER.getMessage());
            }
        } else {
            sender.sendMessage(Messages.NOT_A_CRATE.getMessage("%Crate%", crateName));
        }
    }

    @SubCommand("ver")
    @Permission("crazycrates.command.player.key.others")
    public void viewOthers(CommandSender sender, @Suggestion("online-players") Player target) {
        if (target == sender) {
            sender.sendMessage(Messages.SAME_PLAYER.getMessage());
            return;
        }

        String header = Messages.OTHER_PLAYER_HEADER.getMessageNoPrefix("%Player%", target.getName());

        String otherPlayer = Messages.OTHER_PLAYER_NO_VIRTUAL_KEYS.getMessage("%Player%", target.getName());

        getKeys(target, sender, header, otherPlayer);
    }

    private void getKeys(Player target, CommandSender sender, String header, String messageContent) {
        List<String> message = new ArrayList<>();

        message.add(header);

        HashMap<Crate, Integer> keys = crazyManager.getVirtualKeys(target);

        boolean hasKeys = false;

        for (Map.Entry<Crate, Integer> entry : keys.entrySet()) {
            Crate crate = entry.getKey();
            int amount = entry.getValue();

            if (amount > 0) {
                hasKeys = true;
                HashMap<String, String> placeholders = new HashMap<>();
                placeholders.put("%Crate%", crate.getFile().getString("Crate.Name"));
                placeholders.put("%Keys%", amount + "");
                message.add(Messages.PER_CRATE.getMessageNoPrefix(placeholders));
            }
        }

        if (hasKeys) {
            sender.sendMessage(Messages.convertList(message));
        } else {
            sender.sendMessage(messageContent);
        }
    }
}
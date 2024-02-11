package com.badbones69.crazycrates.commands.subs.player;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.api.EventLogger;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.events.PlayerReceiveKeyEvent;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import com.badbones69.crazycrates.tasks.crates.CrateManager;
import com.google.common.collect.Lists;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.util.HashMap;
import java.util.List;

@Command(value = "chaves", alias = {"chave"})
public class BaseKeyCommand extends BaseCommand {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final CrateManager crateManager = this.plugin.getCrateManager();

    @NotNull
    private final EventLogger eventLogger = this.plugin.getEventLogger();

    @NotNull
    private final SettingsManager config = this.plugin.getConfigManager().getConfig();

    @Default
    @Permission("crazycrates.command.player.key")
    public void viewPersonal(@NotNull Player player) {
        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("%crates_opened%", String.valueOf(this.plugin.getCrazyHandler().getUserManager().getTotalCratesOpened(player.getUniqueId())));

        getKeys(player, player, Messages.no_virtual_keys_header.getMessage(placeholders).toString(), Messages.no_virtual_keys.getString());
    }

    @SubCommand("ver")
    @Permission("crazycrates.command.player.key.others")
    public void viewOthers(CommandSender sender, @Suggestion("online-players") Player target) {
        if (target == sender) {
            sender.sendMessage(Messages.same_player.getString());
            return;
        }

        HashMap<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", target.getName());
        placeholders.put("%crates_opened%", String.valueOf(this.plugin.getCrazyHandler().getUserManager().getTotalCratesOpened(target.getUniqueId())));

        String header = Messages.other_player_no_keys_header.getMessage(placeholders).toString();

        String otherPlayer = Messages.other_player_no_keys.getMessage("%player%", target.getName()).toString();

        getKeys(target, sender, header, otherPlayer);
    }

    @SubCommand("transferir")
    @Permission(value = "crazycrates.command.player.key.transfer")
    public void onPlayerTransferKeys(Player sender, @Suggestion("crates") String crateName, @Suggestion("online-players") Player player, @Suggestion("numbers") int amount) {
        Crate crate = this.crateManager.getCrateFromName(crateName);

        // If the crate is menu or null. we return
        if (crate == null || crate.getCrateType() == CrateType.menu) {
            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName).toString());
            return;
        }

        // If it's the same player, we return.
        if (player.getName().equalsIgnoreCase(sender.getName())) {
            sender.sendMessage(Messages.same_player.getString());
            return;
        }

        // If they don't have enough keys, we return.
        if (this.plugin.getCrazyHandler().getUserManager().getVirtualKeys(sender.getUniqueId(), crate.getName()) <= amount) {
            sender.sendMessage(Messages.transfer_not_enough_keys.getMessage("%key%", crate.getKey().getItemMeta().getDisplayName()).toString());
            return;
        }

        PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(player, crate, PlayerReceiveKeyEvent.KeyReceiveReason.TRANSFER, amount);
        this.plugin.getServer().getPluginManager().callEvent(event);

        // If the event is cancelled, We return.
        if (event.isCancelled()) {
            return;
        }

        this.plugin.getCrazyHandler().getUserManager().takeKeys(amount, sender.getUniqueId(), crate.getName(), KeyType.virtual_key, false);
        this.plugin.getCrazyHandler().getUserManager().addKeys(amount, player.getUniqueId(), crate.getName(), KeyType.virtual_key);

        HashMap<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%key%", crate.getKey().getItemMeta().getDisplayName());
        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%player%", player.getName());

        sender.sendMessage(Messages.transfer_sent_keys.getMessage(placeholders).toString());

        placeholders.put("%player%", sender.getName());

        player.sendMessage(Messages.transfer_received_keys.getMessage(placeholders).toString());

        this.eventLogger.logKeyEvent(player, sender, crate, KeyType.virtual_key, EventLogger.KeyEventType.KEY_EVENT_RECEIVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
    }

    /**
     * Get keys from player or sender or other player.
     *
     * @param player player to get keys.
     * @param sender sender to send message to.
     * @param header header of the message.
     * @param messageContent content of the message.
     */
    private void getKeys(Player player, CommandSender sender, String header, String messageContent) {
        List<String> message = Lists.newArrayList();

        message.add(header);

        HashMap<Crate, Integer> keys = new HashMap<>();

        this.plugin.getCrateManager().getCrates().forEach(crate -> keys.put(crate, this.plugin.getCrazyHandler().getUserManager().getVirtualKeys(player.getUniqueId(), crate.getName())));

        boolean hasKeys = false;

        for (Crate crate : keys.keySet()) {
            int amount = keys.get(crate);

            if (amount > 0) {
                HashMap<String, String> placeholders = new HashMap<>();

                hasKeys = true;

                placeholders.put("%crate%", crate.getFile().getString("Crate.Name"));
                placeholders.put("%keys%", String.valueOf(amount));
                placeholders.put("%crate_opened%", String.valueOf(this.plugin.getCrazyHandler().getUserManager().getCrateOpened(player.getUniqueId(), crate.getName())));
                message.add(Messages.per_crate.getMessage(placeholders).toString());
            }
        }

        if (hasKeys) {
            message.forEach(sender::sendMessage);
            return;
        }

        sender.sendMessage(messageContent);
    }
}
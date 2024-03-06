package com.badbones69.crazycrates.commands.subs;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.api.EventManager;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.events.PlayerReceiveKeyEvent;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.utils.MiscUtils;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import com.badbones69.crazycrates.tasks.crates.CrateManager;
import com.google.common.collect.Lists;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.Description;
import dev.triumphteam.cmd.core.annotation.Optional;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;
import us.crazycrew.crazycrates.api.users.UserManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Command(value = "chave", alias = {"chaves"})
@Description("Views the amount of keys you/others have.")
public class BaseKeyCommand extends BaseCommand {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final CrateManager crateManager = this.plugin.getCrateManager();

    @NotNull
    private final SettingsManager config = this.plugin.getConfigManager().getConfig();

    @NotNull
    private final UserManager userManager = this.plugin.getCrazyHandler().getUserManager();

    @Default
    @Permission("crazycrates.command.player.key")
    public void viewPersonal(@NotNull Player player) {
        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crates_opened%", String.valueOf(this.userManager.getTotalCratesOpened(player.getUniqueId())));

        getKeys(player, player, Messages.no_virtual_keys_header.getMessage(placeholders, player), Messages.no_virtual_keys.getMessage(player));
    }

    @SubCommand("ver")
    @Permission("crazycrates.command.player.key.others")
    public void viewOthers(CommandSender sender, @Optional @Suggestion("online-players") Player target) {
        if (target == sender || target == null) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Messages.must_be_a_player.getMessage());
                return;
            }

            viewPersonal(player);
            return;
        }

        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%player%", target.getName());
        placeholders.put("%crates_opened%", String.valueOf(this.userManager.getTotalCratesOpened(target.getUniqueId())));

        String header = Messages.other_player_no_keys_header.getMessage(placeholders, sender instanceof Player player ? player : null);

        String otherPlayer = Messages.other_player_no_keys.getMessage("%player%", target.getName(), sender instanceof Player player ? player : null);

        getKeys(target, sender, header, otherPlayer);
    }

    @SubCommand("transferir")
    @Permission(value = "crazycrates.command.player.key.transfer")
    public void onPlayerTransferKeys(Player sender, @Suggestion("crates") String crateName, @Suggestion("online-players") Player player, @Suggestion("numbers") int amount) {
        Crate crate = this.crateManager.getCrateFromName(crateName);

        // If the crate is menu or null. we return
        if (crate == null || crate.getCrateType() == CrateType.menu) {
            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, sender));
            return;
        }

        // If it's the same player, we return.
        if (player.getUniqueId().toString().equalsIgnoreCase(sender.getUniqueId().toString())) {
            sender.sendMessage(Messages.same_player.getMessage(sender));
            return;
        }

        Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%key%", crate.getKeyName());
        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%player%", player.getName());

        // If they don't have enough keys, we return.
        if (this.userManager.getVirtualKeys(sender.getUniqueId(), crate.getName()) < amount) {
            sender.sendMessage(Messages.transfer_not_enough_keys.getMessage(placeholders, sender));
            return;
        }

        PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(player, crate, PlayerReceiveKeyEvent.KeyReceiveReason.TRANSFER, amount);
        this.plugin.getServer().getPluginManager().callEvent(event);

        // If the event is cancelled, We return.
        if (event.isCancelled()) return;

        this.userManager.takeKeys(amount, sender.getUniqueId(), crate.getName(), KeyType.virtual_key, false);
        this.userManager.addKeys(amount, player.getUniqueId(), crate.getName(), KeyType.virtual_key);

        sender.sendMessage(Messages.transfer_sent_keys.getMessage(placeholders, sender));

        placeholders.put("%player%", sender.getName());

        player.sendMessage(Messages.transfer_received_keys.getMessage(placeholders, player));

        EventManager.logKeyEvent(player, sender, crate, KeyType.virtual_key, EventManager.KeyEventType.KEY_EVENT_RECEIVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
    }

    /**
     * Get keys from player or sender or other player.
     *
     * @param player         player to get keys.
     * @param sender         sender to send message to.
     * @param header         header of the message.
     * @param messageContent content of the message.
     */
    private void getKeys(Player player, CommandSender sender, String header, String messageContent) {
        List<String> message = Lists.newArrayList();

        message.add(header);

        Map<Crate, Integer> keys = new HashMap<>();

        this.plugin.getCrateManager().getUsableCrates().forEach(crate -> keys.put(crate, this.userManager.getVirtualKeys(player.getUniqueId(), crate.getName())));

        boolean hasKeys = false;

        for (Crate crate : keys.keySet()) {
            int amount = keys.get(crate);

            if (amount > 0) {
                Map<String, String> placeholders = new HashMap<>();

                hasKeys = true;

                placeholders.put("%crate%", crate.getFile().getString("Crate.Name"));
                placeholders.put("%keys%", String.valueOf(amount));
                placeholders.put("%crate_opened%", String.valueOf(this.userManager.getCrateOpened(player.getUniqueId(), crate.getName())));

                message.add(Messages.per_crate.getMessage(placeholders, player));
            }
        }

        if (MiscUtils.isPapiActive()) {
            if (sender instanceof Player person) {
                if (hasKeys) {
                    message.forEach(line -> person.sendMessage(PlaceholderAPI.setPlaceholders(person, line)));
                    return;
                }

                sender.sendMessage(PlaceholderAPI.setPlaceholders(person, messageContent));
            }

            return;
        }

        if (hasKeys) {
            message.forEach(sender::sendMessage);
            return;
        }

        sender.sendMessage(messageContent);
    }
}
package us.crazycrew.crazycrates.listeners.crates;

import us.crazycrew.crazycrates.CrazyCrates;
import us.crazycrew.crazycrates.managers.crates.CrateManager;
import us.crazycrew.crazycrates.api.enums.Messages;
import us.crazycrew.crazycrates.api.events.CrateOpenEvent;
import com.badbones69.crazycrates.api.objects.Crate;
import us.crazycrew.crazycrates.api.support.libraries.PluginSupport;
import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.CrazyHandler;
import us.crazycrew.crazycrates.other.MsgUtils;
import java.util.List;

public class CrateOpenListener implements Listener {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final CrazyHandler crazyHandler = this.plugin.getCrazyHandler();

    @NotNull
    private final CrateManager crateManager = this.plugin.getCrateManager();

    @EventHandler
    public void onCrateOpen(CrateOpenEvent event) {
        Player player = event.getPlayer();
        Crate crate = event.getCrate();

        if (crate.getCrateType() != CrateType.menu) {
            if (!crate.canWinPrizes(player)) {
                player.sendMessage(Messages.no_prizes_found.getString());
                this.crateManager.removePlayerFromOpeningList(player);
                this.crateManager.removePlayerKeyType(player);

                event.setCancelled(true);

                return;
            }
        }

        if (!(player.hasPermission("crazycrates.open." + crate.getName()) || player.hasPermission("crazycrates.open.*"))) {
            player.sendMessage(Messages.no_crate_permission.getString());
            this.crateManager.removePlayerFromOpeningList(player);
            this.crateManager.removeCrateInUse(player);

            event.setCancelled(true);

            return;
        }

        this.crateManager.addPlayerToOpeningList(player, crate);
        if (crate.getCrateType() != CrateType.cosmic) this.crazyHandler.getUserManager().addOpenedCrate(player.getUniqueId(), crate.getName());

        FileConfiguration configuration = event.getConfiguration();

        String broadcastMessage = configuration.getString("Crate.BroadCast", "");
        boolean broadcastToggle = configuration.contains("Crate.OpeningBroadCast") && configuration.getBoolean("Crate.OpeningBroadCast");

        if (broadcastToggle) {
            if (!broadcastMessage.isBlank()) {
                //noinspection deprecation
                this.plugin.getServer().broadcastMessage(MsgUtils.color(broadcastMessage.replaceAll("%prefix%", MsgUtils.getPrefix())).replaceAll("%player%", player.getName()));
            }
        }

        boolean commandToggle = configuration.contains("Crate.opening-command") && configuration.getBoolean("Crate.opening-command.toggle");

        if (commandToggle) {
            List<String> commands = configuration.getStringList("Crate.opening-command.commands");

            if (!commands.isEmpty()) {
                commands.forEach(line -> {
                    String builder;

                    if (PluginSupport.PLACEHOLDERAPI.isPluginEnabled()) {
                        builder = PlaceholderAPI.setPlaceholders(player, line.replaceAll("%prefix%", MsgUtils.getPrefix()).replaceAll("%player%", player.getName()));
                    } else {
                        builder = line.replaceAll("%prefix%", MsgUtils.getPrefix()).replaceAll("%player%", player.getName());
                    }

                    this.plugin.getServer().dispatchCommand(this.plugin.getServer().getConsoleSender(), builder);
                });
            }
        }
    }
}
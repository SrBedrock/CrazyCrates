package com.badbones69.crazycrates.listeners;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.api.builders.types.CrateMainMenu;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.events.KeyCheckEvent;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.objects.other.CrateLocation;
import com.badbones69.crazycrates.api.utils.MiscUtils;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import com.badbones69.crazycrates.tasks.InventoryManager;
import com.badbones69.crazycrates.tasks.crates.CrateManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.util.HashMap;

public class CrateControlListener implements Listener {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final InventoryManager inventoryManager = this.plugin.getCrazyHandler().getInventoryManager();

    @NotNull
    private final SettingsManager config = this.plugin.getConfigManager().getConfig();

    @NotNull
    private final CrateManager crateManager = this.plugin.getCrateManager();

    // This must run as highest, so it doesn't cause other plugins to check
    // the items that were added to the players inventory and replaced the item in the player's hand.
    // This is only an issue with QuickCrate
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCrateOpen(final PlayerInteractEvent e) {
        final Player player = e.getPlayer();
        if (e.getHand() == EquipmentSlot.OFF_HAND) return;
        final Block clickedBlock = e.getClickedBlock();

        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // Loops through all loaded physical locations.
            for (final CrateLocation location : this.plugin.getCrateManager().getCrateLocations()) {
                // Checks to see if the clicked block is the same as a physical crate.
                if (clickedBlock != null && location.getLocation().equals(clickedBlock.getLocation())) {
                    // Checks to see if the player is removing a crate location.
                    if (player.getGameMode() == GameMode.CREATIVE && player.isSneaking() && player.hasPermission("crazycrates.admin")) {
                        e.setCancelled(true);
                        this.plugin.getCrateManager().removeCrateLocation(location.getID());
                        player.sendMessage(Messages.removed_physical_crate.getMessage("%id%", location.getID(), player));
                        return;
                    }

                    e.setCancelled(true);

                    if (location.getCrateType() != CrateType.menu) {
                        final Crate crate = location.getCrate();

                        if (location.getCrate().isPreviewEnabled()) {
                            this.inventoryManager.addViewer(player);
                            this.inventoryManager.openNewCratePreview(player, location.getCrate(), crate.getCrateType() == CrateType.cosmic || crate.getCrateType() == CrateType.casino);
                        } else {
                            player.sendMessage(Messages.preview_disabled.getMessage(player));
                        }
                    }
                }
            }
        } else if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            //Checks to see if the clicked block is a physical crate.
            CrateLocation crateLocation = null;

            //todo() if someone complains, remove null check.
            if (clickedBlock != null) {
                crateLocation = this.crateManager.getCrateLocation(clickedBlock.getLocation());
            }

            if (crateLocation != null && crateLocation.getCrate() != null) {
                final Crate crate = crateLocation.getCrate();
                e.setCancelled(true);

                if (crate.getCrateType() == CrateType.menu) {
                    //This is to stop players in QuadCrate to not be able to try and open a crate set to menu.
                    if (!this.crateManager.isInOpeningList(player) && this.config.getProperty(ConfigKeys.enable_crate_menu)) {
                        final CrateMainMenu crateMainMenu = new CrateMainMenu(player, this.config.getProperty(ConfigKeys.inventory_size), this.config.getProperty(ConfigKeys.inventory_name));

                        player.openInventory(crateMainMenu.build().getInventory());
                    } else {
                        player.sendMessage(Messages.feature_disabled.getMessage(player));
                    }

                    return;
                }

                final KeyCheckEvent event = new KeyCheckEvent(player, crateLocation);
                player.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    boolean hasKey = false;
                    boolean useQuickCrateAgain = false;
                    final String keyName = crate.getKeyName();

                    final int requiredKeys = this.plugin.getCrateManager().getCrateFromName(crate.getName()).getRequiredKeys();

                    final int totalKeys = this.plugin.getCrazyHandler().getUserManager().getTotalKeys(player.getUniqueId(), crate.getName());

                    if (requiredKeys > 0 && totalKeys < requiredKeys) {
                        final HashMap<String, String> placeholders = new HashMap<>();
                        placeholders.put("%key-amount%", String.valueOf(requiredKeys));
                        placeholders.put("%crate%", crate.getPreviewName());
                        placeholders.put("%amount%", String.valueOf(totalKeys));

                        player.sendMessage(Messages.required_keys.getMessage(placeholders, player));
                        return;
                    }

                    if (this.config.getProperty(ConfigKeys.physical_accepts_virtual_keys) && this.plugin.getCrazyHandler().getUserManager().getVirtualKeys(player.getUniqueId(), crate.getName()) >= 1) {
                        hasKey = true;
                    }

                    if (hasKey) {
                        // Checks if the player uses the quick crate again.
                        if (this.crateManager.isInOpeningList(player) && this.crateManager.getOpeningCrate(player).getCrateType() == CrateType.quick_crate && this.crateManager.isCrateInUse(player) && this.crateManager.getCrateInUseLocation(player).equals(crateLocation.getLocation())) {
                            useQuickCrateAgain = true;
                        }

                        if (!useQuickCrateAgain) {
                            if (this.crateManager.isInOpeningList(player)) {
                                player.sendMessage(Messages.already_opening_crate.getMessage("%key%", keyName, player));
                                return;
                            }

                            if (this.crateManager.getCratesInUse().containsValue(crateLocation.getLocation())) {
                                player.sendMessage(Messages.quick_crate_in_use.getMessage(player));
                                return;
                            }
                        }

                        if (MiscUtils.isInventoryFull(player)) {
                            player.sendMessage(Messages.inventory_not_empty.getMessage(player));
                            return;
                        }

                        if (useQuickCrateAgain) {
                            this.plugin.getCrateManager().endQuickCrate(player, crateLocation.getLocation(), crate, true);
                        }

                        final KeyType keyType = KeyType.virtual_key;

                        // Only cosmic crate type uses this method.
                        if (crate.getCrateType() == CrateType.cosmic) {
                            this.crateManager.addPlayerKeyType(player, keyType);
                        }

                        this.crateManager.addPlayerToOpeningList(player, crate);

                        this.crateManager.openCrate(player, crate, keyType, crateLocation.getLocation(), false, true);
                    } else {
                        if (crate.getCrateType() != CrateType.crate_on_the_go) {
                            if (this.config.getProperty(ConfigKeys.knock_back)) {
                                knockBack(player, clickedBlock.getLocation());
                            }

                            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                                player.playSound(player.getLocation(), Sound.valueOf(this.config.getProperty(ConfigKeys.need_key_sound)), SoundCategory.PLAYERS, 1f, 1f);
                            }

                            player.sendMessage(Messages.no_keys.getMessage("%key%", keyName, player));
                        }
                    }
                }
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(final PlayerQuitEvent e) {
        final Player player = e.getPlayer();

        if (this.crateManager.hasCrateTask(player)) {
            this.crateManager.endCrate(player);
        }

        if (this.crateManager.hasQuadCrateTask(player)) {
            this.crateManager.endQuadCrate(player);
        }

        if (this.crateManager.isInOpeningList(player)) {
            this.crateManager.removePlayerFromOpeningList(player);
        }
    }

    private void knockBack(final Player player, final Location location) {
        final Vector vector = player.getLocation().toVector().subtract(location.toVector()).normalize().multiply(1).setY(.1);

        if (player.isInsideVehicle() && player.getVehicle() != null) {
            player.getVehicle().setVelocity(vector);
            return;
        }

        player.setVelocity(vector);
    }
}
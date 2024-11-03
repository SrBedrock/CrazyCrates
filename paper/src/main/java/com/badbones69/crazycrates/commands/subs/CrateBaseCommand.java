package com.badbones69.crazycrates.commands.subs;

import ch.jalu.configme.SettingsManager;
import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.CrazyHandler;
import com.badbones69.crazycrates.api.EventManager;
import com.badbones69.crazycrates.api.FileManager;
import com.badbones69.crazycrates.api.FileManager.Files;
import com.badbones69.crazycrates.api.PrizeManager;
import com.badbones69.crazycrates.api.builders.types.CrateAdminMenu;
import com.badbones69.crazycrates.api.builders.types.CrateMainMenu;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.enums.Permissions;
import com.badbones69.crazycrates.api.events.PlayerPrizeEvent;
import com.badbones69.crazycrates.api.events.PlayerReceiveKeyEvent;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.objects.Prize;
import com.badbones69.crazycrates.api.objects.other.CrateLocation;
import com.badbones69.crazycrates.api.utils.FileUtils;
import com.badbones69.crazycrates.api.utils.MiscUtils;
import com.badbones69.crazycrates.api.utils.MsgUtils;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import com.badbones69.crazycrates.tasks.crates.CrateManager;
import dev.triumphteam.cmd.bukkit.annotation.Permission;
import dev.triumphteam.cmd.core.BaseCommand;
import dev.triumphteam.cmd.core.annotation.ArgName;
import dev.triumphteam.cmd.core.annotation.Command;
import dev.triumphteam.cmd.core.annotation.Default;
import dev.triumphteam.cmd.core.annotation.Description;
import dev.triumphteam.cmd.core.annotation.Optional;
import dev.triumphteam.cmd.core.annotation.SubCommand;
import dev.triumphteam.cmd.core.annotation.Suggestion;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.permissions.PermissionDefault;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;
import us.crazycrew.crazycrates.api.users.UserManager;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

@Command(value = "crates", alias = {"crazycrates", "crazycrate", "crate", "cc"})
@Description("The base command for CrazyCrates")
public class CrateBaseCommand extends BaseCommand {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final CrazyHandler crazyHandler = this.plugin.getCrazyHandler();

    @NotNull
    private final UserManager userManager = this.plugin.getUserManager();

    @NotNull
    private final CrateManager crateManager = this.plugin.getCrateManager();

    @NotNull
    private final FileManager fileManager = this.plugin.getFileManager();

    @NotNull
    private final SettingsManager config = this.plugin.getConfigManager().getConfig();

    @NotNull
    private final FileConfiguration locations = Files.LOCATIONS.getFile();

    @Default
    @Permission(value = "crazycrates.command.player.menu", def = PermissionDefault.TRUE)
    public void onDefaultMenu(final Player player) {
        if (this.config.getProperty(ConfigKeys.enable_crate_menu)) {
            final CrateMainMenu crateMainMenu = new CrateMainMenu(player, this.config.getProperty(ConfigKeys.inventory_size), this.config.getProperty(ConfigKeys.inventory_name));

            player.openInventory(crateMainMenu.build().getInventory());

            return;
        }

        player.sendMessage(Messages.feature_disabled.getMessage(player));
    }

    @SubCommand("help")
    @Permission(value = "crazycrates.help", def = PermissionDefault.TRUE)
    public void onHelp(final CommandSender sender) {
        if (sender instanceof final ConsoleCommandSender commandSender) {
            commandSender.sendMessage(Messages.admin_help.getMessage());

            return;
        }

        if (sender instanceof final Player player) {
            if (player.hasPermission("crazycrates.admin-access")) {
                player.sendMessage(Messages.admin_help.getMessage(player));

                return;
            }

            player.sendMessage(Messages.help.getMessage(player));
        }
    }

    @SubCommand("transfer")
    @Permission(value = "crazycrates.command.player.transfer", def = PermissionDefault.OP)
    public void onPlayerTransferKeys(final Player sender, @Suggestion("crates") final String crateName, @Suggestion("online-players") final Player player, @Suggestion("numbers") final int amount) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);

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

        // If they don't have enough keys, we return.
        if (this.userManager.getVirtualKeys(sender.getUniqueId(), crate.getName()) <= amount) {
            sender.sendMessage(Messages.transfer_not_enough_keys.getMessage("%crate%", crate.getName(), sender));

            return;
        }

        final PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(player, crate, PlayerReceiveKeyEvent.KeyReceiveReason.TRANSFER, amount);
        this.plugin.getServer().getPluginManager().callEvent(event);

        // If the event is cancelled, We return.
        if (event.isCancelled()) return;

        this.userManager.takeKeys(amount, sender.getUniqueId(), crate.getName(), KeyType.virtual_key, false);
        this.userManager.addKeys(amount, player.getUniqueId(), crate.getName(), KeyType.virtual_key);

        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%player%", player.getName());

        sender.sendMessage(Messages.transfer_sent_keys.getMessage(placeholders, sender));

        placeholders.put("%player%", sender.getName());

        player.sendMessage(Messages.transfer_received_keys.getMessage("%player%", sender.getName(), player));

        EventManager.logKeyEvent(player, sender, crate, KeyType.virtual_key, EventManager.KeyEventType.KEY_EVENT_RECEIVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
    }

    @SubCommand("reload")
    @Permission(value = "crazycrates.command.admin.reload", def = PermissionDefault.OP)
    public void onReload(final CommandSender sender) {
        this.plugin.getConfigManager().reload();
        this.crazyHandler.cleanFiles();

        // Close previews
        if (this.config.getProperty(ConfigKeys.take_out_of_preview)) {
            this.plugin.getServer().getOnlinePlayers().forEach(player -> {
                this.crazyHandler.getInventoryManager().closeCratePreview(player);

                if (this.config.getProperty(ConfigKeys.send_preview_taken_out_message)) {
                    player.sendMessage(Messages.reloaded_forced_out_of_preview.getMessage(player));
                }
            });
        }

        this.crateManager.loadCrates();

        if (sender instanceof final Player player) {
            player.sendMessage(Messages.reloaded_plugin.getMessage(player));
            return;
        }

        sender.sendMessage(Messages.reloaded_plugin.getMessage());
    }

    @SubCommand("debug")
    @Permission(value = "crazycrates.command.admin.debug", def = PermissionDefault.OP)
    public void onDebug(final Player player, @Suggestion("crates") final String crateName) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null) {
            player.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, player));

            return;
        }

        crate.getPrizes().forEach(prize -> PrizeManager.givePrize(player, prize, crate));
    }

    @SubCommand("save")
    @Permission(value = "crazycrates.save", def = PermissionDefault.OP)
    public void onSchematicSave(final Player player) {
        player.sendMessage(MsgUtils.color("&cThis feature is not yet developed internally by &eRyder Belserion."));
    }

    /*@SubCommand("wand")
    @Permission(value = "crazycrates.wand", def = PermissionDefault.OP)
    public void onWandGive(Player player) {
        player.getInventory().addItem(getItem(PersistentKeys.selector_wand.getNamespacedKey(), Material.DIAMOND_AXE, "&c&lPoint Selector"));
        player.getInventory().addItem(getItem(PersistentKeys.crate_prize.getNamespacedKey(), Material.IRON_AXE, "&c&lTest Wand"));
    }

    private ItemStack getItem(NamespacedKey key, Material material, String name) {
        ItemBuilder builder = new ItemBuilder();

        builder.setMaterial(material).setName(name).setLore(List.of(
                "&eSelect &cpoint #1 &eand &cpoint #2 &eto create a schematic.",
                "&eOnce you select 2 points, Stand in the center",
                "&eand run &c/schem-save to save your schematic."
        ));

        ItemMeta itemMeta = builder.getItemMeta();

        PersistentDataContainer container = itemMeta.getPersistentDataContainer();

        container.set(key, PersistentDataType.STRING, "none");

        builder.setItemMeta(itemMeta);

        return builder.build();
    }*/

    @SubCommand("admin")
    @Permission(value = "crazycrates.command.admin.access", def = PermissionDefault.OP)
    public void onAdminMenu(final Player player) {
        final CrateAdminMenu inventory = new CrateAdminMenu(player, 54, "&c&lAdmin Keys");

        player.openInventory(inventory.build().getInventory());
    }

    @SubCommand("list")
    @Permission(value = "crazycrates.command.admin.list", def = PermissionDefault.OP)
    public void onAdminList(final CommandSender sender) {
        final StringBuilder crates = new StringBuilder();
        final String brokeCrates;

        this.crateManager.getUsableCrates().forEach(crate -> crates.append("&a").append(crate.getName()).append("&8, "));

        final StringBuilder brokeCratesBuilder = new StringBuilder();

        this.crateManager.getBrokeCrates().forEach(crate -> brokeCratesBuilder.append("&c").append(crate).append(".yml&8,"));

        brokeCrates = brokeCratesBuilder.toString();

        sender.sendMessage(MsgUtils.color("&e&lCrates:&f " + crates));

        if (!brokeCrates.isEmpty())
            sender.sendMessage(MsgUtils.color("&6&lBroken Crates:&f " + brokeCrates.substring(0, brokeCrates.length() - 2)));

        sender.sendMessage(MsgUtils.color("&e&lAll Crate Locations:"));
        sender.sendMessage(MsgUtils.color("&c[ID]&8, &c[Crate]&8, &c[World]&8, &c[X]&8, &c[Y]&8, &c[Z]"));
        int line = 1;

        for (final CrateLocation loc : this.crateManager.getCrateLocations()) {
            final Crate crate = loc.getCrate();
            final String world = loc.getLocation().getWorld().getName();

            final int x = loc.getLocation().getBlockX();
            final int y = loc.getLocation().getBlockY();
            final int z = loc.getLocation().getBlockZ();

            sender.sendMessage(MsgUtils.color("&8[&b" + line + "&8]: " + "&c" + loc.getID() + "&8, &c" + crate.getName() + "&8, &c" + world + "&8, &c" + x + "&8, &c" + y + "&8, &c" + z));
            line++;
        }
    }

    @SubCommand("tp")
    @Permission(value = "crazycrates.command.admin.teleport", def = PermissionDefault.OP)
    public void onAdminTeleport(final Player player, @Suggestion("locations") final String id) {
        if (!this.locations.contains("Locations")) {
            this.locations.set("Locations.Clear", null);

            Files.LOCATIONS.saveFile();
        }

        for (final String name : this.locations.getConfigurationSection("Locations").getKeys(false)) {
            if (name.equalsIgnoreCase(id)) {
                final World world = this.plugin.getServer().getWorld(Objects.requireNonNull(this.locations.getString("Locations." + name + ".World")));

                final int x = this.locations.getInt("Locations." + name + ".X");
                final int y = this.locations.getInt("Locations." + name + ".Y");
                final int z = this.locations.getInt("Locations." + name + ".Z");

                final Location loc = new Location(world, x, y, z);

                player.teleport(loc.add(.5, 0, .5));

                player.sendMessage(MsgUtils.getPrefix("&7You have been teleported to &6" + name + "&7."));

                return;
            }
        }

        player.sendMessage(MsgUtils.getPrefix("&cThere is no location called &6" + id + "&c."));
    }

    @SubCommand("additem")
    @Permission(value = "crazycrates.command.admin.additem", def = PermissionDefault.OP)
    public void onAdminCrateAddItem(final Player player, @Suggestion("crates") final String crateName, @Suggestion("prizes") final String prize, @Suggestion("numbers") final int chance, @Optional @Suggestion("tiers") final String tier) {
        final ItemStack item = player.getInventory().getItemInMainHand();

        if (item.getType() == Material.AIR) {
            player.sendMessage(Messages.no_item_in_hand.getMessage(player));

            return;
        }

        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null) {
            player.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, player));

            return;
        }

        try {
            if (tier == null) {
                crate.addEditorItem(prize, item, chance);
            } else {
                crate.addEditorItem(prize, item, crate.getTier(tier), chance);
            }
        } catch (final Exception exception) {
            this.plugin.getServer().getLogger().log(Level.WARNING, "Failed to add a new prize to the " + crate.getName() + " crate.", exception);

            return;
        }

        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%prize%", prize);

        player.sendMessage(Messages.added_item_with_editor.getMessage(placeholders, player));
    }

    @SubCommand("preview")
    @Permission(value = "crazycrates.command.admin.preview", def = PermissionDefault.OP)
    public void onAdminCratePreview(final CommandSender sender, @Suggestion("crates") final String crateName, @Suggestion("online-players") final Player player) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            if (sender instanceof final Player person) {
                person.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, person));

                return;
            }

            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName));

            return;
        }

        if (!crate.isPreviewEnabled()) {
            if (sender instanceof final Player person) {
                person.sendMessage(Messages.preview_disabled.getMessage(player));

                return;
            }

            sender.sendMessage(Messages.preview_disabled.getMessage());

            return;
        }

        this.crazyHandler.getInventoryManager().addViewer(player);
        this.crazyHandler.getInventoryManager().openNewCratePreview(player, crate, crate.getCrateType() == CrateType.cosmic || crate.getCrateType() == CrateType.casino);
    }

    @SubCommand("open-others")
    @Permission(value = "crazycrates.command.admin.open.others", def = PermissionDefault.OP)
    public void onAdminCrateOpenOthers(final CommandSender sender, @Suggestion("crates") final String crateName, @Suggestion("online-players") final Player player, @Optional @Suggestion("key-types") final KeyType keyType) {
        if (sender == player && keyType != KeyType.free_key) {
            onAdminCrateOpen(player, crateName);

            return;
        }

        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (player == null) {
            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.not_online.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.not_online.getMessage());

            return;
        }

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            if (sender instanceof final Player person) {
                person.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, person));

                return;
            }

            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName));

            return;
        }

        if (crate.getCrateType() == CrateType.crate_on_the_go || crate.getCrateType() == CrateType.quick_crate || crate.getCrateType() == CrateType.fire_cracker || crate.getCrateType() == CrateType.quad_crate) {
            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.cant_be_a_virtual_crate.getMessage());

            return;
        }

        if (this.crateManager.isInOpeningList(player)) {
            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.already_opening_crate.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.already_opening_crate.getMessage());

            return;
        }

        final CrateType crateType = crate.getCrateType();

        if (crateType == null) {
            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.internal_error.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.internal_error.getMessage());

            this.plugin.getLogger().severe("An error has occurred: The crate type is null for the crate named " + crate.getName());

            return;
        }

        boolean hasKey = false;
        KeyType type = keyType != null ? keyType : KeyType.physical_key;

        if (type == KeyType.free_key) {
            this.crateManager.openCrate(player, crate, type, player.getLocation(), true, false);

            final HashMap<String, String> placeholders = new HashMap<>();

            placeholders.put("%Crate%", crate.getName());
            placeholders.put("%Player%", player.getName());

            player.sendMessage(Messages.opened_a_crate.getMessage(placeholders, player));

            EventManager.logKeyEvent(player, player, crate, type, EventManager.KeyEventType.KEY_EVENT_REMOVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));

            return;
        }

        final boolean hasVirtual = this.userManager.getVirtualKeys(player.getUniqueId(), crate.getName()) >= 1;

        final boolean hasPhysical = this.userManager.hasPhysicalKey(player.getUniqueId(), crate.getName(), false);

        if (hasVirtual) {
            hasKey = true;
        } else {
            if (this.config.getProperty(ConfigKeys.virtual_accepts_physical_keys)) {
                if (hasPhysical) {
                    hasKey = true;
                    type = KeyType.physical_key;
                }
            }
        }

        if (!hasKey) {
            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                player.playSound(player.getLocation(), Sound.valueOf(this.config.getProperty(ConfigKeys.need_key_sound)), SoundCategory.PLAYERS, 1f, 1f);
            }

            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.no_virtual_key.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.no_virtual_key.getMessage());

            return;
        }

        if (MiscUtils.isInventoryFull(player)) {
            if (sender instanceof final Player person) {
                sender.sendMessage(Messages.inventory_not_empty.getMessage(person));

                return;
            }

            sender.sendMessage(Messages.inventory_not_empty.getMessage());

            return;
        }

        this.crateManager.openCrate(player, crate, type, player.getLocation(), true, false);

        final HashMap<String, String> placeholders = new HashMap<>();

        placeholders.put("%Crate%", crate.getName());
        placeholders.put("%Player%", player.getName());

        player.sendMessage(Messages.opened_a_crate.getMessage(placeholders, player));

        EventManager.logKeyEvent(player, player, crate, type, EventManager.KeyEventType.KEY_EVENT_REMOVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
    }

    @SubCommand("open")
    @Permission(value = "crazycrates.command.admin.open", def = PermissionDefault.OP)
    public void onAdminCrateOpen(final Player player, @Suggestion("crates") final String crateName) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            player.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, player));

            return;
        }

        if (crate.getCrateType() == CrateType.crate_on_the_go || crate.getCrateType() == CrateType.quick_crate || crate.getCrateType() == CrateType.fire_cracker || crate.getCrateType() == CrateType.quad_crate) {
            player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));

            return;
        }

        if (this.crateManager.isInOpeningList(player)) {
            player.sendMessage(Messages.already_opening_crate.getMessage(player));

            return;
        }

        final CrateType type = crate.getCrateType();

        if (type == null) {
            player.sendMessage(Messages.internal_error.getMessage(player));

            this.plugin.getLogger().severe("An error has occurred: The crate type is null for the crate named " + crate.getName());

            return;
        }

        boolean hasKey = false;
        KeyType keyType = KeyType.virtual_key;

        if (this.userManager.getVirtualKeys(player.getUniqueId(), crate.getName()) >= 1) {
            hasKey = true;
        } else {
            if (this.config.getProperty(ConfigKeys.virtual_accepts_physical_keys)) {
                if (this.userManager.hasPhysicalKey(player.getUniqueId(), crate.getName(), false)) {
                    hasKey = true;
                    keyType = KeyType.physical_key;
                }
            }
        }

        if (!hasKey) {
            if (this.config.getProperty(ConfigKeys.need_key_sound_toggle)) {
                player.playSound(player.getLocation(), Sound.valueOf(this.config.getProperty(ConfigKeys.need_key_sound)), SoundCategory.PLAYERS, 1f, 1f);
            }

            player.sendMessage(Messages.no_virtual_key.getMessage(player));

            return;
        }

        if (MiscUtils.isInventoryFull(player)) {
            player.sendMessage(Messages.inventory_not_empty.getMessage(player));

            return;
        }

        this.crateManager.openCrate(player, crate, keyType, player.getLocation(), true, false);

        EventManager.logKeyEvent(player, player, crate, keyType, EventManager.KeyEventType.KEY_EVENT_REMOVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
    }

    @SubCommand("mass-open")
    @Permission(value = "crazycrates.command.admin.massopen", def = PermissionDefault.OP)
    public void onAdminCrateMassOpen(final Player player, @Suggestion("crates") final String crateName, @Suggestion("key-types") final String keyType, @Suggestion("numbers") final int amount) {
        final KeyType type = KeyType.getFromName(keyType);

        if (type == null || type == KeyType.free_key) {
            player.sendMessage(MsgUtils.color(MsgUtils.getPrefix() + "&cPlease use Virtual/V or Physical/P for a Key type."));

            return;
        }

        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            player.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, player));

            return;
        }

        if (crate.getCrateType() == CrateType.crate_on_the_go || crate.getCrateType() == CrateType.quick_crate || crate.getCrateType() == CrateType.fire_cracker || crate.getCrateType() == CrateType.quad_crate) {
            player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));

            return;
        }

        this.crateManager.addPlayerToOpeningList(player, crate);

        int keys = type == KeyType.physical_key ? this.userManager.getPhysicalKeys(player.getUniqueId(), crate.getName()) : this.userManager.getVirtualKeys(player.getUniqueId(), crate.getName());
        int keysUsed = 0;

        if (keys == 0) {
            player.sendMessage(Messages.no_virtual_key.getMessage(player));

            return;
        }

        for (; keys > 0; keys--) {
            if (MiscUtils.isInventoryFull(player)) break;
            if (keysUsed >= amount) break;
            if (keysUsed >= crate.getMaxMassOpen()) break;

            final Prize prize = crate.pickPrize(player);

            PrizeManager.givePrize(player, prize, crate);

            this.plugin.getServer().getPluginManager().callEvent(new PlayerPrizeEvent(player, crate, crate.getName(), prize));

            if (prize.useFireworks()) MiscUtils.spawnFirework((player).getLocation().clone().add(.5, 1, .5), null);

            keysUsed++;
        }

        if (!this.userManager.takeKeys(keysUsed, player.getUniqueId(), crate.getName(), type, false)) {
            MiscUtils.failedToTakeKey(player, crate);

            this.crateManager.removeCrateInUse(player);
            this.crateManager.removePlayerFromOpeningList(player);

            return;
        }

        this.crateManager.removePlayerFromOpeningList(player);
    }

    @SubCommand("forceopen")
    @Permission(value = "crazycrates.command.admin.forceopen", def = PermissionDefault.OP)
    public void onAdminForceOpen(final CommandSender sender, @Suggestion("crates") final String crateName, @Suggestion("online-players") final Player player) {
        onAdminCrateOpenOthers(sender, crateName, player, KeyType.free_key);
    }

    @SubCommand("set")
    @Permission(value = "crazycrates.command.admin.set", def = PermissionDefault.OP)
    public void onAdminCrateSet(final Player player, @Suggestion("crates") final String crateName) {
        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null) {
            player.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, player));

            return;
        }

        final Block block = player.getTargetBlock(null, 5);

        if (block.isEmpty()) {
            player.sendMessage(Messages.must_be_looking_at_block.getMessage(player));

            return;
        }

        this.crateManager.addCrateLocation(block.getLocation(), crate);

        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%prefix%", MsgUtils.getPrefix());

        player.sendMessage(Messages.created_physical_crate.getMessage(placeholders, player));
    }

    @SubCommand("give-random")
    @Permission(value = "crazycrates.command.admin.giverandomkey", def = PermissionDefault.OP)
    public void onAdminCrateGiveRandom(final CommandSender sender, @Suggestion("key-types") final String keyType, @Suggestion("numbers") final int amount, @Suggestion("online-players") final CustomPlayer target) {
        final Crate crate = this.crateManager.getUsableCrates().get((int) MiscUtils.pickNumber(0, (this.crateManager.getUsableCrates().size() - 2)));

        onAdminCrateGive(sender, keyType, crate.getName(), amount, target);
    }

    @SubCommand("give")
    @Permission(value = "crazycrates.command.admin.givekey", def = PermissionDefault.OP)
    public void onAdminCrateGive(final CommandSender sender, @Suggestion("key-types") final String keyType, @Suggestion("crates") final String crateName, @Suggestion("numbers") final int amount, @Optional @Suggestion("online-players") CustomPlayer target) {
        final KeyType type = KeyType.getFromName(keyType);
        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (type == null || type == KeyType.free_key) {
            sender.sendMessage(MsgUtils.color(MsgUtils.getPrefix() + "&cPlease use Virtual/V or Physical/P for a Key type."));
            return;
        }

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            if (sender instanceof final Player human) {
                human.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, human));

                return;
            }

            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName));

            return;
        }

        if (amount <= 0) {
            if (sender instanceof final Player human) {
                human.sendMessage(Messages.not_a_number.getMessage("%number%", String.valueOf(amount), human));

                return;
            }

            sender.sendMessage(Messages.not_a_number.getMessage("%number%", String.valueOf(amount)));

            return;
        }

        if (target == null) {
            target = new CustomPlayer(sender.getName());
        }

        if (target.getPlayer() != null) {
            final Player player = target.getPlayer();

            addKey(sender, player, null, crate, type, amount);

            return;
        }

        final OfflinePlayer offlinePlayer = target.getOfflinePlayer();

        addKey(sender, null, offlinePlayer, crate, type, amount);
    }

    private void addKey(final CommandSender sender, final Player player, final OfflinePlayer offlinePlayer, final Crate crate, final KeyType type, final int amount) {
        final PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(player, crate, PlayerReceiveKeyEvent.KeyReceiveReason.GIVE_COMMAND, amount);

        this.plugin.getServer().getPluginManager().callEvent(event);

        if (event.isCancelled()) return;

        if (player != null) {
            if (crate.getCrateType() == CrateType.crate_on_the_go) {
                player.getInventory().addItem(crate.getKey(amount, player));
            } else {
                this.userManager.addKeys(amount, player.getUniqueId(), crate.getName(), type);
            }

            final HashMap<String, String> placeholders = new HashMap<>();

            placeholders.put("%amount%", String.valueOf(amount));
            placeholders.put("%player%", player.getName());
            placeholders.put("%key%", crate.getKeyName());

            final boolean fullMessage = this.config.getProperty(ConfigKeys.notify_player_when_inventory_full);
            final boolean inventoryCheck = this.config.getProperty(ConfigKeys.give_virtual_keys_when_inventory_full);

            if (sender instanceof final Player person) {
                person.sendMessage(Messages.gave_a_player_keys.getMessage(placeholders, person));
            } else {
                sender.sendMessage(Messages.gave_a_player_keys.getMessage(placeholders));
            }

            if (!inventoryCheck || !fullMessage && !MiscUtils.isInventoryFull(player) && player.isOnline())
                player.sendMessage(Messages.obtaining_keys.getMessage(placeholders, player));

            EventManager.logKeyEvent(player, sender, crate, type, EventManager.KeyEventType.KEY_EVENT_GIVEN, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));

            return;
        }

        if (!this.userManager.addOfflineKeys(offlinePlayer.getUniqueId(), crate.getName(), amount, type)) {
            if (sender instanceof final Player person) {
                person.sendMessage(Messages.internal_error.getMessage(person));
            } else {
                sender.sendMessage(Messages.internal_error.getMessage());
            }
        } else {
            final Map<String, String> placeholders = new HashMap<>();

            placeholders.put("%amount%", String.valueOf(amount));
            placeholders.put("%player%", offlinePlayer.getName());

            if (sender instanceof final Player person) {
                person.sendMessage(Messages.given_offline_player_keys.getMessage(placeholders, person));
            } else {
                sender.sendMessage(Messages.given_offline_player_keys.getMessage(placeholders));
            }

            EventManager.logKeyEvent(offlinePlayer, sender, crate, type, EventManager.KeyEventType.KEY_EVENT_GIVEN, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
        }
    }

    @SubCommand("take")
    @Permission(value = "crazycrates.command.admin.takekey", def = PermissionDefault.OP)
    public void onAdminCrateTake(final CommandSender sender, @Suggestion("key-types") final String keyType, @Suggestion("crates") final String crateName, @Suggestion("numbers") final int amount, @Optional @Suggestion("online-players") CustomPlayer target) {
        final KeyType type = KeyType.getFromName(keyType);

        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (type == null || type == KeyType.free_key) {
            sender.sendMessage(MsgUtils.color(MsgUtils.getPrefix() + "&cPlease use Virtual/V or Physical/P for a Key type."));

            return;
        }

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            if (sender instanceof final Player human) {
                human.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, human));

                return;
            }

            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName));

            return;
        }

        if (amount <= 0) {
            if (sender instanceof final Player human) {
                human.sendMessage(Messages.not_a_number.getMessage("%number%", String.valueOf(amount), human));

                return;
            }

            sender.sendMessage(Messages.not_a_number.getMessage("%number%", String.valueOf(amount)));

            return;
        }

        if (target == null) {
            target = new CustomPlayer(sender.getName());
        }

        if (target.getPlayer() != null) {
            final Player player = target.getPlayer();

            takeKey(sender, player, null, crate, type, amount);

            return;
        }

        takeKey(sender, null, target.getOfflinePlayer(), crate, type, amount);
    }

    /**
     * Take keys from a player whether offline or not.
     *
     * @param sender        the sender of the command.
     * @param player        the target of the command.
     * @param offlinePlayer the other target of the command.
     * @param crate         the crate.
     * @param type          the type of key.
     * @param amount        the amount of keys.
     */
    private void takeKey(final CommandSender sender, @Nullable final Player player, final OfflinePlayer offlinePlayer, final Crate crate, final KeyType type, int amount) {
        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%crate%", crate.getName());
        placeholders.put("%key%", crate.getKeyName());

        if (player != null) {
            placeholders.put("%player%", player.getName());

            final int totalKeys = this.userManager.getTotalKeys(player.getUniqueId(), crate.getName());

            if (totalKeys < 1) {
                this.plugin.debug(() -> "The player " + player.getName() + " does not have enough keys to take.", Level.WARNING);

                placeholders.put("%amount%", String.valueOf(amount));

                if (sender instanceof final Player human) {
                    human.sendMessage(Messages.cannot_take_keys.getMessage(placeholders, human));
                    return;
                }

                sender.sendMessage(Messages.cannot_take_keys.getMessage(placeholders));

                return;
            }

            // If total keys is 30, Amount is 35.
            // It will check the key type and fetch the keys of the type, and it will set amount to the current virtual keys or physical keys.
            // If the check doesn't meet, It just uses amount as is.
            if (totalKeys < amount) {
                amount = type == KeyType.physical_key ? this.userManager.getPhysicalKeys(player.getUniqueId(), crate.getName()) : this.userManager.getVirtualKeys(player.getUniqueId(), crate.getName());
            }

            this.userManager.takeKeys(amount, player.getUniqueId(), crate.getName(), type, false);

            placeholders.put("%amount%", String.valueOf(amount));

            if (sender instanceof final Player human) {
                human.sendMessage(Messages.take_player_keys.getMessage(placeholders, human));
            } else {
                sender.sendMessage(Messages.take_player_keys.getMessage(placeholders));
            }

            EventManager.logKeyEvent(player, sender, crate, type, EventManager.KeyEventType.KEY_EVENT_REMOVED, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));

            return;
        }

        placeholders.put("%player%", offlinePlayer.getName());
        placeholders.put("%amount%", String.valueOf(amount));

        if (sender instanceof final Player human) {
            human.sendMessage(Messages.take_offline_player_keys.getMessage(placeholders, human));
        } else {
            sender.sendMessage(Messages.take_offline_player_keys.getMessage(placeholders));
        }

        this.userManager.takeOfflineKeys(offlinePlayer.getUniqueId(), crate.getName(), amount, type);
    }

    @SubCommand("clear")
    @Permission(value = "crazycrates.command.admin.clearkey", def = PermissionDefault.OP)
    public void onClearKey(final CommandSender sender, @Optional @Suggestion("online-players") final Player player) {
        if (player == null) {
            if (sender instanceof final Player human) {
                clearKey(sender, human);
            } else {
                sender.sendMessage(Messages.must_be_a_player.getMessage());
            }

            return;
        }

        clearKey(sender, player);
    }

    private void clearKey(final CommandSender sender, @NotNull final Player player) {
        final Map<String, String> placeholders = new HashMap<>();
        placeholders.put("%player%", player.getName());
        for (final Crate crate : this.crateManager.getUsableCrates()) {
            final String crateName = crate.getName();
            final int amount = this.userManager.getVirtualKeys(player.getUniqueId(), crateName);
            if (amount > 0) {
                this.userManager.takeKeys(amount, player.getUniqueId(), crateName, KeyType.virtual_key, false);
                placeholders.put("%crate%", crateName);
                placeholders.put("%key%", crate.getKeyName());
                placeholders.put("%amount%", String.valueOf(amount));
                sender.sendMessage(Messages.cleared_player_keys.getMessage(placeholders));
            }
        }
    }

    @SubCommand("giveall")
    @Permission(value = "crazycrates.command.admin.giveall", def = PermissionDefault.OP)
    public void onAdminCrateGiveAllKeys(final CommandSender sender, @Suggestion("key-types") @ArgName("key-type") final String keyType, @Suggestion("crates") @ArgName("crate-name") final String crateName, @Suggestion("numbers") final int amount) {
        final KeyType type = KeyType.getFromName(keyType);

        if (type == null || type == KeyType.free_key) {
            sender.sendMessage(MsgUtils.color(MsgUtils.getPrefix() + "&cPlease use Virtual/V or Physical/P for a Key type."));

            return;
        }

        final Crate crate = this.crateManager.getCrateFromName(crateName);

        if (crate == null || crate.getCrateType() == CrateType.menu) {
            if (sender instanceof final Player human) {
                human.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName, human));

                return;
            }

            sender.sendMessage(Messages.not_a_crate.getMessage("%crate%", crateName));

            return;
        }

        final Map<String, String> placeholders = new HashMap<>();

        placeholders.put("%amount%", String.valueOf(amount));
        placeholders.put("%key%", crate.getKeyName());

        if (sender instanceof final Player human) {
            human.sendMessage(Messages.given_everyone_keys.getMessage(placeholders, human));
        } else {
            sender.sendMessage(Messages.given_everyone_keys.getMessage(placeholders));
        }

        for (final Player onlinePlayer : this.plugin.getServer().getOnlinePlayers()) {
            if (Permissions.CRAZYCRATES_PLAYER_EXCLUDE.hasPermission(onlinePlayer)) continue;

            final PlayerReceiveKeyEvent event = new PlayerReceiveKeyEvent(onlinePlayer, crate, PlayerReceiveKeyEvent.KeyReceiveReason.GIVE_ALL_COMMAND, amount);
            onlinePlayer.getServer().getPluginManager().callEvent(event);

            if (event.isCancelled()) return;

            onlinePlayer.sendMessage(Messages.obtaining_keys.getMessage(placeholders, onlinePlayer));

            if (crate.getCrateType() == CrateType.crate_on_the_go) {
                onlinePlayer.getInventory().addItem(crate.getKey(amount, onlinePlayer));

                return;
            }

            this.userManager.addKeys(amount, onlinePlayer.getUniqueId(), crate.getName(), type);

            EventManager.logKeyEvent(onlinePlayer, sender, crate, type, EventManager.KeyEventType.KEY_EVENT_GIVEN, this.config.getProperty(ConfigKeys.log_to_file), this.config.getProperty(ConfigKeys.log_to_console));
        }
    }

    public record CustomPlayer(String name) {
        private static final CrazyCrates plugin = CrazyCrates.getPlugin(CrazyCrates.class);

        public @NotNull OfflinePlayer getOfflinePlayer() {
            final CompletableFuture<UUID> future = CompletableFuture.supplyAsync(() -> plugin.getServer().getOfflinePlayer(name)).thenApply(OfflinePlayer::getUniqueId);

            return plugin.getServer().getOfflinePlayer(future.join());
        }

        public Player getPlayer() {
            return plugin.getServer().getPlayer(name);
        }
    }
}
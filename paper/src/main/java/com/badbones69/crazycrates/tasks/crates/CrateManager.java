package com.badbones69.crazycrates.tasks.crates;

import com.badbones69.crazycrates.CrazyCrates;
import com.badbones69.crazycrates.api.ChestManager;
import com.badbones69.crazycrates.api.FileManager;
import com.badbones69.crazycrates.api.FileManager.Files;
import com.badbones69.crazycrates.api.builders.CrateBuilder;
import com.badbones69.crazycrates.api.builders.types.CrateMainMenu;
import com.badbones69.crazycrates.api.enums.Messages;
import com.badbones69.crazycrates.api.enums.PersistentKeys;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.objects.Prize;
import com.badbones69.crazycrates.api.objects.Tier;
import com.badbones69.crazycrates.api.objects.other.BrokeLocation;
import com.badbones69.crazycrates.api.objects.other.CrateLocation;
import com.badbones69.crazycrates.api.objects.other.ItemBuilder;
import com.badbones69.crazycrates.api.utils.ItemUtils;
import com.badbones69.crazycrates.common.config.types.ConfigKeys;
import com.badbones69.crazycrates.common.crates.CrateHologram;
import com.badbones69.crazycrates.common.crates.quadcrates.CrateSchematic;
import com.badbones69.crazycrates.support.PluginSupport;
import com.badbones69.crazycrates.support.holograms.HologramManager;
import com.badbones69.crazycrates.support.holograms.types.DecentHologramsSupport;
import com.badbones69.crazycrates.tasks.crates.types.CasinoCrate;
import com.badbones69.crazycrates.tasks.crates.types.CosmicCrate;
import com.badbones69.crazycrates.tasks.crates.types.CrateOnTheGo;
import com.badbones69.crazycrates.tasks.crates.types.CsgoCrate;
import com.badbones69.crazycrates.tasks.crates.types.FireCrackerCrate;
import com.badbones69.crazycrates.tasks.crates.types.QuadCrate;
import com.badbones69.crazycrates.tasks.crates.types.QuickCrate;
import com.badbones69.crazycrates.tasks.crates.types.RouletteCrate;
import com.badbones69.crazycrates.tasks.crates.types.WarCrate;
import com.badbones69.crazycrates.tasks.crates.types.WheelCrate;
import com.badbones69.crazycrates.tasks.crates.types.WonderCrate;
import org.apache.commons.lang.WordUtils;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import us.crazycrew.crazycrates.api.enums.types.CrateType;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;

public class CrateManager {

    @NotNull
    private final CrazyCrates plugin = CrazyCrates.get();

    @NotNull
    private final FileManager fileManager = this.plugin.getFileManager();

    private final List<CrateLocation> crateLocations = new ArrayList<>();
    private final List<CrateSchematic> crateSchematics = new ArrayList<>();
    private final List<BrokeLocation> brokeLocations = new ArrayList<>();
    private final HashMap<UUID, Location> cratesInUse = new HashMap<>();
    private final List<String> brokeCrates = new ArrayList<>();
    private final List<Crate> crates = new ArrayList<>();
    // The crate that the player is opening.
    private final HashMap<UUID, Crate> playerOpeningCrates = new HashMap<>();
    // Keys that are being used in crates. Only needed in cosmic due to it taking the key after the player picks a prize and not in a start method.
    private final HashMap<UUID, KeyType> playerKeys = new HashMap<>();
    // A list of all current crate tasks that are running that a time. Used to force stop any crates it needs to.
    private final HashMap<UUID, BukkitTask> currentTasks = new HashMap<>();
    private final HashMap<UUID, TimerTask> timerTasks = new HashMap<>();
    // A list of tasks being run by the QuadCrate type.
    private final HashMap<UUID, List<BukkitTask>> currentQuadTasks = new HashMap<>();
    // War Crate
    private final HashMap<UUID, Boolean> canPick = new HashMap<>();
    private final HashMap<UUID, Boolean> canClose = new HashMap<>();
    private final HashMap<UUID, Boolean> checkHands = new HashMap<>();
    // QuickCrate/FireCracker
    private final List<Entity> allRewards = new ArrayList<>();
    private final HashMap<UUID, Entity> rewards = new HashMap<>();
    private HologramManager holograms;
    private boolean giveNewPlayersKeys;

    /**
     * Reloads an individual crate.
     *
     * @param crate the crate object.
     */
    public void reloadCrate(final Crate crate) {
        try {
            // Close previews
            this.plugin.getServer().getOnlinePlayers().forEach(player -> this.plugin.getCrazyHandler().getInventoryManager().closeCratePreview(player));

            // Grab the new file.
            final FileConfiguration file = crate.getFile();

            crate.purge();

            // Profit?
            final List<Prize> prizes = new ArrayList<>();

            final ConfigurationSection prizesSection = file.getConfigurationSection("Crate.Prizes");

            if (prizesSection != null) {
                for (final String prize : prizesSection.getKeys(false)) {
                    final ConfigurationSection prizeSection = prizesSection.getConfigurationSection(prize);

                    final List<Tier> tierPrizes = new ArrayList<>();

                    if (prizeSection != null) {
                        for (final String tier : prizeSection.getStringList("Tiers")) {
                            for (final Tier key : crate.getTiers()) {
                                if (key.getName().equalsIgnoreCase(tier)) {
                                    tierPrizes.add(key);
                                }
                            }
                        }

                        Prize alternativePrize = null;

                        final ConfigurationSection alternativeSection = prizeSection.getConfigurationSection("Alternative-Prize");

                        if (alternativeSection != null) {
                            final boolean isEnabled = alternativeSection.getBoolean("Toggle");

                            if (isEnabled) {
                                alternativePrize = new Prize(prizeSection.getString("DisplayName", WordUtils.capitalizeFully(prizeSection.getString("DisplayItem", "STONE").replaceAll("_", " "))), prizeSection.getName(), alternativeSection);
                            }
                        }

                        prizes.add(new Prize(
                                prizeSection,
                                tierPrizes,
                                crate.getName(),
                                alternativePrize
                        ));
                    }
                }
            }

            crate.setPrize(prizes);
            crate.setPreviewItems(crate.getPreviewItems());

            for (final UUID uuid : this.plugin.getCrazyHandler().getInventoryManager().getViewers()) {
                final Player player = this.plugin.getServer().getPlayer(uuid);

                if (player != null) {
                    this.plugin.getCrazyHandler().getInventoryManager().openNewCratePreview(player, crate, crate.getCrateType() == CrateType.cosmic || crate.getCrateType() == CrateType.casino);
                }
            }

            this.plugin.getCrazyHandler().getInventoryManager().purge();
        } catch (final Exception exception) {
            this.brokeCrates.add(crate.getName());
            this.plugin.getLogger().log(Level.WARNING, "There was an error while loading the " + crate.getName() + ".yml file.", exception);
        }
    }

    /**
     * Loads the crates.
     */
    public void loadCrates() {
        this.giveNewPlayersKeys = false;
        purge();

        // Removes all holograms so that they can be replaced.
        if (this.holograms != null) {
            this.holograms.removeAllHolograms();
        }

        if (PluginSupport.DECENT_HOLOGRAMS.isPluginEnabled()) {
            this.holograms = new DecentHologramsSupport();
            this.plugin.debug(() -> "DecentHolograms support has been enabled.", Level.INFO);
        } else {
            this.plugin.debug(() -> "No holograms plugin were found. If using CMI, make sure holograms module is enabled.", Level.WARNING);
        }

        this.plugin.debug(() -> "Loading all crate information...", Level.INFO);

        for (final String crateName : this.fileManager.getAllCratesNames()) {
            try {
                this.fileManager.reloadFile(crateName);
                final FileConfiguration file = this.fileManager.getFile(crateName).getFile();
                final ConfigurationSection section = file.getConfigurationSection("Crate.Tiers");

                final List<Tier> tiers = new ArrayList<>();
                if (file.contains("Crate.Tiers") && section != null) {
                    for (final String tier : section.getKeys(false)) {
                        final String path = "Crate.Tiers." + tier;

                        final ConfigurationSection tierSection = file.getConfigurationSection(path);

                        if (tierSection != null) {
                            tiers.add(new Tier(tier, tierSection));
                        }
                    }
                }

                final CrateType crateType = CrateType.getFromName(file.getString("Crate.CrateType"));
                final boolean isTiersEmpty = crateType == CrateType.cosmic || crateType == CrateType.casino;

                if (isTiersEmpty && tiers.isEmpty()) {
                    this.brokeCrates.add(crateName);
                    this.plugin.debug(() -> "No tiers were found for this cosmic crate " + crateName + ".yml file.", Level.WARNING);
                    continue;
                }

                final ConfigurationSection prizesSection = file.getConfigurationSection("Crate.Prizes");

                final List<Prize> prizes = new ArrayList<>();
                if (prizesSection != null) {
                    for (final String prize : prizesSection.getKeys(false)) {
                        final ConfigurationSection prizeSection = prizesSection.getConfigurationSection(prize);

                        final List<Tier> tierPrizes = new ArrayList<>();

                        Prize alternativePrize = null;

                        if (prizeSection != null) {
                            for (final String tier : prizeSection.getStringList("Tiers")) {
                                for (final Tier key : tiers) {
                                    if (key.getName().equalsIgnoreCase(tier)) {
                                        tierPrizes.add(key);
                                    }
                                }
                            }

                            final ConfigurationSection alternativeSection = prizeSection.getConfigurationSection("Alternative-Prize");

                            if (alternativeSection != null) {
                                final boolean isEnabled = alternativeSection.getBoolean("Toggle");

                                if (isEnabled) {
                                    alternativePrize = new Prize(prizeSection.getString("DisplayName", WordUtils.capitalizeFully(prizeSection.getString("DisplayItem", "STONE").replaceAll("_", " "))), prizeSection.getName(), alternativeSection);
                                }
                            }

                            prizes.add(new Prize(prizeSection, tierPrizes, crateName, alternativePrize));
                        }
                    }
                }

                final int newPlayersKeys = file.getInt("Crate.StartingKeys");

                if (!this.giveNewPlayersKeys && (newPlayersKeys > 0)) {
                    this.giveNewPlayersKeys = true;
                }

                final CrateHologram holo = new CrateHologram(file.getBoolean("Crate.Hologram.Toggle"), file.getDouble("Crate.Hologram.Height", 0.0), file.getInt("Crate.Hologram.Range", 8), file.getStringList("Crate.Hologram.Message"));

                final List<String> prizeMessage = file.contains("Crate.Prize-Message") ? file.getStringList("Crate.Prize-Message") : List.of();
                final String previewName = file.contains("Crate.Preview-Name") ? file.getString("Crate.Preview-Name") : file.getString("Crate.Name");
                final int maxMassOpen = file.contains("Crate.Max-Mass-Open") ? file.getInt("Crate.Max-Mass-Open") : 10;
                final int requiredKeys = file.contains("Crate.RequiredKeys") ? file.getInt("Crate.RequiredKeys") : 0;

                addCrate(new Crate(crateName, previewName, crateType, getKey(file), file.getString("Crate.PhysicalKey.Name"), prizes, file, newPlayersKeys, tiers, maxMassOpen, requiredKeys, prizeMessage, holo));
                this.plugin.getLogger().info("Loaded " + crateName + ".yml file.");

                final Permission doesExist = this.plugin.getServer().getPluginManager().getPermission("crazycrates.open." + crateName);
                if (doesExist == null) {
                    final Permission permission = new Permission(
                            "crazycrates.open." + crateName,
                            "Allows you to open " + crateName,
                            PermissionDefault.TRUE
                    );

                    this.plugin.getServer().getPluginManager().addPermission(permission);
                }
            } catch (final Exception exception) {
                this.brokeCrates.add(crateName);
                this.plugin.getLogger().log(Level.WARNING, "There was an error while loading the " + crateName + ".yml file.", exception);
            }
        }

        addCrate(new Crate("Menu", "Menu", CrateType.menu, new ItemStack(Material.AIR), "", new ArrayList<>(), null, 0, null, 0, 0, Collections.emptyList(), null));

        List.of(
                "All crate information has been loaded.",
                "Loading all the physical crate locations."
        ).forEach(line -> this.plugin.debug(() -> line, Level.INFO));

        final FileConfiguration locations = FileManager.Files.LOCATIONS.getFile();
        int loadedAmount = 0;
        int brokeAmount = 0;

        final ConfigurationSection section = locations.getConfigurationSection("Locations");

        if (section != null) {
            for (final String locationName : section.getKeys(false)) {
                try {
                    final String worldName = locations.getString("Locations." + locationName + ".World");

                    // If name is null, we return.
                    if (worldName == null) return;

                    // If name is empty or blank, we return.
                    if (worldName.isEmpty() || worldName.isBlank()) return;

                    final World world = this.plugin.getServer().getWorld(worldName);
                    final int x = locations.getInt("Locations." + locationName + ".X");
                    final int y = locations.getInt("Locations." + locationName + ".Y");
                    final int z = locations.getInt("Locations." + locationName + ".Z");
                    final Location location = new Location(world, x, y, z);
                    final Crate crate = this.plugin.getCrateManager().getCrateFromName(locations.getString("Locations." + locationName + ".Crate"));

                    if (world != null && crate != null) {
                        this.crateLocations.add(new CrateLocation(locationName, crate, location));

                        if (this.holograms != null) {
                            this.holograms.createHologram(location.getBlock(), crate);
                        }

                        loadedAmount++;
                    } else {
                        this.brokeLocations.add(new BrokeLocation(locationName, crate, x, y, z, worldName));
                        brokeAmount++;
                    }

                } catch (final Exception ignored) {
                }
            }
        }

        // Checking if all physical locations loaded
        if (this.plugin.isLogging()) {
            if (loadedAmount > 0 || brokeAmount > 0) {
                if (brokeAmount <= 0) {
                    this.plugin.getLogger().info("All physical crate locations have been loaded.");
                } else {
                    this.plugin.getLogger().info("Loaded " + loadedAmount + " physical crate locations.");
                    this.plugin.getLogger().info("Failed to load " + brokeAmount + " physical crate locations.");
                }
            }

            this.plugin.getLogger().info("Searching for schematics to load.");
        }

        // Loading schematic files
        final String[] schems = new File(this.plugin.getDataFolder() + "/schematics/").list();

        if (schems != null) {
            for (final String schematicName : schems) {
                if (schematicName.endsWith(".nbt")) {
                    this.crateSchematics.add(new CrateSchematic(schematicName, new File(plugin.getDataFolder() + "/schematics/" + schematicName)));

                    this.plugin.debug(() -> schematicName + " was successfully found and loaded.", Level.INFO);
                }
            }
        }

        this.plugin.debug(() -> "All schematics were found and loaded.", Level.INFO);

        cleanDataFile();

        this.plugin.getCrazyHandler().getInventoryManager().loadButtons();
    }

    /**
     * Opens a crate for a player.
     *
     * @param player    the player that is having the crate opened for them.
     * @param crate     the crate that is being used.
     * @param location  the location that may be needed for some crate types.
     * @param checkHand if it just checks the players hand or if it checks their inventory.
     */
    public void openCrate(final Player player, final Crate crate, final KeyType keyType, final Location location, final boolean virtualCrate, final boolean checkHand) {
        if (crate.getCrateType() == CrateType.menu) {
            if (this.plugin.getConfigManager().getConfig().getProperty(ConfigKeys.enable_crate_menu)) {
                final CrateMainMenu crateMainMenu = new CrateMainMenu(player, this.plugin.getConfigManager().getConfig().getProperty(ConfigKeys.inventory_size), this.plugin.getConfigManager().getConfig().getProperty(ConfigKeys.inventory_name));

                player.openInventory(crateMainMenu.build().getInventory());
                return;
            }

            player.sendMessage(Messages.feature_disabled.getMessage(player));

            return;
        }

        final CrateBuilder crateBuilder;

        switch (crate.getCrateType()) {
            case csgo -> crateBuilder = new CsgoCrate(crate, player, 27);
            case casino -> crateBuilder = new CasinoCrate(crate, player, 27);
            case wonder -> crateBuilder = new WonderCrate(crate, player, 45);
            case wheel -> crateBuilder = new WheelCrate(crate, player, 54);
            case roulette -> crateBuilder = new RouletteCrate(crate, player, 45);
            case war -> crateBuilder = new WarCrate(crate, player, 9);
            case cosmic -> crateBuilder = new CosmicCrate(crate, player, 27);
            case quad_crate -> {
                if (virtualCrate) {
                    player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                crateBuilder = new QuadCrate(crate, player, location);
            }
            case fire_cracker -> {
                if (this.cratesInUse.containsValue(location)) {
                    player.sendMessage(Messages.quick_crate_in_use.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                if (virtualCrate) {
                    player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                crateBuilder = new FireCrackerCrate(crate, player, 45, location);
            }
            case crate_on_the_go -> {
                if (virtualCrate) {
                    player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                crateBuilder = new CrateOnTheGo(crate, player);
            }
            case quick_crate -> {
                if (this.cratesInUse.containsValue(location)) {
                    player.sendMessage(Messages.quick_crate_in_use.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                if (virtualCrate) {
                    player.sendMessage(Messages.cant_be_a_virtual_crate.getMessage(player));
                    removePlayerFromOpeningList(player);
                    return;
                }

                crateBuilder = new QuickCrate(crate, player, location);
            }
            default -> {
                crateBuilder = new CsgoCrate(crate, player, 27);

                List.of(
                        crate.getCrateInventoryName() + " has an invalid crate type. Your Value: " + crate.getFile().getString("Crate.CrateType"),
                        "We will use " + CrateType.csgo.getName() + " until you change the crate type.",
                        "Valid Crate Types: CSGO/Casino/Cosmic/QuadCrate/QuickCrate/Roulette/CrateOnTheGo/FireCracker/Wonder/Wheel/War"
                ).forEach(line -> this.plugin.debug(() -> line, Level.WARNING));
            }
        }

        // Open the crate.
        crateBuilder.open(keyType, checkHand);
    }

    /**
     * Adds a crate in use for when a player opens a crate.
     *
     * @param player   the player opening the crate.
     * @param location the location the crate is at.
     */
    public void addCrateInUse(final Player player, final Location location) {
        this.cratesInUse.put(player.getUniqueId(), location);
    }

    /**
     * @param player the player attempting to open a crate.
     * @return the location of the crate in use.
     */
    public Location getCrateInUseLocation(final Player player) {
        return this.cratesInUse.get(player.getUniqueId());
    }

    /**
     * @param player the player attempting to open a crate.
     * @return true or false.
     */
    public boolean isCrateInUse(final Player player) {
        return this.cratesInUse.containsKey(player.getUniqueId());
    }

    /**
     * Removes a crate in use.
     *
     * @param player the player finihsing a crate.
     */
    public void removeCrateInUse(final Player player) {
        this.cratesInUse.remove(player.getUniqueId());
    }

    /**
     * @return hashmap of crates in use.
     */
    public Map<UUID, Location> getCratesInUse() {
        return Collections.unmodifiableMap(this.cratesInUse);
    }

    /**
     * This forces a crate to end and will not give out a prize. This is meant for people who leave the server to stop any errors or lag from happening.
     *
     * @param player the player that the crate is being ended for.
     */
    public void endCrate(@NotNull final Player player) {
        if (this.currentTasks.containsKey(player.getUniqueId())) {
            this.currentTasks.get(player.getUniqueId()).cancel();
        }
    }

    /**
     * Ends the tasks running by a player.
     *
     * @param player the player using the crate.
     */
    public void endQuadCrate(@NotNull final Player player) {
        if (this.currentQuadTasks.containsKey(player.getUniqueId())) {
            for (final BukkitTask task : this.currentQuadTasks.get(player.getUniqueId())) {
                task.cancel();
            }

            this.currentQuadTasks.remove(player.getUniqueId());
        }
    }

    /**
     * Add a quad crate task that is going on for a player.
     *
     * @param player the player opening the crate.
     * @param task   the task of the quad crate.
     */
    public void addQuadCrateTask(@NotNull final Player player, final BukkitTask task) {
        if (!this.currentQuadTasks.containsKey(player.getUniqueId())) {
            this.currentQuadTasks.put(player.getUniqueId(), new ArrayList<>());
        }

        this.currentQuadTasks.get(player.getUniqueId()).add(task);
    }

    /**
     * Checks to see if the player has a quad crate task going on.
     *
     * @param player player that is being checked.
     * @return true if they do have a task and false if not.
     */
    public boolean hasQuadCrateTask(@NotNull final Player player) {
        return this.currentQuadTasks.containsKey(player.getUniqueId());
    }

    /**
     * Add a crate task that is going on for a player.
     *
     * @param player player opening the crate.
     * @param task   task of the crate.
     */
    public void addCrateTask(@NotNull final Player player, final BukkitTask task) {
        this.currentTasks.put(player.getUniqueId(), task);
    }

    /**
     * Adds a repeating timer task for a player opening a crate.
     *
     * @param player player opening the crate.
     * @param task   task of the crate.
     * @param delay  delay before running the task.
     * @param period interval between task runs.
     */
    public void addRepeatingCrateTask(@NotNull final Player player, final TimerTask task, final Long delay, final Long period) {
        this.timerTasks.put(player.getUniqueId(), task);

        this.plugin.getTimer().scheduleAtFixedRate(task, delay, period);
    }

    /**
     * This forces a crate to end and will not give out a prize. This is meant for people who leave the server to stop any errors or lag from happening.
     *
     * @param player player that the crate is being ended for.
     */
    public void removeCrateTask(@NotNull final Player player) {
        // Get uuid
        final UUID uuid = player.getUniqueId();

        // Check if contains.
        if (this.timerTasks.containsKey(uuid)) {
            // Cancel the task.
            this.timerTasks.get(uuid).cancel();

            // Remove the player.
            this.timerTasks.remove(uuid);
        }
    }

    /**
     * Adds a timer task for a player opening a crate.
     *
     * @param player player opening the crate.
     * @param task   task of the crate.
     * @param delay  delay before running the task.
     */
    public void addCrateTask(@NotNull final Player player, final TimerTask task, final Long delay) {
        this.timerTasks.put(player.getUniqueId(), task);

        this.plugin.getTimer().schedule(task, delay);
    }

    /**
     * Gets a crate task that is on going for a player.
     *
     * @param player the player opening the crate.
     * @return the task of the crate.
     */
    public BukkitTask getCrateTask(@NotNull final Player player) {
        return this.currentTasks.get(player.getUniqueId());
    }

    /**
     * Checks to see if the player has a crate task going on.
     *
     * @param player the player that is being checked.
     * @return true if they do have a task and false if not.
     */
    public boolean hasCrateTask(@NotNull final Player player) {
        return this.currentTasks.containsKey(player.getUniqueId());
    }

    /**
     * Add a player to the list of players that are currently opening crates.
     *
     * @param player the player that is opening a crate.
     * @param crate  the crate the player is opening.
     */
    public void addPlayerToOpeningList(@NotNull final Player player, final Crate crate) {
        this.playerOpeningCrates.put(player.getUniqueId(), crate);
    }

    /**
     * Remove a player from the list of players that are opening crates.
     *
     * @param player the player that has finished opening a crate.
     */
    public void removePlayerFromOpeningList(@NotNull final Player player) {
        this.playerOpeningCrates.remove(player.getUniqueId());
    }

    /**
     * Check if a player is opening a crate.
     *
     * @param player the player you are checking.
     * @return true if they are opening a crate and false if they are not.
     */
    public boolean isInOpeningList(@NotNull final Player player) {
        return this.playerOpeningCrates.containsKey(player.getUniqueId());
    }

    /**
     * Get the crate the player is currently opening.
     *
     * @param player the player you want to check.
     * @return the Crate of which the player is opening. May return null if no crate found.
     */
    public Crate getOpeningCrate(@NotNull final Player player) {
        return this.playerOpeningCrates.get(player.getUniqueId());
    }

    /**
     * Set the type of key the player is opening a crate for.
     * This is only used in the Cosmic CrateType currently.
     *
     * @param player  the player that is opening the crate.
     * @param keyType the KeyType that they are using.
     */
    public void addPlayerKeyType(@NotNull final Player player, final KeyType keyType) {
        this.playerKeys.put(player.getUniqueId(), keyType);
    }

    /**
     * Remove the player from the list as they have finished the crate.
     * Currently, only used in the Cosmic CrateType.
     *
     * @param player the player you are removing.
     */
    public void removePlayerKeyType(@NotNull final Player player) {
        this.playerKeys.remove(player.getUniqueId());
    }

    /**
     * Check if the player is in the list.
     *
     * @param player the player you are checking.
     * @return true if they are in the list and false if not.
     */
    public boolean hasPlayerKeyType(@NotNull final Player player) {
        return this.playerKeys.containsKey(player.getUniqueId());
    }

    /**
     * The key type the player's current crate is using.
     *
     * @param player the player that is using the crate.
     * @return the key type of the crate the player is using.
     */
    public KeyType getPlayerKeyType(@NotNull final Player player) {
        return this.playerKeys.get(player.getUniqueId());
    }

    /**
     * Nukes all data.
     */
    public void purge() {
        this.crates.clear();
        this.brokeCrates.clear();
        this.crateLocations.clear();
        this.crateSchematics.clear();
    }

    /**
     * Set a new player's default amount of keys.
     *
     * @param player the player that has just joined.
     */
    public void setNewPlayerKeys(final Player player) {
        if (this.giveNewPlayersKeys) { // Checks if any crate gives new players keys and if not then no need to do all this stuff.
            final String uuid = player.getUniqueId().toString();

            if (!player.hasPlayedBefore()) {
                this.plugin.getCrateManager().getCrates().stream()
                        .filter(Crate::doNewPlayersGetKeys)
                        .forEach(crate -> {
                            FileManager.Files.DATA.getFile().set("Players." + uuid + "." + crate.getName(), crate.getNewPlayerKeys());
                            FileManager.Files.DATA.saveFile();
                        });
            }
        }
    }

    /**
     * Adds a crate to the arraylist.
     *
     * @param crate crate object.
     */
    public void addCrate(final Crate crate) {
        this.crates.add(crate);
    }

    public void addLocation(final CrateLocation crateLocation) {
        this.crateLocations.add(crateLocation);
    }

    /**
     * Removes a crate from the arraylist.
     *
     * @param crate crate object
     */
    public void removeCrate(final Crate crate) {
        this.crates.remove(crate);
    }

    /**
     * @return true if the arraylist has a crate object otherwise false.
     */
    public boolean hasCrate(final Crate crate) {
        return this.crates.contains(crate);
    }

    /**
     * Add a new physical crate location.
     *
     * @param location the location you wish to add.
     * @param crate    the crate which you would like to set it to.
     */
    public void addCrateLocation(final Location location, final Crate crate) {
        final FileConfiguration locations = Files.LOCATIONS.getFile();
        String id = "1"; // Location ID

        for (int i = 1; locations.contains("Locations." + i); i++) {
            id = (i + 1) + "";
        }

        for (final CrateLocation crateLocation : getCrateLocations()) {
            if (crateLocation.getLocation().equals(location)) {
                id = crateLocation.getID();
                break;
            }
        }

        locations.set("Locations." + id + ".Crate", crate.getName());
        locations.set("Locations." + id + ".World", location.getWorld().getName());
        locations.set("Locations." + id + ".X", location.getBlockX());
        locations.set("Locations." + id + ".Y", location.getBlockY());
        locations.set("Locations." + id + ".Z", location.getBlockZ());
        Files.LOCATIONS.saveFile();

        addLocation(new CrateLocation(id, crate, location));

        if (this.holograms != null) {
            this.holograms.createHologram(location.getBlock(), crate);
        }
    }

    /**
     * Remove a physical crate location.
     *
     * @param id the id of the location.
     */
    public void removeCrateLocation(final String id) {
        Files.LOCATIONS.getFile().set("Locations." + id, null);
        Files.LOCATIONS.saveFile();
        CrateLocation location = null;

        for (final CrateLocation crateLocation : getCrateLocations()) {
            if (crateLocation.getID().equalsIgnoreCase(id)) {
                location = crateLocation;
                break;
            }
        }

        if (location != null) {
            removeLocation(location);

            if (this.holograms != null) this.holograms.removeHologram(location.getLocation().getBlock());
        }
    }

    /**
     * @return an unmodifiable list of crate objects.
     */
    public List<Crate> getUsableCrates() {
        final List<Crate> crateList = new ArrayList<>(this.crates);
        crateList.removeIf(crate -> crate.getCrateType() == CrateType.menu);

        return Collections.unmodifiableList(crateList);
    }

    /**
     * @return an unmodifiable list of crate objects.
     */
    public List<Crate> getCrates() {
        return Collections.unmodifiableList(this.crates);
    }

    /**
     * Gets a crate object using the crate name.
     *
     * @param name name of the crate.
     * @return the crate object.
     */
    public Crate getCrateFromName(final String name) {
        for (final Crate crate : this.crates) {
            if (crate.getName().equalsIgnoreCase(name)) {
                return crate;
            }
        }

        return null;
    }

    /**
     * Checks to see if the location is a physical crate.
     *
     * @param location location you are checking.
     * @return true if it is a physical crate and false if not.
     */
    public boolean isCrateLocation(final Location location) {
        for (final CrateLocation crateLocation : getCrateLocations()) {
            if (crateLocation.getLocation().equals(location)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Check if an item is a key for a crate.
     *
     * @param item the item you are checking.
     * @return true if the item is a key and false if it is not.
     */
    public boolean isKey(final ItemStack item) {
        return getCrateFromKey(item) != null;
    }

    /**
     * Get a Crate from a key ItemStack the player.
     *
     * @param item the key ItemStack you are checking.
     * @return a crate if is a key from a crate otherwise null if it is not.
     */
    public Crate getCrateFromKey(final ItemStack item) {
        if (item != null && item.getType() != Material.AIR) {
            for (final Crate crate : getCrates()) {
                if (crate.getCrateType() != CrateType.menu && (isKeyFromCrate(item, crate))) {
                    return crate;
                }
            }
        }

        return null;
    }

    /**
     * Gets the physical crate of the location.
     *
     * @param location location you are checking.
     * @return a crate location if the location is a physical crate otherwise null if not.
     */
    public CrateLocation getCrateLocation(final Location location) {
        for (final CrateLocation crateLocation : this.crateLocations) {
            if (crateLocation.getLocation().equals(location)) {
                return crateLocation;
            }
        }

        return null;
    }

    /**
     * Get a schematic based on its name.
     *
     * @param name the name of the schematic.
     * @return the CrateSchematic otherwise returns null if not found.
     */
    public CrateSchematic getCrateSchematic(final String name) {
        for (final CrateSchematic schematic : this.crateSchematics) {
            if (schematic.getSchematicName().equalsIgnoreCase(name)) {
                return schematic;
            }
        }

        return null;
    }

    /**
     * Check if an entity is a display reward for a crate.
     *
     * @param entity entity you wish to check.
     * @return true if it is a display reward item and false if not.
     */
    public boolean isDisplayReward(final Entity entity) {
        if (entity instanceof final Item item) {
            final ItemStack itemStack = item.getItemStack();

            if (itemStack.getType() == Material.AIR) return false;

            final ItemMeta itemMeta = itemStack.getItemMeta();

            final PersistentKeys prize = PersistentKeys.crate_prize;

            final PersistentDataContainer container = itemMeta.getPersistentDataContainer();

            return container.has(prize.getNamespacedKey());
        }

        return false;
    }

    /**
     * Check if a key is from a specific Crate.
     *
     * @param item  the key ItemStack you are checking.
     * @param crate the Crate you are checking.
     * @return true if it belongs to that Crate and false if it does not.
     */
    public boolean isKeyFromCrate(final ItemStack item, @NotNull final Crate crate) {
        if (crate.getCrateType() != CrateType.menu && (item != null && item.getType() != Material.AIR)) {
            return ItemUtils.isSimilar(item, crate);
        }

        return false;
    }

    /**
     * @return the hologram handler.
     */
    public HologramManager getHolograms() {
        return this.holograms;
    }

    /**
     * @return an unmodifiable list of crate locations.
     */
    public List<CrateLocation> getCrateLocations() {
        return Collections.unmodifiableList(this.crateLocations);
    }

    /**
     * Removes a crate location.
     *
     * @param crateLocation the location to remove.
     */
    public void removeLocation(final CrateLocation crateLocation) {
        this.crateLocations.remove(crateLocation);
    }

    /**
     * @return an unmodifiable list of broke crates.
     */
    public List<String> getBrokeCrates() {
        return Collections.unmodifiableList(this.brokeCrates);
    }

    /**
     * @return an unmodifiable list of broken crate locations.
     */
    public List<BrokeLocation> getBrokeLocations() {
        return Collections.unmodifiableList(this.brokeLocations);
    }

    /**
     * Removes broken locations.
     *
     * @param crateLocation list of locations to remove.
     */
    public void removeBrokeLocation(final List<BrokeLocation> crateLocation) {
        this.brokeLocations.removeAll(crateLocation);
    }

    /**
     * @return an unmodifiable list of crate schematics.
     */
    public List<CrateSchematic> getCrateSchematics() {
        return Collections.unmodifiableList(this.crateSchematics);
    }

    // Internal methods.
    private ItemStack getKey(@NotNull final FileConfiguration file) {
        final String name = file.getString("Crate.PhysicalKey.Name");
        final List<String> lore = file.getStringList("Crate.PhysicalKey.Lore");
        final String id = file.getString("Crate.PhysicalKey.Item", "TRIPWIRE_HOOK");
        final boolean glowing = file.getBoolean("Crate.PhysicalKey.Glowing", true);

        return new ItemBuilder().setMaterial(id).setName(name).setLore(lore).setGlow(glowing).build();
    }

    // Cleans the data file.
    private void cleanDataFile() {
        try {
            final FileConfiguration data = FileManager.Files.DATA.getFile();
            if (!data.contains("Players")) {
                return;
            }

            if (!backupDataFile()) {
                this.plugin.debug(() -> "Failed to backup data.yml file.", Level.WARNING);
                return;
            }

            this.plugin.debug(() -> "Cleaning up the data.yml file.", Level.INFO);

            final ConfigurationSection playersSection = data.getConfigurationSection("Players");
            if (playersSection == null) {
                this.plugin.debug(() -> "The configuration section Players is null.", Level.INFO);
                return;
            }

            final Set<String> reservedNames = Set.of("Name");
            final Set<String> validCrateNames = new HashSet<>(getCrates().stream().map(Crate::getName).toList());
            final List<String> removePlayers = new ArrayList<>();

            for (final String uuid : playersSection.getKeys(false)) {
                final ConfigurationSection playerSection = data.getConfigurationSection("Players." + uuid);
                if (playerSection == null) continue;

                boolean playerHasValidKeys = false;

                final List<String> cratesToRemove = new ArrayList<>();

                for (final String crateName : playerSection.getKeys(false)) {
                    if (reservedNames.contains(crateName)) {
                        continue;
                    }
                    if (crateName.equals("tracking")) {
                        data.set("Players." + uuid + ".tracking", null);
                    }

                    if (!validCrateNames.contains(crateName)) {
                        cratesToRemove.add(crateName);
                        continue;
                    }

                    final int keys = data.getInt("Players." + uuid + "." + crateName);
                    if (keys <= 0) {
                        data.set("Players." + uuid + "." + crateName, null);
                    } else {
                        playerHasValidKeys = true;
                    }
                }

                // Remove invalid crates and tracking data
                cratesToRemove.forEach(crate -> data.set("Players." + uuid + "." + crate, null));

                if (!playerHasValidKeys) {
                    removePlayers.add(uuid);
                }
            }

            // Remove players without valid keys or empty data
            removePlayers.forEach(id -> data.set("Players." + id, null));

            if (!removePlayers.isEmpty()) {
                this.plugin.debug(() -> removePlayers.size() + " player's data has been marked to be removed.", Level.INFO);
            }

            this.plugin.debug(() -> "All empty player data has been removed.", Level.INFO);

            FileManager.Files.DATA.saveFile();
            this.plugin.debug(() -> "The data.yml file has been cleaned.", Level.INFO);
        } catch (final Exception e) {
            this.plugin.debug(() -> "Error cleaning data.yml file: " + e.getMessage(), Level.WARNING);
        }
    }

    private boolean backupDataFile() {
        try {
            final File dataFolder = this.plugin.getDataFolder();
            final File backupFolder = new File(dataFolder, "backup");

            if (!backupFolder.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backupFolder.mkdirs();
            }

            final File dataFile = new File(dataFolder, "data.yml");
            final File backupFile = new File(backupFolder, "data_%s.yml".formatted(String.valueOf(System.currentTimeMillis())));

            com.google.common.io.Files.copy(dataFile, backupFile);
            this.plugin.getLogger().info("Created a backup of the data.yml file.");
            return true;
        } catch (final IOException e) {
            this.plugin.getLogger().log(Level.WARNING, "Failed to create a backup of the data.yml file.", e);
            return false;
        }
    }

    public void addPicker(@NotNull final Player player, final boolean value) {
        this.canPick.put(player.getUniqueId(), value);
    }

    public boolean containsPicker(@NotNull final Player player) {
        return this.canPick.containsKey(player.getUniqueId());
    }

    public boolean isPicker(@NotNull final Player player) {
        return this.canPick.get(player.getUniqueId());
    }

    public void removePicker(@NotNull final Player player) {
        this.canPick.remove(player.getUniqueId());
    }

    public void addCloser(@NotNull final Player player, final boolean value) {
        this.canClose.put(player.getUniqueId(), value);
    }

    public boolean containsCloser(@NotNull final Player player) {
        return this.canClose.containsKey(player.getUniqueId());
    }

    public void removeCloser(@NotNull final Player player) {
        this.canClose.remove(player.getUniqueId());
    }

    public void addHands(@NotNull final Player player, final boolean checkHand) {
        this.checkHands.put(player.getUniqueId(), checkHand);
    }

    public void removeHands(@NotNull final Player player) {
        this.checkHands.remove(player.getUniqueId());
    }

    public boolean getHand(@NotNull final Player player) {
        return this.checkHands.get(player.getUniqueId());
    }

    public void addReward(@NotNull final Player player, final Entity entity) {
        this.allRewards.add(entity);
        this.rewards.put(player.getUniqueId(), entity);
    }

    public void endQuickCrate(final Player player, final Location location, final Crate crate, final boolean useQuickCrateAgain) {
        if (hasCrateTask(player)) {
            getCrateTask(player).cancel();
            removeCrateTask(player);
        }

        if (this.rewards.get(player.getUniqueId()) != null) {
            this.allRewards.remove(this.rewards.get(player.getUniqueId()));

            this.rewards.get(player.getUniqueId()).remove();
            this.rewards.remove(player.getUniqueId());
        }

        ChestManager.closeChest(location.getBlock(), false);

        removeCrateInUse(player);
        removePlayerFromOpeningList(player);

        if (!useQuickCrateAgain && holograms != null && crate != null && crate.getHologram().isEnabled() && crate.getHologram() == null) {
            holograms.createHologram(location.getBlock(), crate);
        }
    }

    public void purgeRewards() {
        if (!this.allRewards.isEmpty()) this.allRewards.stream().filter(Objects::nonNull).forEach(Entity::remove);
    }
}
package com.badbones69.crazycrates.tasks.crates.types;

import com.badbones69.crazycrates.api.PrizeManager;
import com.badbones69.crazycrates.api.builders.CrateBuilder;
import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.objects.Prize;
import com.badbones69.crazycrates.api.objects.other.ItemBuilder;
import com.badbones69.crazycrates.api.utils.MiscUtils;
import org.bukkit.Material;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import us.crazycrew.crazycrates.api.enums.types.KeyType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class CsgoCrate extends CrateBuilder {

    public CsgoCrate(final Crate crate, final Player player, final int size) {
        super(crate, player, size);
    }

    @Override
    public void open(final KeyType type, final boolean checkHand) {
        // Crate event failed so we return.
        if (isCrateEventValid(type, checkHand)) {
            return;
        }

        // Check if the player has the required keys.
        final int requiredKeys = getCrate().getRequiredKeys();
        final int keyAmount = Math.max(1, requiredKeys);

        final boolean keyCheck = this.plugin.getCrazyHandler().getUserManager().takeKeys(keyAmount, getPlayer().getUniqueId(), getCrate().getName(), type, checkHand);

        if (!keyCheck) {
            // Send the message about failing to take the key.
            MiscUtils.failedToTakeKey(getPlayer(), getCrate());

            // Remove from opening list.
            this.plugin.getCrateManager().removePlayerFromOpeningList(getPlayer());

            return;
        }

        // Set the glass/display items to the inventory.
        populate();

        // Open the inventory.
        getPlayer().openInventory(getInventory());

        addCrateTask(new BukkitRunnable() {
            int time = 1;

            int full = 0;

            int open = 0;

            @Override
            public void run() {
                if (this.full <= 50) { // When Spinning
                    moveItemsAndSetGlass();
                    playSound("cycle-sound", SoundCategory.PLAYERS, "BLOCK_NOTE_BLOCK_XYLOPHONE");
                }

                this.open++;

                if (this.open >= 5) {
                    getPlayer().openInventory(getInventory());
                    this.open = 0;
                }

                this.full++;

                if (this.full > 51) {
                    if (MiscUtils.slowSpin(120, 15).contains(this.time)) { // When Slowing Down
                        moveItemsAndSetGlass();

                        playSound("cycle-sound", SoundCategory.PLAYERS, "BLOCK_NOTE_BLOCK_XYLOPHONE");
                    }

                    this.time++;

                    if (this.time == 60) { // When done
                        playSound("stop-sound", SoundCategory.PLAYERS, "ENTITY_PLAYER_LEVELUP");

                        plugin.getCrateManager().endCrate(getPlayer());

                        final ItemStack item = getInventory().getItem(13);

                        if (item != null) {
                            final Prize prize = getCrate().getPrize(item);

                            PrizeManager.givePrize(getPlayer(), getCrate(), prize);
                        }

                        plugin.getCrateManager().removePlayerFromOpeningList(getPlayer());

                        cancel();

                        new BukkitRunnable() {
                            @Override
                            public void run() {
                                if (getPlayer().getOpenInventory().getTopInventory().equals(getInventory()))
                                    getPlayer().closeInventory();
                            }
                        }.runTaskLater(plugin, 40);
                    } else if (this.time > 60) { // Added this due reports of the prizes spamming when low tps.
                        cancel();
                    }
                }
            }
        }.runTaskTimer(this.plugin, 1, 1));
    }

    private void populate() {
        final HashMap<Integer, ItemStack> glass = new HashMap<>();

        for (int index = 0; index < 10; index++) {
            if (index < 9 && index != 3) glass.put(index, getInventory().getItem(index));
        }

        for (final int index : glass.keySet()) {
            if (getInventory().getItem(index) == null) {
                setCustomGlassPane(index);
                setCustomGlassPane(index + 18);
            }
        }

        for (int index = 1; index < 10; index++) {
            if (index < 9 && index != 4) glass.put(index, getInventory().getItem(index));
        }

        setItem(0, glass.get(1));

        setItem(1, glass.get(2));
        setItem(1 + 18, glass.get(2));

        setItem(2, glass.get(3));
        setItem(2 + 18, glass.get(3));

        setItem(3, glass.get(5));
        setItem(3 + 18, glass.get(5));

        final ItemStack itemStack = new ItemBuilder().setMaterial(Material.BLACK_STAINED_GLASS_PANE).setName(" ").build();
        setItem(4, itemStack);
        setItem(4 + 18, itemStack);

        setItem(5, glass.get(6));
        setItem(5 + 18, glass.get(6));

        setItem(6, glass.get(7));
        setItem(6 + 18, glass.get(7));

        setItem(7, glass.get(8));
        setItem(7 + 18, glass.get(8));

        setCustomGlassPane(8);
        setCustomGlassPane(8 + 18);

        // Set display items.
        for (int index = 9; index > 8 && index < 18; index++) {
            setItem(index, getCrate().pickPrize(getPlayer()).getDisplayItem(getPlayer()));
        }
    }

    private void moveItemsAndSetGlass() {
        final List<ItemStack> items = new ArrayList<>();

        for (int i = 9; i > 8 && i < 17; i++) {
            items.add(getInventory().getItem(i));
        }

        setItem(9, getCrate().pickPrize(getPlayer()).getDisplayItem(getPlayer()));

        for (int i = 0; i < 8; i++) {
            setItem(i + 10, items.get(i));
        }
    }

    @Override
    public void run() {

    }
}
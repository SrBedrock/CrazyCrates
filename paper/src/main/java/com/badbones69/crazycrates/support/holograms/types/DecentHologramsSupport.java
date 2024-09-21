package com.badbones69.crazycrates.support.holograms.types;

import com.badbones69.crazycrates.api.objects.Crate;
import com.badbones69.crazycrates.api.utils.MsgUtils;
import com.badbones69.crazycrates.common.crates.CrateHologram;
import com.badbones69.crazycrates.support.holograms.HologramManager;
import eu.decentsoftware.holograms.api.DHAPI;
import eu.decentsoftware.holograms.api.holograms.Hologram;
import org.bukkit.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DecentHologramsSupport extends HologramManager {

    private final Map<Block, Hologram> holograms = new HashMap<>();

    @Override
    public void createHologram(Block block, @NotNull Crate crate) {
        CrateHologram crateHologram = crate.getHologram();
        if (!crateHologram.isEnabled()) return;

        Hologram hologram = DHAPI.createHologram("CrazyCrates-" + UUID.randomUUID(), block.getLocation().add(.5, crateHologram.getHeight(), .5));
        hologram.setDownOrigin(true);

        hologram.setDisplayRange(crateHologram.getRange());

        crateHologram.getMessages().forEach(line -> DHAPI.addHologramLine(hologram, MsgUtils.color(line)));

        this.holograms.put(block, hologram);
    }

    @Override
    public void removeHologram(Block block) {
        if (!this.holograms.containsKey(block)) {
            return;
        }

        Hologram hologram = this.holograms.get(block);

        this.holograms.remove(block);
        hologram.delete();
    }

    @Override
    public void removeAllHolograms() {
        this.holograms.forEach((key, value) -> value.delete());
        this.holograms.clear();
    }
}
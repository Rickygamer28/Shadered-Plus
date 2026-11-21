package com.example.skyblocks;

import net.minecraft.world.item.Item;

/**
 * Lays smooth train track (TrackNetwork): right-click a start, then an end - a curve between them, turning and rising
 * smoothly, shown first (green: it'll do; red: too tight, too steep, too long). Ends snap onto track there already;
 * started or ended along a piece, a switch is made there. All done on the client (client/TrackTool), built by the server.
 */
public class TrackToolItem extends Item {
    public TrackToolItem(Properties p) {
        super(p);
    }
}

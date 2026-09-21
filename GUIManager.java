package com.voiddupe;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public final class GUIManager {
    private final VoidDupe plugin;
    private final PlayerData data;

    public GUIManager(VoidDupe plugin, PlayerData data) {
        this.plugin = plugin;
        this.data = data;
    }

    public void openCosmetics(Player player) {
        String rank = data.getRank(player);
        Inventory inv = Bukkit.createInventory(new Economy.MenuHolder("cosmetics"), 27, Component.text("VoidDupe Cosmetics"));
        String[] cosmetics = {"Flame Trail","Bubble Trail","Void Trail","Snow Trail","Slime Trail","Star Trail","White Chat","Aqua Chat","Purple Chat"};
        for (int i = 0; i < cosmetics.length; i++) {
            String cosmetic = cosmetics[i];
            ItemStack item = new ItemStack(cosmetic.contains("Chat") ? Material.NAME_TAG : Material.FIREWORK_STAR);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text("§d" + cosmetic));
            meta.lore(List.of(Component.text(unlocked(rank, i) ? "§aUnlocked" : "§cRequires a higher rank")));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "cosmetic"), PersistentDataType.STRING, cosmetic);
            item.setItemMeta(meta);
            inv.setItem(i, item);
        }
        player.openInventory(inv);
    }

    private boolean unlocked(String rank, int index) {
        if (rank.equals("OWNER") || rank.equals("ADMIN") || rank.equals("VOID")) return true;
        if (rank.equals("WARDEN")) return index < 3;
        if (rank.equals("ECHO")) return index == 0;
        return false;
    }

    public void handleClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Economy.MenuHolder holder)) return;
        if (!holder.id().equals("cosmetics")) return;
        event.setCancelled(true);
        ItemStack item = event.getCurrentItem();
        if (item == null || !item.hasItemMeta()) return;
        String cosmetic = item.getItemMeta().getPersistentDataContainer().get(
                new NamespacedKey(plugin, "cosmetic"), PersistentDataType.STRING);
        if (cosmetic == null) return;
        String rank = data.getRank(player);
        if (!unlocked(rank, event.getRawSlot())) {
            player.sendMessage("§cThat cosmetic is locked for your rank.");
            return;
        }
        player.sendMessage("§aSelected cosmetic: §d" + cosmetic);
    }
}

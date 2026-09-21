package com.voiddupe;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class Crates {
    private final VoidDupe plugin;
    private final PlayerData data;
    private final NamespacedKey crateItemKey;
    private final NamespacedKey crateBlockKey;
    private final NamespacedKey crateTypeKey;

    public Crates(VoidDupe plugin, PlayerData data) {
        this.plugin = plugin;
        this.data = data;
        this.crateItemKey = new NamespacedKey(plugin, "crate_item");
        this.crateBlockKey = new NamespacedKey(plugin, "crate_block");
        this.crateTypeKey = new NamespacedKey(plugin, "crate_type");
    }

    public boolean isCrateItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(crateItemKey, PersistentDataType.BYTE);
    }

    public ItemStack key(String type) {
        String pretty = pretty(type) + " Key";
        ItemStack item = new ItemStack(Material.TRIPWIRE_HOOK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("§d" + pretty));
        meta.getPersistentDataContainer().set(crateTypeKey, PersistentDataType.STRING, type.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        return item;
    }

    public boolean isKey(ItemStack item, String type) {
        if (item == null || item.getType() != Material.TRIPWIRE_HOOK || !item.hasItemMeta()) return false;
        String stored = item.getItemMeta().getPersistentDataContainer().get(crateTypeKey, PersistentDataType.STRING);
        return type.equalsIgnoreCase(stored);
    }

    public void setPhysicalCrate(Block block, String type) {
        if (!(block.getState() instanceof Chest chest)) return;
        chest.getPersistentDataContainer().set(crateBlockKey, PersistentDataType.STRING, type.toLowerCase(Locale.ROOT));
        chest.update(true);
    }

    public String getPhysicalCrate(Block block) {
        if (!(block.getState() instanceof Chest chest)) return null;
        return chest.getPersistentDataContainer().get(crateBlockKey, PersistentDataType.STRING);
    }

    public void openPhysical(Player player, Block block, String type) {
        String stored = getPhysicalCrate(block);
        if (stored == null || !stored.equalsIgnoreCase(type)) return;
        if (!consumeKey(player, type)) {
            player.sendMessage("§cYou need a matching " + pretty(type) + " Key.");
            return;
        }
        openCrate(player, type, 1);
    }

    public boolean consumeKey(Player player, String type) {
        PlayerInventory inv = player.getInventory();
        for (int i = 0; i < inv.getSize(); i++) {
            ItemStack item = inv.getItem(i);
            if (!isKey(item, type)) continue;
            if (item.getAmount() <= 1) inv.setItem(i, null);
            else item.setAmount(item.getAmount() - 1);
            return true;
        }
        return false;
    }

    public void openVirtual(Player player, String type, int amount) {
        type = type.toLowerCase(Locale.ROOT);
        if (amount < 1 || amount > 64) {
            player.sendMessage("§cAmount must be between 1 and 64.");
            return;
        }
        if (type.equals("heart")) {
            double cost = 250_000_000D * amount;
            if (data.getMoney(player) < cost) {
                player.sendMessage("§cYou need $" + Economy.format(cost) + ". Balance: $" + Economy.format(data.getMoney(player)));
                return;
            }
            data.addMoney(player, -cost);
            for (int i = 0; i < amount; i++) openCrate(player, type, 1);
            return;
        }
        int heartCost = switch (type) {
            case "echo" -> 2;
            case "warden" -> 5;
            case "void" -> 10;
            case "voidplus" -> 15;
            default -> -1;
        };
        if (heartCost < 0) {
            player.sendMessage("§cUnknown crate.");
            return;
        }
        if (data.getHearts(player) < heartCost * amount) {
            player.sendMessage("§cYou need " + (heartCost * amount) + " hearts. You have " + data.getHearts(player) + ".");
            return;
        }
        data.addHearts(player, -(heartCost * amount));
        for (int i = 0; i < amount; i++) openCrate(player, type, 1);
    }

    public void openCrate(Player player, String type, int amount) {
        ItemStack reward = reward(type);
        giveOrDrop(player, reward);
        Firework fw = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class);
        FireworkMeta fm = fw.getFireworkMeta();
        fm.setPower(0);
        fm.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BALL_LARGE).flicker(true).trail(true).build());
        fw.setFireworkMeta(fm);
        player.playSound(player.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_BLAST, 1f, 1f);
        player.sendMessage("§d✦ §f" + pretty(type) + " Crate opened! §7Reward: §f" + reward.getType().name() + " x" + reward.getAmount());
    }

    private ItemStack reward(String type) {
        type = type.toLowerCase(Locale.ROOT);
        if (type.equals("heart")) {
            int amount = ThreadLocalRandom.current().nextInt(1, 11);
            ItemStack heart = new ItemStack(Material.NETHER_STAR, amount);
            ItemMeta meta = heart.getItemMeta();
            meta.displayName(Component.text("§cHeart"));
            meta.getPersistentDataContainer().set(crateItemKey, PersistentDataType.BYTE, (byte) 1);
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "heart_item"), PersistentDataType.BYTE, (byte) 1);
            heart.setItemMeta(meta);
            return heart;
        }

        int level = switch (type) {
            case "echo" -> 4;
            case "warden" -> 6;
            case "void" -> 8;
            case "voidplus" -> 10;
            default -> 4;
        };

        Material material;
        if (type.equals("voidplus") && ThreadLocalRandom.current().nextDouble() < 0.05) {
            material = Material.MACE;
        } else {
            material = switch (ThreadLocalRandom.current().nextInt(3)) {
                case 0 -> Material.NETHERITE_SWORD;
                case 1 -> Material.NETHERITE_CHESTPLATE;
                default -> Material.NETHERITE_AXE;
            };
        }

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(crateItemKey, PersistentDataType.BYTE, (byte) 1);

        if (material == Material.NETHERITE_SWORD || material == Material.NETHERITE_AXE) {
            meta.addEnchant(Enchantment.SHARPNESS, level, true);
            if (type.equals("voidplus")) {
                meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                meta.addEnchant(Enchantment.MENDING, 10, true);
            }
        } else if (material instanceof Material) {
            meta.addEnchant(Enchantment.PROTECTION, level, true);
            if (type.equals("voidplus")) {
                meta.addEnchant(Enchantment.UNBREAKING, 10, true);
                meta.addEnchant(Enchantment.MENDING, 10, true);
            }
        }
        meta.displayName(Component.text("§d" + pretty(type) + " Reward"));
        item.setItemMeta(meta);
        return item;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    private String pretty(String type) {
        return switch (type.toLowerCase(Locale.ROOT)) {
            case "voidplus" -> "Void+";
            case "echo" -> "Echo";
            case "warden" -> "Warden";
            case "void" -> "Void";
            case "heart" -> "Heart";
            default -> type;
        };
    }
}

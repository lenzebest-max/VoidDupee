package com.voiddupe;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class PlayerData {
    private final JavaPlugin plugin;
    private final File file;
    private FileConfiguration data;

    public PlayerData(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "players.yml");
        load();
    }

    public void load() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdirs();
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save players.yml: " + ex.getMessage());
        }
    }

    public String getRank(Player player) {
        String rank = data.getString(path(player, "rank"), "PLAYER");
        try {
            Rank.valueOf(rank.toUpperCase(Locale.ROOT));
            return rank.toUpperCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "PLAYER";
        }
    }

    public void setRank(Player player, String rank) {
        data.set(path(player, "rank"), rank.toUpperCase(Locale.ROOT));
        save();
        applyHeartLimit(player);
    }

    public double getMoney(Player player) {
        return data.getDouble(path(player, "money"), 0.0D);
    }

    public void setMoney(Player player, double amount) {
        data.set(path(player, "money"), Math.max(0.0D, amount));
        save();
    }

    public int getHearts(Player player) {
        return Math.max(1, data.getInt(path(player, "hearts"), 10));
    }

    public void setHearts(Player player, int hearts) {
        int safe = Math.max(1, Math.min(100, hearts));
        data.set(path(player, "hearts"), safe);
        save();
        applyHeartLimit(player);
    }

    public void addMoney(Player player, double amount) {
        setMoney(player, getMoney(player) + amount);
    }

    public void addHearts(Player player, int amount) {
        setHearts(player, getHearts(player) + amount);
    }

    public boolean isKnown(Player player) {
        return data.contains(path(player, "rank"));
    }

    public void initialize(Player player) {
        boolean changed = false;
        String base = path(player, "");
        if (!data.contains(base + "rank")) {
            data.set(base + "rank", "PLAYER");
            changed = true;
        }
        if (!data.contains(base + "money")) {
            data.set(base + "money", 0.0D);
            changed = true;
        }
        if (!data.contains(base + "hearts")) {
            data.set(base + "hearts", 10);
            changed = true;
        }
        if (changed) save();
        applyHeartLimit(player);
    }

    public boolean hasPermission(Player player, String permission) {
        String rank = getRank(player);
        if ("OWNER".equals(rank)) return true;
        if ("ADMIN".equals(rank) && !"voiddupe.owner".equals(permission)) return true;
        if ("VOID".equals(rank)) {
            return permission.equals("voiddupe.void") || permission.equals("voiddupe.player");
        }
        if ("WARDEN".equals(rank)) {
            return permission.equals("voiddupe.warden") || permission.equals("voiddupe.player");
        }
        if ("ECHO".equals(rank)) {
            return permission.equals("voiddupe.echo") || permission.equals("voiddupe.player");
        }
        return permission.equals("voiddupe.player");
    }

    public boolean isStaff(Player player) {
        String rank = getRank(player);
        return rank.equals("OWNER") || rank.equals("ADMIN");
    }

    public boolean isVoidOrAbove(Player player) {
        String rank = getRank(player);
        return rank.equals("OWNER") || rank.equals("ADMIN") || rank.equals("VOID");
    }

    public boolean isAtLeastWarden(Player player) {
        String rank = getRank(player);
        return rank.equals("OWNER") || rank.equals("ADMIN") || rank.equals("VOID") || rank.equals("WARDEN");
    }

    public boolean isAtLeastEcho(Player player) {
        return true;
    }

    public String prefix(Player player) {
        return prefix(getRank(player));
    }

    public static String prefix(String rank) {
        return switch (rank.toUpperCase(Locale.ROOT)) {
            case "OWNER" -> "§4[OWNER] §f";
            case "ADMIN" -> "§c[ADMIN] §f";
            case "VOID" -> "§5[VOID] §f";
            case "WARDEN" -> "§9[WARDEN] §f";
            case "ECHO" -> "§b[ECHO] §f";
            default -> "§7";
        };
    }

    public void applyHeartLimit(Player player) {
        double hearts = getHearts(player);
        var attribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (attribute != null) {
            attribute.setBaseValue(hearts * 2.0D);
            if (player.getHealth() > attribute.getValue()) {
                player.setHealth(attribute.getValue());
            }
        }
    }

    private String path(Player player, String suffix) {
        return "players." + player.getUniqueId() + (suffix.isEmpty() ? "." : "." + suffix);
    }

    public enum Rank {
        OWNER, ADMIN, VOID, WARDEN, ECHO, PLAYER
    }
}

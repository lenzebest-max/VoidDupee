package com.voiddupe;

import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public final class VoidDupe extends JavaPlugin implements Listener {
    private PlayerData playerData;
    private Economy economy;
    private Crates crates;
    private GUIManager gui;
    private CommandHandler commands;
    private final Map<UUID, Long> dupeCooldown = new ConcurrentHashMap<>();
    private NamespacedKey crateItemKey;

    @Override
    public void onEnable() {
        playerData = new PlayerData(this);
        economy = new Economy(this, playerData);
        crates = new Crates(this, playerData);
        gui = new GUIManager(this, playerData);
        commands = new CommandHandler(this, playerData, economy, crates, gui);
        crateItemKey = new NamespacedKey(this, "crate_item");

        getServer().getPluginManager().registerEvents(this, this);
        getCommand("help").setExecutor(commands); getCommand("help").setTabCompleter(commands);
        getCommand("dupe").setExecutor(commands); getCommand("dupe").setTabCompleter(commands);
        for (String name : List.of("fly","god","heal","vanish","speed","sudo","freeze","launch","smite","rank","money","hearts","sellaxe","kit","ah","shop","crate","cosmetics","setcrate")) {
            Objects.requireNonNull(getCommand(name), "Missing command " + name).setExecutor(commands);
            getCommand(name).setTabCompleter(commands);
        }

        getServer().getScheduler().runTaskTimer(this, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) playerData.applyHeartLimit(p);
        }, 20L, 200L);

        getLogger().info("VoidDupe enabled.");
    }

    @Override
    public void onDisable() {
        playerData.save();
        getLogger().info("VoidDupe disabled.");
    }

    public PlayerData data() { return playerData; }
    public Economy economy() { return economy; }
    public Crates crates() { return crates; }
    public CommandHandler commands() { return commands; }

    public void dupe(Player player) {
        long now = System.currentTimeMillis();
        long last = dupeCooldown.getOrDefault(player.getUniqueId(), 0L);
        long remaining = 3000L - (now - last);
        if (remaining > 0) {
            player.sendMessage("§cDupe cooldown: " + ((remaining + 999) / 1000) + "s.");
            return;
        }
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            player.sendMessage("§cHold an item in your main hand.");
            return;
        }
        if (!playerData.isStaff(player) && isBlockedForDupe(hand)) {
            player.sendMessage("§cThat item cannot be duplicated.");
            return;
        }
        dupeCooldown.put(player.getUniqueId(), now);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(hand.clone());
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§aDuplicated §f" + hand.getType().name() + " §ax" + hand.getAmount() + ".");
    }

    public boolean isBlockedForDupe(ItemStack item) {
        if (crates.isCrateItem(item)) return true;
        if (item.getType() == Material.MACE) return true;
        ItemMetaLike meta = new ItemMetaLike(item);
        if (meta.displayName().toLowerCase(Locale.ROOT).contains("heart")) return true;
        if (item.getType().name().contains("NETHERITE") && hasOverVanillaEnchant(item)) return true;
        if (containsCrateItem(item)) return true;
        return false;
    }

    private boolean hasOverVanillaEnchant(ItemStack item) {
        for (var entry : item.getEnchantments().entrySet()) {
            int max = entry.getKey().getMaxLevel();
            if (entry.getValue() > max) return true;
        }
        return false;
    }

    private boolean containsCrateItem(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta instanceof BlockStateMeta bsm && bsm.getBlockState() instanceof ShulkerBox shulker) {
            for (ItemStack child : shulker.getInventory().getContents()) {
                if (containsCrateItem(child)) return true;
            }
        }
        if (meta instanceof BundleMeta bundle) {
            try {
                Method m = bundle.getClass().getMethod("getItems");
                Object result = m.invoke(bundle);
                if (result instanceof Iterable<?> iterable) {
                    for (Object obj : iterable) if (obj instanceof ItemStack child && containsCrateItem(child)) return true;
                }
            } catch (ReflectiveOperationException ignored) {
                // Paper/API variants can expose bundle contents differently.
            }
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        playerData.initialize(p);
        event.joinMessage(Component.text(playerData.prefix(p) + "§a" + p.getName() + " joined."));
        p.sendMessage("§d§lWelcome to VoidDupe!");
        p.sendMessage("§7Rank: " + playerData.prefix(p) + " §7Hearts: §c" + playerData.getHearts(p) + " §7Balance: §6$" + Economy.format(playerData.getMoney(p)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onChat(AsyncPlayerChatEvent event) {
        event.setFormat(playerData.prefix(event.getPlayer()) + "%2$s §f%1$s");
    }

    @EventHandler
    public void onBreak(BlockBreakEvent event) {
        Player p = event.getPlayer();
        if (playerData.isStaff(p)) return;

        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand != null && economy.isSellAxe(hand) && event.getBlock().getState() instanceof org.bukkit.block.Chest chest) {
            event.setDropItems(false);
            economy.sellChest(p, chest.getBlockInventory());
            return;
        }

        event.setCancelled(true);
        p.sendMessage("§cYou cannot break blocks here.");
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent event) {
        if (!playerData.isStaff(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("§cYou cannot place blocks here.");
        }
    }

    @EventHandler
    public void onPhysicalCrate(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null) return;
        String type = crates.getPhysicalCrate(block);
        if (type == null) return;
        event.setCancelled(true);
        ItemStack hand = event.getItem();
        if (!crates.isKey(hand,type)) {
            event.getPlayer().sendMessage("§cYou need the matching " + type + " key.");
            return;
        }
        if (crates.consumeKey(event.getPlayer(),type)) crates.openCrate(event.getPlayer(),type,1);
    }

    @EventHandler
    public void onInventory(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player p)) return;
        economy.handleShopClick(p,event);
        economy.handleAuctionClick(p,event);
        gui.handleClick(p,event);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        if (commands.isFrozen(event.getPlayer()) && event.getFrom().distanceSquared(event.getTo()) > 0.001) {
            Location from = event.getFrom();
            event.setTo(from);
        }
    }

    @EventHandler
    public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player p && commands.isGod(p)) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        dupeCooldown.remove(event.getPlayer().getUniqueId());
    }

    private static final class ItemMetaLike {
        private final ItemStack item;
        ItemMetaLike(ItemStack item) { this.item = item; }
        String displayName() {
            if (!item.hasItemMeta() || item.getItemMeta().displayName() == null) return "";
            return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                    .serialize(item.getItemMeta().displayName());
        }
    }
}

package com.voiddupe;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public final class Economy {
    private final VoidDupe plugin;
    private final PlayerData data;
    private final NamespacedKey auctionKey;
    private final Map<UUID, AuctionListing> listings = new LinkedHashMap<>();
    private final Map<Material, Double> sellPrices = new EnumMap<>(Material.class);

    public Economy(VoidDupe plugin, PlayerData data) {
        this.plugin = plugin;
        this.data = data;
        this.auctionKey = new NamespacedKey(plugin, "auction_id");
        setupPrices();
    }

    private void setupPrices() {
        price(Material.BAMBOO,30); price(Material.CRIMSON_STEM,25); price(Material.WARPED_STEM,25);
        price(Material.SHROOMLIGHT,28); price(Material.CRIMSON_ROOTS,12); price(Material.WARPED_ROOTS,12);
        price(Material.CLAY_BALL,28); price(Material.MUD,18); price(Material.NETHER_WART,90);
        price(Material.WHEAT,25); price(Material.CARROT,30); price(Material.POTATO,30);
        price(Material.BEETROOT,28); price(Material.SUGAR_CANE,18); price(Material.MELON_SLICE,12);
        price(Material.PUMPKIN,22); price(Material.KELP,12); price(Material.COCOA_BEANS,25);
        price(Material.SWEET_BERRIES,12); price(Material.GLOW_BERRIES,22); price(Material.CACTUS,22);
        price(Material.CHORUS_FRUIT,50);
    }

    private void price(Material material, double value) {
        sellPrices.put(material, value);
    }

    public double getMoney(Player player) {
        return data.getMoney(player);
    }

    public boolean take(Player player, double amount) {
        if (amount < 0 || getMoney(player) < amount) return false;
        data.addMoney(player, -amount);
        return true;
    }

    public void give(Player player, double amount) {
        if (amount > 0) data.addMoney(player, amount);
    }

    public void set(Player player, double amount) {
        data.setMoney(player, amount);
    }

    public void openShop(Player player) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("shop:main"), 27, Component.text("VoidDupe Shop"));
        String[] names = {"Farming","Building","Redstone","Combat","Utility","Food","Mob Drops","Bulk"};
        for (int i = 0; i < names.length; i++) {
            ItemStack item = new ItemStack(categoryMaterial(names[i]));
            ItemMeta meta = item.getItemMeta();
            meta.displayName(Component.text(names[i]));
            meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "shop_category"), PersistentDataType.STRING, names[i]);
            item.setItemMeta(meta);
            inv.setItem(i + 9, item);
        }
        player.openInventory(inv);
    }

    public void openCategory(Player player, String category) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("shop:" + category), 54, Component.text("Shop • " + category));
        List<ShopItem> items = shopItems(category);
        for (int i = 0; i < Math.min(items.size(), 54); i++) {
            ShopItem s = items.get(i);
            ItemStack display = new ItemStack(s.material(), s.displayAmount());
            ItemMeta meta = display.getItemMeta();
            meta.displayName(Component.text(s.material().name() + " §7— §6$" + format(s.price())));
            display.setItemMeta(meta);
            inv.setItem(i, display);
        }
        player.openInventory(inv);
    }

    public void handleShopClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder)) return;
        if (!holder.id().startsWith("shop:")) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        String id = holder.id();
        if (id.equals("shop:main")) {
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || clicked.getType().isAir()) return;
            ItemMeta meta = clicked.getItemMeta();
            if (meta == null) return;
            String category = meta.getPersistentDataContainer().get(new NamespacedKey(plugin, "shop_category"), PersistentDataType.STRING);
            if (category != null) openCategory(player, category);
            return;
        }
        List<ShopItem> items = shopItems(id.substring("shop:".length()));
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= items.size()) return;
        ShopItem s = items.get(slot);
        int amount = event.isShiftClick() ? s.stack() : s.displayAmount();
        double cost = s.price() * amount;
        if (!take(player, cost)) {
            player.sendMessage("§cYou need $" + format(cost) + ". Balance: $" + format(getMoney(player)));
            return;
        }
        giveOrDrop(player, new ItemStack(s.material(), amount));
        player.sendMessage("§aPurchased " + amount + "x " + s.material().name() + " for $" + format(cost) + ".");
    }

    private List<ShopItem> shopItems(String category) {
        List<ShopItem> list = new ArrayList<>();
        switch (category) {
            case "Farming" -> {
                add(list, Material.WHEAT,25,16,64); add(list,Material.CARROT,30,16,64);
                add(list,Material.POTATO,30,16,64); add(list,Material.SUGAR_CANE,18,16,64);
                add(list,Material.BAMBOO,30,16,64); add(list,Material.PUMPKIN,22,16,64);
                add(list,Material.MELON_SLICE,12,16,64); add(list,Material.NETHER_WART,90,16,64);
            }
            case "Building" -> {
                add(list,Material.STONE,8,64,64); add(list,Material.COBBLESTONE,5,64,64);
                add(list,Material.OAK_PLANKS,10,64,64); add(list,Material.BRICKS,50,16,64);
                add(list,Material.GLASS,25,32,64); add(list,Material.BONE_BLOCK,5.0/64.0,64,64);
            }
            case "Redstone" -> {
                add(list,Material.REDSTONE,20,16,64); add(list,Material.REDSTONE_TORCH,50,16,64);
                add(list,Material.REPEATER,250,1,64); add(list,Material.COMPARATOR,300,1,64);
                add(list,Material.PISTON,500,1,64); add(list,Material.HOPPER,1000,1,64);
            }
            case "Combat" -> {
                add(list,Material.IRON_SWORD,1000,1,64); add(list,Material.DIAMOND_SWORD,10000,1,64);
                add(list,Material.BOW,2500,1,64); add(list,Material.ARROW,25,16,64);
                add(list,Material.SHIELD,2500,1,64); add(list,Material.ENCHANTED_GOLDEN_APPLE,50000,1,64);
                add(list,Material.TOTEM_OF_UNDYING,500000,1,64);
            }
            case "Utility" -> {
                add(list,Material.CHEST,500,1,64); add(list,Material.ENDER_CHEST,5000,1,64);
                add(list,Material.ENDER_PEARL,250,4,64); add(list,Material.WATER_BUCKET,1000,1,64);
                add(list,Material.LAVA_BUCKET,1000,1,64);
            }
            case "Food" -> {
                add(list,Material.BREAD,50,16,64); add(list,Material.COOKED_BEEF,100,16,64);
                add(list,Material.GOLDEN_CARROT,500,8,64); add(list,Material.GOLDEN_APPLE,5000,1,64);
            }
            case "Mob Drops" -> {
                add(list,Material.BONE,50,16,64); add(list,Material.STRING,50,16,64);
                add(list,Material.GUNPOWDER,100,16,64); add(list,Material.ENDER_PEARL,250,4,64);
                add(list,Material.BLAZE_ROD,500,4,64); add(list,Material.SLIME_BALL,150,4,64);
            }
            case "Bulk" -> {
                add(list,Material.BONE_BLOCK,5.0/64.0,64,64);
                add(list,Material.COBBLESTONE,2,64,64); add(list,Material.DIRT,2,64,64);
                add(list,Material.NETHERRACK,2,64,64); add(list,Material.END_STONE,10,64,64);
            }
        }
        return list;
    }

    private void add(List<ShopItem> list, Material material, double price, int displayAmount, int stack) {
        list.add(new ShopItem(material, price, displayAmount, stack));
    }

    private Material categoryMaterial(String name) {
        return switch (name) {
            case "Farming" -> Material.WHEAT;
            case "Building" -> Material.BRICKS;
            case "Redstone" -> Material.REDSTONE;
            case "Combat" -> Material.DIAMOND_SWORD;
            case "Utility" -> Material.CHEST;
            case "Food" -> Material.GOLDEN_CARROT;
            case "Mob Drops" -> Material.BONE;
            default -> Material.BONE_BLOCK;
        };
    }

    public ItemStack createSellAxe() {
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta meta = axe.getItemMeta();
        meta.displayName(Component.text("§6Sell Axe"));
        meta.lore(List.of(Component.text("§7Break a chest to sell its sellable contents.")));
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "sell_axe"), PersistentDataType.BYTE, (byte) 1);
        axe.setItemMeta(meta);
        return axe;
    }

    public boolean isSellAxe(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_AXE || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(
                new NamespacedKey(plugin, "sell_axe"), PersistentDataType.BYTE);
    }

    public double sellChest(Player player, Inventory inventory) {
        double total = 0;
        for (int i = 0; i < inventory.getSize(); i++) {
            ItemStack item = inventory.getItem(i);
            if (item == null || item.getType().isAir()) continue;
            Double price = sellPrices.get(item.getType());
            if (price == null) continue;
            total += price * item.getAmount();
            inventory.setItem(i, null);
        }
        if (total > 0) {
            data.addMoney(player, total);
            player.sendMessage("§aSold chest contents for §6$" + format(total) + "§a.");
        } else {
            player.sendMessage("§eThere was nothing sellable in that chest.");
        }
        return total;
    }

    public void listItem(Player player, double price) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand.isEmpty()) {
            player.sendMessage("§cHold an item to sell.");
            return;
        }
        if (price <= 0) {
            player.sendMessage("§cPrice must be greater than zero.");
            return;
        }
        ItemStack copy = hand.clone();
        player.getInventory().setItemInMainHand(null);
        UUID id = UUID.randomUUID();
        listings.put(id, new AuctionListing(id, player.getUniqueId(), copy, price));
        player.sendMessage("§aListed " + copy.getAmount() + "x " + copy.getType() + " for §6$" + format(price) + "§a.");
    }

    public void openAuction(Player player) {
        Inventory inv = Bukkit.createInventory(new MenuHolder("ah"), 54, Component.text("VoidDupe Auction House"));
        int slot = 0;
        for (AuctionListing listing : listings.values()) {
            if (slot >= 54) break;
            ItemStack display = listing.item().clone();
            ItemMeta meta = display.getItemMeta();
            List<Component> lore = new ArrayList<>();
            if (meta != null && meta.lore() != null) lore.addAll(meta.lore());
            lore.add(Component.text("§6Price: $" + format(listing.price())));
            lore.add(Component.text("§7Click to buy"));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(auctionKey, PersistentDataType.STRING, listing.id().toString());
            display.setItemMeta(meta);
            inv.setItem(slot++, display);
        }
        player.openInventory(inv);
    }

    public void handleAuctionClick(Player player, InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof MenuHolder holder) || !holder.id().equals("ah")) return;
        event.setCancelled(true);
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String raw = clicked.getItemMeta().getPersistentDataContainer().get(auctionKey, PersistentDataType.STRING);
        if (raw == null) return;
        UUID id;
        try { id = UUID.fromString(raw); } catch (IllegalArgumentException ex) { return; }
        AuctionListing listing = listings.get(id);
        if (listing == null) {
            player.sendMessage("§cThat listing is no longer available.");
            openAuction(player);
            return;
        }
        if (!take(player, listing.price())) {
            player.sendMessage("§cYou need $" + format(listing.price()) + ".");
            return;
        }
        listings.remove(id);
        giveOrDrop(player, listing.item().clone());
        Player seller = Bukkit.getPlayer(listing.seller());
        if (seller != null) {
            data.addMoney(seller, listing.price());
            seller.sendMessage("§aYour auction sold for §6$" + format(listing.price()) + "§a.");
        }
        player.sendMessage("§aPurchased the item for §6$" + format(listing.price()) + "§a.");
        openAuction(player);
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(i -> player.getWorld().dropItemNaturally(player.getLocation(), i));
    }

    public static String format(double value) {
        return String.format(Locale.US, "%,.2f", value);
    }

    public record ShopItem(Material material, double price, int displayAmount, int stack) {}
    public record AuctionListing(UUID id, UUID seller, ItemStack item, double price) {}

    public static final class MenuHolder implements InventoryHolder {
        private final String id;
        public MenuHolder(String id) { this.id = id; }
        public String id() { return id; }
        @Override public Inventory getInventory() { return null; }
    }
}

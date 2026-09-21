package com.voiddupe;

import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.*;
import java.util.stream.Collectors;

public final class CommandHandler implements CommandExecutor, TabCompleter {
    private final VoidDupe plugin;
    private final PlayerData data;
    private final Economy economy;
    private final Crates crates;
    private final GUIManager gui;
    private final Set<UUID> frozen = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Set<UUID> god = new HashSet<>();

    public CommandHandler(VoidDupe plugin, PlayerData data, Economy economy, Crates crates, GUIManager gui) {
        this.plugin = plugin;
        this.data = data;
        this.economy = economy;
        this.crates = crates;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;

        if (cmd.equals("help")) { help(sender); return true; }
        if (cmd.equals("money")) { money(sender,args); return true; }
        if (!(sender instanceof Player)) {
            sender.sendMessage("This command requires a player.");
            return true;
        }

        switch (cmd) {
            case "dupe" -> plugin.dupe(player);
            case "fly" -> staff(player, () -> { player.setAllowFlight(!player.getAllowFlight()); player.setFlying(player.getAllowFlight()); player.sendMessage("§aFly: " + player.getAllowFlight()); });
            case "god" -> staff(player, () -> { toggle(god, player); player.sendMessage("§aGod: " + god.contains(player.getUniqueId())); });
            case "heal" -> staff(player, () -> { player.setHealth(player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()); player.setFoodLevel(20); player.setFireTicks(0); player.sendMessage("§aHealed."); });
            case "vanish" -> staff(player, () -> toggleVanish(player));
            case "speed" -> speed(player,args);
            case "sudo" -> sudo(player,args);
            case "freeze" -> freeze(player,args);
            case "launch" -> targetAction(player,args,"launch");
            case "smite" -> targetAction(player,args,"smite");
            case "rank" -> rank(player,args);
            case "hearts" -> hearts(player,args);
            case "sellaxe" -> sellAxe(player);
            case "kit" -> kit(player,args);
            case "ah" -> ah(player,args);
            case "shop" -> economy.openShop(player);
            case "crate" -> crate(player,args);
            case "cosmetics" -> gui.openCosmetics(player);
            case "setcrate" -> setCrate(player,args);
            default -> player.sendMessage("§cUnknown command. Use /help.");
        }
        return true;
    }

    private void help(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§dVoidDupe commands: /money /dupe /shop /ah /crate /kit /sellaxe /cosmetics");
            return;
        }
        player.sendMessage("§d§lVoidDupe §7— §fCommands");
        player.sendMessage("§f/dupe §7/ /money §7/ /sellaxe §7/ /kit §7/ /ah §7/ /shop §7/ /crate §7/ /cosmetics");
        if (data.isStaff(player)) {
            player.sendMessage("§cStaff: §f/fly /god /heal /vanish /speed /sudo /freeze /launch /smite /rank /hearts /setcrate");
        }
    }

    private void money(CommandSender sender, String[] args) {
        if (args.length == 0) {
            if (!(sender instanceof Player p)) { sender.sendMessage("§cUsage: /money <player>"); return; }
            sender.sendMessage("§aBalance: §6$" + Economy.format(data.getMoney(p)));
            return;
        }
        if (args.length == 1 && sender instanceof Player p) {
            Player target = Bukkit.getPlayerExact(args[0]);
            if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
            sender.sendMessage(target.getName() + " has §6$" + Economy.format(data.getMoney(target)));
            return;
        }
        if (!(sender instanceof Player p) || !data.isStaff(p)) {
            sender.sendMessage("§cStaff only.");
            return;
        }
        if (args.length != 3) { sender.sendMessage("§c/money <give|take|set> <player> <amount>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage("§cPlayer not found."); return; }
        double amount = parseDouble(args[2]);
        if (amount < 0) { sender.sendMessage("§cInvalid amount."); return; }
        switch (args[0].toLowerCase()) {
            case "give" -> economy.give(target, amount);
            case "take" -> economy.take(target, amount);
            case "set" -> economy.set(target, amount);
            default -> { sender.sendMessage("§cUse give, take, or set."); return; }
        }
        sender.sendMessage("§aUpdated " + target.getName() + "'s balance to §6$" + Economy.format(data.getMoney(target)));
    }

    private void speed(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        if (args.length != 1) { p.sendMessage("§c/speed <1-10>"); return; }
        try {
            float n = Math.max(1, Math.min(10, Float.parseFloat(args[0])));
            p.setWalkSpeed(Math.min(1f, n / 10f));
            p.setFlySpeed(Math.min(1f, n / 10f));
            p.sendMessage("§aSpeed set to " + n + ".");
        } catch (NumberFormatException e) { p.sendMessage("§cInvalid speed."); }
    }

    private void sudo(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        if (args.length < 2) { p.sendMessage("§c/sudo <player> <cmd>"); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage("§cPlayer not found."); return; }
        String command = String.join(" ", Arrays.copyOfRange(args,1,args.length));
        target.performCommand(command);
    }

    private void freeze(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        Player target = target(p,args);
        if (target == null) return;
        toggle(frozen,target);
        target.sendMessage(frozen.contains(target.getUniqueId()) ? "§cYou are frozen." : "§aYou are unfrozen.");
        p.sendMessage("§aFreeze: " + frozen.contains(target.getUniqueId()));
    }

    private void targetAction(Player p, String[] args, String action) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        Player target = target(p,args);
        if (target == null) return;
        if (action.equals("launch")) target.setVelocity(new org.bukkit.util.Vector(0,1.5,0));
        else target.getWorld().strikeLightningEffect(target.getLocation());
    }

    private void rank(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        if (args.length != 2) { p.sendMessage("§c/rank <player> <rank>"); return; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) { p.sendMessage("§cPlayer not found."); return; }
        try {
            PlayerData.Rank.valueOf(args[1].toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            p.sendMessage("§cRanks: OWNER ADMIN VOID WARDEN ECHO PLAYER");
            return;
        }
        if (args[1].equalsIgnoreCase("OWNER") && !data.getRank(p).equals("OWNER")) {
            p.sendMessage("§cOnly OWNER can grant OWNER.");
            return;
        }
        data.setRank(target,args[1]);
        p.sendMessage("§aRank updated.");
        target.sendMessage("§aYour rank is now " + args[1].toUpperCase(Locale.ROOT) + ".");
    }

    private void hearts(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        if (args.length != 3) { p.sendMessage("§c/hearts <add|remove|set> <player> <amount>"); return; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { p.sendMessage("§cPlayer not found."); return; }
        int amount;
        try { amount = Integer.parseInt(args[2]); } catch (NumberFormatException e) { p.sendMessage("§cInvalid amount."); return; }
        switch(args[0].toLowerCase()) {
            case "add" -> data.addHearts(target,amount);
            case "remove" -> data.addHearts(target,-amount);
            case "set" -> data.setHearts(target,amount);
            default -> { p.sendMessage("§cUse add, remove, or set."); return; }
        }
        p.sendMessage("§a" + target.getName() + " now has " + data.getHearts(target) + " hearts.");
    }

    private void sellAxe(Player p) {
        double cost = 10_000_000D;
        if (!economy.take(p,cost)) {
            p.sendMessage("§cYou need $10,000,000. Balance: $" + Economy.format(data.getMoney(p)));
            return;
        }
        Map<Integer,ItemStack> leftovers = p.getInventory().addItem(economy.createSellAxe());
        leftovers.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(),i));
        p.sendMessage("§aPurchased the Sell Axe for §6$10,000,000§a.");
    }

    private void kit(Player p, String[] args) {
        if (args.length != 1) { p.sendMessage("§c/kit <echo|warden|void>"); return; }
        String type = args[0].toLowerCase();
        boolean ok = switch(type) {
            case "void" -> data.isVoidOrAbove(p);
            case "warden" -> data.isAtLeastWarden(p);
            case "echo" -> true;
            default -> false;
        };
        if (!ok) { p.sendMessage("§cYou do not have access to that kit."); return; }
        if (!type.equals("echo") && data.getRank(p).equals("PLAYER")) { p.sendMessage("§cRank required."); return; }
        ItemStack item = crates.key(type);
        p.getInventory().addItem(item);
        p.sendMessage("§aClaimed " + type + " kit.");
    }

    private void ah(Player p, String[] args) {
        if (args.length == 0) economy.openAuction(p);
        else if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            try { economy.listItem(p,Double.parseDouble(args[1])); } catch (NumberFormatException e) { p.sendMessage("§cInvalid price."); }
        } else p.sendMessage("§c/ah or /ah sell <price>");
    }

    private void crate(Player p, String[] args) {
        if (args.length != 2) { p.sendMessage("§c/crate <echo|warden|void|voidplus|heart> <amount>"); return; }
        try { crates.openVirtual(p,args[0],Integer.parseInt(args[1])); } catch (NumberFormatException e) { p.sendMessage("§cInvalid amount."); }
    }

    private void setCrate(Player p, String[] args) {
        if (!data.isStaff(p)) { p.sendMessage("§cStaff only."); return; }
        if (args.length != 1) { p.sendMessage("§c/setcrate <type>"); return; }
        BlockTarget bt = lookChest(p);
        if (bt == null) { p.sendMessage("§cLook at a chest within 5 blocks."); return; }
        String type = args[0].toLowerCase();
        if (!Set.of("echo","warden","void","voidplus","heart").contains(type)) { p.sendMessage("§cInvalid crate type."); return; }
        crates.setPhysicalCrate(bt.block(),type);
        p.sendMessage("§aChest set as a " + type + " physical crate.");
    }

    private BlockTarget lookChest(Player p) {
        org.bukkit.block.Block b = p.getTargetBlockExact(5);
        if (b != null && b.getState() instanceof org.bukkit.block.Chest) return new BlockTarget(b);
        return null;
    }

    private Player target(Player p,String[] args) {
        if (args.length != 1) { p.sendMessage("§cProvide a player."); return null; }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) p.sendMessage("§cPlayer not found.");
        return target;
    }

    private void toggle(Set<UUID> set, Player p) {
        if (!set.add(p.getUniqueId())) set.remove(p.getUniqueId());
    }

    private void toggleVanish(Player p) {
        UUID id = p.getUniqueId();
        if (vanished.add(id)) {
            for (Player other : Bukkit.getOnlinePlayers()) other.hidePlayer(plugin,p);
            p.sendMessage("§aVanish enabled.");
        } else {
            for (Player other : Bukkit.getOnlinePlayers()) other.showPlayer(plugin,p);
            p.sendMessage("§aVanish disabled.");
        }
    }

    private void staff(Player p,Runnable action) {
        if (!data.isStaff(p)) p.sendMessage("§cStaff only.");
        else action.run();
    }

    private double parseDouble(String s) {
        try { return Double.parseDouble(s); } catch (NumberFormatException e) { return -1; }
    }

    public boolean isFrozen(Player p) { return frozen.contains(p.getUniqueId()); }
    public boolean isGod(Player p) { return god.contains(p.getUniqueId()); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String cmd = command.getName().toLowerCase();
        if (cmd.equals("rank") && args.length == 2) return filter(List.of("OWNER","ADMIN","VOID","WARDEN","ECHO","PLAYER"),args[1]);
        if (cmd.equals("money") && args.length == 1) return filter(List.of("give","take","set"),args[0]);
        if (cmd.equals("hearts") && args.length == 1) return filter(List.of("add","remove","set"),args[0]);
        if (cmd.equals("kit") && args.length == 1) return filter(List.of("echo","warden","void"),args[0]);
        if (cmd.equals("crate") && args.length == 1) return filter(List.of("echo","warden","void","voidplus","heart"),args[0]);
        if (cmd.equals("setcrate") && args.length == 1) return filter(List.of("echo","warden","void","voidplus","heart"),args[0]);
        return List.of();
    }

    private List<String> filter(List<String> values,String prefix) {
        return values.stream().filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase())).collect(Collectors.toList());
    }

    private record BlockTarget(org.bukkit.block.Block block) {}
}

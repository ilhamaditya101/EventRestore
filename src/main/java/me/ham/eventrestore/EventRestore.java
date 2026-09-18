package me.ham.eventrestore;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.*;

public final class EventRestore extends JavaPlugin implements Listener, CommandExecutor {
    private final Map<UUID, StoredInventory> storage = new HashMap<>();
    private final Map<UUID, Inventory> openGuis = new HashMap<>();

    @Override
    public void onEnable() {
        getDataFolder().mkdirs();
        loadAll();
        Objects.requireNonNull(getCommand("event")).setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("EventRestore enabled.");
    }

    @Override
    public void onDisable() {
        saveAll();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can use this command.");
            return true;
        }

        if (args.length == 0) {
            help(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "restore" -> backupAndClear(player);
            case "claim" -> openClaim(player);
            case "admin" -> adminCommand(player, args);
            default -> help(player);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(ChatColor.AQUA + "EventRestore");
        p.sendMessage(ChatColor.GRAY + "/event restore " + ChatColor.WHITE + "- save and clear your inventory");
        p.sendMessage(ChatColor.GRAY + "/event claim " + ChatColor.WHITE + "- open your saved inventory");
        if (p.hasPermission("eventrestore.admin")) {
            p.sendMessage(ChatColor.GRAY + "/event admin <player> " + ChatColor.WHITE + "- inspect saved inventory");
            p.sendMessage(ChatColor.GRAY + "/event admin restore <player> " + ChatColor.WHITE + "- restore everything");
        }
    }

    private void backupAndClear(Player p) {
        UUID uuid = p.getUniqueId();

        if (storage.containsKey(uuid) && storage.get(uuid).hasItems()) {
            p.sendMessage(ChatColor.RED + "You already have a saved inventory. Claim it first.");
            return;
        }

        PlayerInventory inv = p.getInventory();
        ItemStack[] contents = cloneArray(inv.getStorageContents());
        ItemStack[] armor = cloneArray(inv.getArmorContents());
        ItemStack offhand = cloneItem(inv.getItemInOffHand());

        storage.put(uuid, new StoredInventory(contents, armor, offhand, p.getName()));
        save(uuid);

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setItemInOffHand(new ItemStack(Material.AIR));

        p.updateInventory();
        p.sendMessage(ChatColor.GREEN + "Inventory saved and cleared for the event.");
    }

    private void openClaim(Player p) {
        StoredInventory saved = storage.get(p.getUniqueId());
        if (saved == null || !saved.hasItems()) {
            p.sendMessage(ChatColor.YELLOW + "You have no saved inventory.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54, ChatColor.DARK_AQUA + "Your Saved Inventory");

        ItemStack[] contents = saved.contents;
        for (int i = 0; i < Math.min(contents.length, 36); i++) {
            if (contents[i] != null && contents[i].getType() != Material.AIR) {
                gui.setItem(i, cloneItem(contents[i]));
            }
        }

        gui.setItem(45, named(Material.IRON_CHESTPLATE, ChatColor.AQUA + "Armor",
                ChatColor.GRAY + "Click to reclaim armor."));
        gui.setItem(47, named(Material.SHIELD, ChatColor.AQUA + "Offhand",
                ChatColor.GRAY + "Click to reclaim offhand."));
        gui.setItem(49, named(Material.CHEST, ChatColor.GREEN + "Claim All",
                ChatColor.GRAY + "Reclaim all saved items."));
        gui.setItem(53, named(Material.BARRIER, ChatColor.RED + "Close",
                ChatColor.GRAY + "Close this menu."));

        openGuis.put(p.getUniqueId(), gui);
        p.openInventory(gui);
    }

    private void adminCommand(Player admin, String[] args) {
        if (!admin.hasPermission("eventrestore.admin")) {
            admin.sendMessage(ChatColor.RED + "No permission.");
            return;
        }
        if (args.length < 2) {
            admin.sendMessage(ChatColor.YELLOW + "/event admin <player>");
            admin.sendMessage(ChatColor.YELLOW + "/event admin restore <player>");
            return;
        }

        if (args[1].equalsIgnoreCase("restore")) {
            if (args.length < 3) {
                admin.sendMessage(ChatColor.YELLOW + "/event admin restore <player>");
                return;
            }
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) {
                admin.sendMessage(ChatColor.RED + "Player must be online.");
                return;
            }
            restoreAll(target);
            admin.sendMessage(ChatColor.GREEN + "Restored inventory for " + target.getName() + ".");
            return;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Player must be online.");
            return;
        }
        openAdmin(admin, target);
    }

    private void openAdmin(Player admin, Player target) {
        StoredInventory saved = storage.get(target.getUniqueId());
        if (saved == null || !saved.hasItems()) {
            admin.sendMessage(ChatColor.YELLOW + "That player has no saved inventory.");
            return;
        }

        Inventory gui = Bukkit.createInventory(null, 54,
                ChatColor.DARK_RED + "Saved: " + target.getName());

        for (int i = 0; i < Math.min(saved.contents.length, 36); i++) {
            if (saved.contents[i] != null && saved.contents[i].getType() != Material.AIR) {
                gui.setItem(i, cloneItem(saved.contents[i]));
            }
        }
        gui.setItem(49, named(Material.CHEST, ChatColor.GREEN + "Restore All",
                ChatColor.GRAY + "Click to restore to the player."));
        openGuis.put(admin.getUniqueId(), gui);
        admin.openInventory(gui);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui == null || e.getView().getTopInventory() != gui) return;

        e.setCancelled(true);
        if (e.getClickedInventory() != gui) return;

        int slot = e.getRawSlot();

        if (gui.getHolder() != null) return;

        if (e.getView().getTitle().equals(ChatColor.DARK_AQUA + "Your Saved Inventory")) {
            handlePlayerClaimClick(p, slot);
        } else if (e.getView().getTitle().startsWith(ChatColor.DARK_RED + "Saved: ")) {
            handleAdminClick(p, slot);
        }
    }

    private void handlePlayerClaimClick(Player p, int slot) {
        StoredInventory saved = storage.get(p.getUniqueId());
        if (saved == null) return;

        if (slot >= 0 && slot < 36) {
            ItemStack item = saved.contents[slot];
            if (valid(item) && giveSafely(p, item)) {
                saved.contents[slot] = null;
                save(p.getUniqueId());
                refreshClaimGui(p);
            }
        } else if (slot == 45) {
            ItemStack[] armor = saved.armor;
            if (armor.length == 4) {
                boolean changed = false;
                for (int i = 0; i < armor.length; i++) {
                    if (valid(armor[i])) {
                        if (giveSafely(p, armor[i])) {
                            armor[i] = null;
                            changed = true;
                        } else break;
                    }
                }
                if (changed) save(p.getUniqueId());
                refreshClaimGui(p);
            }
        } else if (slot == 47) {
            if (valid(saved.offhand) && giveSafely(p, saved.offhand)) {
                saved.offhand = null;
                save(p.getUniqueId());
                refreshClaimGui(p);
            }
        } else if (slot == 49) {
            restoreAll(p);
            p.closeInventory();
        } else if (slot == 53) {
            p.closeInventory();
        }
    }

    private void handleAdminClick(Player admin, int slot) {
        if (slot != 49) return;
        String title = admin.getOpenInventory().getTitle();
        String targetName = ChatColor.stripColor(title).replace("Saved: ", "");
        Player target = Bukkit.getPlayerExact(targetName);
        if (target == null) {
            admin.sendMessage(ChatColor.RED + "Target player is no longer online.");
            admin.closeInventory();
            return;
        }
        restoreAll(target);
        admin.sendMessage(ChatColor.GREEN + "Inventory restored for " + target.getName() + ".");
        admin.closeInventory();
    }

    private void restoreAll(Player p) {
        StoredInventory saved = storage.get(p.getUniqueId());
        if (saved == null || !saved.hasItems()) return;

        // Restore normal inventory only where possible; overflow goes to drops at the player's feet.
        PlayerInventory inv = p.getInventory();

        ItemStack[] contents = cloneArray(saved.contents);
        for (int i = 0; i < contents.length && i < 36; i++) {
            if (valid(contents[i])) {
                inv.setItem(i, contents[i]);
            }
        }

        if (saved.armor.length == 4) {
            inv.setArmorContents(cloneArray(saved.armor));
        }
        if (valid(saved.offhand)) {
            inv.setItemInOffHand(cloneItem(saved.offhand));
        }

        storage.remove(p.getUniqueId());
        save(p.getUniqueId());
        p.updateInventory();
        p.sendMessage(ChatColor.GREEN + "Your saved inventory has been restored.");
    }

    private boolean giveSafely(Player p, ItemStack item) {
        Map<Integer, ItemStack> leftovers = p.getInventory().addItem(cloneItem(item));
        if (leftovers.isEmpty()) return true;

        // Do not delete the stored item when inventory is full.
        return false;
    }

    private void refreshClaimGui(Player p) {
        p.closeInventory();
        Bukkit.getScheduler().runTask(this, () -> openClaim(p));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            openGuis.remove(p.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        openGuis.remove(e.getPlayer().getUniqueId());
        save(e.getPlayer().getUniqueId());
    }

    private void loadAll() {
        File dir = new File(getDataFolder(), "players");
        if (!dir.exists()) return;

        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            try {
                UUID uuid = UUID.fromString(file.getName().replace(".yml", ""));
                StoredInventory saved = StoredInventory.load(file);
                if (saved != null) storage.put(uuid, saved);
            } catch (Exception ex) {
                getLogger().warning("Could not load " + file.getName() + ": " + ex.getMessage());
            }
        }
    }

    private void saveAll() {
        for (UUID uuid : storage.keySet()) save(uuid);
    }

    private void save(UUID uuid) {
        StoredInventory saved = storage.get(uuid);
        File dir = new File(getDataFolder(), "players");
        if (!dir.exists()) dir.mkdirs();

        File file = new File(dir, uuid + ".yml");
        if (saved == null || !saved.hasItems()) {
            if (file.exists()) file.delete();
            return;
        }
        saved.save(file);
    }

    private static ItemStack[] cloneArray(ItemStack[] source) {
        if (source == null) return new ItemStack[0];
        ItemStack[] out = new ItemStack[source.length];
        for (int i = 0; i < source.length; i++) out[i] = cloneItem(source[i]);
        return out;
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static boolean valid(ItemStack item) {
        return item != null && item.getType() != Material.AIR;
    }

    private static ItemStack named(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        var meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class StoredInventory {
        ItemStack[] contents;
        ItemStack[] armor;
        ItemStack offhand;
        String playerName;

        StoredInventory(ItemStack[] contents, ItemStack[] armor, ItemStack offhand, String playerName) {
            this.contents = contents;
            this.armor = armor;
            this.offhand = offhand;
            this.playerName = playerName;
        }

        boolean hasItems() {
            if (valid(offhand)) return true;
            for (ItemStack item : contents) if (valid(item)) return true;
            for (ItemStack item : armor) if (valid(item)) return true;
            return false;
        }

        void save(File file) {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);
            y.set("player-name", playerName);
            y.set("contents", Arrays.asList(contents));
            y.set("armor", Arrays.asList(armor));
            y.set("offhand", offhand);
            try {
                y.save(file);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        static StoredInventory load(File file) {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file);

            List<?> c = y.getList("contents");
            List<?> a = y.getList("armor");
            ItemStack[] contents = c == null ? new ItemStack[36] : c.toArray(new ItemStack[0]);
            ItemStack[] armor = a == null ? new ItemStack[4] : a.toArray(new ItemStack[0]);
            ItemStack offhand = y.getItemStack("offhand");
            String name = y.getString("player-name", "Unknown");

            return new StoredInventory(contents, armor, offhand, name);
        }
    }
}

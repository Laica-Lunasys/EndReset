package de.V10lator.EndReset;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EnderDragon;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class EndReset extends JavaPlugin implements Listener, Runnable {
    private final HashMap<String, HashMap<String, Long>> resetchunks = new HashMap<String, HashMap<String, Long>>();
    private final HashMap<String, Long> cvs = new HashMap<String, Long>();

    private final HashSet<String> reg = new HashSet<String>();

    private final HashMap<String, Integer> pids = new HashMap<String, Integer>();
    private long it = 1200;
    private boolean save = false;
    private final AtomicBoolean saveLock = new AtomicBoolean(false);

    private final HashSet<String> dontHandle = new HashSet<String>();
    private final HashMap<String, EndResetWorld> forceReset = new HashMap<String, EndResetWorld>();
    private final HashMap<String, Short> dragonAmount = new HashMap<String, Short>();

    private final HashMap<String, RegenThread> threads = new HashMap<String, RegenThread>();
    private final HashMap<String, Long> suspendedTasks = new HashMap<String, Long>();

    @Override
    @SuppressWarnings("unchecked")
    public void onEnable() {
        Server server = getServer();
        Logger log = getLogger();
        BukkitScheduler scheduler = server.getScheduler();
        try {
            File file = new File(getDataFolder(), "EndReset.sav");
            if (!file.exists()) {
                getDataFolder().mkdir();
            }
            else {
                ObjectInputStream in = new ObjectInputStream(new FileInputStream(file));
                // TODO: Rewrite (no more Object[] reading - holds compat for < 1.5)
                int sfv;
                Object[] sa = null;
                try {
                    Object o = in.readObject();
                    if (o == null || !(o instanceof Object[])) {
                        log.info("ERROR: セーブファイルを読み込めません！");
                        server.getPluginManager().disablePlugin(this);
                        in.close();
                        return;
                    }
                    sa = (Object[]) o;
                    sfv = (Integer) sa[0];
                } catch (OptionalDataException e) {
                    sfv = in.readInt();
                }

                RegenThread regenThread;
                World world;
                long tr;
                for (Entry<String, HashMap<String, Long>> e : ((HashMap<String, HashMap<String, Long>>) in.readObject()).entrySet())
                    resetchunks.put(e.getKey(), e.getValue());
                for (Entry<String, Long> e : ((HashMap<String, Long>) in.readObject()).entrySet())
                    cvs.put(e.getKey(), e.getValue());
                for (String regen : (HashSet<String>) in.readObject())
                    reg.add(regen);
                it = in.readLong();
                for (String dh : (HashSet<String>) in.readObject())
                    dontHandle.add(dh);
                for (Entry<String, EndResetWorld> e : ((HashMap<String, EndResetWorld>) in.readObject()).entrySet())
                    forceReset.put(e.getKey(), e.getValue());
                for (Entry<String, Short> e : ((HashMap<String, Short>) in.readObject()).entrySet())
                    dragonAmount.put(e.getKey(), e.getValue());
                for (Entry<String, Long> e : ((HashMap<String, Long>) in.readObject()).entrySet()) {
                    String w = e.getKey();
                    reg.add(w);
                    world = server.getWorld(w);
                    if (world != null) {
                        tr = e.getValue();
                        regenThread = new RegenThread(w, world.getFullTime() + tr);
                        scheduler.scheduleSyncDelayedTask(this, regenThread, tr);
                    }
                }
                
                in.close();
            }
        } catch (Exception ex) {
            log.info("セーブファイルを読み込めません！");
            ex.printStackTrace();
            server.getPluginManager().disablePlugin(this);
            return;
        }

        saveConfig();

        for (World world : server.getWorlds()) {
            if (world.getEnvironment() != Environment.THE_END) continue;
            onWorldLoad(new WorldLoadEvent(world));
        }

        scheduler.scheduleSyncRepeatingTask(this, new SaveThread(), 36000L, 36000L);
        scheduler.scheduleSyncRepeatingTask(this, this, 1L, 1L);
        scheduler.scheduleSyncRepeatingTask(this, new ForceThread(), 20L, 72000L);

        PluginManager pm = server.getPluginManager();
        pm.registerEvents(this, this);
        log.info("v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        getServer().getScheduler().cancelTasks(this);
        if (save) (new SaveThread()).run();
        getServer().getLogger().info("[" + getName() + "] disabled!");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("endreset.config")) return true;

        if (args.length < 1) {
            if (!(sender instanceof Player)) return true;

            World world = ((Player) sender).getWorld();
            if (world.getEnvironment() != Environment.THE_END) {
                sender.sendMessage(ChatColor.RED + "ここはエンドワールドではありません！");
                return true;
            }
            String worldName = world.getName();
            Actions.broadcastMessage("&c[SakuraServer] '&6" + worldName + "&d'をリセットしています.. (" + sender.getName() + ")");

            for (final Player player : world.getPlayers()) {
                player.teleport(getServer().getWorlds().get(0).getSpawnLocation());
                Actions.message(player, "&c[SakuraServer] &dこのワールドはリセットされます！");
            }

            long toRun = 1L;
            RegenThread regenThread = new RegenThread(worldName, world.getFullTime() + toRun);
            pids.put(worldName, getServer().getScheduler().scheduleSyncDelayedTask(this, regenThread, toRun));
            threads.put(worldName, regenThread);
            return true;
        }

        try {
            it = Integer.parseInt(args[0]);
            sender.sendMessage("New inactive time: " + it + " minutes");
            it = it * 20 * 60;
        } catch (NumberFormatException e) {
            if (args[0].equalsIgnoreCase("force")) {
                if (args.length < 3) {
                    sender.sendMessage("/EndReset force add/remove World_Name");
                    return true;
                }
                if (args[1].equalsIgnoreCase("add")) {
                    if (args.length < 4) {
                        sender.sendMessage("/EndReset force add World_Name hours");
                        return true;
                    }
                    try {
                        forceReset.put(args[2], new EndResetWorld(Integer.parseInt(args[3])));
                    } catch (NumberFormatException ex) {
                        sender.sendMessage("Invalid hours: " + args[3]);
                        return true;
                    }
                    sender.sendMessage(ChatColor.GREEN + args[2] + " will reset every " + args[3] + " hours now!");
                } else if (args[1].equalsIgnoreCase("remove")) {
                    if (!forceReset.containsKey(args[2])) {
                        sender.sendMessage("World " + args[2] + " not found!");
                        return true;
                    }
                    forceReset.remove(args[2]);
                    sender.sendMessage(ChatColor.GOLD + args[2] + " won't reset at a given time intervall anymore!");
                } else {
                    sender.sendMessage("/EndReset force add/remove");
                    return true;
                }
            } else if (args[0].equalsIgnoreCase("ignore")) {
                if (args.length < 2) {
                    sender.sendMessage("/EndReset ignore World_Name");
                    return true;
                }
                if (dontHandle.contains(args[1])) {
                    dontHandle.remove(args[1]);
                    sender.sendMessage(ChatColor.YELLOW + args[1] + " is no longer ignored!");
                } else {
                    dontHandle.add(args[1]);
                    sender.sendMessage(ChatColor.YELLOW + args[1] + " is ignored now!");
                }
            } else if (args[0].equalsIgnoreCase("amount")) {
                if (args.length < 3) {
                    sender.sendMessage("/EndReset amount World_Name X");
                    return true;
                }
                short a;
                try {
                    a = Short.parseShort(args[2]);
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Invalid amount: " + args[2]);
                    return true;
                }
                if (a == 1)
                    dragonAmount.remove(args[1]);
                else
                    dragonAmount.put(args[1], a);
                sender.sendMessage(ChatColor.BLUE + "New dragon amount for world " + args[1] + ": " + a);
            } else if (args[0].equalsIgnoreCase("list")) {
                List<World> wl = getServer().getWorlds();
                if (wl.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No worlds found!");
                    return true;
                }
                StringBuilder sb = new StringBuilder();
                World world;
                boolean first = true;
                for (int i = 1; i < wl.size(); i++) {
                    world = wl.get(i);
                    if (world != null && world.getEnvironment() == Environment.THE_END) {
                        if (!first)
                            sb.append(' ');
                        else
                            first = false;
                        sb.append(world.getName());
                    }
                }
                sb.insert(0, ChatColor.LIGHT_PURPLE);
                sender.sendMessage(sb.toString());
            } else
                return false;
        }
        save = true;
        return true;
    }

    private void regen(World world) {
        String worldName = world.getName();
        long cv = cvs.get(worldName) + 1;
        if (cv == Long.MAX_VALUE) cv = Long.MIN_VALUE;
        cvs.put(worldName, cv);
        for (Chunk chunk : world.getLoadedChunks())
            onChunkLoad(new ChunkLoadEvent(chunk, false));

        short amount;
        if (dragonAmount.containsKey(worldName))
            amount = dragonAmount.get(worldName);
        else
            amount = 1;
        if (amount > 1) {
            amount--;
            Location loc = world.getSpawnLocation();
            loc.setY(world.getMaxHeight() - 1);
            for (short i = 0; i < amount; i++)
                world.spawnEntity(loc, EntityType.ENDER_DRAGON);
        }
        save = true;
        Actions.broadcastMessage("&c[SakuraServer] &dエンドワールド'&6" + worldName + "&d'はリセットされました！");
    }

    @Override
    public void run() {
        int pc;
        int pid;
        BukkitScheduler scheduler = getServer().getScheduler();
        for (World world : getServer().getWorlds()) {
            if (world.getEnvironment() != Environment.THE_END) continue;
            
            String worldName = world.getName();
            if (!reg.contains(worldName)) continue;
            pc = world.getPlayers().size();
            if (pc < 1 && !pids.containsKey(worldName)) {
                long tr;
                if (!suspendedTasks.containsKey(worldName))
                    tr = it;
                else {
                    tr = suspendedTasks.get(worldName);
                    suspendedTasks.remove(worldName);
                }
                RegenThread regenThread = new RegenThread(worldName, world.getFullTime() + tr);
                pids.put(worldName, scheduler.scheduleSyncDelayedTask(EndReset.this, regenThread, tr));
                threads.put(worldName, regenThread);
            } else if (pc > 0 && pids.containsKey(worldName)) {
                pid = pids.get(worldName);
                scheduler.cancelTask(pid);
                pids.remove(worldName);
                suspendedTasks.put(worldName, threads.get(worldName).getRemainingDelay());
                threads.remove(worldName);
            }
        }
    }

    private class RegenThread implements Runnable {
        private final String worldName;
        private final long toRun;

        private RegenThread(String worldName, long toRun) {
            this.worldName = worldName;
            this.toRun = toRun;
        }

        @Override
        public void run() {
            if (!pids.containsKey(worldName)) return;
            World world = getServer().getWorld(worldName);
            if (world != null) regen(world);
            reg.remove(worldName);
            pids.remove(worldName);
            threads.remove(this);
        }

        long getRemainingDelay() {
            World world = getServer().getWorld(worldName);
            if (world == null) return -1;
            return toRun - world.getFullTime();
        }
    }

    private class ForceThread implements Runnable {
        @Override
        public void run() {
            if (forceReset.isEmpty()) return;
            long now = System.currentTimeMillis() * 1000;
            EndResetWorld vw;
            Server s = getServer();
            for (Entry<String, EndResetWorld> e : forceReset.entrySet()) {
                vw = e.getValue();
                if (vw.lastReset + vw.hours >= now) {
                    regen(s.getWorld(e.getKey()));
                    vw.lastReset = now;
                    save = true;
                }
            }
        }
    }

    private class SaveThread implements Runnable {
        @Override
        public void run() {
            if (!save) return;
            save = false;
            while (!saveLock.compareAndSet(false, true))
                continue;
            try {
                File file = new File(getDataFolder(), "EndReset.sav");
                if (!file.exists()) file.createNewFile();
                ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));

                out.writeInt(6);
                out.writeObject(resetchunks);
                out.writeObject(cvs);
                out.writeObject(reg);
                out.writeLong(it);
                out.writeObject(dontHandle);
                out.writeObject(forceReset);
                out.writeObject(dragonAmount);
                out.writeObject(suspendedTasks);

                getServer().getScheduler().scheduleAsyncDelayedTask(EndReset.this, new AsyncSaveThread(out));
            } catch (Exception ex) {
                saveLock.set(false);
                getServer().getLogger().info("[" + getName() + "] can't write savefile!");
                ex.printStackTrace();
            }
        }
    }

    private class AsyncSaveThread implements Runnable {
        private final ObjectOutputStream out;

        private AsyncSaveThread(ObjectOutputStream out) {
            this.out = out;
        }

        @Override
        public void run() {
            try {
                out.flush();
                out.close();
            } catch (Exception ex) {
                getServer().getLogger().info("[" + getName() + "] can't write savefile!");
                ex.printStackTrace();
            }
            saveLock.set(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof EnderDragon)) return;
        World world = entity.getWorld();
        if (world.getEnvironment() != Environment.THE_END) return;
        String worldName = world.getName();
        if (dontHandle.contains(worldName)) return;
        reg.add(worldName);
        save = true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != Environment.THE_END) return;
        World world = event.getWorld();
        String worldName = world.getName();
        HashMap<String, Long> worldMap;
        if (resetchunks.containsKey(worldName))
            worldMap = resetchunks.get(worldName);
        else {
            worldMap = new HashMap<String, Long>();
            resetchunks.put(worldName, worldMap);
        }

        Chunk chunk = event.getChunk();
        int x = chunk.getX();
        int z = chunk.getZ();
        String hash = x + "/" + z;
        long cv = cvs.get(worldName);

        if (worldMap.containsKey(hash)) {
            if (worldMap.get(hash) != cv) {
                for (Entity e : chunk.getEntities())
                    e.remove();
                world.regenerateChunk(x, z);
                worldMap.put(hash, cv);
                save = true;
            }
        } else
            worldMap.put(hash, cv);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(final WorldLoadEvent event) {
        World world = event.getWorld();
        if (world.getEnvironment() != Environment.THE_END) return;
        String worldName = world.getName();
        if (!cvs.containsKey(worldName)) {
            cvs.put(worldName, Long.MIN_VALUE);
            save = true;
        }
    }
}

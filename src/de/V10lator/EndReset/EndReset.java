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
        final Server s = getServer();
        final Logger log = getLogger();
        final BukkitScheduler bs = s.getScheduler();
        try {
            File f = new File(getDataFolder(), "EndReset.sav");
            boolean nf;
            if (!f.exists()) {
                // TODO: Remove (holds compat for < 1.4)
                final File f2 = new File("plugins/EndReset.sav");
                if (f2.exists()) {
                    getDataFolder().mkdirs();
                    f2.renameTo(f);
                    f = f2;
                    nf = false;
                } else {
                    nf = true;
                }
            } else {
                nf = false;
            }
            if (nf) {
                getDataFolder().mkdir();
            } else {
                final ObjectInputStream in = new ObjectInputStream(new FileInputStream(f));
                // TODO: Rewrite (no more Object[] reading - holds compat for <
                // 1.5)
                int sfv;
                Object[] sa = null;
                try {
                    final Object o = in.readObject();
                    if (o == null || !(o instanceof Object[])) {
                        log.info("ERROR: セーブファイルを読み込めません！");
                        s.getPluginManager().disablePlugin(this);
                        in.close();
                        return;
                    }
                    sa = (Object[]) o;
                    sfv = (Integer) sa[0];
                } catch (final OptionalDataException e) {
                    sfv = in.readInt();
                }

                if (sfv < 6) {
                    HashMap<String, Long> tmpMap;
                    boolean save = false;
                    if (sfv < 4) {
                        for (final EndResetChunk vc : (ArrayList<EndResetChunk>) sa[1]) {
                            if (resetchunks.containsKey(vc.world)) {
                                tmpMap = resetchunks.get(vc.world);
                            } else {
                                tmpMap = new HashMap<String, Long>();
                                resetchunks.put(vc.world, tmpMap);
                            }
                            tmpMap.put(vc.x + "/" + vc.z, vc.v);
                        }
                        save = true;
                    } else {
                        for (final Entry<String, HashMap<String, Long>> e : ((HashMap<String, HashMap<String, Long>>) sa[1]).entrySet()) {
                            resetchunks.put(e.getKey(), e.getValue());
                        }
                    }
                    for (final Entry<String, Long> e : ((HashMap<String, Long>) sa[2]).entrySet()) {
                        cvs.put(e.getKey(), e.getValue());
                    }
                    int i;
                    if (sfv < 2) {
                        i = 4;
                    } else {
                        i = 3;
                    }
                    for (final String regen : (HashSet<String>) sa[i]) {
                        reg.add(regen);
                    }
                    it = (Long) sa[i + 1];
                    if (sfv > 2) {
                        for (final String dh : (HashSet<String>) sa[5]) {
                            dontHandle.add(dh);
                        }
                        for (final Entry<String, EndResetWorld> e : ((HashMap<String, EndResetWorld>) sa[6]).entrySet()) {
                            forceReset.put(e.getKey(), e.getValue());
                        }
                        for (final Entry<String, Short> e : ((HashMap<String, Short>) sa[7]).entrySet()) {
                            dragonAmount.put(e.getKey(), e.getValue());
                        }
                    }
                    this.save = save;
                } else {
                    RegenThread rt;
                    World wo;
                    long tr;
                    for (final Entry<String, HashMap<String, Long>> e : ((HashMap<String, HashMap<String, Long>>) in.readObject()).entrySet()) {
                        resetchunks.put(e.getKey(), e.getValue());
                    }
                    for (final Entry<String, Long> e : ((HashMap<String, Long>) in.readObject()).entrySet()) {
                        cvs.put(e.getKey(), e.getValue());
                    }
                    for (final String regen : (HashSet<String>) in.readObject()) {
                        reg.add(regen);
                    }
                    it = in.readLong();
                    for (final String dh : (HashSet<String>) in.readObject()) {
                        dontHandle.add(dh);
                    }
                    for (final Entry<String, EndResetWorld> e : ((HashMap<String, EndResetWorld>) in.readObject()).entrySet()) {
                        forceReset.put(e.getKey(), e.getValue());
                    }
                    for (final Entry<String, Short> e : ((HashMap<String, Short>) in.readObject()).entrySet()) {
                        dragonAmount.put(e.getKey(), e.getValue());
                    }
                    for (final Entry<String, Long> e : ((HashMap<String, Long>) in.readObject()).entrySet()) {
                        final String w = e.getKey();
                        reg.add(w);
                        wo = s.getWorld(w);
                        if (wo != null) {
                            tr = e.getValue();
                            rt = new RegenThread(w, wo.getFullTime() + tr);
                            bs.scheduleSyncDelayedTask(this, rt, tr);
                        }
                    }
                }

                in.close();
            }
        } catch (final Exception e) {
            log.info("セーブファイルに書き込めません！");
            e.printStackTrace();
            s.getPluginManager().disablePlugin(this);
            return;
        }

        saveConfig();

        for (final World w : s.getWorlds()) {
            if (w.getEnvironment() != Environment.THE_END) {
                continue;
            }
            onWorldLoad(new WorldLoadEvent(w));
        }

        bs.scheduleSyncRepeatingTask(this, new SaveThread(), 36000L, 36000L);
        bs.scheduleSyncRepeatingTask(this, this, 1L, 1L);
        bs.scheduleSyncRepeatingTask(this, new ForceThread(), 20L, 72000L);

        final PluginManager pm = s.getPluginManager();
        pm.registerEvents(this, this);
        log.info("v" + getDescription().getVersion() + " enabled!");
    }

    @Override
    public void onDisable() {
        final Server s = getServer();
        s.getScheduler().cancelTasks(this);
        if (save) {
            (new SaveThread()).run();
        }
        s.getLogger().info("[" + getName() + "] disabled!");
    }

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String label, final String[] args) {
        if (!sender.hasPermission("endreset.config")) return true;

        if (args.length < 1) {
            if (!(sender instanceof Player)) return true;

            final World world = ((Player) sender).getWorld();
            if (world.getEnvironment() != Environment.THE_END) {
                sender.sendMessage(ChatColor.RED + "ここはエンドワールドではありません！");
                return true;
            }
            final String wn = world.getName();
            Actions.broadcastMessage("&c[SakuraServer] '&6" + wn + "&d'をリセットしています.. (" + sender.getName() + ")");

            for (final Player p : world.getPlayers()) {
                p.teleport(getServer().getWorlds().get(0).getSpawnLocation());
                Actions.message(p, "&c[SakuraServer] &dこのワールドはリセットされます！");
            }

            final long toRun = 1L;
            final RegenThread t = new RegenThread(wn, world.getFullTime() + toRun);
            pids.put(wn, getServer().getScheduler().scheduleSyncDelayedTask(this, t, toRun));
            threads.put(wn, t);
            return true;
        }

        try {
            it = Integer.parseInt(args[0]);
            sender.sendMessage("New inactive time: " + it + " minutes");
            it = it * 20 * 60;
        } catch (final NumberFormatException e) {
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
                    } catch (final NumberFormatException ex) {
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
                } catch (final NumberFormatException ex) {
                    sender.sendMessage("Invalid amount: " + args[2]);
                    return true;
                }
                if (a == 1) {
                    dragonAmount.remove(args[1]);
                } else {
                    dragonAmount.put(args[1], a);
                }
                sender.sendMessage(ChatColor.BLUE + "New dragon amount for world " + args[1] + ": " + a);
            } else if (args[0].equalsIgnoreCase("list")) {
                final List<World> wl = getServer().getWorlds();
                if (wl.isEmpty()) {
                    sender.sendMessage(ChatColor.RED + "No worlds found!");
                    return true;
                }
                final StringBuilder sb = new StringBuilder();
                World world;
                boolean first = true;
                for (int i = 1; i < wl.size(); i++) {
                    world = wl.get(i);
                    if (world != null && world.getEnvironment() == Environment.THE_END) {
                        if (!first) {
                            sb.append(' ');
                        } else {
                            first = false;
                        }
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

    private void regen(final World world) {
        final String wn = world.getName();
        long cv = cvs.get(wn) + 1;
        if (cv == Long.MAX_VALUE) {
            cv = Long.MIN_VALUE;
        }
        cvs.put(wn, cv);
        for (final Chunk c : world.getLoadedChunks()) {
            onChunkLoad(new ChunkLoadEvent(c, false));
        }

        short a;
        if (dragonAmount.containsKey(wn)) {
            a = dragonAmount.get(wn);
        } else {
            a = 1;
        }
        if (a > 1) {
            a--;
            final Location loc = world.getSpawnLocation();
            loc.setY(world.getMaxHeight() - 1);
            for (short i = 0; i < a; i++) {
                world.spawnEntity(loc, EntityType.ENDER_DRAGON);
            }
        }
        save = true;
        Actions.broadcastMessage("&c[SakuraServer] &dエンドワールド'&6" + wn + "&d'はリセットされました！");
    }

    @Override
    public void run() {
        int pc;
        int pid;
        final BukkitScheduler s = getServer().getScheduler();
        for (final World w : getServer().getWorlds()) {
            if (w.getEnvironment() != Environment.THE_END) {
                continue;
            }
            final String wn = w.getName();
            if (!reg.contains(wn)) {
                continue;
            }
            pc = w.getPlayers().size();
            if (pc < 1 && !pids.containsKey(wn)) {
                long tr;
                if (!suspendedTasks.containsKey(wn)) {
                    tr = it;
                } else {
                    tr = suspendedTasks.get(wn);
                    suspendedTasks.remove(wn);
                }
                final RegenThread t = new RegenThread(wn, w.getFullTime() + tr);
                pids.put(wn, s.scheduleSyncDelayedTask(EndReset.this, t, tr));
                threads.put(wn, t);
            } else if (pc > 0 && pids.containsKey(wn)) {
                pid = pids.get(wn);
                s.cancelTask(pid);
                pids.remove(wn);
                suspendedTasks.put(wn, threads.get(wn).getRemainingDelay());
                threads.remove(wn);
            }
        }
    }

    private class RegenThread implements Runnable {
        private final String wn;
        private final long toRun;

        private RegenThread(final String wn, final long toRun) {
            this.wn = wn;
            this.toRun = toRun;
        }

        @Override
        public void run() {
            if (!pids.containsKey(wn)) return;
            final World w = getServer().getWorld(wn);
            if (w != null) {
                regen(w);
            }
            reg.remove(wn);
            pids.remove(wn);
            threads.remove(this);
        }

        long getRemainingDelay() {
            final World w = getServer().getWorld(wn);
            if (w == null) return -1;
            return toRun - w.getFullTime();
        }
    }

    private class ForceThread implements Runnable {
        @Override
        public void run() {
            if (forceReset.isEmpty()) return;
            final long now = System.currentTimeMillis() * 1000;
            EndResetWorld vw;
            final Server s = getServer();
            for (final Entry<String, EndResetWorld> e : forceReset.entrySet()) {
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
            while (!saveLock.compareAndSet(false, true)) {
                continue;
            }
            try {
                final File f = new File(getDataFolder(), "EndReset.sav");
                if (!f.exists()) {
                    f.createNewFile();
                }
                final ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(f));

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
            } catch (final Exception e) {
                saveLock.set(false);
                getServer().getLogger().info("[" + getName() + "] can't write savefile!");
                e.printStackTrace();
            }
        }
    }

    private class AsyncSaveThread implements Runnable {
        private final ObjectOutputStream out;

        private AsyncSaveThread(final ObjectOutputStream out) {
            this.out = out;
        }

        @Override
        public void run() {
            try {
                out.flush();
                out.close();
            } catch (final Exception e) {
                getServer().getLogger().info("[" + getName() + "] can't write savefile!");
                e.printStackTrace();
            }
            saveLock.set(false);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(final EntityDeathEvent event) {
        final Entity e = event.getEntity();
        if (!(e instanceof EnderDragon)) return;
        final World w = e.getWorld();
        if (w.getEnvironment() != Environment.THE_END) return;
        final String wn = w.getName();
        if (dontHandle.contains(wn)) return;
        reg.add(wn);
        save = true;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChunkLoad(final ChunkLoadEvent event) {
        if (event.getWorld().getEnvironment() != Environment.THE_END) return;
        final World world = event.getWorld();
        final String wn = world.getName();
        HashMap<String, Long> worldMap;
        if (resetchunks.containsKey(wn)) {
            worldMap = resetchunks.get(wn);
        } else {
            worldMap = new HashMap<String, Long>();
            resetchunks.put(wn, worldMap);
        }

        final Chunk chunk = event.getChunk();
        final int x = chunk.getX();
        final int z = chunk.getZ();
        final String hash = x + "/" + z;
        final long cv = cvs.get(wn);

        if (worldMap.containsKey(hash)) {
            if (worldMap.get(hash) != cv) {
                for (final Entity e : chunk.getEntities()) {
                    e.remove();
                }
                world.regenerateChunk(x, z);
                worldMap.put(hash, cv);
                save = true;
            }
        } else {
            worldMap.put(hash, cv);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onWorldLoad(final WorldLoadEvent event) {
        final World w = event.getWorld();
        if (w.getEnvironment() != Environment.THE_END) return;
        final String wn = w.getName();
        if (!cvs.containsKey(wn)) {
            cvs.put(wn, Long.MIN_VALUE);
            save = true;
        }
    }
}

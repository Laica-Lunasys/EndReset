/**
 * EndReset - Package: de.V10lator.EndReset Created: 2012/10/13 23:25:25
 */
package de.V10lator.EndReset;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/**
 * Actions (Actions.java)
 * 
 * @author syam(syamn)
 */
public class Actions {
    private final EndReset plugin;

    public Actions(final EndReset plugin) {
        this.plugin = plugin;
    }

    /****************************************/
    // メッセージ送信系関数
    /****************************************/
    /**
     * メッセージをユニキャスト
     * 
     * @param message
     *            メッセージ
     */
    public static void message(final CommandSender sender, final String message) {
        if (sender != null && message != null) {
            sender.sendMessage(message.replaceAll("&([0-9a-fk-or])", "\u00A7$1"));
        }
    }

    public static void message(final Player player, final String message) {
        if (player != null && message != null) {
            player.sendMessage(message.replaceAll("&([0-9a-fk-or])", "\u00A7$1"));
        }
    }

    /**
     * メッセージをブロードキャスト
     * 
     * @param message
     *            メッセージ
     */
    public static void broadcastMessage(String message) {
        if (message != null) {
            message = message.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
            // debug(message);//debug
            Bukkit.broadcastMessage(message);
        }
    }

    /**
     * メッセージをワールドキャスト
     * 
     * @param world
     * @param message
     */
    public static void worldcastMessage(final World world, String message) {
        if (world != null && message != null) {
            message = message.replaceAll("&([0-9a-fk-or])", "\u00A7$1");
            for (final Player player : world.getPlayers()) {
                player.sendMessage(message);
            }
            System.out.println("[Worldcast][" + world.getName() + "]: " + message);
        }
    }

    /**
     * メッセージをパーミッションキャスト(指定した権限ユーザにのみ送信)
     * 
     * @param permission
     *            受信するための権限ノード
     * @param message
     *            メッセージ
     */
    public static void permcastMessage(final String permission, final String message) {
        // OK
        int i = 0;
        for (final Player player : Bukkit.getServer().getOnlinePlayers()) {
            if (player.hasPermission(permission)) {
                Actions.message(player, message);
                i++;
            }
        }

        System.out.println("Received " + i + "players: " + message);
    }

    /****************************************/
    // ユーティリティ
    /****************************************/
    /**
     * 文字配列をまとめる
     * 
     * @param s
     *            つなげるString配列
     * @param glue
     *            区切り文字 通常は半角スペース
     * @return
     */
    public static String combine(final String[] s, final String glue) {
        final int k = s.length;
        if (k == 0) return null;
        final StringBuilder out = new StringBuilder();
        out.append(s[0]);
        for (int x = 1; x < k; x++) {
            out.append(glue).append(s[x]);
        }
        return out.toString();
    }

    /**
     * コマンドをコンソールから実行する
     * 
     * @param command
     */
    public static void executeCommandOnConsole(final String command) {
        Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    /**
     * 文字列の中に全角文字が含まれているか判定
     * 
     * @param s
     *            判定する文字列
     * @return 1文字でも全角文字が含まれていればtrue 含まれていなければfalse
     * @throws UnsupportedEncodingException
     */
    public static boolean containsZen(final String s) throws UnsupportedEncodingException {
        for (int i = 0; i < s.length(); i++) {
            final String s1 = s.substring(i, i + 1);
            if (URLEncoder.encode(s1, "MS932").length() >= 4) return true;
        }
        return false;
    }

    /**
     * 現在の日時を yyyy-MM-dd HH:mm:ss 形式の文字列で返す
     * 
     * @return
     */
    public static String getDatetime() {

        final Date date = new Date();
        final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return df.format(date);
    }

    /**
     * 座標データを ワールド名:x, y, z の形式の文字列にして返す
     * 
     * @param loc
     * @return
     */
    public static String getLocationString(final Location loc) {
        return loc.getWorld().getName() + ":" + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    public static String getBlockLocationString(final Location loc) {
        return loc.getWorld().getName() + ":" + loc.getBlockX() + "," + loc.getBlockY() + "," + loc.getBlockZ();
    }

    /**
     * デバッグ用 syamnがオンラインならメッセージを送る
     * 
     * @param msg
     */
    public static void debug(final String msg) {
        final OfflinePlayer syamn = Bukkit.getServer().getOfflinePlayer("syamn");
        if (syamn.isOnline()) {
            Actions.message((Player) syamn, msg);
        }
    }

    /**
     * 文字列の&(char)をカラーコードに変換して返す
     * 
     * @param string
     *            文字列
     * @return 変換後の文字列
     */
    public static String coloring(final String string) {
        if (string == null) return null;
        return string.replaceAll("&([0-9a-fA-Fk-oK-Or])", "\u00A7$1");
    }

    /****************************************/
    /* その他 */
    /****************************************/
    // プレイヤーがオンラインかチェックしてテレポートさせる
    public static void tpPlayer(final Player player, final Location loc) {
        if (player == null || loc == null || !player.isOnline()) return;
        player.teleport(loc);
    }

    // プレイヤーのインベントリをその場にドロップさせる
    public static void dropInventoryItems(final Player player) {
        if (player == null) return;

        final PlayerInventory inv = player.getInventory();
        final Location loc = player.getLocation();

        // インベントリアイテム
        for (final ItemStack i : inv.getContents()) {
            if (i != null && i.getType() != Material.AIR) {
                inv.remove(i);
                player.getWorld().dropItemNaturally(loc, i);
            }
        }

        // 防具アイテム
        for (final ItemStack i : inv.getArmorContents()) {
            if (i != null && i.getType() != Material.AIR) {
                inv.remove(i);
                player.getWorld().dropItemNaturally(loc, i);
            }
        }
    }

    /**
     * 座標からSignまたはnullに変換して返す
     * 
     * @param loc
     * @return
     */
    public static Sign getSign(final Location loc) {
        if (loc == null) return null;
        final Block block = loc.getBlock();
        final int id = block.getTypeId();
        if (id == Material.SIGN_POST.getId() || id == Material.WALL_SIGN.getId())
            return (Sign) block.getState();
        else
            return null;
    }
}

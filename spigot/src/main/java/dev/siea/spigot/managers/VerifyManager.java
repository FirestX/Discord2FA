package dev.siea.spigot.managers;

import dev.siea.common.base.BaseVerifyManager;
import dev.siea.common.storage.models.Account;
import dev.siea.spigot.Discord2FA;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class VerifyManager implements Listener, BaseVerifyManager {
    private final List<Player> verifyingPlayers = new CopyOnWriteArrayList<>();
    private final List<Player> forcedPlayers = new CopyOnWriteArrayList<>();
    private List<String> allowedCommands;
    private final Map<Player, Integer> titleCooldown = new ConcurrentHashMap<>();
    private boolean forceLink;

    public VerifyManager(){
        Bukkit.getScheduler().scheduleSyncRepeatingTask(Discord2FA.getPlugin(), () -> {
            titleCooldown.entrySet().removeIf(entry -> {
                int newValue = entry.getValue() - 1;
                if (newValue <= 0) return true;
                entry.setValue(newValue);
                return false;
            });
        }, 0, 20);
    }

    public void loadConfig(){
        allowedCommands = Discord2FA.getCommon().getConfig().getConfig().getStringList("allowedCommands");
        forceLink = Discord2FA.getCommon().getConfig().getConfig().getBoolean("force-link");
    }

    public void linked(Player player){
        forcedPlayers.remove(player);
        Discord2FA.getStorageManager().updateIPAddress(player.getUniqueId().toString(), Objects.requireNonNull(player.getAddress()).getAddress().toString());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent e) {
        Player player = e.getPlayer();
        String uuid = player.getUniqueId().toString();
        String ip = Objects.requireNonNull(player.getAddress()).getAddress().toString();
        String hostAddress = Objects.requireNonNull(player.getAddress()).getAddress().getHostAddress();

        Bukkit.getScheduler().runTaskAsynchronously(Discord2FA.getPlugin(), () -> {
            Account account = Discord2FA.getStorageManager().findAccountByUUID(uuid);
            boolean remembered = Discord2FA.getStorageManager().isRemembered(uuid, ip);

            Bukkit.getScheduler().runTask(Discord2FA.getPlugin(), () -> {
                if (account != null) {
                    if (remembered) return;
                    verifyingPlayers.add(player);
                    sendTitle(player);
                    Discord2FA.getDiscordUtils().sendVerify(account, hostAddress);
                } else if (forceLink) {
                    forcedPlayers.add(player);
                    player.sendMessage(Discord2FA.getMessages().get("forceLink"));
                }
            });
        });
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent e){
        verifyingPlayers.remove(e.getPlayer());
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerMove(PlayerMoveEvent e){
        Player player = e.getPlayer();
        if (verifyingPlayers.contains(player)) {
            e.setCancelled(true);
            sendTitle(player);
        }
        else if (forcedPlayers.contains(player)){
            e.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onInventoryOpen(InventoryOpenEvent e){
        Player player = (Player) e.getPlayer();
        if (verifyingPlayers.contains(player)) {
            e.setCancelled(true);
            sendTitle(player);
        }
        else if (forcedPlayers.contains(player)){
            e.setCancelled(true);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onCommandEntered(PlayerCommandPreprocessEvent e){
        if (allowedCommands.contains(e.getMessage().split(" ")[0])) return;
        Player player = e.getPlayer();
        if (verifyingPlayers.contains(player)) {
            e.setCancelled(true);
            sendTitle(player);
        }
        else if (forcedPlayers.contains(player)){
            e.setCancelled(true);
            sendTitle(player);
        }
    }

    @EventHandler (priority = EventPriority.HIGHEST)
    public void onPlayerDropItem(PlayerDropItemEvent e){
        Player player = e.getPlayer();
        if (verifyingPlayers.contains(player)) {
            e.setCancelled(true);
            sendTitle(player);
        }
        else if (forcedPlayers.contains(player)){
            e.setCancelled(true);
        }
    }

    @EventHandler  (priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent e){
        Player player = e.getPlayer();
        if (verifyingPlayers.contains(player)) {
            e.setCancelled(true);
            sendTitle(player);
        }
        else if (forcedPlayers.contains(player)){
            e.setCancelled(true);
        }
    }

    @Override
    public void verifying(String p, boolean allowed){
        Player player = Discord2FA.getPlugin().getServer().getPlayer(UUID.fromString(p));
        assert player != null;
       if (!verifyingPlayers.contains(player)) return;
       if(allowed){
           verifyingPlayers.remove(player);
           player.sendMessage(Discord2FA.getMessages().get("verifySuccess"));
           Discord2FA.getStorageManager().updateIPAddress(player.getUniqueId().toString(), Objects.requireNonNull(player.getAddress()).getAddress().toString());
        }else{
           verifyingPlayers.remove(player);
           kickPlayerAsync(player, Discord2FA.getMessages().get("verifyDenied"));
       }
    }

    private void kickPlayerAsync(Player player, String kickMessage) {
        Bukkit.getScheduler().runTask(Discord2FA.getPlugin(), () -> player.kickPlayer(kickMessage));
    }

    public boolean isVerifying(Player player){
        return verifyingPlayers.contains(player);
    }

    private void sendTitle(Player p){
        if (titleCooldown.containsKey(p)) return;
        p.sendTitle(Discord2FA.getMessages().get("verifyTitle"),"", 10, 70, 20);
        titleCooldown.put(p, 5);
    }
}

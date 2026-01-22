package dev.veyno.aiMcperformance.monitor;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class MonitorListener implements Listener {
    private final BossBarMonitor bossBarMonitor;

    public MonitorListener(BossBarMonitor bossBarMonitor) {
        this.bossBarMonitor = bossBarMonitor;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        bossBarMonitor.disableAll(event.getPlayer());
    }
}

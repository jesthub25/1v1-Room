package org.jestron.arena1v1;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jestron.arena1v1.command.ArenaCommand;
import org.jestron.arena1v1.config.Settings;
import org.jestron.arena1v1.listener.ArenaListener;
import org.jestron.arena1v1.listener.CommandBlockListener;
import org.jestron.arena1v1.manager.ArenaManager;
import org.jestron.arena1v1.manager.MatchManager;
import org.jestron.arena1v1.manager.SelectionManager;

public final class Arena1v1Plugin extends JavaPlugin {

    private Settings settings;
    private ArenaManager arenaManager;
    private MatchManager matchManager;
    private SelectionManager selectionManager;

    @Override
    public void onEnable() {
        this.settings = new Settings(this);
        this.settings.load();

        this.arenaManager = new ArenaManager(this);
        this.arenaManager.load();

        this.selectionManager = new SelectionManager();
        this.matchManager = new MatchManager(this);

        PluginCommand command = getCommand("1v1");
        if (command == null) {
            getLogger().severe("plugin.yml icinde '1v1' komutu bulunamadi! Plugin kapatiliyor.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        ArenaCommand executor = new ArenaCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new ArenaListener(this), this);
        pm.registerEvents(new CommandBlockListener(this), this);

        // Dunyalar (Multiverse vb.) tamamen yuklendikten sonra, onceki bir cokme/kapanmadan
        // kalmis olabilecek kapi bloklarini temizle. Kapilar sadece mac sirasinda kapalidir.
        getServer().getScheduler().runTask(this, () -> {
            if (matchManager != null) {
                matchManager.openAllIdleDoors();
            }
        });

        getLogger().info("1v1Arena v" + getDescription().getVersion() + " aktif edildi! ("
                + arenaManager.getArenas().size() + " arena)");
    }

    @Override
    public void onDisable() {
        if (matchManager != null) {
            matchManager.shutdown();
        }
        getLogger().info("1v1Arena plugini devre disi birakildi!");
    }

    public Settings getSettings() {
        return settings;
    }

    public ArenaManager getArenaManager() {
        return arenaManager;
    }

    public MatchManager getMatchManager() {
        return matchManager;
    }

    public SelectionManager getSelectionManager() {
        return selectionManager;
    }
}

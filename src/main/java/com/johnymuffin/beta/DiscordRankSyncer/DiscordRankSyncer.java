package com.johnymuffin.beta.DiscordRankSyncer;

import com.johnymuffin.beta.discordauth.DiscordAuthentication;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.retromc.discordcore.v6.DiscordCorePlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordRankSyncer extends JavaPlugin {
    private Logger log;
    private PluginDescriptionFile pdf;
    private DiscordRankSyncer plugin;
    private DiscordCorePlugin discord;
    private DiscordAuthentication discordAuthCore;
    private DiscordRankSyncerDatastore discordRankSyncerDatastore;
    private RetroBridgeAccess retroBridgeAccess;

    @Override
    public void onEnable() {
        log = this.getServer().getLogger();
        pdf = this.getDescription();
        plugin = this;

        log.info("[" + pdf.getName() + "] Is loading, Version: " + pdf.getVersion() + " | Bukkit: " + Bukkit.getServer().getVersion());

        PluginManager pm = Bukkit.getServer().getPluginManager();
        if (!(pm.getPlugin("DiscordCore-6") instanceof DiscordCorePlugin) || !pm.getPlugin("DiscordCore-6").isEnabled()) {
            log.info("}---------------ERROR---------------{");
            log.info("DiscordRankSyncer Requires DiscordCore-6");
            log.info("}---------------ERROR---------------{");
            pm.disablePlugin(this);
            return;
        }

        if (pm.getPlugin("DiscordAuthentication") == null || !pm.getPlugin("DiscordAuthentication").isEnabled()) {
            log.info("}---------------ERROR---------------{");
            log.info("DiscordRankSyncer Requires DiscordAuthentication");
            log.info("}---------------ERROR---------------{");
            pm.disablePlugin(this);
            return;
        }

        discord = (DiscordCorePlugin) getServer().getPluginManager().getPlugin("DiscordCore-6");
        discordAuthCore = (DiscordAuthentication) Bukkit.getServer().getPluginManager().getPlugin("DiscordAuthentication");
        retroBridgeAccess = new RetroBridgeAccess();

        if (!retroBridgeAccess.isAvailable()) {
            log.info("}---------------ERROR---------------{");
            log.info("DiscordRankSyncer Requires RetroBridge");
            log.info("}---------------ERROR---------------{");
            pm.disablePlugin(this);
            return;
        }

        discordRankSyncerDatastore = new DiscordRankSyncerDatastore(plugin);

        final DiscordRankSyncerPlayerListener discordRankSyncerPlayerListener = new DiscordRankSyncerPlayerListener(plugin);
        getServer().getPluginManager().registerEvents(discordRankSyncerPlayerListener, this);
    }

    @Override
    public void onDisable() {
//        discordRankSyncerDatastore.saveConfig();
        log.info("[" + pdf.getName() + "] Has Been Disabled");
    }

    public void logger(Level level, String message) {
        log.log(level, "[" + pdf.getName() + "] " + message);
    }

    public void debugLogger(Level info, String message) {
        if (discordRankSyncerDatastore.getDebugMode().get()) {
            Bukkit.getServer().getLogger().log(info, "[" + pdf.getName() + "-DEBUG] " + message);
        }
    }

    public DiscordAuthentication getDiscordAuthCore() {
        return discordAuthCore;
    }

    public DiscordRankSyncerDatastore getDiscordRankSyncerDatastore() {
        return discordRankSyncerDatastore;
    }

    public DiscordCorePlugin getDiscord() {
        return discord;
    }

    public RetroBridgeAccess getRetroBridgeAccess() {
        return retroBridgeAccess;
    }
}

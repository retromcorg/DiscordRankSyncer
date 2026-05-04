package com.johnymuffin.beta.DiscordRankSyncer;

import com.johnymuffin.beta.discordauth.DiscordAuthentication;
import com.johnymuffin.discordcore.DiscordCore;
import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.logging.Level;
import java.util.logging.Logger;

public class DiscordRankSyncer extends JavaPlugin {
    private Logger log;
    private PluginDescriptionFile pdf;
    private DiscordRankSyncer plugin;
    public DiscordCore discord;
    private DiscordAuthentication discordAuthCore;
    private DiscordRankSyncerDatastore discordRankSyncerDatastore;

    @Override
    public void onEnable() {
        log = this.getServer().getLogger();
        pdf = this.getDescription();
        plugin = this;

        log.info("[" + pdf.getName() + "] Is loading, Version: " + pdf.getVersion() + " | Bukkit: " + Bukkit.getServer().getVersion());

        PluginManager pm = Bukkit.getServer().getPluginManager();
        if (pm.getPlugin("DiscordCore") == null || !pm.getPlugin("DiscordCore").isEnabled()) {
            log.info("}---------------ERROR---------------{");
            log.info("DiscordRankSyncer Requires Discord Core");
            log.info("Download it at: https://github.com/RhysB/Discord-Bot-Core");
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
        discord = (DiscordCore) getServer().getPluginManager().getPlugin("DiscordCore");
        discordAuthCore = (DiscordAuthentication) Bukkit.getServer().getPluginManager().getPlugin("DiscordAuthentication");

        discordRankSyncerDatastore = new DiscordRankSyncerDatastore(plugin);

        final DiscordRankSyncerPlayerListener discordRankSyncerPlayerListener = new DiscordRankSyncerPlayerListener(plugin);
        getServer().getPluginManager().registerEvents(discordRankSyncerPlayerListener, this);
        getServer().getPluginManager().registerEvent(Event.Type.CUSTOM_EVENT, discordRankSyncerPlayerListener, Event.Priority.Normal, this);
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

    public DiscordCore getDiscord() {
        return discord;
    }
}

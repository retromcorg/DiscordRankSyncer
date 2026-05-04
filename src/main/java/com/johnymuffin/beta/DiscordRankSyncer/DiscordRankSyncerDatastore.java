package com.johnymuffin.beta.DiscordRankSyncer;

import org.bukkit.util.config.Configuration;

import java.io.File;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;

public class DiscordRankSyncerDatastore {
    private DiscordRankSyncer plugin;
    private Configuration config;
    private HashMap<String, HashMap<String, String>> rankStore = new HashMap<String, HashMap<String, String>>();

    private AtomicBoolean debugMode = new AtomicBoolean(false);

    public DiscordRankSyncerDatastore(DiscordRankSyncer plugin) {
        this.plugin = plugin;
        config = new Configuration(new File(plugin.getDataFolder(), "config.yml"));
        config.load();

        // Migrate old "autorRankups" to "autoRankups" if present
        if (config.getProperty("autorRankups") != null) {
            rankStore = (HashMap<String, HashMap<String, String>>) config.getProperty("autorRankups");
            config.removeProperty("autorRankups");
            config.setProperty("autoRankups", rankStore);
            plugin.logger(Level.INFO, "Migrated old 'autorRankups' to 'autoRankups'");
        } else if (config.getProperty("autoRankups") == null) {
            config.setProperty("autoRankups", (Object) new HashMap());

            // Generate default rankup
            generateAutoRankup("default", "1234567890", "default", "1234567890");
            generateAutoRankup("vip", "1234567890", "vip", "1234567890");

        } else {
            rankStore = (HashMap<String, HashMap<String, String>>) config.getProperty("autoRankups");
        }

        // Debug mode
        if (config.getProperty("debugMode") == null) {
            config.setProperty("debugMode", false);
        }
        debugMode = new AtomicBoolean((Boolean) config.getProperty("debugMode"));

        saveConfig();
    }

    public HashMap<String, HashMap<String, String>> getRankups() {
        return rankStore;
    }


    public void generateAutoRankup(String rankupName, String guildID, String groupName, String roleID) {
        final HashMap<String, String> tmp = new HashMap<String, String>();
        tmp.put("groupName", groupName);
        tmp.put("guildID", guildID);
        tmp.put("roleID", roleID);


        rankStore.put(rankupName, tmp);

    }

    public AtomicBoolean getDebugMode() {
        return debugMode;
    }

    public void saveConfig() {
        config.setProperty("autoRankups", rankStore);
        config.save();
    }


}

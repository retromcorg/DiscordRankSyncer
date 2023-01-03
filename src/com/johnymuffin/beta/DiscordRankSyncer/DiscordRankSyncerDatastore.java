package com.johnymuffin.beta.DiscordRankSyncer;

import org.bukkit.util.config.Configuration;

import java.io.File;
import java.util.HashMap;

public class DiscordRankSyncerDatastore {
    private DiscordRankSyncer plugin;
    private Configuration config;
    private HashMap<String, HashMap<String, String>> rankStore = new HashMap<String, HashMap<String, String>>();

    public DiscordRankSyncerDatastore(DiscordRankSyncer plugin) {
        this.plugin = plugin;
        config = new Configuration(new File(plugin.getDataFolder(), "config.yml"));
        config.load();
        if (config.getProperty("autorRankups") == null) {
            config.setProperty("autorRankups", (Object) new HashMap());
        }
        rankStore = (HashMap<String, HashMap<String, String>>) config.getProperty("autorRankups");

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



    public void saveConfig() {
        config.setProperty("autorRankups", rankStore);
        config.save();
    }


}

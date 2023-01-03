package com.johnymuffin.beta.DiscordRankSyncer;

import com.johnymuffin.beta.discordauth.DiscordAuthentication;
import com.johnymuffin.discordcore.DiscordCore;
import com.johnymuffin.jperms.beta.JohnyPerms;
import com.johnymuffin.jperms.beta.JohnyPermsAPI;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import org.bukkit.Bukkit;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerListener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;

public class DiscordRankSyncerPlayerListener extends PlayerListener {
    private DiscordRankSyncer plugin;
    private JohnyPermsAPI johnyPermsAPI;
    private DiscordAuthentication discordAuthentication;
    private DiscordCore discordCore;
    private DiscordRankSyncerDatastore discordRankSyncerDatastore;

    public DiscordRankSyncerPlayerListener(DiscordRankSyncer plugin) {
        this.plugin = plugin;
        this.johnyPermsAPI = JohnyPerms.getJPermsAPI();
        this.discordAuthentication = plugin.getDiscordAuthCore();
        this.discordCore = plugin.getDiscord();
        this.discordRankSyncerDatastore = plugin.getDiscordRankSyncerDatastore();
    }

    @Override
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null) {
            return;
        }
        if (!event.getPlayer().isOnline()) {
            return;
        }
        //Is user linked to Discord
        final String playerUUID = event.getPlayer().getUniqueId().toString();
        if (!discordAuthentication.data.isUUIDAlreadyLinked(playerUUID)) {
            //If user isn't linked, cancel
            return;
        }
        //Rankups
        final HashMap<String, HashMap<String, String>> rankups = discordRankSyncerDatastore.getRankups();
        //Get Discord ID
        final String discordID = discordAuthentication.getData().getDiscordIDFromUUID(playerUUID);


        //Check if user is in guild
        User user = discordCore.getDiscordBot().getJda().getUserById(discordID);
        if (user == null) {
            plugin.logger(Level.WARNING, "User is null, they might not share a server with the bot");
            return;
        }


        //Load member data into cache
        for (String key : rankups.keySet()) {
            String guildID = rankups.get(key).get("guildID");
            Guild guild = discordCore.getDiscordBot().getJda().getGuildById(guildID);
            if(guild == null){
                plugin.logger(Level.WARNING, "Guild with ID: " + guildID + " is not found");
                continue;
            }

            if(!guild.isMember(user)) {
                //User isn't in guild
                plugin.logger(Level.WARNING, "User " + event.getPlayer().getName() + " is not in guild " + guild.getName() + " so cannot be ranked up.");
                continue;
            }
            discordCore.getDiscordBot().jda.getGuildById(guildID).retrieveMemberById(discordID).queue();
        }

        Bukkit.getServer().getScheduler().scheduleSyncDelayedTask(plugin, () -> {
            try {
                //Check if user is still online, if so test for ranks
                if (!event.getPlayer().isOnline()) {
                    return;
                }

                //Get JPerms groups
                final String[] userGroups = new String[1];
                userGroups[0] = johnyPermsAPI.getUser(event.getPlayer().getUniqueId()).getGroup().getName();
                final List<Guild> mutualGuilds = discordCore.getDiscordBot().getJda().getUserById(discordID).getMutualGuilds();
                final List<String> mutualGuildString = getMutualGuildsString(mutualGuilds);

                //Run rankup tests
                for (String key : rankups.keySet()) {
                    String groupName = rankups.get(key).get("groupName");
                    String guildID = rankups.get(key).get("guildID");
                    String roleID = rankups.get(key).get("roleID");
                    //Check if user has correct Group
                    if (!isUserInGroup(groupName, userGroups)) {
                        continue;
                    }
                    //Check if user and bot still share a Guild
                    if (!mutualGuildString.contains(guildID)) {
                        continue;
                    }
                    Member member = discordCore.Discord().jda.getGuildById(guildID).getMemberById(discordID);
                    //Check if user already has role
                    for (Role r : member.getRoles()) {
                        if (r.getId().equalsIgnoreCase(roleID)) {
                            continue;
                        }
                    }
                    //Check role exists
                    Role role = discordCore.getDiscordBot().jda.getRoleById(roleID);
                    if (role == null) {
                        System.out.println("Role: " + roleID + " doesn't exist");
                        continue;
                    }
                    //Give user role
                    discordCore.Discord().jda.getGuildById(guildID).addRoleToMember(member, role).queue();
                    System.out.println("Issued Role " + role.getName() + " to user " + member.getUser().getName() + " who is know as " + event.getPlayer().getName() + " in-game");

                }
            } catch (Exception exception) {
                Bukkit.getServer().getLogger().log(Level.WARNING, "Failed to sync ranks for " + event.getPlayer().getName(), exception);
            }
        }, 100L);


    }

    private Boolean isUserInGroup(String groupName, String[] userGroups) {
        if (groupName.equalsIgnoreCase("*")) {
            return true;
        }
        for (String s : userGroups) {
            if (groupName.equalsIgnoreCase(s)) {
                return true;
            }
        }
        return false;
    }

    private List<String> getMutualGuildsString(List<Guild> guildList) {
        ArrayList<String> guildListString = new ArrayList<String>();
        for (Guild guild : guildList) {
            guildListString.add(guild.getId());
        }
        return guildListString;
    }
}

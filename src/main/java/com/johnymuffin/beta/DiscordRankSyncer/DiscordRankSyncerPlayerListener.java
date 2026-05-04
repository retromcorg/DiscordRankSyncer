package com.johnymuffin.beta.DiscordRankSyncer;

import com.johnymuffin.beta.discordauth.DiscordAuthentication;
import com.johnymuffin.beta.discordauth.events.DiscordAuthenticationLinkEvent;
import com.johnymuffin.beta.discordauth.events.DiscordAuthenticationUnlinkEvent;
import com.johnymuffin.discordcore.DiscordCore;
import com.johnymuffin.jperms.beta.JohnyPerms;
import com.johnymuffin.jperms.beta.JohnyPermsAPI;
import com.projectposeidon.api.PoseidonUUID;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.event.CustomEventListener;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class DiscordRankSyncerPlayerListener extends CustomEventListener {
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

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer().isOnline() == false) {
            return;
        }

        //Is user linked to Discord
        final String playerUUID = event.getPlayer().getUniqueId().toString();
        if (!discordAuthentication.data.isUUIDAlreadyLinked(playerUUID)) {
            //If user isn't linked, cancel
            plugin.debugLogger(Level.INFO, "User " + event.getPlayer().getName() + " isn't linked to Discord. Cancelling Rank Sync.");
            return;
        }

        plugin.debugLogger(Level.INFO, "User " + event.getPlayer().getName() + " is linked to Discord. Syncing Ranks.");

        //Sync Discord Profile when user joins
        syncDiscordProfile(event.getPlayer().getUniqueId(), Long.parseLong(discordAuthentication.getData().getDiscordIDFromUUID(playerUUID)));
    }

    @Override
    public void onCustomEvent(Event event) {
        if (event == null) {
            return;
        }

        if (event.getEventName().equalsIgnoreCase("DiscordAuthenticationLinkEvent") && event instanceof DiscordAuthenticationLinkEvent) {
            onDiscordAuthenticationLink((DiscordAuthenticationLinkEvent) event);
        } else if (event.getEventName().equalsIgnoreCase("DiscordAuthenticationUnlinkEvent") && event instanceof DiscordAuthenticationUnlinkEvent) {
            onDiscordAuthenticationUnlink((DiscordAuthenticationUnlinkEvent) event);
        }
    }

    private void onDiscordAuthenticationLink(DiscordAuthenticationLinkEvent event) {
        plugin.debugLogger(Level.INFO, "DiscordAuthenticationLinkEvent detected. Syncing Discord Profile for " + event.getMinecraftUUID() + " and " + event.getDiscordID());
        syncDiscordProfile(event.getMinecraftUUID(), event.getDiscordID());
    }

    private void onDiscordAuthenticationUnlink(DiscordAuthenticationUnlinkEvent event) {
        plugin.debugLogger(Level.INFO, "DiscordAuthenticationUnlinkEvent detected. Removing Discord Ranks for " + event.getMinecraftUUID() + " and " + event.getDiscordID());
        removeDiscordRanks(event.getMinecraftUUID(), event.getDiscordID());
    }


    private void syncDiscordProfile(UUID minecraftUUID, long discordID) {
        // Load member data into cache

        plugin.debugLogger(Level.INFO, "Syncing Discord Profile for " + minecraftUUID + " and " + discordID);

        this.discordCore.getDiscordBot().getJda().retrieveUserById(discordID).queue();

        final HashMap<String, HashMap<String, String>> rankups = discordRankSyncerDatastore.getRankups();

        final String[] userGroups = new String[1];
        userGroups[0] = johnyPermsAPI.getUser(minecraftUUID).getGroup().getName();

        String username = PoseidonUUID.getPlayerUsernameFromUUID(minecraftUUID) == null ? "Unknown" : PoseidonUUID.getPlayerUsernameFromUUID(minecraftUUID);


        // Get Discord User (Moving to an asynchronous task)
        plugin.getDiscord().getDiscordBot().getJda().retrieveUserById(discordID).queue(user -> {
            plugin.debugLogger(Level.INFO, "Retrieved Discord User " + user.getName() + " for " + username);
            // Run rankup tests
            rankupLoop:
            for (String key : rankups.keySet()) {
                String groupName = rankups.get(key).get("groupName");
                String guildID = rankups.get(key).get("guildID");
                String roleID = rankups.get(key).get("roleID");

                plugin.debugLogger(Level.INFO, "Checking Rankup for " + username + " in " + guildID + " for " + groupName);

                // Check if user has correct Group
                if (!isUserInGroup(groupName, userGroups)) {
                    plugin.debugLogger(Level.INFO, "User " + username + " doesn't have group " + groupName + ". Skipping rank issue.");
                    continue;
                }

                // Check if guild exists
                if (plugin.getDiscord().getDiscordBot().getJda().getGuildById(guildID) == null) {
                    plugin.debugLogger(Level.WARNING, "Guild " + guildID + " doesn't exist. Skipping rank issue.");
                    continue;
                }

                // Check if user is in guild
                Member member = plugin.getDiscord().getDiscordBot().getJda().getGuildById(guildID).getMember(user);

                if (member == null) {
                    plugin.debugLogger(Level.INFO, "User " + username + " isn't in guild " + guildID + ". Skipping rank issue.");
                    continue;
                }

                // Check if user already has role
                for (Role r : member.getRoles()) {
                    if (r.getId().equalsIgnoreCase(roleID)) {
                        plugin.debugLogger(Level.INFO, "User " + username + " already has role " + r.getName() + ". Skipping role issue.");
                        continue rankupLoop;
                    }
                }

                // Check role exists
                Role role = this.discordCore.getDiscordBot().getJda().getRoleById(roleID);
                if (role == null) {
                    this.plugin.logger(Level.WARNING, "Role: " + roleID + " doesn't exist. Failed to issue role to user " + username);
                    continue;
                }

                // Give user role
                this.discordCore.getDiscordBot().getJda().getGuildById(guildID).addRoleToMember(member, role).queue();
                this.plugin.logger(Level.INFO, "Issued Role " + role.getName() + " to user " + member.getUser().getName() + " who is know as " + username + " in-game");
            }


        });
    }

    private void removeDiscordRanks(UUID minecraftUUID, long discordID) {
        final String username = PoseidonUUID.getPlayerUsernameFromUUID(minecraftUUID) == null ? minecraftUUID.toString() : PoseidonUUID.getPlayerUsernameFromUUID(minecraftUUID);

        // Queue retrieval of the user by their Discord ID
        this.discordCore.getDiscordBot().getJda().retrieveUserById(discordID).queue(user -> {
            for (HashMap<String, String> rankup : discordRankSyncerDatastore.getRankups().values()) {
                final String guildID = rankup.get("guildID");
                final String roleID = rankup.get("roleID");

                Guild guild = this.discordCore.getDiscordBot().getJda().getGuildById(guildID);
                if (guild == null) {
                    this.plugin.logger(Level.WARNING, "Guild: " + guildID + " doesn't exist. Failed to remove role " + roleID + " from user " + username);
                    continue;
                }

                Role role = guild.getRoleById(roleID);
                if (role == null) {
                    this.plugin.logger(Level.WARNING, "Role: " + roleID + " doesn't exist in guild " + guild.getName() + ". Failed to remove role from user " + username);
                    continue;
                }

                guild.retrieveMember(user).queue(member -> {
                    if (!member.getRoles().contains(role)) {
                        plugin.debugLogger(Level.INFO, "User " + username + " does not have role " + role.getName() + " in guild " + guild.getName() + ". Skipping removal.");
                        return;
                    }

                    guild.removeRoleFromMember(member, role).queue(
                            success -> this.plugin.logger(Level.INFO, "Removed role " + role.getName() + " from user " + user.getName() + " who is known as " + username + " in-game"),
                            error -> this.plugin.logger(Level.WARNING, "Failed to remove role " + role.getName() + " from user " + user.getName() + " in guild " + guild.getName())
                    );
                }, error -> this.plugin.logger(Level.WARNING, "Failed to retrieve member " + user.getName() + " from guild " + guild.getName()));
            }
        }, error -> {
            this.plugin.logger(Level.SEVERE, "Failed to retrieve Discord user with ID " + discordID);
        });
    }



    private Boolean isUserInGroup(String groupName, String[] userGroups) {
        // Handle the case where groupName is "*", which means any group is accepted.
        if (groupName.equalsIgnoreCase("*")) {
            return true;
        }

        // Split the groupName by commas to handle multiple groups.
        String[] groups = groupName.split(",");

        // Iterate over each group in groups array.
        for (String group : groups) {
            // Trim whitespace and check if the group is in userGroups.
            for (String userGroup : userGroups) {
                if (group.trim().equalsIgnoreCase(userGroup)) {
                    return true;
                }
            }
        }

        // If no match is found, return false.
        return false;
    }

}

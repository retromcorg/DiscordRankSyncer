package com.johnymuffin.beta.DiscordRankSyncer;

import com.johnymuffin.beta.discordauth.DiscordAuthentication;
import com.johnymuffin.beta.discordauth.events.DiscordAuthenticationLinkEvent;
import com.johnymuffin.beta.discordauth.events.DiscordAuthenticationUnlinkEvent;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.retromc.discordcore.v6.DiscordCorePlugin;
import org.retromc.retrobridge.bridge.permission.PermissionBridge;

import java.util.HashMap;
import java.util.UUID;
import java.util.logging.Level;

public class DiscordRankSyncerPlayerListener implements Listener {
    private final DiscordRankSyncer plugin;
    private final DiscordAuthentication discordAuthentication;
    private final DiscordCorePlugin discordCore;
    private final DiscordRankSyncerDatastore discordRankSyncerDatastore;
    private final RetroBridgeAccess retroBridgeAccess;

    public DiscordRankSyncerPlayerListener(DiscordRankSyncer plugin) {
        this.plugin = plugin;
        this.discordAuthentication = plugin.getDiscordAuthCore();
        this.discordCore = plugin.getDiscord();
        this.discordRankSyncerDatastore = plugin.getDiscordRankSyncerDatastore();
        this.retroBridgeAccess = plugin.getRetroBridgeAccess();
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (event == null || event.getPlayer().isOnline() == false) {
            return;
        }

        //Is user linked to Discord
        final UUID playerUUID = event.getPlayer().getUniqueId();
        if (!discordAuthentication.getData().isUUIDAlreadyLinked(playerUUID)) {
            //If user isn't linked, cancel
            plugin.debugLogger(Level.INFO, "User " + event.getPlayer().getName() + " isn't linked to Discord. Cancelling Rank Sync.");
            return;
        }

        plugin.debugLogger(Level.INFO, "User " + event.getPlayer().getName() + " is linked to Discord. Syncing Ranks.");

        //Sync Discord Profile when user joins
        syncDiscordProfile(playerUUID, discordAuthentication.getData().getDiscordIDFromUUID(playerUUID));
    }

    @EventHandler
    public void onDiscordAuthenticationLink(DiscordAuthenticationLinkEvent event) {
        plugin.debugLogger(Level.INFO, "DiscordAuthenticationLinkEvent detected. Syncing Discord Profile for " + event.getMinecraftUUID() + " and " + event.getDiscordID());
        syncDiscordProfile(event.getMinecraftUUID(), event.getDiscordID());
    }

    @EventHandler
    public void onDiscordAuthenticationUnlink(DiscordAuthenticationUnlinkEvent event) {
        plugin.debugLogger(Level.INFO, "DiscordAuthenticationUnlinkEvent detected. Removing Discord Ranks for " + event.getMinecraftUUID() + " and " + event.getDiscordID());
        removeDiscordRanks(event.getMinecraftUUID(), event.getDiscordID());
    }


    private void syncDiscordProfile(UUID minecraftUUID, long discordID) {
        // Load member data into cache

        plugin.debugLogger(Level.INFO, "Syncing Discord Profile for " + minecraftUUID + " and " + discordID);

        final HashMap<String, HashMap<String, String>> rankups = discordRankSyncerDatastore.getRankups();
        final PermissionBridge permissionBridge = retroBridgeAccess.getPermissionBridge();

        if (permissionBridge == null) {
            this.plugin.logger(Level.WARNING, "RetroBridge permissions bridge is unavailable. Failed to sync Discord roles for " + minecraftUUID);
            return;
        }

        final String[] userGroups = permissionBridge.getGroups(minecraftUUID);
        final String primaryGroup = permissionBridge.getPrimaryGroup(minecraftUUID);

        final String resolvedUsername = Bukkit.getOfflinePlayer(minecraftUUID).getName();
        final String username = resolvedUsername == null ? "Unknown" : resolvedUsername;


        // Get Discord User (Moving to an asynchronous task)
        plugin.getDiscord().getDiscordBot().getJDA().retrieveUserById(discordID).queue(user -> {
            plugin.debugLogger(Level.INFO, "Retrieved Discord User " + user.getName() + " for " + username);
            // Run rankup tests
            rankupLoop:
            for (String key : rankups.keySet()) {
                String groupName = rankups.get(key).get("groupName");
                String guildID = rankups.get(key).get("guildID");
                String roleID = rankups.get(key).get("roleID");

                plugin.debugLogger(Level.INFO, "Checking Rankup for " + username + " in " + guildID + " for " + groupName);

                // Check if user has correct Group
                if (!isUserInGroup(groupName, primaryGroup, userGroups)) {
                    plugin.debugLogger(Level.INFO, "User " + username + " doesn't have group " + groupName + ". Skipping rank issue.");
                    continue;
                }

                // Check if guild exists
                if (plugin.getDiscord().getDiscordBot().getJDA().getGuildById(guildID) == null) {
                    plugin.debugLogger(Level.WARNING, "Guild " + guildID + " doesn't exist. Skipping rank issue.");
                    continue;
                }

                // Check if user is in guild
                Member member = plugin.getDiscord().getDiscordBot().getJDA().getGuildById(guildID).getMember(user);

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
                Role role = this.discordCore.getDiscordBot().getJDA().getRoleById(roleID);
                if (role == null) {
                    this.plugin.logger(Level.WARNING, "Role: " + roleID + " doesn't exist. Failed to issue role to user " + username);
                    continue;
                }

                // Give user role
                this.discordCore.getDiscordBot().getJDA().getGuildById(guildID).addRoleToMember(member, role).queue();
                this.plugin.logger(Level.INFO, "Issued Role " + role.getName() + " to user " + member.getUser().getName() + " who is know as " + username + " in-game");
            }


        });
    }

    private void removeDiscordRanks(UUID minecraftUUID, long discordID) {
        final String resolvedUsername = Bukkit.getOfflinePlayer(minecraftUUID).getName();
        final String username = resolvedUsername == null ? minecraftUUID.toString() : resolvedUsername;

        // Queue retrieval of the user by their Discord ID
        this.discordCore.getDiscordBot().getJDA().retrieveUserById(discordID).queue(user -> {
            for (HashMap<String, String> rankup : discordRankSyncerDatastore.getRankups().values()) {
                final String guildID = rankup.get("guildID");
                final String roleID = rankup.get("roleID");

                Guild guild = this.discordCore.getDiscordBot().getJDA().getGuildById(guildID);
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



    private Boolean isUserInGroup(String groupName, String primaryGroup, String[] userGroups) {
        // Handle the case where groupName is "*", which means any group is accepted.
        if (groupName.equalsIgnoreCase("*")) {
            return true;
        }

        // Split the groupName by commas to handle multiple groups.
        String[] groups = groupName.split(",");

        // Iterate over each group in groups array.
        for (String group : groups) {
            String trimmedGroup = group.trim();

            if (primaryGroup != null && trimmedGroup.equalsIgnoreCase(primaryGroup)) {
                return true;
            }

            // Trim whitespace and check if the group is in userGroups.
            if (userGroups != null) {
                for (String userGroup : userGroups) {
                    if (trimmedGroup.equalsIgnoreCase(userGroup)) {
                        return true;
                    }
                }
            }
        }

        // If no match is found, return false.
        return false;
    }

}

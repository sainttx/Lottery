package com.daegonner.lottery;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Created by Matthew on 26/10/2014.
 */
public class Lottery extends JavaPlugin {

    /*
     * Players who have purchased tickets
     */
    private Map<UUID, Integer> tickets = new HashMap<UUID, Integer>();

    /*
     * The amount of time left until the draw
     */
    private int timeLeft;

    /*
     * A random used to get the winner
     */
    private Random findWinner = new Random();

    /*
     * Vault economy implementation
     */
    private Economy economy;

    /*
     * Prefixes for messages
     */
    private final String PREFIX = color("&6[LOTTERY]&r ");

    @Override
    public void onEnable() {
        saveDefaultConfig();
        this.timeLeft = getConfig().getInt("seconds");
        getServer().getScheduler().scheduleSyncRepeatingTask(this, new LotteryTimer(), 0L, 20L);

        economy = getServer().getServicesManager().getRegistration(Economy.class).getProvider();
    }

    @Override
    public void onDisable() {
        end();
        getServer().getScheduler().cancelTasks(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || !args[0].equalsIgnoreCase("buy")) {
            double ticketPrice = getConfig().getDouble("cost-per-ticket");
            sender.sendMessage(PREFIX + color("Draw in &c" + TimeUtil.formatDateDiff(getEndDate())));
            sender.sendMessage(PREFIX + color("Buy a ticket for &c$" + ticketPrice + " &fwith &c/lottery buy"));
            sender.sendMessage(PREFIX + color("Current jackpot is &c$" + (getTotalTickets() * ticketPrice)));
            sender.sendMessage(PREFIX + color("You currently have &c" + getTickets(sender) + " tickets"));
            return false;
        } else if (!(sender instanceof Player)) {
            sender.sendMessage("Only players can buy lottery tickets");
        } else if (args.length != 2) {
            sender.sendMessage(PREFIX + "/lottery buy <tickets>");
            return false;
        } else if (!args[1].matches("[0-9]{0,12}")) {
            sender.sendMessage(PREFIX + "&cYou must provide a valid number of tickets");
            return false;
        }

        Player player = (Player) sender;
        double ticketCost = getConfig().getDouble("cost-per-ticket");
        int wantedTickets = Integer.parseInt(args[1]);

        if (wantedTickets + getTickets(player) > getConfig().getInt("max-tickets-per-user")) {
            sender.sendMessage(PREFIX + ChatColor.RED + "You may only purchase a max of " + getConfig().getInt("max-tickets-per-user") + " tickets!");
            return false;
        } else if (!economy.has(player, wantedTickets * ticketCost)) {
            sender.sendMessage(PREFIX + "&cYou don't have enough money!");
            return false;
        }

        this.addTickets(player, wantedTickets);
        economy.withdrawPlayer(player, wantedTickets * ticketCost);
        sender.sendMessage(PREFIX + color("You purchased &c" + wantedTickets + " &ftickets for &c$" + (wantedTickets * ticketCost)));
        return false;
    }

    /*
     * Adds tickets to a players total
     */
    private void addTickets(Player player, int numTickets) {
        int currentTickets = tickets.containsKey(player.getUniqueId()) ? tickets.get(player.getUniqueId()) : 0;
        tickets.put(player.getUniqueId(), currentTickets + numTickets);
    }

    /*
     * Gets the total number of tickets purchased
     */
    private int getTotalTickets() {
        int ret = 0;

        for (int i : tickets.values()) {
            ret += i;
        }

        return ret;
    }

    /*
     * Gets the amount of tickets a command sender has
     */
    private int getTickets(CommandSender sender) {
        if (sender instanceof Player) {
            Player player = (Player) sender;
            return tickets.containsKey(player.getUniqueId()) ? tickets.get(player.getUniqueId()) : 0;
        }

        return 0;
    }

    /*
     * Gets the end date of the lottery
     */
    private long getEndDate() {
        return System.currentTimeMillis() + (timeLeft * 1000L);
    }

    /**
     * Ends the lottery
     */
    public void end() {
        if (tickets.isEmpty()) {
            this.timeLeft = getConfig().getInt("seconds");
            Bukkit.broadcastMessage(PREFIX + color("&cNo tickets sold this round. Thats a shame."));
            return;
        }

        List<String> participants = new ArrayList<String>();
        for (Map.Entry<UUID, Integer> ticketEntries : tickets.entrySet()) {
            for (int i = 0 ; i < ticketEntries.getValue() ; i++) {
                participants.add(ticketEntries.getKey().toString());
            }
        }

        // Find a winner out of all players
        Collections.shuffle(participants);
        UUID winnerID = UUID.fromString(participants.get(findWinner.nextInt(participants.size())));
        OfflinePlayer winner = Bukkit.getOfflinePlayer(winnerID);

        // Lottery information
        int totalTickets = getTotalTickets();
        int numTickets = tickets.get(winner.getUniqueId());
        double winnings = totalTickets * getConfig().getDouble("cost-per-ticket");

        double tax = getConfig().getInt("tax-percentage");
        double trueWinnings = winnings - (winnings * (tax / 100));

        // Give player money and announce
        economy.depositPlayer(winner, trueWinnings);
        Bukkit.broadcastMessage(PREFIX + color("&c" + winner.getName() + " &fhas won the lottery for &c$" + winnings + " &fwith &c" + numTickets + " tickets &fout of a total &c" + totalTickets));

        if (winner.isOnline()) {
            winner.getPlayer().sendMessage(PREFIX + color("You received &c$" + trueWinnings + " &fafter a &c" + tax + "% &ftax"));
        }

        // Reset
        tickets.clear();
        timeLeft = getConfig().getInt("seconds");
    }

    /*
     * Colors a string
     */
    private String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    /**
     * Handles the lottery time
     */
    public class LotteryTimer extends BukkitRunnable {

        @Override
        public void run() {
            if (--timeLeft <= 0) {
                end();
                return;
            }

            switch(timeLeft) {
                case 3600:
                case 1800:
                case 600:
                case 300:
                case 60:
                case 30:
                case 10:
                case 3:
                case 2:
                case 1:
                    Bukkit.broadcastMessage(PREFIX + color("Draw in: &c" + TimeUtil.formatDateDiff(getEndDate())));
            }
        }
    }
}

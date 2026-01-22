package dev.veyno.aiMcperformance.command;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.util.StringUtil;

public class PerformanceTabCompleter implements TabCompleter {
    private static final List<String> SUBCOMMANDS = List.of("monitor", "report");
    private static final List<String> METRICS = List.of("mspt", "tps", "entities", "ram", "cpu", "chunks", "viewdistance");
    private static final List<String> ACTIONS = List.of("on", "off", "toggle");

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], SUBCOMMANDS, new java.util.ArrayList<>());
        }
        if (args.length == 2 && "monitor".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[1], METRICS, new java.util.ArrayList<>());
        }
        if (args.length == 3 && "monitor".equalsIgnoreCase(args[0])) {
            return StringUtil.copyPartialMatches(args[2].toLowerCase(Locale.ROOT), ACTIONS, new java.util.ArrayList<>());
        }
        return List.of();
    }
}

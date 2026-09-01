package com.thx.aiplatform.server;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

@Component
class ServerCommandRiskClassifier {

    private static final Set<String> READ_ONLY_COMMANDS = Set.of(
            "cat", "cut", "df", "dig", "du", "file", "free", "grep", "head", "iostat", "ls", "lsblk",
            "lsof", "mpstat", "netstat", "nproc", "nslookup", "ps", "rg", "sort", "stat", "tail", "tr",
            "uname", "uniq", "uptime", "vmstat", "wc", "who",
            "whoami"
    );

    ServerCommandRisk classify(String commandText) {
        if (commandText == null || commandText.isBlank()) return ServerCommandRisk.DANGEROUS;
        String command = commandText.trim();
        // Shell 控制符能够串入额外动作；无法可靠证明只读时一律升级为危险操作。
        if (command.contains("\n") || command.contains("\r") || command.contains(";") || command.contains("&&")
                || command.contains("||") || command.contains(">") || command.contains("<")
                || command.contains("`") || command.contains("$(") || command.contains("${")
                || command.contains("&")) return ServerCommandRisk.DANGEROUS;

        for (String segment : command.split("\\|")) {
            String value = segment.trim();
            if (value.isEmpty()) return ServerCommandRisk.DANGEROUS;
            String[] parts = value.split("\\s+");
            String executable = parts[0].toLowerCase(Locale.ROOT);
            if (READ_ONLY_COMMANDS.contains(executable) && isReadOnlyGeneric(executable, parts)) continue;
            if ("hostname".equals(executable) && isReadOnlyHostname(parts)) continue;
            if ("ip".equals(executable) && isReadOnlyIp(parts)) continue;
            if ("journalctl".equals(executable) && isReadOnlyJournalctl(parts)) continue;
            if ("ping".equals(executable) && isReadOnlyPing(parts)) continue;
            if ("ss".equals(executable) && isReadOnlySocketQuery(parts)) continue;
            if ("systemctl".equals(executable) && isReadOnlySystemctl(parts)) continue;
            if ("docker".equals(executable) && isReadOnlyDocker(parts)) continue;
            return ServerCommandRisk.DANGEROUS;
        }
        return ServerCommandRisk.NORMAL;
    }

    private boolean isReadOnlyGeneric(String executable, String[] parts) {
        if (!"rg".equals(executable)) return true;
        return Arrays.stream(parts).noneMatch(value -> "--pre".equals(value) || value.startsWith("--pre="));
    }

    private boolean isReadOnlyHostname(String[] parts) {
        if (parts.length == 1) return true;
        return Set.of("-a", "-A", "-d", "-f", "-F", "-i", "-I", "-s", "-y", "--fqdn", "--short")
                .contains(parts[1]);
    }

    private boolean isReadOnlyIp(String[] parts) {
        if (parts.length < 2) return false;
        String operation = parts[1].toLowerCase(Locale.ROOT);
        if (Set.of("-br", "-brief", "-details", "-d", "-statistics", "-s").contains(operation)) {
            if (parts.length < 3) return false;
            operation = parts[2].toLowerCase(Locale.ROOT);
        }
        return Set.of("address", "addr", "link", "route", "neighbour", "neighbor", "neigh", "rule")
                .contains(operation) && !String.join(" ", parts).matches("(?i).*\\s(add|append|change|delete|del|flush|replace|set)\\s.*");
    }

    private boolean isReadOnlyJournalctl(String[] parts) {
        String command = String.join(" ", parts).toLowerCase(Locale.ROOT);
        return !command.matches(".*--(vacuum|rotate|flush|sync|relinquish-var|smart-relinquish-var|setup-keys|update-catalog).*");
    }

    private boolean isReadOnlyPing(String[] parts) {
        return Arrays.stream(parts).noneMatch(value -> "-f".equals(value) || "-b".equals(value));
    }

    private boolean isReadOnlySocketQuery(String[] parts) {
        return Arrays.stream(parts).noneMatch(value -> "-K".equals(value) || "--kill".equals(value));
    }

    private boolean isReadOnlySystemctl(String[] parts) {
        return parts.length > 1 && Set.of("status", "is-active", "is-enabled", "show", "list-units", "list-unit-files")
                .contains(parts[1].toLowerCase(Locale.ROOT));
    }

    private boolean isReadOnlyDocker(String[] parts) {
        return parts.length > 1 && Set.of("ps", "inspect", "logs", "stats", "top", "version", "info")
                .contains(parts[1].toLowerCase(Locale.ROOT));
    }
}

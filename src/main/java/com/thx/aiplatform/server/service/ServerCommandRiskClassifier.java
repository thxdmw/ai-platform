package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.model.ServerCommandRisk;

import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;

/**
 * 命令风险分类器：以白名单方式判定命令是否只读。白名单内的命令（且参数未超出只读语义）
 * 判为普通风险，其余一律判为危险——安全优先：误判危险最多让用户多一次点击确认，误判
 * 只读则可能直接把破坏性命令执行出去。管道命令按「|」切段，每一段的可执行文件都必须
 * 通过只读检查。
 *
 * <p>这个分类器只决定「是否需要二次确认」，不替代执行层面的主机密钥校验与输出上限。</p>
 */
@Component
public class ServerCommandRiskClassifier {

    private static final Set<String> READ_ONLY_COMMANDS = Set.of(
            "cat", "cut", "df", "dig", "du", "file", "free", "grep", "head", "iostat", "ls", "lsblk",
            "lsof", "mpstat", "netstat", "nproc", "nslookup", "ps", "rg", "sort", "stat", "tail", "tr",
            "uname", "uniq", "uptime", "vmstat", "wc", "who",
            "whoami"
    );

    /**
     * 判定命令风险。第一道防线是拒绝一切含 Shell 控制符/重定向/命令替换的命令：这些语法
     * 能以不可见方式串入额外动作，永远无法可靠证明只读；随后把管道拆成段，逐段校验
     * 可执行文件是否在白名单且参数只读。
     */
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

    /**
     * 白名单命令默认只读，唯独 rg 例外：rg --pre 会执行任意命令，必须显式排除才安全。
     */
    private boolean isReadOnlyGeneric(String executable, String[] parts) {
        if (!"rg".equals(executable)) return true;
        return Arrays.stream(parts).noneMatch(value -> "--pre".equals(value) || value.startsWith("--pre="));
    }

    private boolean isReadOnlyHostname(String[] parts) {
        if (parts.length == 1) return true;
        return Set.of("-a", "-A", "-d", "-f", "-F", "-i", "-I", "-s", "-y", "--fqdn", "--short")
                .contains(parts[1]);
    }

    /**
     * ip 命令的只读判定要看「操作子命令」而不是第一个参数：-br 等全局选项出现时真正的
     * 操作位后移，取错位置会把修改类操作误判成只读；再用正则排除 add/del/change 等
     * 修改动作，防止 ip link set eth0 down 之类被放行。
     */
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

    /**
     * journalctl 默认只读，但 --vacuum/--rotate 等维护参数会改动日志库，出现即判危险。
     */
    private boolean isReadOnlyJournalctl(String[] parts) {
        String command = String.join(" ", parts).toLowerCase(Locale.ROOT);
        return !command.matches(".*--(vacuum|rotate|flush|sync|relinquish-var|smart-relinquish-var|setup-keys|update-catalog).*");
    }

    /**
     * ping 本身无害，但 -f（洪水）和 -b（广播）会把网络打满，属于「只读但有害」，
     * 同样判危险。
     */
    private boolean isReadOnlyPing(String[] parts) {
        return Arrays.stream(parts).noneMatch(value -> "-f".equals(value) || "-b".equals(value));
    }

    /**
     * ss 只读查询安全，但 -K/--kill 会直接杀掉远端连接，必须按修改类动作处理。
     */
    private boolean isReadOnlySocketQuery(String[] parts) {
        return Arrays.stream(parts).noneMatch(value -> "-K".equals(value) || "--kill".equals(value));
    }

    /**
     * systemctl 只放行白名单内的状态查询子命令；白名单外的子命令（restart、stop 等）
     * 以及不带子命令的用法一律判危险——identify 不到只读证据就按危险处理。
     */
    private boolean isReadOnlySystemctl(String[] parts) {
        return parts.length > 1 && Set.of("status", "is-active", "is-enabled", "show", "list-units", "list-unit-files")
                .contains(parts[1].toLowerCase(Locale.ROOT));
    }

    /**
     * docker 与 systemctl 同理：只放行 ps/inspect/logs 等查询子命令，run/rm/exec 等
     * 一律危险。
     */
    private boolean isReadOnlyDocker(String[] parts) {
        return parts.length > 1 && Set.of("ps", "inspect", "logs", "stats", "top", "version", "info")
                .contains(parts[1].toLowerCase(Locale.ROOT));
    }
}

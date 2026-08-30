package com.thx.aiplatform.server;

final class ServerCommandCatalog {

    private static final String TARGET_PATTERN = "[A-Za-z0-9_.@-]{1,96}";

    private ServerCommandCatalog() { }

    static String overview() {
        return "LC_ALL=C; echo '=== UPTIME ==='; uptime; echo '=== MEMORY(MB) ==='; free -m; "
                + "echo '=== DISK ==='; df -h -x tmpfs -x devtmpfs; echo '=== SYSTEM ==='; uname -srmo";
    }

    static String processes() {
        return "LC_ALL=C ps -eo pid,user,%cpu,%mem,comm --sort=-%cpu | head -n 16";
    }

    static String dockerStatus() {
        return "docker ps --format 'table {{.Names}}\\t{{.Status}}\\t{{.Image}}'";
    }

    static String serviceStatus(String service) {
        return "systemctl --no-pager --full status -- " + target(service) + " | head -n 80";
    }

    static String serviceLogs(String service, int lines) {
        return "journalctl --no-pager -u " + target(service) + " -n " + Math.max(20, Math.min(lines, 500));
    }

    static String containerLogs(String container, int lines) {
        return "docker logs --tail " + Math.max(20, Math.min(lines, 500)) + " " + target(container) + " 2>&1";
    }

    static String restartService(String service) {
        return "sudo -n systemctl restart -- " + target(service) + " && systemctl is-active -- " + target(service);
    }

    static String restartContainer(String container) {
        return "docker restart -- " + target(container) + " && docker inspect --format '{{.State.Status}}' " + target(container);
    }

    static String target(String value) {
        if (value == null || !value.matches(TARGET_PATTERN)) throw new IllegalArgumentException("操作目标格式不合法");
        return value;
    }
}

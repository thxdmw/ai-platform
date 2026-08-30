package com.thx.aiplatform.server;

public record SshExecutionResult(
        String serverId,
        int exitCode,
        String stdout,
        String stderr,
        long durationMillis,
        boolean truncated
) {
    boolean successful() { return exitCode == 0; }

    String forModel() {
        return "服务器=" + serverId + "\n退出码=" + exitCode + "\n耗时毫秒=" + durationMillis
                + "\n标准输出：\n" + empty(stdout) + "\n标准错误：\n" + empty(stderr)
                + (truncated ? "\n[输出已截断]" : "");
    }

    private String empty(String value) { return value == null || value.isBlank() ? "（空）" : value; }
}

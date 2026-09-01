package com.thx.aiplatform.server.model;

/**
 * 单条命令的完整执行结果：退出码、stdout/stderr、耗时与截断标记。forModel() 把结果
 * 格式化成语义明确的文本交给模型判断——truncated 标记尤其重要，模型必须知道输出不完整，
 * 避免基于残缺输出下结论。
 */
public record SshExecutionResult(
        String serverId,
        int exitCode,
        String stdout,
        String stderr,
        long durationMillis,
        boolean truncated
) {
    public boolean successful() { return exitCode == 0; }

    public String forModel() {
        return "服务器=" + serverId + "\n退出码=" + exitCode + "\n耗时毫秒=" + durationMillis
                + "\n标准输出：\n" + empty(stdout) + "\n标准错误：\n" + empty(stderr)
                + (truncated ? "\n[输出已截断]" : "");
    }

    private String empty(String value) { return value == null || value.isBlank() ? "（空）" : value; }
}

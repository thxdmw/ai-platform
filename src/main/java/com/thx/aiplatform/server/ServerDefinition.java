package com.thx.aiplatform.server;

import java.util.List;

record ServerDefinition(
        String id,
        String name,
        String host,
        Integer port,
        String username,
        String passwordEnv,
        String privateKeyPath,
        String passphraseEnv,
        String knownHostsPath,
        List<String> allowedServices,
        List<String> allowedContainers,
        Boolean enabled
) {
    private static final String ID_PATTERN = "[A-Za-z0-9_-]{1,32}";
    private static final String TARGET_PATTERN = "[A-Za-z0-9_.@-]{1,96}";

    ServerDefinition normalized() {
        String normalizedId = required(id, "服务器 ID");
        if (!normalizedId.matches(ID_PATTERN)) throw new IllegalArgumentException("服务器 ID 格式不合法：" + normalizedId);
        String normalizedHost = required(host, "服务器地址");
        String normalizedUser = required(username, "SSH 用户名");
        int normalizedPort = port == null ? 22 : port;
        if (normalizedPort < 1 || normalizedPort > 65_535) throw new IllegalArgumentException("SSH 端口不合法：" + normalizedId);
        String keyPath = nullable(privateKeyPath);
        String passwordVariable = nullable(passwordEnv);
        if (keyPath == null && passwordVariable == null) throw new IllegalArgumentException("服务器必须配置 privateKeyPath 或 passwordEnv：" + normalizedId);
        String knownHosts = required(knownHostsPath, "known_hosts 路径");
        return new ServerDefinition(
                normalizedId,
                nullable(name) == null ? normalizedId : name.trim(),
                normalizedHost,
                normalizedPort,
                normalizedUser,
                passwordVariable,
                keyPath,
                nullable(passphraseEnv),
                knownHosts,
                normalizeTargets(allowedServices, "服务名"),
                normalizeTargets(allowedContainers, "容器名"),
                enabled == null || enabled
        );
    }

    boolean allowsService(String service) { return allowedServices.contains(service); }
    boolean allowsContainer(String container) { return allowedContainers.contains(container); }

    private List<String> normalizeTargets(List<String> values, String name) {
        if (values == null) return List.of();
        return values.stream().map(this::nullable).filter(value -> value != null).peek(value -> {
            if (!value.matches(TARGET_PATTERN)) throw new IllegalArgumentException(name + "格式不合法：" + value);
        }).distinct().limit(100).toList();
    }

    private String required(String value, String name) {
        String result = nullable(value);
        if (result == null) throw new IllegalArgumentException(name + "不能为空");
        return result;
    }

    private String nullable(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}

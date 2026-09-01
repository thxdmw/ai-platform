package com.thx.aiplatform.server;

record ServerDefinition(
        String id,
        String name,
        String host,
        int port,
        String username,
        ServerAuthenticationType authenticationType,
        String credentialCiphertext,
        String passphraseCiphertext,
        String hostKey,
        boolean enabled
) { }

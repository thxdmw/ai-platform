package com.thx.aiplatform.server;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

@Component
class SshCommandExecutor {

    private final ServerAssistantProperties properties;
    private final ServerCredentialCipher credentialCipher;
    private final Clock clock;

    @Autowired
    SshCommandExecutor(ServerAssistantProperties properties, ServerCredentialCipher credentialCipher) {
        this(properties, credentialCipher, Clock.systemUTC());
    }

    SshCommandExecutor(ServerAssistantProperties properties, ServerCredentialCipher credentialCipher, Clock clock) {
        this.properties = properties;
        this.credentialCipher = credentialCipher;
        this.clock = clock;
    }

    void testConnection(ServerDefinition server) {
        Session session = null;
        byte[] credential = null;
        byte[] passphrase = null;
        try {
            credential = credentialCipher.decrypt(server.credentialCiphertext());
            passphrase = decryptOptional(server.passphraseCiphertext());
            session = connect(server, credential, passphrase);
        } catch (Exception exception) {
            throw new IllegalStateException("SSH 连接失败：" + safeMessage(exception));
        } finally {
            if (session != null) session.disconnect();
            clear(credential);
            clear(passphrase);
        }
    }

    SshExecutionResult execute(ServerDefinition server, String command) {
        Session session = null;
        ChannelExec channel = null;
        byte[] credential = null;
        byte[] passphrase = null;
        long started = clock.millis();
        try {
            credential = credentialCipher.decrypt(server.credentialCiphertext());
            passphrase = decryptOptional(server.passphraseCiphertext());
            session = connect(server, credential, passphrase);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getExtInputStream();
            channel.connect(Math.toIntExact(properties.getConnectTimeout().toMillis()));
            CapturedOutput captured = capture(channel, stdout, stderr, properties.getCommandTimeout(), properties.getMaxOutputBytes());
            return new SshExecutionResult(server.id(), channel.getExitStatus(), captured.stdout(), captured.stderr(),
                    clock.millis() - started, captured.truncated());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("SSH 操作被中断");
        } catch (Exception exception) {
            throw new IllegalStateException("SSH 连接或执行失败：" + safeMessage(exception));
        } finally {
            if (channel != null) channel.disconnect();
            if (session != null) session.disconnect();
            clear(credential);
            clear(passphrase);
        }
    }

    private Session connect(ServerDefinition server, byte[] credential, byte[] passphrase) throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(new ByteArrayInputStream(server.hostKey().getBytes(StandardCharsets.UTF_8)));
        if (server.authenticationType() == ServerAuthenticationType.PRIVATE_KEY) {
            jsch.addIdentity("server-" + server.id(), credential, null, passphrase);
        }
        Session session = jsch.getSession(server.username(), server.host(), server.port());
        try {
            if (server.authenticationType() == ServerAuthenticationType.PASSWORD) session.setPassword(credential);
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications",
                    server.authenticationType() == ServerAuthenticationType.PRIVATE_KEY ? "publickey" : "password,keyboard-interactive");
            session.connect(Math.toIntExact(properties.getConnectTimeout().toMillis()));
            return session;
        } catch (Exception exception) {
            session.disconnect();
            throw exception;
        }
    }

    private byte[] decryptOptional(String ciphertext) {
        return ciphertext == null || ciphertext.isBlank() ? null : credentialCipher.decrypt(ciphertext);
    }

    private CapturedOutput capture(ChannelExec channel, InputStream stdout, InputStream stderr,
                                   Duration timeout, int maxBytes) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        boolean truncated = false;
        while (true) {
            truncated |= drain(stdout, out, Math.max(0, maxBytes - out.size() - err.size()));
            truncated |= drain(stderr, err, Math.max(0, maxBytes - out.size() - err.size()));
            if (channel.isClosed() && stdout.available() == 0 && stderr.available() == 0) break;
            if (System.nanoTime() >= deadline) throw new IllegalStateException("SSH 命令执行超时");
            Thread.sleep(25);
        }
        return new CapturedOutput(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), truncated);
    }

    private boolean drain(InputStream input, ByteArrayOutputStream output, int remainingCapacity) throws Exception {
        boolean truncated = false;
        int capacity = remainingCapacity;
        byte[] buffer = new byte[2_048];
        while (input.available() > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, input.available()));
            if (read < 0) break;
            int writable = Math.min(read, Math.max(0, capacity));
            if (writable > 0) output.write(buffer, 0, writable);
            capacity -= writable;
            if (read > writable) truncated = true;
        }
        return truncated;
    }

    private void clear(byte[] value) { if (value != null) Arrays.fill(value, (byte) 0); }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record CapturedOutput(String stdout, String stderr, boolean truncated) { }
}

package com.thx.aiplatform.server;

import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;

@Component
class SshCommandExecutor {

    private final ServerAssistantProperties properties;
    private final Clock clock;

    @Autowired
    SshCommandExecutor(ServerAssistantProperties properties) {
        this(properties, Clock.systemUTC());
    }

    SshCommandExecutor(ServerAssistantProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    SshExecutionResult execute(ServerDefinition server, String command) {
        Session session = null;
        ChannelExec channel = null;
        byte[] password = null;
        byte[] passphrase = null;
        long started = clock.millis();
        try {
            JSch jsch = new JSch();
            jsch.setKnownHosts(server.knownHostsPath());
            if (server.privateKeyPath() != null) {
                passphrase = secret(server.passphraseEnv(), server.passphraseEnv() != null);
                jsch.addIdentity(server.privateKeyPath(), passphrase);
            }
            session = jsch.getSession(server.username(), server.host(), server.port());
            if (server.passwordEnv() != null) {
                password = secret(server.passwordEnv(), true);
                session.setPassword(password);
            }
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications", "publickey,password,keyboard-interactive");
            session.connect(Math.toIntExact(properties.getConnectTimeout().toMillis()));

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
            if (password != null) Arrays.fill(password, (byte) 0);
            if (passphrase != null) Arrays.fill(passphrase, (byte) 0);
        }
    }

    private CapturedOutput capture(ChannelExec channel, InputStream stdout, InputStream stderr,
                                   Duration timeout, int maxBytes) throws Exception {
        long deadline = System.nanoTime() + timeout.toNanos();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        boolean truncated = false;
        while (true) {
            truncated |= drain(stdout, out, maxBytes);
            truncated |= drain(stderr, err, maxBytes);
            if (channel.isClosed() && stdout.available() == 0 && stderr.available() == 0) break;
            if (System.nanoTime() >= deadline) throw new IllegalStateException("SSH 命令执行超时");
            Thread.sleep(25);
        }
        return new CapturedOutput(out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8), truncated);
    }

    private boolean drain(InputStream input, ByteArrayOutputStream output, int maxBytes) throws Exception {
        boolean truncated = false;
        byte[] buffer = new byte[2_048];
        while (input.available() > 0) {
            int read = input.read(buffer, 0, Math.min(buffer.length, input.available()));
            if (read < 0) break;
            int remaining = maxBytes - output.size();
            if (remaining > 0) output.write(buffer, 0, Math.min(read, remaining));
            if (read > remaining) truncated = true;
        }
        return truncated;
    }

    private byte[] secret(String environmentName, boolean required) {
        if (environmentName == null) return null;
        String value = System.getenv(environmentName);
        if (value == null || value.isBlank()) {
            if (required) throw new IllegalStateException("SSH 密码环境变量未配置：" + environmentName);
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }

    private String safeMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? exception.getClass().getSimpleName() : message;
    }

    private record CapturedOutput(String stdout, String stderr, boolean truncated) { }
}

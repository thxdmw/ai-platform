package com.thx.aiplatform.server.service;
import com.thx.aiplatform.server.security.ServerCredentialCipher;
import com.thx.aiplatform.server.model.SshExecutionResult;
import com.thx.aiplatform.server.entity.ServerEntity;
import com.thx.aiplatform.server.enums.ServerAuthenticationType;
import com.thx.aiplatform.server.config.ServerAssistantProperties;

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

/**
 * SSH 命令执行器（JSch）。三条不可破坏的安全约束：
 *
 * <ol>
 *   <li>强制主机公钥校验：known_hosts 只来自页面保存的公钥流且 StrictHostKeyChecking=yes，
 *       主机密钥不匹配直接失败——这是防止连到错误主机的唯一防线，不允许放宽任何模式；</li>
 *   <li>凭据生命周期最短化：从密文解密为字节数组，执行完立即清零，明文不在内存中长时间
 *       驻留，也绝不进入日志或异常消息；</li>
 *   <li>资源有界：连接超时、命令超时（默认 30 秒）、stdout+stderr 合计 16KB 上限，
 *       超限截断而不是让远端输出把内存或模型上下文打爆。</li>
 * </ol>
 */
@Component
public class SshCommandExecutor {

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

    /**
     * 只验证连接与主机密钥，不执行任何命令；页面据此确信配置可用。
     */
    public void testConnection(ServerEntity server) {
        Session session = null;
        byte[] credential = null;
        byte[] passphrase = null;
        try {
            credential = credentialCipher.decrypt(server.getCredentialCiphertext());
            passphrase = decryptOptional(server.getPassphraseCiphertext());
            session = connect(server, credential, passphrase);
        } catch (Exception exception) {
            throw new IllegalStateException("SSH 连接失败：" + safeMessage(exception));
        } finally {
            if (session != null) session.disconnect();
            clear(credential);
            clear(passphrase);
        }
    }

    /**
     * 执行单条命令。stdin 置 null 阻止远端命令读取到任何用户输入；退出码、输出、耗时和
     * 截断标记一起封装回 {@link SshExecutionResult} 供模型判断。
     */
    public SshExecutionResult execute(ServerEntity server, String command) {
        Session session = null;
        ChannelExec channel = null;
        byte[] credential = null;
        byte[] passphrase = null;
        long started = clock.millis();
        try {
            credential = credentialCipher.decrypt(server.getCredentialCiphertext());
            passphrase = decryptOptional(server.getPassphraseCiphertext());
            session = connect(server, credential, passphrase);
            channel = (ChannelExec) session.openChannel("exec");
            channel.setCommand(command);
            channel.setInputStream(null);
            InputStream stdout = channel.getInputStream();
            InputStream stderr = channel.getExtInputStream();
            channel.connect(Math.toIntExact(properties.getConnectTimeout().toMillis()));
            CapturedOutput captured = capture(channel, stdout, stderr, properties.getCommandTimeout(), properties.getMaxOutputBytes());
            return new SshExecutionResult(server.getId(), channel.getExitStatus(), captured.stdout(), captured.stderr(),
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

    /**
     * 建立会话：known_hosts 直接用页面保存的公钥流，而不是依赖运行环境的主目录文件——
     * 服务器配置必须可移植；StrictHostKeyChecking=yes 使主机密钥不匹配直接失败。密码认证
     * 额外允许 keyboard-interactive，因为部分服务器禁用了 password 但允许键盘交互式密码
     * 认证，两者都是「密码类」认证，不引入公钥之外的信任。
     */
    private Session connect(ServerEntity server, byte[] credential, byte[] passphrase) throws Exception {
        JSch jsch = new JSch();
        jsch.setKnownHosts(new ByteArrayInputStream(server.getHostKey().getBytes(StandardCharsets.UTF_8)));
        if (server.getAuthenticationType() == ServerAuthenticationType.PRIVATE_KEY) {
            jsch.addIdentity("server-" + server.getId(), credential, null, passphrase);
        }
        Session session = jsch.getSession(server.getUsername(), server.getHost(), server.getPort());
        try {
            if (server.getAuthenticationType() == ServerAuthenticationType.PASSWORD) session.setPassword(credential);
            session.setConfig("StrictHostKeyChecking", "yes");
            session.setConfig("PreferredAuthentications",
                    server.getAuthenticationType() == ServerAuthenticationType.PRIVATE_KEY ? "publickey" : "password,keyboard-interactive");
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

    /**
     * 轮询排空 stdout/stderr 直到通道关闭：ChannelExec 的流在通道关闭前可能不完整，
     * 阻塞读无法同时兼顾超时与上限。stdout 与 stderr 共享同一个上限，避免某一端（如
     * 报错刷屏）独占全部配额；超时直接抛异常终止——远端命令即使还在跑，本端也不再等待。
     */
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

    /**
     * 排空单个流：达到上限后仍继续读完剩余字节（否则远端管道写满会卡死会话），只是把
     * 超出的部分丢弃并打上截断标记——调用方据此知道输出不完整，可能影响结论。
     */
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

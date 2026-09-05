package com.thx.aiplatform.server.service;

import com.thx.aiplatform.server.dto.ServerModelProviderProbeRequest;
import com.thx.aiplatform.server.vo.ServerModelProviderProbeResult;

/** 提供方连接探测接口：只读取模型目录，不发送聊天内容，既能验证地址与密钥，也不会产生模型费用。 */
public interface ServerModelProviderProbeService {

    ServerModelProviderProbeResult probe(ServerModelProviderProbeRequest request);
}
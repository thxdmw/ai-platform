package com.thx.aiplatform.server.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thx.aiplatform.server.entity.ServerCommandEntity;
import com.thx.aiplatform.server.model.ServerCommandParameterDefinition;
import com.thx.aiplatform.server.model.ServerCommandParameterType;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 参数化命令的安全边界：模板只能在完整 Shell 参数位置使用 {{name}}，参数先按类型与
 * 白名单校验，再统一做 POSIX Shell 单引号转义。这样路径或服务名可以每次变化，但模型
 * 仍然没有拼接任意 Shell 的能力。
 */
@Service
public class ServerCommandTemplateService {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([A-Za-z][A-Za-z0-9_]{0,31})}}", Pattern.MULTILINE);
    private static final Pattern PARAMETER_NAME = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,31}");
    private static final int MAX_PARAMETERS = 12;
    private final ObjectMapper objectMapper;

    public ServerCommandTemplateService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String normalizeSchema(String template, String schemaJson) {
        List<ServerCommandParameterDefinition> definitions = parseSchema(schemaJson);
        validateTemplate(template, definitions);
        try {
            return objectMapper.writeValueAsString(definitions);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("参数规则无法序列化", exception);
        }
    }

    public List<ServerCommandParameterDefinition> parameters(ServerCommandEntity command) {
        return parseSchema(command.getParameterSchema());
    }

    public String render(ServerCommandEntity command, String argumentsJson) {
        return render(command.getCommandText(), parseSchema(command.getParameterSchema()), argumentsJson);
    }

    public String classificationText(String template, String schemaJson) {
        List<ServerCommandParameterDefinition> definitions = parseSchema(schemaJson);
        validateTemplate(template, definitions);
        Map<String, String> samples = new LinkedHashMap<>();
        for (ServerCommandParameterDefinition definition : definitions) {
            samples.put(definition.name(), sampleValue(definition));
        }
        try {
            return render(template, definitions, objectMapper.writeValueAsString(samples));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("参数规则无法用于风险判定", exception);
        }
    }

    private String render(String template, List<ServerCommandParameterDefinition> definitions, String argumentsJson) {
        Map<String, String> arguments = parseArguments(argumentsJson);
        Set<String> expected = definitions.stream().map(ServerCommandParameterDefinition::name).collect(java.util.stream.Collectors.toSet());
        if (!arguments.keySet().equals(expected)) {
            Set<String> missing = new HashSet<>(expected);
            missing.removeAll(arguments.keySet());
            Set<String> extra = new HashSet<>(arguments.keySet());
            extra.removeAll(expected);
            throw new IllegalArgumentException("命令参数不匹配，缺少=" + missing + "，多余=" + extra);
        }
        Map<String, ServerCommandParameterDefinition> byName = new HashMap<>();
        definitions.forEach(value -> byName.put(value.name(), value));
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String replacement = validateAndQuote(byName.get(matcher.group(1)), arguments.get(matcher.group(1)));
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString();
    }

    private List<ServerCommandParameterDefinition> parseSchema(String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) return List.of();
        try {
            List<ServerCommandParameterDefinition> values = objectMapper.readValue(schemaJson,
                    new TypeReference<List<ServerCommandParameterDefinition>>() { });
            if (values.size() > MAX_PARAMETERS) throw new IllegalArgumentException("单条命令最多配置 " + MAX_PARAMETERS + " 个参数");
            List<ServerCommandParameterDefinition> normalized = new ArrayList<>();
            Set<String> names = new HashSet<>();
            for (ServerCommandParameterDefinition value : values) {
                if (value == null || value.name() == null || !PARAMETER_NAME.matcher(value.name()).matches()) {
                    throw new IllegalArgumentException("参数名称必须以字母开头且只包含字母、数字和下划线");
                }
                if (!names.add(value.name())) throw new IllegalArgumentException("参数名称不能重复：" + value.name());
                if (value.type() == null) throw new IllegalArgumentException("参数类型不能为空：" + value.name());
                normalized.add(normalizeDefinition(value));
            }
            return List.copyOf(normalized);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("参数规则必须是合法的 JSON 数组", exception);
        }
    }

    private ServerCommandParameterDefinition normalizeDefinition(ServerCommandParameterDefinition value) {
        List<String> allowedValues = cleanList(value.allowedValues());
        List<String> allowedRoots = cleanList(value.allowedRoots());
        int maxLength = value.maxLength() == null ? 500 : value.maxLength();
        if (maxLength < 1 || maxLength > 2000) throw new IllegalArgumentException("参数 maxLength 必须在 1 到 2000 之间：" + value.name());
        if (value.type() == ServerCommandParameterType.ENUM && allowedValues.isEmpty()) {
            throw new IllegalArgumentException("枚举参数必须配置 allowedValues：" + value.name());
        }
        if (value.type() == ServerCommandParameterType.PATH) {
            if (allowedRoots.isEmpty()) throw new IllegalArgumentException("路径参数必须配置 allowedRoots：" + value.name());
            allowedRoots.forEach(root -> validateAllowedRoot(value.name(), root));
        }
        if (value.minValue() != null && value.maxValue() != null && value.minValue() > value.maxValue()) {
            throw new IllegalArgumentException("整数参数最小值不能大于最大值：" + value.name());
        }
        if (value.pattern() != null && value.pattern().length() > 300) {
            throw new IllegalArgumentException("参数正则过长：" + value.name());
        }
        if (value.pattern() != null) Pattern.compile(value.pattern());
        return new ServerCommandParameterDefinition(value.name(), value.type(), allowedValues, allowedRoots,
                blankToNull(value.pattern()), maxLength, value.minValue(), value.maxValue());
    }

    private void validateTemplate(String template, List<ServerCommandParameterDefinition> definitions) {
        if (template == null || template.isBlank()) throw new IllegalArgumentException("命令模板不能为空");
        if (template.indexOf('\0') >= 0) throw new IllegalArgumentException("命令模板不能包含空字符");
        Matcher matcher = PLACEHOLDER.matcher(template);
        Set<String> placeholders = new HashSet<>();
        while (matcher.find()) {
            char before = matcher.start() == 0 ? ' ' : template.charAt(matcher.start() - 1);
            char after = matcher.end() == template.length() ? ' ' : template.charAt(matcher.end());
            if (!Character.isWhitespace(before) || !Character.isWhitespace(after)) {
                throw new IllegalArgumentException("参数占位符必须独占一个 Shell 参数：" + matcher.group());
            }
            placeholders.add(matcher.group(1));
        }
        Set<String> names = definitions.stream().map(ServerCommandParameterDefinition::name).collect(java.util.stream.Collectors.toSet());
        if (!placeholders.equals(names)) throw new IllegalArgumentException("模板占位符必须与参数规则一一对应");
    }

    private Map<String, String> parseArguments(String argumentsJson) {
        String value = argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson;
        if (value.length() > 8000) throw new IllegalArgumentException("命令参数过长");
        try {
            JsonNode root = objectMapper.readTree(value);
            if (!root.isObject()) throw new IllegalArgumentException("命令参数必须是 JSON 对象");
            Map<String, String> result = new LinkedHashMap<>();
            root.fields().forEachRemaining(entry -> {
                JsonNode node = entry.getValue();
                if (!node.isValueNode() || node.isNull()) throw new IllegalArgumentException("命令参数必须是标量值：" + entry.getKey());
                result.put(entry.getKey(), node.asText());
            });
            return result;
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("命令参数必须是合法的 JSON 对象", exception);
        }
    }

    private String validateAndQuote(ServerCommandParameterDefinition definition, String raw) {
        if (definition == null) throw new IllegalArgumentException("命令参数规则不存在");
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("命令参数不能为空：" + definition.name());
        if (raw.length() > definition.maxLength() || raw.indexOf('\0') >= 0 || raw.contains("\n") || raw.contains("\r")) {
            throw new IllegalArgumentException("命令参数长度或字符不合法：" + definition.name());
        }
        String value = switch (definition.type()) {
            case ENUM -> validateEnum(definition, raw);
            case PATH -> validatePath(definition, raw);
            case INTEGER -> validateInteger(definition, raw);
            case TEXT -> validateText(definition, raw);
        };
        return shellQuote(value);
    }

    private String validateEnum(ServerCommandParameterDefinition definition, String raw) {
        if (!definition.allowedValues().contains(raw)) throw new IllegalArgumentException("参数不在允许值中：" + definition.name());
        return raw;
    }

    private String validatePath(ServerCommandParameterDefinition definition, String raw) {
        if (!raw.startsWith("/") || raw.contains("//") || raw.endsWith("/.") || raw.contains("/./")
                || raw.endsWith("/..") || raw.contains("/../")) {
            throw new IllegalArgumentException("路径参数必须是无越界片段的绝对路径：" + definition.name());
        }
        boolean allowed = definition.allowedRoots().stream()
                .anyMatch(root -> raw.equals(root) || raw.startsWith(root.endsWith("/") ? root : root + "/"));
        if (!allowed) throw new IllegalArgumentException("路径超出允许目录：" + definition.name());
        return raw;
    }

    private String validateInteger(ServerCommandParameterDefinition definition, String raw) {
        try {
            long value = Long.parseLong(raw);
            if (definition.minValue() != null && value < definition.minValue()) throw new IllegalArgumentException("整数参数过小：" + definition.name());
            if (definition.maxValue() != null && value > definition.maxValue()) throw new IllegalArgumentException("整数参数过大：" + definition.name());
            return Long.toString(value);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("参数必须是整数：" + definition.name());
        }
    }

    private String validateText(ServerCommandParameterDefinition definition, String raw) {
        if (definition.pattern() != null && !Pattern.matches(definition.pattern(), raw)) {
            throw new IllegalArgumentException("文本参数不符合规则：" + definition.name());
        }
        return raw;
    }

    private String sampleValue(ServerCommandParameterDefinition definition) {
        return switch (definition.type()) {
            case ENUM -> definition.allowedValues().get(0);
            case PATH -> definition.allowedRoots().get(0);
            case INTEGER -> Long.toString(definition.minValue() == null ? 0 : definition.minValue());
            case TEXT -> "sample";
        };
    }

    private String shellQuote(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private void validateAllowedRoot(String name, String root) {
        if (!root.startsWith("/") || root.length() > 500 || root.contains("//") || root.contains("/../")
                || root.endsWith("/..") || root.contains("/./") || root.endsWith("/.")) {
            throw new IllegalArgumentException("allowedRoots 必须是规范的绝对路径：" + name);
        }
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) return List.of();
        return values.stream().map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}

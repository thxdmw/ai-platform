#!/bin/bash

# 生产部署采用“先构建候选镜像，再切换并健康检查”的流程；失败时恢复上一镜像。
set -Eeuo pipefail

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

info() { echo -e "${BLUE}[INFO]${NC} $1"; }
success() { echo -e "${GREEN}[SUCCESS]${NC} $1"; }
warn() { echo -e "${YELLOW}[WARN]${NC} $1"; }
error() { echo -e "${RED}[ERROR]${NC} $1"; }

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "${SCRIPT_DIR}"

if [[ "${1:-}" = "compose" ]]; then
    info "使用 Docker Compose 部署。"
    COMPOSE_DOCKER_CLI_BUILD=1 DOCKER_BUILDKIT=1 docker compose up -d --build
    success "Compose 部署完成。"
    exit 0
fi

# .env 放在 Git 仓库外，更新代码时不会覆盖生产配置。
ENV_FILE="${SCRIPT_DIR}/../.env"
if [[ ! -f "${ENV_FILE}" && -f "${SCRIPT_DIR}/.env" ]]; then
    warn "未找到 ${ENV_FILE}，回退使用仓库内 .env。"
    ENV_FILE="${SCRIPT_DIR}/.env"
fi
if [[ ! -f "${ENV_FILE}" ]]; then
    error "未找到生产配置。请把 .env.example 复制为 /app/ai-platform/.env 并填写真实密钥。"
    exit 1
fi

APP_NAME="ai-platform"
CONTAINER_NAME="${APP_NAME}-container"
STABLE_IMAGE="${APP_NAME}:latest"
RELEASE_TAG="${DEPLOY_RELEASE_TAG:-$(git rev-parse --short=12 HEAD 2>/dev/null || date +%Y%m%d%H%M%S)}"

if [[ ! "${RELEASE_TAG}" =~ ^[A-Za-z0-9_.-]+$ ]]; then
    error "DEPLOY_RELEASE_TAG 只能包含字母、数字、点、下划线和短横线。"
    exit 1
fi

CANDIDATE_IMAGE="${APP_NAME}:${RELEASE_TAG}"
HEALTH_RETRIES="${DEPLOY_HEALTH_RETRIES:-45}"
HEALTH_INTERVAL_SECONDS="${DEPLOY_HEALTH_INTERVAL_SECONDS:-2}"
RELEASES_TO_KEEP="${DEPLOY_RELEASES_TO_KEEP:-4}"

for value in "${HEALTH_RETRIES}" "${HEALTH_INTERVAL_SECONDS}" "${RELEASES_TO_KEEP}"; do
    if [[ ! "${value}" =~ ^[1-9][0-9]*$ ]]; then
        error "健康检查次数、间隔和镜像保留数必须是正整数。"
        exit 1
    fi
done

# 只读取端口，不 source 含密钥的文件，避免特殊字符被 Shell 二次解释。
PORT="$(sed -n 's/^[[:space:]]*SERVER_PORT=//p' "${ENV_FILE}" | tail -n 1 | tr -d '\r')"
PORT="${PORT:-20005}"
if [[ ! "${PORT}" =~ ^[0-9]+$ ]] || ((PORT < 1 || PORT > 65535)); then
    error "SERVER_PORT 必须是 1 到 65535 的数字，当前值：${PORT}"
    exit 1
fi
HEALTH_URL="http://127.0.0.1:${PORT}/actuator/health"

export DOCKER_BUILDKIT=1

OLD_IMAGE="$(docker inspect --format '{{.Image}}' "${CONTAINER_NAME}" 2>/dev/null || true)"
if [[ -z "${OLD_IMAGE}" ]]; then
    OLD_IMAGE="$(docker image inspect --format '{{.Id}}' "${STABLE_IMAGE}" 2>/dev/null || true)"
fi

run_container() {
    local image="$1"
    docker run -d \
      --name "${CONTAINER_NAME}" \
      --network host \
      --env-file "${ENV_FILE}" \
      -e SPRING_PROFILES_ACTIVE=prod \
      --restart unless-stopped \
      "${image}"
}

healthy() {
    local attempt
    for ((attempt=1; attempt<=HEALTH_RETRIES; attempt++)); do
        if ! docker inspect --format '{{.State.Running}}' "${CONTAINER_NAME}" 2>/dev/null | grep -q true; then
            error "容器已经退出。"
            return 1
        fi
        if curl --fail --silent --max-time 3 "${HEALTH_URL}" >/dev/null; then
            return 0
        fi
        info "等待应用就绪（${attempt}/${HEALTH_RETRIES}）..."
        sleep "${HEALTH_INTERVAL_SECONDS}"
    done
    return 1
}

rollback() {
    warn "候选版本未通过健康检查，开始恢复上一镜像。"
    docker rm -f "${CONTAINER_NAME}" >/dev/null 2>&1 || true
    if [[ -z "${OLD_IMAGE}" ]]; then
        error "首次部署没有可回滚镜像，请检查候选容器日志。"
        return 1
    fi
    if run_container "${OLD_IMAGE}" >/dev/null && healthy; then
        success "已恢复上一版本 ${OLD_IMAGE}。"
        return 0
    fi
    error "上一版本也未能恢复，请立即检查 Docker 和应用日志。"
    return 1
}

cleanup_release_images() {
    local index image
    local -a releases=()
    while IFS= read -r image; do
        [[ -z "${image}" || "${image}" == "${STABLE_IMAGE}" ]] && continue
        releases+=("${image}")
    done < <(docker image ls --filter "reference=${APP_NAME}:*" --format '{{.Repository}}:{{.Tag}}')

    if ((${#releases[@]} <= RELEASES_TO_KEEP)); then
        return 0
    fi
    for ((index=RELEASES_TO_KEEP; index<${#releases[@]}; index++)); do
        docker image rm "${releases[index]}" >/dev/null || warn "旧镜像 ${releases[index]} 清理失败。"
    done
}

info "构建候选镜像 ${CANDIDATE_IMAGE}；当前服务继续运行。"
docker build -t "${CANDIDATE_IMAGE}" .

if docker inspect "${CONTAINER_NAME}" >/dev/null 2>&1; then
    docker stop "${CONTAINER_NAME}" >/dev/null
    docker rm "${CONTAINER_NAME}" >/dev/null
fi

if ! run_container "${CANDIDATE_IMAGE}" >/dev/null; then
    error "候选容器启动失败。"
    rollback || true
    exit 1
fi

if ! healthy; then
    error "健康检查未通过，输出候选容器最后 120 行日志。"
    docker logs --tail 120 "${CONTAINER_NAME}" || true
    rollback || true
    exit 1
fi

docker tag "${CANDIDATE_IMAGE}" "${STABLE_IMAGE}"
success "${CANDIDATE_IMAGE} 已通过健康检查。"
cleanup_release_images || warn "旧镜像清理未完成，不影响当前版本。"
info "应用地址：http://127.0.0.1:${PORT}"
info "生产域名：https://ai.thxdxw.cn"

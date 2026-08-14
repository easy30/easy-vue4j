#!/usr/bin/env bash
# ============================================================================
# easy-vue4j 发布到 Maven Central
# 发布坐标：io.github.easy30:easy-vue4j  (central.sonatype.com 命名空间 io.github.easy30)
# 前置条件：
#   1) ~/.m2/settings.xml 里已配置 <server id="central"> 的 User Token（非网页登录密码）
#   2) 本机已有 gpg 签名私钥，且公钥已上传 keyserver（见 ai-wiki《Maven-Central发布-easy-vue4j》）
#   3) 已确认版本号（非 SNAPSHOT），改好 easy-vue4j/easy-vue4j/pom.xml 的 <version>
# 用法：
#   ./deploy.sh            # 发布到 Maven Central
#   ALL_PROXY=... ./deploy.sh   # 网络慢时可先设 socks5h 代理
# ============================================================================
set -euo pipefail

# 本脚本所在目录即项目根（发布模块在嵌套同名目录 easy-vue4j 下）
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
MODULE_DIR="$ROOT/easy-vue4j"

# 编译工具：优先 mvnd（快），回退 mvn
MVND_HOME=/Users/apple/app/maven-mvnd-1.0.6-darwin-amd64
if [ -x "$MVND_HOME/bin/mvnd" ]; then
    MVN="$MVND_HOME/bin/mvnd"
else
    MVN="mvn"
fi

# gpg 签名依赖 /usr/local/bin（gpg 2.5）
export PATH="/usr/local/bin:$PATH"

echo "=== 发布 easy-vue4j 到 Maven Central ==="
echo "模块目录: $MODULE_DIR"
echo "Maven:    $MVN"

cd "$MODULE_DIR"
exec "$MVN" -B deploy -Pcentral -DskipTests "$@"

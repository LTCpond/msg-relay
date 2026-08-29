# Msg Relay 企业 IM 系统 - Docker中间件一键启动脚本

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Msg Relay 企业 IM 系统 - 中间件启动" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# 检查 Docker 是否运行
$dockerRunning = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "[ERROR] Docker 未运行，请先启动 Docker Desktop" -ForegroundColor Red
    exit 1
}

Write-Host "[1/5] 停止旧容器（保留数据卷）..." -ForegroundColor Yellow
docker compose down 2>$null

Write-Host "[2/5] 拉取镜像..." -ForegroundColor Yellow
docker compose pull

Write-Host "[3/5] 启动服务..." -ForegroundColor Yellow
docker compose up -d

Write-Host "[4/5] 等待服务就绪..." -ForegroundColor Yellow

# 等待各服务健康检查通过
$services = @(
    @{Name="MySQL"; Container="msg-relay-mysql"},
    @{Name="Redis"; Container="msg-relay-redis"},
    @{Name="RocketMQ NameServer"; Container="msg-relay-rocketmq-namesrv"},
    @{Name="Elasticsearch"; Container="msg-relay-elasticsearch"}
)

foreach ($svc in $services) {
    Write-Host "  等待 $($svc.Name) ..." -NoNewline
    $ready = $false
    for ($i = 0; $i -lt 30; $i++) {
        $state = docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' $svc.Container 2>$null
        if ($state -eq "healthy" -or $state -eq "running") {
            $ready = $true
            break
        }
        Start-Sleep -Seconds 2
        Write-Host "." -NoNewline
    }
    if ($ready) {
        Write-Host " OK" -ForegroundColor Green
    } else {
        Write-Host " TIMEOUT" -ForegroundColor Yellow
    }
}

Write-Host "  等待 RocketMQ Broker ..." -NoNewline
for ($i = 0; $i -lt 15; $i++) {
    $running = docker ps --filter "name=msg-relay-rocketmq-broker" --format "{{.Status}}" 2>$null
    if ($running -match "Up") {
        Write-Host " OK" -ForegroundColor Green
        break
    }
    Start-Sleep -Seconds 3
    Write-Host "." -NoNewline
}
Write-Host ""
Write-Host "[5/5] 服务状态:" -ForegroundColor Yellow
docker compose ps

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  中间件启动完成!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  MySQL:           localhost:3307  (root，密码读取 .env 中的 DB_PASSWORD)" -ForegroundColor White
Write-Host "  Redis:           localhost:6379" -ForegroundColor White
Write-Host "  RocketMQ NS:     localhost:9876" -ForegroundColor White
Write-Host "  RocketMQ Broker: localhost:10911" -ForegroundColor White
Write-Host "  Elasticsearch:   http://localhost:9200" -ForegroundColor White
Write-Host ""
Write-Host "  启动应用: cd msg-relay && mvn spring-boot:run -pl msg-relay-starter -am" -ForegroundColor White
Write-Host "  停止中间件: docker compose down" -ForegroundColor White

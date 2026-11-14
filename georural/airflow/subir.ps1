
$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

function Ok($m)    { Write-Host "  ok    $m" -ForegroundColor Green }
function Aviso($m) { Write-Host "  aviso $m" -ForegroundColor Yellow }
function Erro($m)  { Write-Host "  erro  $m" -ForegroundColor Red }

Write-Host ""
Write-Host "Airflow do GeoRural"
Write-Host "==================="
Write-Host ""

# ---------- 1. docker ----------
if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Erro "Docker nao encontrado. Instale o Docker Desktop e rode de novo."
    exit 1
}

docker info *> $null
if ($LASTEXITCODE -ne 0) {
    Erro "O Docker esta instalado mas nao esta rodando. Abra o Docker Desktop."
    exit 1
}
Ok "Docker respondendo"

# ---------- 2. .env ----------
if (-not (Test-Path ".env")) {
    Write-Host ""
    Write-Host "Primeira vez: gerando o .env"
    Copy-Item ".env.exemplo" ".env"

    $versao = "3.1.2"
    $fernet = (docker run --rm "apache/airflow:$versao" python -c "from cryptography.fernet import Fernet; print(Fernet.generate_key().decode())").Trim()
    if (-not $fernet) {
        Erro "nao consegui gerar a chave Fernet (a imagem do Airflow baixou?)"
        exit 1
    }

    $bytes = New-Object byte[] 32
    [System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
    $jwt = [Convert]::ToBase64String($bytes)

    $conteudo = Get-Content ".env"
    $conteudo = $conteudo -replace '^AIRFLOW_FERNET_KEY=.*', "AIRFLOW_FERNET_KEY=$fernet"
    $conteudo = $conteudo -replace '^AIRFLOW_JWT_SECRET=.*', "AIRFLOW_JWT_SECRET=$jwt"
    # Sem BOM: o docker compose nao le .env com BOM.
    [System.IO.File]::WriteAllLines((Resolve-Path ".env"), $conteudo, (New-Object System.Text.UTF8Encoding $false))

    Ok ".env criado com as chaves"
    Aviso "o .env nao vai para o git; guarde-o se quiser manter as Connections"
} else {
    Ok ".env ja existe"
}

# ---------- 3. sobe ----------
$porta = (Select-String -Path ".env" -Pattern '^AIRFLOW_PORTA=(.*)$').Matches.Groups[1].Value
if (-not $porta) { $porta = "8081" }

Write-Host ""
Write-Host "Subindo (a primeira vez baixa as imagens, pode levar alguns minutos)..."
docker compose up -d
if ($LASTEXITCODE -ne 0) { Erro "o docker compose falhou; veja a mensagem acima"; exit 1 }

Write-Host ""
Write-Host "Esperando a interface responder..."
for ($i = 0; $i -lt 60; $i++) {
    try {
        Invoke-WebRequest -UseBasicParsing -Uri "http://localhost:$porta/api/v2/monitor/health" -TimeoutSec 3 *> $null
        Ok "interface no ar"
        break
    } catch { Start-Sleep -Seconds 3 }
}

$senha = (Get-Content "config/simple_auth_manager_passwords.json" | ConvertFrom-Json).admin

Write-Host ""
Write-Host "==================================================================="
Write-Host "  Interface:  http://localhost:$porta"
Write-Host "  Usuario:    admin"
Write-Host "  Senha:      $senha"
Write-Host ""
Write-Host "  As DAGs nascem PAUSADAS. Ligue na interface quando quiser que"
Write-Host "  comecem a varrer."
Write-Host ""
Write-Host "  Antes disso, confira se o Airflow alcanca a API do Spring:"
Write-Host "      docker compose exec scheduler python /opt/airflow/verificar.py"
Write-Host ""
Write-Host "  Logs:       docker compose logs -f"
Write-Host "  Derrubar:   docker compose down"
Write-Host "==================================================================="
Write-Host ""

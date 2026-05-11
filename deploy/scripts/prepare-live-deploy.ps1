param(
    [Parameter(Mandatory = $true)]
    [string]$BackendHost,

    [Parameter(Mandatory = $true)]
    [string]$FrontendOrigin,

    [Parameter(Mandatory = $true)]
    [string]$OperatorEmail,

    [string]$Image = "ghcr.io/hoeongj/ssuai-backend:latest",

    [string]$SourceDir = "deploy/k8s",

    [string]$OutputDir = "deploy/generated/k8s"
)

$ErrorActionPreference = "Stop"

function Require-NoTrailingSlash {
    param(
        [string]$Name,
        [string]$Value
    )

    if ($Value.EndsWith("/")) {
        throw "$Name must not end with '/': $Value"
    }
}

function Require-HostOnly {
    param([string]$CheckHost)

    if ($CheckHost.StartsWith("http://") -or $CheckHost.StartsWith("https://") -or $CheckHost.Contains("/")) {
        throw "BackendHost must be a host only, for example 'ssumcp.duckdns.org'. Received: $CheckHost"
    }
}

Require-HostOnly -CheckHost $BackendHost
function Replace-FileText {
    param(
        [string]$Path,
        [hashtable]$Replacements
    )

    $content = Get-Content -Raw -Encoding UTF8 $Path
    foreach ($key in $Replacements.Keys) {
        $content = $content.Replace($key, [string]$Replacements[$key])
    }
    Set-Content -Encoding UTF8 -NoNewline -Path $Path -Value $content
}

Require-NoTrailingSlash -Name "FrontendOrigin" -Value $FrontendOrigin

if (-not (Test-Path $SourceDir)) {
    throw "SourceDir not found: $SourceDir"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
Copy-Item -Force -Path (Join-Path $SourceDir "*.yaml") -Destination $OutputDir

Replace-FileText -Path (Join-Path $OutputDir "clusterissuer.yaml") -Replacements @{
    "REPLACE_WITH_OPERATOR_EMAIL@example.com" = $OperatorEmail
}

Replace-FileText -Path (Join-Path $OutputDir "ingress.yaml") -Replacements @{
    "ssuai-api.duckdns.org" = $BackendHost
    "ssuai-api-tls" = (($BackendHost -replace "[^A-Za-z0-9-]", "-") + "-tls")
}

Replace-FileText -Path (Join-Path $OutputDir "configmap.yaml") -Replacements @{
    "https://ssuai.vercel.app" = $FrontendOrigin
}

Replace-FileText -Path (Join-Path $OutputDir "deployment.yaml") -Replacements @{
    "ghcr.io/hoeongj/ssuai-backend:latest" = $Image
}

Write-Host "Generated deploy manifests:"
Write-Host "- $OutputDir"
Write-Host ""
Write-Host "Backend MCP endpoint:"
Write-Host "https://$BackendHost/sse"
Write-Host ""
Write-Host "Next step after kubectl is connected:"
Write-Host "powershell -ExecutionPolicy Bypass -File deploy/scripts/apply-live-deploy.ps1 -ManifestDir $OutputDir"

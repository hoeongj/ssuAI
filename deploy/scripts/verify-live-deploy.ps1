param(
    [Parameter(Mandatory = $true)]
    [string]$BackendHost,

    [string]$FrontendOrigin = ""
)

$ErrorActionPreference = "Stop"

if ($BackendHost.StartsWith("http://") -or $BackendHost.StartsWith("https://") -or $BackendHost.Contains("/")) {
    throw "BackendHost must be a host only, for example 'ssuai-api.duckdns.org'. Received: $BackendHost"
}

$baseUrl = "https://$BackendHost"

Write-Host "Checking backend health..."
curl.exe -i "$baseUrl/actuator/health"

Write-Host ""
Write-Host "Checking REST API..."
curl.exe "$baseUrl/api/meals/today"

if ($FrontendOrigin) {
    Write-Host ""
    Write-Host "Checking CORS allowlist for frontend origin..."
    curl.exe -I -H "Origin: $FrontendOrigin" "$baseUrl/api/meals/today"
}

Write-Host ""
Write-Host "Checking MCP SSE headers. Press Ctrl+C if the connection stays open after headers."
curl.exe -N -D - "$baseUrl/sse"

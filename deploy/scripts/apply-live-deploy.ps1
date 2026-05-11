param(
    [string]$ManifestDir = "deploy/generated/k8s"
)

$ErrorActionPreference = "Stop"

if (-not (Get-Command kubectl -ErrorAction SilentlyContinue)) {
    throw "kubectl was not found in PATH."
}

if (-not (Test-Path $ManifestDir)) {
    throw "ManifestDir not found: $ManifestDir. Run prepare-live-deploy.ps1 first."
}

$files = @(
    "clusterissuer.yaml",
    "namespace.yaml",
    "configmap.yaml",
    "service.yaml",
    "deployment.yaml",
    "ingress.yaml"
)

foreach ($file in $files) {
    $path = Join-Path $ManifestDir $file
    if (-not (Test-Path $path)) {
        throw "Required manifest missing: $path"
    }
}

foreach ($file in $files) {
    $path = Join-Path $ManifestDir $file
    Write-Host "kubectl apply -f $path"
    kubectl apply -f $path
}

Write-Host ""
Write-Host "Waiting for backend rollout..."
kubectl -n ssuai-prod rollout status deploy/ssuai-backend

Write-Host ""
Write-Host "Current resources:"
kubectl -n ssuai-prod get pods,svc,ingress
kubectl get certificate -A

# Loads ../.env into this PowerShell session, then runs the given Maven goals.
# Usage: .\run-local.ps1 spring-boot:run      or      .\run-local.ps1 test
$envFile = Join-Path $PSScriptRoot "..\.env"
Get-Content $envFile | Where-Object { $_ -match '^\s*[^#\s][^=]*=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$($name.Trim())" -Value $value.Trim()
}
& "$PSScriptRoot\mvnw.cmd" @args

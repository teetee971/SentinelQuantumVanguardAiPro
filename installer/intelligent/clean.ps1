Write-Host 'Cleaning build folders...'; if (Test-Path 'dist') { Remove-Item dist -Recurse -Force }; if (Test-Path 'build') { Remove-Item build -Recurse -Force }; Write-Host 'Cleanup done.'

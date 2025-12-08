Write-Host 'Cleaning log files...'; Get-ChildItem -Recurse -Filter *.log | Remove-Item -Force; Write-Host 'Log cleanup done.'

Write-Host 'Building Web project...'; if (Test-Path 'package.json') { npm install; npm run build } else { Write-Host 'No web project detected.' }

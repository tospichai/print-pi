$WshShell = New-Object -comObject WScript.Shell
$DesktopPath = [Environment]::GetFolderPath("Desktop")
$ShortcutPath = Join-Path $DesktopPath "Mae Pra Nam Printer.lnk"
$TargetFile = Join-Path $PSScriptRoot "run_printer.bat"
$IconFile = Join-Path $PSScriptRoot "printer_icon.ico" 

$Shortcut = $WshShell.CreateShortcut($ShortcutPath)
$Shortcut.TargetPath = $TargetFile
$Shortcut.WorkingDirectory = $PSScriptRoot
$Shortcut.WindowStyle = 1
$Shortcut.Description = "เปิดระบบเครื่องปริ้นแม่พระฌาม"

# If you have a .ico file, uncomment the next line
# $Shortcut.IconLocation = $IconFile

$Shortcut.Save()

Write-Host "สร้างทางลัด (Shortcut) บนหน้าจอเรียบร้อยแล้ว: $ShortcutPath" -ForegroundColor Green
Write-Host "คุณสามารถดับเบิ้ลคลิกที่ไอคอนบนหน้าจอเพื่อเริ่มใช้งานได้ทันที"
Start-Sleep -Seconds 5

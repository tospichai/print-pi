@echo off
chcp 65001 > nul
title Mae Pra Nam Printer Service (แม่พระฌาม ปริ้นเตอร์)
color 0A
cls

echo ========================================================
echo               MAE PRA NAM PRINTER SERVICE
echo             ระบบบริการเครื่องปริ้นแม่พระฌาม
echo ========================================================
echo.
echo [สถานะ] กำลังตรวจสอบสภาพแวดล้อม...

if not exist ".env" (
    echo [แจ้งเตือน] ไม่พบไฟล์ .env! กำลังคัดลอกไฟล์ตั้งต้นให้...
    copy env-example .env
    echo [ข้อมูล] กรุณาแก้ไขไฟล์ .env เพื่อใส่ข้อมูลการเชื่อมต่อ
    pause
    exit
)

if not exist "node_modules" (
    echo [ข้อมูล] กำลังติดตั้งโปรแกรมที่จำเป็น (Dependencies)...
    call npm install
)

echo.
echo [สถานะ] กำลังเริ่มระบบเครื่องปริ้น...
echo [ข้อมูล] ห้ามปิดหน้าต่างนี้ เพื่อให้เครื่องปริ้นทำงานได้ตลอด
echo [ข้อมูล] หากต้องการปิดโปรแกรม ให้กดปิดหน้าต่างนี้ได้เลย
echo.
echo ========================================================
echo.

:run
node index.js
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo [ข้อผิดพลาด] โปรแกรมหยุดทำงานด้วยรหัส %ERRORLEVEL%
    echo [ข้อมูล] กำลังเริ่มใหม่ใน 5 วินาที...
    timeout /t 5
    goto run
)

pause

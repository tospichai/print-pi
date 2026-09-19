# Mae Pra Nam Android Network Printer

แอป Android สำหรับรับ Pusher event เดิมจาก channel `orders` / event `print` แล้วพิมพ์รูปใบเสร็จจาก `fullPath` ไปยังเครื่องพิมพ์ ESC/POS ผ่าน TCP/IP

## ข้อกำหนด

- โทรศัพท์หรือแท็บเล็ต Android 8.0 ขึ้นไป
- Android และเครื่องพิมพ์อยู่ใน LAN/Wi-Fi เดียวกัน
- เครื่องพิมพ์เปิดรับ raw TCP โดยปกติใช้ port `9100`
- สำหรับ build ต้องมี JDK 17 และ Android SDK 36
- APK รอบนี้เป็น internal debug build สำหรับทดสอบหน้างาน ไม่ใช่ Play Store release

## Build APK

จากโฟลเดอร์ `android-printer`:

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk
./gradlew clean testDebugUnitTest lintDebug assembleDebug
```

ไฟล์ที่ได้อยู่ที่:

```text
app/build/outputs/apk/debug/app-debug.apk
```

ติดตั้งผ่าน ADB:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

หากติดตั้งด้วยตนเอง ให้คัดลอก APK ไปยังเครื่อง Android เปิดไฟล์ แล้วอนุญาต “Install unknown apps” ให้แอปที่ใช้เปิดไฟล์ เมื่อทดสอบเสร็จสามารถปิดสิทธิ์นี้ได้

## ตั้งค่า

1. เปิด `.env` ของ `print-pi` เดิม
2. คัดลอก `PUSHER_APP_KEY` ไปที่ช่อง **Pusher App Key**
3. คัดลอก `PUSHER_APP_CLUSTER` ไปที่ช่อง **Pusher Cluster** เช่น `ap1`
4. ใส่ IP ของเครื่องพิมพ์ เช่น `192.168.1.50`
5. ใส่ port ปกติคือ `9100`
6. กด **บันทึกและเริ่มรับงาน** แล้วอนุญาต notification
7. รอจนสถานะเป็น **พร้อมรับงาน**

ห้ามนำ `PUSHER_APP_SECRET` มาใส่ในแอปหรือฝังใน APK แอปใช้เฉพาะ client app key เท่านั้น

## Checklist ทดสอบหน้างาน

- [ ] ติดตั้งและเปิดแอปได้บน Android 8.0 ขึ้นไป
- [ ] กรอก Pusher key/cluster และ IP/port แล้วบันทึกได้
- [ ] อนุญาต notification และเห็น “Mae Pra Nam Printer” ใน notification drawer
- [ ] สถานะเปลี่ยนจาก “กำลังเชื่อมต่อ” เป็น “พร้อมรับงาน”
- [ ] กด **ทดสอบพิมพ์** แล้วได้กระดาษหัวข้อ `MAE PRA NAM`
- [ ] สร้างใบเสร็จจริงให้ระบบส่ง Pusher event แล้วภาพใบเสร็จถูกพิมพ์
- [ ] ส่งสามงานติดกันและกระดาษออกตามลำดับโดยข้อมูลไม่ซ้อนกัน
- [ ] ปิด Wi-Fi แล้วเปิดใหม่ จากนั้นสถานะกลับมา “พร้อมรับงาน”
- [ ] ทดลอง IP ที่ติดต่อไม่ได้และเห็น error หลัง retry โดยแอปไม่ปิดตัว
- [ ] ย่อแอป/ดับหน้าจอขณะ foreground service ทำงาน แล้วงานใหม่ยังถูกพิมพ์
- [ ] ภาพอยู่กึ่งกลาง อ่านชัด และไม่ถูกตัดด้านข้าง
- [ ] ระยะ feed หลังภาพเพียงพอ
- [ ] คำสั่งตัดกระดาษทำงานกับเครื่องพิมพ์รุ่นจริง

## ข้อจำกัดของ MVP

- ถ้า Force stop แอป, restart Android หรือกดหยุดรับงาน งานในคิวและ event ที่ส่งมาตอนหยุดจะไม่ถูกเรียกคืน
- แอปถือว่าส่งสำเร็จเมื่อเขียนข้อมูลลง TCP socket สำเร็จ แต่ยืนยันไม่ได้ว่ากระดาษออกจริง
- ยังไม่มี auto-start หลัง reboot, durable server queue, print acknowledgement หรือ kiosk mode
- รองรับภาพกว้างสูงสุด 576 dots; หากเครื่องพิมพ์รุ่นจริงใช้ความกว้างอื่นต้องปรับหลังทดสอบใบแรก

## แก้ปัญหาเบื้องต้น

| อาการ | ตรวจสอบ |
| --- | --- |
| Pusher ไม่ขึ้นพร้อมรับงาน | ตรวจ `PUSHER_APP_KEY`, cluster, อินเทอร์เน็ต และเวลาเครื่อง Android |
| ต่อเครื่องพิมพ์ไม่ได้ | ตรวจว่า Android กับ printer อยู่ LAN เดียวกัน, IP ถูกต้อง และไม่มี guest Wi-Fi isolation |
| Connection refused/timeout | ตรวจ port `9100`, เปิดเครื่องพิมพ์ และ ping/ทดสอบ TCP จากเครือข่ายเดียวกัน |
| ดาวน์โหลดรูปไม่ได้ | เปิด URL `fullPath` จาก browser ของ Android และตรวจว่า HTTP/HTTPS เข้าถึงได้ |
| รูปจางหรืออ่านยาก | ตรวจกระดาษ/หัวพิมพ์ก่อน; หากยังจางให้ปรับ threshold ใน `EscPosEncoder` |
| รูปถูกตัดหรือกว้างไม่พอดี | ยืนยันความกว้างจริงของรุ่นเครื่องพิมพ์ว่าเป็น 576 dots หรือไม่ |
| ไม่ตัดกระดาษ | เครื่องบางรุ่นใช้ cutter command ต่างกันหรือไม่มี auto-cutter |

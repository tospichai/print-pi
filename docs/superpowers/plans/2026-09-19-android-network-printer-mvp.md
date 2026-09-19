# Android Network Printer MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an installable Android application that receives the existing Pusher `orders` / `print` event and prints its `fullPath` receipt image to an ESC/POS network printer over TCP port 9100.

**Architecture:** A single Android activity stores settings and controls a foreground service. The service owns one Pusher connection and feeds a bounded FIFO queue whose single worker downloads, rasterizes, and sends each receipt to the printer. Pure Kotlin boundaries isolate payload parsing, validation, retry policy, queueing, and ESC/POS encoding for local unit tests; Android adapters handle storage, bitmap decoding, notifications, and service lifecycle.

**Tech Stack:** Kotlin with AGP 9.4.0, Gradle 9.6.0, JDK 17, Android SDK 36, XML layouts, Pusher Java Client 2.4.4, Gson 2.13.2, JUnit 4.13.2

**Spec:** `docs/superpowers/specs/2026-09-19-android-network-printer-mvp-design.md`

## Global Constraints

- The application package is `com.maepranam.networkprinter` and the Android project lives at `android-printer/` in the `print-pi` repository.
- `minSdk` is 26, `targetSdk` and `compileSdk` are 36, and the build uses JDK 17.
- The Pusher subscription contract remains public channel `orders`, event `print`, JSON field `fullPath`.
- Printer transport is raw TCP to the user-configured IPv4/hostname and port, default `9100`.
- Print width is fixed at 576 dots for the MVP.
- The in-memory queue holds at most 50 jobs and processes exactly one job at a time.
- Downloads and printer connections each make at most three attempts and use finite timeouts.
- No Laravel, Node receipt service, reboot receiver, kiosk mode, durable queue, or server acknowledgement changes are included.
- The Pusher application secret is never collected or bundled; only the publishable app key and cluster are stored.

## File Structure

- `android-printer/settings.gradle.kts` — plugin repositories and module inclusion.
- `android-printer/build.gradle.kts` — AGP plugin version.
- `android-printer/gradle.properties` — deterministic Gradle and Android defaults.
- `android-printer/app/build.gradle.kts` — Android SDK levels and dependencies.
- `android-printer/app/src/main/AndroidManifest.xml` — network, notification, and foreground-service declarations.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/MainActivity.kt` — one-screen configuration and status UI.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/PrinterService.kt` — foreground lifecycle, Pusher subscription, and worker ownership.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/config/PrinterConfig.kt` — immutable settings and validation.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/config/PrinterConfigStore.kt` — SharedPreferences adapter.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/job/PrintRequest.kt` — receipt/test request models and Pusher payload parser.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/job/SerialPrintQueue.kt` — bounded FIFO abstraction.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/job/PrintPipeline.kt` — one-job download/encode/send orchestration and cleanup.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/net/ReceiptDownloader.kt` — bounded HTTP(S) image download.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/net/NetworkPrinter.kt` — bounded TCP connection and byte delivery.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/RasterImage.kt` — framework-free image pixels.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/EscPosEncoder.kt` — pure ESC/POS raster byte generation.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/AndroidImageAdapter.kt` — decode, scale, and test-image generation.
- `android-printer/app/src/main/java/com/maepranam/networkprinter/runtime/PrinterRuntime.kt` — thread-safe status and bounded logs for UI polling.
- `android-printer/app/src/main/res/layout/activity_main.xml` — configuration controls, actions, status, and logs.
- `android-printer/app/src/main/res/values/strings.xml` — English and Thai UI copy.
- `android-printer/app/src/main/res/values/themes.xml` — dependency-free Material-like platform theme.
- `android-printer/app/src/test/...` — JVM unit tests for every pure Kotlin boundary.
- `android-printer/README.md` — build, install, configuration, and physical acceptance steps.

## Review Focus

- A Pusher event may be empty, malformed JSON, use a non-web scheme, or omit `fullPath`; parsing must reject it without stopping later jobs, pinned in Task 4.
- A very large or extremely wide image can exhaust memory or exceed printer width; downloads must cap bytes and decoding must sample/scale to 576 dots, pinned in Task 5.
- Transparent pixels and widths not divisible by eight can corrupt raster rows; transparency must become white and rows must be byte-padded, pinned in Task 2.
- A full queue or a failed first job must not block subsequent jobs; offer must fail fast and the worker loop must continue, pinned in Tasks 4 and 6.
- A hostname that never connects or a socket that stalls must finish after exactly three bounded attempts and leave the service alive, pinned in Task 3.

---

### Task 1: Android Project and Validated Configuration

**Files:**
- Create: `android-printer/settings.gradle.kts`
- Create: `android-printer/build.gradle.kts`
- Create: `android-printer/gradle.properties`
- Create: `android-printer/app/build.gradle.kts`
- Create: `android-printer/app/src/main/AndroidManifest.xml`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/config/PrinterConfig.kt`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/config/PrinterConfigStore.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/config/PrinterConfigTest.kt`

**Interfaces:**
- Consumes: none.
- Produces: `PrinterConfig(appKey: String, cluster: String, printerHost: String, printerPort: Int)`, `PrinterConfig.validate(): List<String>`, `PrinterConfigStore.load(): PrinterConfig`, and `PrinterConfigStore.save(config: PrinterConfig)`.

- [ ] **Step 1: Create the build skeleton and Gradle wrapper**

Use AGP 9.4.0 with its built-in Kotlin support and these module dependencies:

```kotlin
// android-printer/settings.gradle.kts
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "MaePraNamNetworkPrinter"
include(":app")

// android-printer/build.gradle.kts
plugins { id("com.android.application") version "9.4.0" apply false }

// android-printer/app/build.gradle.kts
plugins { id("com.android.application") }

android {
    namespace = "com.maepranam.networkprinter"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.maepranam.networkprinter"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "android.app.InstrumentationTestRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("com.pusher:pusher-java-client:2.4.4")
    implementation("com.google.code.gson:gson:2.13.2")
    testImplementation("junit:junit:4.13.2")
}
```

Create `gradle.properties` with the following exact values, then generate wrapper version 9.6.0 with `gradle wrapper --gradle-version 9.6.0`.

```properties
org.gradle.jvmargs=-Xmx2g -Dfile.encoding=UTF-8
org.gradle.configuration-cache=true
android.useAndroidX=false
```

- [ ] **Step 2: Write failing configuration tests**

```kotlin
class PrinterConfigTest {
    @Test fun validConfigHasNoErrors() {
        assertTrue(PrinterConfig("key", "ap1", "192.168.1.50", 9100).validate().isEmpty())
    }

    @Test fun everyRequiredFieldAndPortAreValidated() {
        val errors = PrinterConfig(" ", "", " ", 70000).validate()
        assertEquals(listOf("Pusher App Key is required", "Pusher Cluster is required",
            "Printer IP or hostname is required", "Printer port must be between 1 and 65535"), errors)
    }
}
```

- [ ] **Step 3: Run the configuration tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*PrinterConfigTest'`

Expected: compilation fails because `PrinterConfig` does not exist.

- [ ] **Step 4: Implement configuration and storage**

```kotlin
data class PrinterConfig(
    val appKey: String = "",
    val cluster: String = "",
    val printerHost: String = "",
    val printerPort: Int = 9100,
) {
    fun validate(): List<String> = buildList {
        if (appKey.isBlank()) add("Pusher App Key is required")
        if (cluster.isBlank()) add("Pusher Cluster is required")
        if (printerHost.isBlank()) add("Printer IP or hostname is required")
        if (printerPort !in 1..65535) add("Printer port must be between 1 and 65535")
    }
}
```

`PrinterConfigStore` must use a private SharedPreferences file named `printer_config`, trim string values before saving, use `9100` when no port exists, and expose only `load` and `save`.

- [ ] **Step 5: Add the minimal manifest**

Declare `INTERNET`, `POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`, and `CHANGE_NETWORK_STATE`. Set `android:usesCleartextTraffic="true"` because the approved spec accepts HTTP receipt URLs. Declare `MainActivity` exported with the launcher intent and declare `PrinterService` non-exported with `android:foregroundServiceType="connectedDevice"`.

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
    <uses-permission android:name="android.permission.CHANGE_NETWORK_STATE" />
    <application android:label="Mae Pra Nam Printer" android:usesCleartextTraffic="true">
        <activity android:name=".MainActivity" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
        <service android:name=".PrinterService" android:exported="false"
            android:foregroundServiceType="connectedDevice" />
    </application>
</manifest>
```

- [ ] **Step 6: Run tests and a debug build**

Run: `cd android-printer && ./gradlew testDebugUnitTest assembleDebug`

Expected: configuration tests pass and `app/build/outputs/apk/debug/app-debug.apk` exists.

- [ ] **Step 7: Commit Task 1**

```bash
git add android-printer
git commit -m "feat: scaffold Android printer app"
```

### Task 2: Pure ESC/POS Raster Encoding

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/RasterImage.kt`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/EscPosEncoder.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/escpos/EscPosEncoderTest.kt`

**Interfaces:**
- Consumes: no earlier interfaces.
- Produces: `RasterImage(width: Int, height: Int, argb: IntArray)` and `EscPosEncoder.encode(image: RasterImage): ByteArray`.

- [ ] **Step 1: Write failing encoder tests**

```kotlin
class EscPosEncoderTest {
    private val encoder = EscPosEncoder(threshold = 160)

    @Test fun padsRowsWhoseWidthIsNotDivisibleByEight() {
        val black = 0xff000000.toInt()
        val bytes = encoder.encode(RasterImage(9, 1, IntArray(9) { black }))
        assertArrayEquals(byteArrayOf(0x1b, 0x40, 0x1b, 0x61, 0x01,
            0x1d, 0x76, 0x30, 0x00, 0x02, 0x00, 0x01, 0x00,
            0xff.toByte(), 0x80.toByte(), 0x0a, 0x0a, 0x0a, 0x0a, 0x1d, 0x56, 0x00), bytes)
    }

    @Test fun transparentPixelsPrintWhite() {
        val bytes = encoder.encode(RasterImage(8, 1, IntArray(8) { 0x00000000 }))
        assertEquals(0x00, bytes[13].toInt())
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsPixelCountThatDoesNotMatchDimensions() {
        RasterImage(2, 2, intArrayOf(0)).also(encoder::encode)
    }
}
```

- [ ] **Step 2: Run the encoder tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*EscPosEncoderTest'`

Expected: compilation fails because the raster types do not exist.

- [ ] **Step 3: Implement `RasterImage` and `EscPosEncoder`**

`RasterImage` validates positive dimensions and exact pixel count. `EscPosEncoder` must emit `ESC @`, center alignment `ESC a 1`, the `GS v 0` header, padded one-bit raster bytes in most-significant-bit-first order, four line feeds, and full cut `GS V 0`. Treat an alpha value below 128 as white. For opaque pixels use luminance `(red * 299 + green * 587 + blue * 114) / 1000` and print black when luminance is below `threshold`.

```kotlin
class EscPosEncoder(private val threshold: Int = 160) {
    fun encode(image: RasterImage): ByteArray {
        require(image.argb.size == image.width * image.height)
        val rowBytes = (image.width + 7) / 8
        val body = ByteArray(rowBytes * image.height)
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val color = image.argb[y * image.width + x]
            val alpha = color ushr 24 and 0xff
            val red = color ushr 16 and 0xff
            val green = color ushr 8 and 0xff
            val blue = color and 0xff
            val black = alpha >= 128 && (red * 299 + green * 587 + blue * 114) / 1000 < threshold
            if (black) body[y * rowBytes + x / 8] =
                (body[y * rowBytes + x / 8].toInt() or (0x80 ushr (x % 8))).toByte()
        }
        return byteArrayOf(0x1b, 0x40, 0x1b, 0x61, 0x01, 0x1d, 0x76, 0x30, 0x00,
            (rowBytes and 0xff).toByte(), (rowBytes ushr 8).toByte(),
            (image.height and 0xff).toByte(), (image.height ushr 8).toByte()) + body +
            byteArrayOf(0x0a, 0x0a, 0x0a, 0x0a, 0x1d, 0x56, 0x00)
    }
}
```

- [ ] **Step 4: Run focused and full unit tests**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*EscPosEncoderTest' && ./gradlew testDebugUnitTest`

Expected: all tests pass.

- [ ] **Step 5: Commit Task 2**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/escpos android-printer/app/src/test/java/com/maepranam/networkprinter/escpos
git commit -m "feat: encode receipt images as ESC POS raster data"
```

### Task 3: Retrying TCP Printer Transport

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/net/NetworkPrinter.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/net/NetworkPrinterTest.kt`

**Interfaces:**
- Consumes: `PrinterConfig.printerHost` and `PrinterConfig.printerPort`.
- Produces: `SocketConnection` interface, `SocketConnectionFactory`, and `NetworkPrinter.send(host: String, port: Int, bytes: ByteArray)`.

- [ ] **Step 1: Write failing transport tests using fake sockets**

```kotlin
class NetworkPrinterTest {
    @Test fun retriesTwiceThenSucceedsOnThirdAttempt() {
        val factory = FakeSocketFactory(failuresBeforeSuccess = 2)
        NetworkPrinter(factory, attempts = 3, retryDelayMs = 0).send("x", 9100, byteArrayOf(1, 2))
        assertEquals(3, factory.created)
        assertArrayEquals(byteArrayOf(1, 2), factory.bytes.toByteArray())
    }

    @Test fun stopsAfterExactlyThreeFailedAttempts() {
        val factory = FakeSocketFactory(failuresBeforeSuccess = Int.MAX_VALUE)
        assertThrows(IOException::class.java) {
            NetworkPrinter(factory, attempts = 3, retryDelayMs = 0).send("bad", 9100, byteArrayOf(1))
        }
        assertEquals(3, factory.created)
    }
}
```

The fake factory increments `created`, throws from `connect` for the requested number of failures, and records successful output bytes.

- [ ] **Step 2: Run the transport tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*NetworkPrinterTest'`

Expected: compilation fails because `NetworkPrinter` does not exist.

- [ ] **Step 3: Implement bounded socket delivery**

Define a small wrapper so tests never open a real socket:

```kotlin
interface SocketConnection : Closeable {
    fun connect(host: String, port: Int, timeoutMs: Int)
    fun write(bytes: ByteArray)
}
fun interface SocketConnectionFactory { fun create(): SocketConnection }
```

The production wrapper uses `Socket().connect(InetSocketAddress(host, port), 5_000)`, sets `soTimeout = 10_000`, writes and flushes the full byte array, and closes with `use`. `NetworkPrinter` makes three attempts with 1,000 ms between failures, immediately rethrows `InterruptedException` after restoring the thread interrupt flag, and throws the final `IOException` after attempt three.

- [ ] **Step 4: Run focused and full tests**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*NetworkPrinterTest' && ./gradlew testDebugUnitTest`

Expected: all tests pass and the fake sees no fourth connection.

- [ ] **Step 5: Commit Task 3**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/net/NetworkPrinter.kt android-printer/app/src/test/java/com/maepranam/networkprinter/net/NetworkPrinterTest.kt
git commit -m "feat: send print data over retrying TCP transport"
```

### Task 4: Validated Pusher Jobs and Bounded FIFO Queue

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/job/PrintRequest.kt`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/job/SerialPrintQueue.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/job/PrintJobParserTest.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/job/SerialPrintQueueTest.kt`

**Interfaces:**
- Consumes: JSON strings from `PusherEvent.data`.
- Produces: `PrintRequest.Receipt(source: URI)`, `PrintRequest.TestPage`, `PrintJobParser.parse(json: String): Result<PrintRequest.Receipt>`, `SerialPrintQueue.offer(request): Boolean`, `take(): PrintRequest`, and `size(): Int`.

- [ ] **Step 1: Write failing payload parser tests**

```kotlin
class PrintJobParserTest {
    private val parser = PrintJobParser(Gson())

    @Test fun acceptsHttpsFullPath() {
        assertEquals("https://shop.test/r.png", parser.parse("{\"fullPath\":\"https://shop.test/r.png\"}").getOrThrow().source.toString())
    }

    @Test fun rejectsMalformedMissingBlankAndNonWebPayloads() {
        listOf("", "not json", "{}", "{\"fullPath\":\" \"}", "{\"fullPath\":\"file:///tmp/a.png\"}")
            .forEach { assertTrue(parser.parse(it).isFailure) }
    }
}
```

- [ ] **Step 2: Write failing FIFO and capacity tests**

```kotlin
class SerialPrintQueueTest {
    @Test fun remainsFifo() {
        val queue = SerialPrintQueue(2)
        val first = PrintRequest.Receipt(URI("https://x/1")); val second = PrintRequest.Receipt(URI("https://x/2"))
        assertTrue(queue.offer(first)); assertTrue(queue.offer(second))
        assertEquals(first, queue.take()); assertEquals(second, queue.take())
    }

    @Test fun fullQueueRejectsImmediately() {
        val queue = SerialPrintQueue(1)
        assertTrue(queue.offer(PrintRequest.Receipt(URI("https://x/1"))))
        assertFalse(queue.offer(PrintRequest.TestPage))
    }
}
```

- [ ] **Step 3: Run job tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*PrintJobParserTest' --tests '*SerialPrintQueueTest'`

Expected: compilation fails because job classes do not exist.

- [ ] **Step 4: Implement parsing and queueing**

Use Gson to deserialize only `fullPath`. Trim it, parse with `URI`, and accept exactly lowercase-normalized `http` or `https` schemes with a nonblank host. Return parse errors through `Result.failure(IllegalArgumentException(...))`. Define the following request boundary and implement `SerialPrintQueue` with `ArrayBlockingQueue<PrintRequest>(capacity)`, nonblocking `offer`, blocking `take`, and `size`.

```kotlin
sealed interface PrintRequest {
    data class Receipt(val source: URI) : PrintRequest
    data object TestPage : PrintRequest
}
```

- [ ] **Step 5: Run focused and full tests**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*PrintJobParserTest' --tests '*SerialPrintQueueTest' && ./gradlew testDebugUnitTest`

Expected: all tests pass, including every malformed payload and full-capacity case.

- [ ] **Step 6: Commit Task 4**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/job android-printer/app/src/test/java/com/maepranam/networkprinter/job
git commit -m "feat: validate and queue Pusher print jobs"
```

### Task 5: Bounded Receipt Download and Android Bitmap Adapter

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/net/ReceiptDownloader.kt`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/AndroidImageAdapter.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/net/ReceiptDownloaderPolicyTest.kt`

**Interfaces:**
- Consumes: `PrintRequest.Receipt.source` and Android cache directory.
- Produces: `ReceiptDownloader.download(source: URI): File`, `DownloadPolicy.validateLength(contentLength: Long)`, `AndroidImageAdapter.decode(file: File): RasterImage`, and `createTestImage(): RasterImage`.

- [ ] **Step 1: Write failing download policy tests**

```kotlin
class ReceiptDownloaderPolicyTest {
    private val policy = DownloadPolicy(maxBytes = 10L * 1024 * 1024)

    @Test fun acceptsUnknownAndBoundedLengths() {
        policy.validateLength(-1); policy.validateLength(10L * 1024 * 1024)
    }

    @Test fun rejectsDeclaredOversizeResponse() {
        assertThrows(IOException::class.java) { policy.validateLength(10L * 1024 * 1024 + 1) }
    }

    @Test fun streamingLimitRejectsOversizeBody() {
        assertThrows(IOException::class.java) {
            policy.copyLimited(ByteArrayInputStream(ByteArray(9)), ByteArrayOutputStream(), maxBytes = 8)
        }
    }
}
```

- [ ] **Step 2: Run the policy tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*ReceiptDownloaderPolicyTest'`

Expected: compilation fails because `DownloadPolicy` does not exist.

- [ ] **Step 3: Implement bounded download behavior**

`ReceiptDownloader` uses `HttpURLConnection` with 10-second connect and 20-second read timeouts, follows redirects, requires status 200 through 299, checks declared and streamed size against 10 MiB, writes to a unique `.part` file under `cacheDir/receipts`, and retries at most three times. Delete the partial file after every failed attempt. Return the completed file only after a successful bounded copy.

```kotlin
fun downloadOnce(source: URI): File {
    val directory = File(cacheDir, "receipts").apply { mkdirs() }
    val target = File.createTempFile("receipt-", ".part", directory)
    try {
        val connection = (source.toURL().openConnection() as HttpURLConnection).apply {
            connectTimeout = 10_000; readTimeout = 20_000; instanceFollowRedirects = true
        }
        connection.inputStream.use { input ->
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            policy.validateLength(connection.contentLengthLong)
            target.outputStream().use { output -> policy.copyLimited(input, output, policy.maxBytes) }
        }
        return target
    } catch (error: Exception) {
        target.delete()
        throw error
    }
}
```

- [ ] **Step 4: Implement sampled bitmap decoding and test image generation**

First decode bounds with `BitmapFactory.Options.inJustDecodeBounds`, reject non-positive bounds, choose a power-of-two `inSampleSize` until decoded width is near 1,152 pixels, then decode. Scale down to width 576 while preserving aspect ratio; never enlarge a narrower bitmap. Copy pixels using `getPixels` into `RasterImage` and recycle temporary bitmaps that are no longer used.

`createTestImage` creates a 576-by-220 white bitmap and draws centered black text `MAE PRA NAM`, `ANDROID PRINTER TEST`, the current local date/time, printer width `576 dots`, and a horizontal checker pattern. It then returns the same `RasterImage` representation used for downloaded receipts.

```kotlin
fun toRaster(bitmap: Bitmap): RasterImage {
    val scaled = if (bitmap.width > 576) {
        val height = (bitmap.height * (576.0 / bitmap.width)).roundToInt().coerceAtLeast(1)
        Bitmap.createScaledBitmap(bitmap, 576, height, true)
    } else bitmap
    val width = scaled.width
    val height = scaled.height
    val pixels = IntArray(width * height)
    scaled.getPixels(pixels, 0, width, 0, 0, width, height)
    if (scaled !== bitmap) scaled.recycle()
    return RasterImage(width, height, pixels)
}
```

Bounds decoding and checker/text drawing live in the same adapter.

- [ ] **Step 5: Run tests and compile Android adapters**

Run: `cd android-printer && ./gradlew testDebugUnitTest assembleDebug`

Expected: all unit tests pass and Android bitmap APIs compile into the debug APK.

- [ ] **Step 6: Commit Task 5**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/net/ReceiptDownloader.kt android-printer/app/src/main/java/com/maepranam/networkprinter/escpos/AndroidImageAdapter.kt android-printer/app/src/test/java/com/maepranam/networkprinter/net/ReceiptDownloaderPolicyTest.kt
git commit -m "feat: download and prepare bounded receipt images"
```

### Task 6: Print Pipeline, Runtime State, and Resilient Worker

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/job/PrintPipeline.kt`
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/runtime/PrinterRuntime.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/job/PrintPipelineTest.kt`
- Test: `android-printer/app/src/test/java/com/maepranam/networkprinter/runtime/PrinterRuntimeTest.kt`

**Interfaces:**
- Consumes: `PrintRequest`, `PrinterConfig`, downloader, Android image adapter, encoder, and network printer.
- Produces: `PrintPipeline.process(request, config)`, `PrinterRuntime.setStatus`, `appendLog`, `snapshot`, and a worker loop that catches per-request failures and continues.

- [ ] **Step 1: Write failing pipeline cleanup and continuation tests**

```kotlin
class PrintPipelineTest {
    @Test fun deletesDownloadedFileAfterSuccessfulPrint() {
        val file = temporaryReceipt()
        pipeline(file = file, printerFails = false).process(receipt(), config())
        assertFalse(file.exists())
    }

    @Test fun deletesDownloadedFileAfterPrinterFailure() {
        val file = temporaryReceipt()
        assertThrows(IOException::class.java) { pipeline(file, printerFails = true).process(receipt(), config()) }
        assertFalse(file.exists())
    }

    @Test fun workerContinuesAfterFirstJobFails() {
        val processed = mutableListOf<String>()
        runWorkerForTest(listOf(receipt("1"), receipt("2"))) {
            val path = (it as PrintRequest.Receipt).source.path
            if (path.endsWith("1")) error("first fails") else processed += path
        }
        assertEquals(listOf("/2"), processed)
    }
}
```

Use private `temporaryReceipt`, `config`, `receipt`, `pipeline`, and `runWorkerForTest` fixture functions in the same test file. Back them with fake `ReceiptSource`, `ImageSource`, and `PrinterSink` implementations so these JVM tests do not call Android or the network. `runWorkerForTest` must catch each callback failure and continue iterating, which directly exercises the same `runWorker` function used by the service.

- [ ] **Step 2: Write a failing bounded-log test**

```kotlin
@Test fun runtimeKeepsOnlyNewestOneHundredLogLines() {
    val runtime = PrinterRuntime(maxLogs = 100)
    repeat(105) { runtime.appendLog("line-$it") }
    val logs = runtime.snapshot().logs
    assertEquals(100, logs.size)
    assertEquals("line-5", logs.first().message)
}
```

- [ ] **Step 3: Run pipeline/runtime tests and confirm the red state**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*PrintPipelineTest' --tests '*PrinterRuntimeTest'`

Expected: compilation fails because pipeline and runtime types do not exist.

- [ ] **Step 4: Implement orchestration and status storage**

Define `ReceiptSource.download(receipt: PrintRequest.Receipt): File`, `ImageSource.decode(file): RasterImage`, `ImageSource.testPage(): RasterImage`, and `PrinterSink.send(config, bytes)`. `PrintPipeline.process` sets printing status and switches on the request. A receipt is downloaded, decoded, encoded, sent, and its downloaded file is deleted in `finally`. A test page calls `ImageSource.testPage`, then uses the identical encoder and printer sink without creating or deleting a downloaded file.

```kotlin
fun process(request: PrintRequest, config: PrinterConfig) {
    runtime.setStatus(PrinterStatus.PRINTING)
    when (request) {
        is PrintRequest.Receipt -> {
            var downloaded: File? = null
            try {
                downloaded = receiptSource.download(request)
                printerSink.send(config, encoder.encode(imageSource.decode(downloaded)))
            } finally {
                downloaded?.delete()
            }
        }
        PrintRequest.TestPage -> printerSink.send(config, encoder.encode(imageSource.testPage()))
    }
    runtime.appendLog("Print completed")
}
```

`PrinterRuntime` uses one lock to guard `PrinterStatus` and an `ArrayDeque<LogEntry>`. `snapshot()` returns immutable copies. States are `STOPPED`, `CONNECTING`, `READY`, `PRINTING`, and `ERROR`. Each log records timestamp, level, and message; retain the newest 100.

Implement the worker as a loop around `queue.take()`. Catch and log every per-job exception, return to `READY` when still running, and break only when interrupted during service shutdown. This pins the requirement that a failed first job cannot kill later work.

- [ ] **Step 5: Run focused and full tests**

Run: `cd android-printer && ./gradlew testDebugUnitTest --tests '*PrintPipelineTest' --tests '*PrinterRuntimeTest' && ./gradlew testDebugUnitTest`

Expected: cleanup, continuation, and bounded-log tests pass.

- [ ] **Step 6: Commit Task 6**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/job/PrintPipeline.kt android-printer/app/src/main/java/com/maepranam/networkprinter/runtime android-printer/app/src/test/java/com/maepranam/networkprinter/job/PrintPipelineTest.kt android-printer/app/src/test/java/com/maepranam/networkprinter/runtime
git commit -m "feat: orchestrate serial receipt printing"
```

### Task 7: Foreground Service and Pusher Integration

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/PrinterService.kt`
- Modify: `android-printer/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: all configuration, parsing, queue, pipeline, and runtime interfaces from Tasks 1 through 6.
- Produces: service actions `ACTION_START`, `ACTION_STOP`, and `ACTION_TEST_PRINT`; Pusher subscription lifecycle; notification lifecycle; one worker thread.

- [ ] **Step 1: Implement the foreground notification contract**

Create notification channel `printer_service` at low importance. `onCreate` immediately calls `startForeground` with notification ID `9100` and text matching runtime state. On Android 13 and newer, the activity will request `POST_NOTIFICATIONS`; the service still starts if the user declines. The notification opens `MainActivity` through an immutable/update-current `PendingIntent`.

```kotlin
private fun enterForeground() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Printer service", NotificationManager.IMPORTANCE_LOW))
    val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val notification = Notification.Builder(this, CHANNEL_ID)
        .setSmallIcon(android.R.drawable.stat_sys_upload)
        .setContentTitle("Mae Pra Nam Printer")
        .setContentText("Waiting for print jobs")
        .setOngoing(true)
        .setContentIntent(openApp)
        .build()
    startForeground(NOTIFICATION_ID, notification)
}
```

- [ ] **Step 2: Implement explicit service actions**

`ACTION_START` loads and validates saved config, tears down any previous Pusher instance without duplicating subscriptions, starts the worker if absent, subscribes once to `orders` and event `print`, and then connects. `ACTION_STOP` calls `stopSelf`; `ACTION_TEST_PRINT` enqueues `PrintRequest.TestPage`, which is processed through the same queue, encoder, and printer sink without downloading.

Return `START_NOT_STICKY` because the approved MVP does not promise automatic recovery after process death. In `onDestroy`, disconnect Pusher, interrupt and join the worker for at most two seconds, clear the queue, set `STOPPED`, and remove the foreground notification.

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    when (intent?.action) {
        ACTION_START -> startListening()
        ACTION_TEST_PRINT -> if (!queue.offer(PrintRequest.TestPage)) runtime.appendLog("Print queue is full; test page was skipped")
        ACTION_STOP -> stopSelf()
    }
    return START_NOT_STICKY
}

companion object {
    const val ACTION_START = "com.maepranam.networkprinter.START"
    const val ACTION_STOP = "com.maepranam.networkprinter.STOP"
    const val ACTION_TEST_PRINT = "com.maepranam.networkprinter.TEST_PRINT"
    const val CHANNEL_ID = "printer_service"
    const val NOTIFICATION_ID = 9100
}
```

- [ ] **Step 3: Bind Pusher state and events**

Construct `Pusher(config.appKey, PusherOptions().setCluster(config.cluster))`. Register the `orders` / `print` subscription before `connect` so the client preserves it across reconnects. Map `CONNECTING` and `RECONNECTING` to runtime `CONNECTING`, `CONNECTED` to `READY`, and errors/disconnections to a log plus `ERROR` unless shutdown is in progress.

```kotlin
private fun connectPusher(config: PrinterConfig) {
    pusher?.disconnect()
    val client = Pusher(config.appKey, PusherOptions().setCluster(config.cluster))
    client.subscribe("orders").bind("print") { event -> acceptEvent(event.data) }
    client.connect(connectionListener)
    pusher = client
}
```

For every event, call `PrintJobParser.parse(event.data)`. Log and skip failures. If `queue.offer` returns false, log `Print queue is full; receipt was skipped`; otherwise log the accepted URL without query parameters to avoid leaking signed query values.

```kotlin
private fun acceptEvent(json: String) {
    parser.parse(json).fold(
        onSuccess = { receipt ->
            if (queue.offer(receipt)) runtime.appendLog("Receipt queued: ${receipt.source.scheme}://${receipt.source.host}${receipt.source.path}")
            else runtime.appendLog("Print queue is full; receipt was skipped")
        },
        onFailure = { runtime.appendLog("Invalid print event: ${it.message}") },
    )
}
```

- [ ] **Step 4: Compile and inspect the merged manifest**

Run: `cd android-printer && ./gradlew testDebugUnitTest processDebugMainManifest assembleDebug`

Expected: build passes; the merged manifest contains the non-exported connected-device foreground service and all five permissions.

- [ ] **Step 5: Commit Task 7**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/PrinterService.kt android-printer/app/src/main/AndroidManifest.xml
git commit -m "feat: receive Pusher jobs in a foreground service"
```

### Task 8: One-Screen Controls and Live Operational Status

**Files:**
- Create: `android-printer/app/src/main/java/com/maepranam/networkprinter/MainActivity.kt`
- Create: `android-printer/app/src/main/res/layout/activity_main.xml`
- Create: `android-printer/app/src/main/res/values/strings.xml`
- Create: `android-printer/app/src/main/res/values/themes.xml`
- Modify: `android-printer/app/src/main/AndroidManifest.xml`

**Interfaces:**
- Consumes: `PrinterConfigStore`, `PrinterConfig.validate`, `PrinterRuntime.snapshot`, and the three `PrinterService` actions.
- Produces: user-accessible save/start, stop, test-print, status, and log behavior.

- [ ] **Step 1: Build the scrollable single-screen layout**

Use platform widgets only: a vertical `ScrollView`, heading, four labeled `EditText` fields, three `Button` controls, a prominent status `TextView`, and a monospace selectable log `TextView`. Set printer port input type to number, IP/hostname to URI text, and the Pusher key to visible text without autocorrect. Thai labels must state that the app needs to remain started and that missed events are not recovered.

```xml
<ScrollView xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent" android:layout_height="match_parent">
    <LinearLayout android:layout_width="match_parent" android:layout_height="wrap_content"
        android:orientation="vertical" android:padding="20dp">
        <TextView android:id="@+id/title" android:layout_width="wrap_content" android:layout_height="wrap_content"
            android:text="Mae Pra Nam Printer" android:textSize="24sp" />
        <EditText android:id="@+id/appKey" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Pusher App Key" />
        <EditText android:id="@+id/cluster" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Pusher Cluster" />
        <EditText android:id="@+id/printerHost" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="Printer IP / hostname" android:inputType="textUri" />
        <EditText android:id="@+id/printerPort" android:layout_width="match_parent" android:layout_height="wrap_content" android:hint="9100" android:inputType="number" />
        <Button android:id="@+id/start" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="บันทึกและเริ่มรับงาน" />
        <Button android:id="@+id/stop" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="หยุดรับงาน" />
        <Button android:id="@+id/testPrint" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="ทดสอบพิมพ์" />
        <TextView android:id="@+id/status" android:layout_width="match_parent" android:layout_height="wrap_content" android:text="หยุดทำงาน" android:textSize="20sp" />
        <TextView android:id="@+id/logs" android:layout_width="match_parent" android:layout_height="wrap_content" android:fontFamily="monospace" android:textIsSelectable="true" />
    </LinearLayout>
</ScrollView>
```

- [ ] **Step 2: Implement configuration and service controls**

On launch, populate fields from `PrinterConfigStore`. **Save and Start** parses the port without throwing, validates all fields, shows all validation errors in an alert, saves valid values, and calls `ContextCompat`-free `startForegroundService` on API 26+ with `ACTION_START`. **Stop** calls `startService` with `ACTION_STOP`. **Test Print** requires valid saved settings and sends `ACTION_TEST_PRINT` after ensuring the service has started.

Request `POST_NOTIFICATIONS` only on API 33+ and only after the user presses **Save and Start**. A denial is logged but does not block service startup.

```kotlin
private fun startPrinter(action: String) {
    val intent = Intent(this, PrinterService::class.java).setAction(action)
    if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
}

private fun saveAndStart() {
    val port = printerPort.text.toString().toIntOrNull() ?: -1
    val config = PrinterConfig(appKey.text.toString(), cluster.text.toString(), printerHost.text.toString(), port)
    val errors = config.validate()
    if (errors.isNotEmpty()) {
        AlertDialog.Builder(this).setTitle("ตรวจสอบการตั้งค่า").setMessage(errors.joinToString("\n")).setPositiveButton("ตกลง", null).show()
        return
    }
    configStore.save(config)
    if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
        requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
    }
    startPrinter(PrinterService.ACTION_START)
}
```

- [ ] **Step 3: Display runtime state without an AndroidX lifecycle dependency**

In `onResume`, start a main-thread `Handler` runnable every 500 ms. Read `PrinterRuntime.snapshot()`, translate status to Thai/English display text, update the status color, and render newest log entries last. In `onPause`, remove the runnable. UI polling must only read immutable snapshots and never touch service internals.

```kotlin
private val refresh = object : Runnable {
    override fun run() {
        val snapshot = runtime.snapshot()
        status.text = statusLabel(snapshot.status)
        logs.text = snapshot.logs.joinToString("\n") { "${it.time} ${it.level}: ${it.message}" }
        handler.postDelayed(this, 500)
    }
}

override fun onResume() { super.onResume(); handler.post(refresh) }
override fun onPause() { handler.removeCallbacks(refresh); super.onPause() }
```

- [ ] **Step 4: Build and run static verification**

Run: `cd android-printer && ./gradlew testDebugUnitTest lintDebug assembleDebug`

Expected: unit tests pass, lint has no errors, and the APK is produced.

- [ ] **Step 5: Commit Task 8**

```bash
git add android-printer/app/src/main/java/com/maepranam/networkprinter/MainActivity.kt android-printer/app/src/main/res android-printer/app/src/main/AndroidManifest.xml
git commit -m "feat: add Android printer controls and status UI"
```

### Task 9: Operator Documentation and Physical Acceptance Build

**Files:**
- Create: `android-printer/README.md`
- Modify: `print-pi/README.md`

**Interfaces:**
- Consumes: completed debug APK and approved design acceptance criteria.
- Produces: repeatable local build/install instructions and an operator test checklist.

- [ ] **Step 1: Write build and installation instructions**

Document JDK 17 and Android SDK 36 prerequisites, `./gradlew clean testDebugUnitTest lintDebug assembleDebug`, APK location, `adb install -r app/build/outputs/apk/debug/app-debug.apk`, and how to install the APK manually. State Android 8.0 minimum, same-LAN requirement, TCP 9100 requirement, and that this is an internal test build.

- [ ] **Step 2: Document configuration and exact limitations**

Explain where to copy `PUSHER_APP_KEY` and `PUSHER_APP_CLUSTER` from the existing `print-pi/.env`, explicitly warn not to use `PUSHER_APP_SECRET`, and explain printer IP/port. State that force stop, reboot, or service shutdown loses queued and missed events; a successful socket write does not prove paper output; and auto-start/server-side durable jobs are outside this build.

- [ ] **Step 3: Add the physical test checklist**

Include checkboxes for installation, notification permission, ready state, local test print, real Pusher receipt, three rapid receipts in FIFO order, Wi-Fi disconnect/reconnect, unreachable printer retry, background/minimized receipt, image alignment, paper feed, and cutter behavior. Add a troubleshooting table for wrong key/cluster, no network route, printer refused/timeout, HTTP image blocked, and unreadable print density.

- [ ] **Step 4: Point the legacy README to the Android replacement**

Keep the existing Node service available and add a short section to `print-pi/README.md` linking `android-printer/README.md`, making clear that the Android build is an MVP alternative rather than deleting the Pi workflow.

- [ ] **Step 5: Run final automated verification**

Run: `cd android-printer && ./gradlew clean testDebugUnitTest lintDebug assembleDebug`

Expected: clean build, all unit tests pass, lint has no errors, and `app-debug.apk` exists.

- [ ] **Step 6: Inspect repository changes and commits**

Run: `git status --short && git log --oneline --decorate -12`

Expected: only intended tracked changes are present and Tasks 1 through 8 each have a purpose-based commit.

- [ ] **Step 7: Commit documentation**

```bash
git add android-printer/README.md README.md
git commit -m "docs: add Android printer setup and test guide"
```

- [ ] **Step 8: Perform physical acceptance with the user**

Install the APK on the chosen Android device, enter real settings, run the README checklist, and record any printer-specific deviations such as a different dot width, raster command, feed amount, or cutter command. Printer-specific changes found here become a focused follow-up rather than speculative settings in the MVP.

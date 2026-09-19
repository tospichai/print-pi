# Android Network Printer MVP Design

## Purpose

Replace the Raspberry Pi process in `print-pi` with a small Android application that can be installed on a phone or tablet. The first version is intended for immediate on-site testing and retains the existing Pusher message contract and network printer setup.

The application succeeds when an Android 8.0 or newer device can remain connected to the existing Pusher channel, receive a receipt image URL, and print that image through the existing ESC/POS printer at a configured IP address and TCP port 9100.

## Scope

The MVP will:

- support Android 8.0 (API 26) and newer while targeting Android 16 (API 36);
- connect to the existing Pusher application and subscribe to channel `orders` and event `print`;
- read `fullPath` from each event payload;
- download the receipt image and print it through an ESC/POS network printer;
- process print jobs one at a time in FIFO order;
- continue listening while its foreground service is running, including when the activity is no longer visible;
- store connection settings on the Android device;
- provide a direct test-print action and a small operational log.

The MVP will not:

- change the Laravel application or `receipt-thermal-printer` service;
- persist print jobs across app termination or device reboot;
- recover Pusher events missed while the service is stopped;
- start automatically after reboot;
- run as a managed kiosk or device-owner application;
- guarantee that paper physically came out of the printer;
- update the server with an authoritative print-success acknowledgement.

## User Interface

The application has one screen containing:

- Pusher App Key;
- Pusher Cluster;
- Printer IP Address;
- Printer Port, defaulting to `9100`;
- a **Save and Start** button;
- a **Stop** button;
- a **Test Print** button;
- current service and print status;
- a bounded list of recent operational log messages.

Settings are stored with Android SharedPreferences. The Pusher app key is a client-side publishable identifier rather than the Pusher application secret; the secret must never be entered into or bundled with the Android application.

The visible states are: stopped, connecting, ready, printing, and error. While listening, a persistent Android notification shows that the printing service is active. Force-stopping the application stops receipt listening until the user opens the application and starts it again.

## Architecture

### MainActivity

Owns the single configuration screen, validates user input, stores settings, starts or stops the service, requests notification permission where required, and observes service status and recent log messages.

### PrintForegroundService

Owns the Pusher connection for the lifetime of the printing session and displays the required foreground notification. It subscribes to `orders` / `print`, validates each payload, and submits valid jobs to the in-memory print queue. The Pusher SDK handles reconnect attempts; connection changes are surfaced to the UI and log.

### PrintQueue

Maintains a bounded FIFO channel of jobs and exposes a single consumer. Serial processing prevents interleaved bytes from two receipts reaching the printer. Duplicate suppression and durable storage are outside the MVP.

### ReceiptDownloader

Downloads `fullPath` into application cache, requires an HTTP success response, applies connection and read timeouts, and retries transient failures up to three attempts. It rejects data that cannot be decoded as an image and always removes temporary files after processing.

### EscPosImageEncoder

Converts the downloaded bitmap to monochrome raster data supported by an ESC/POS printer. It scales images down to the configured paper width without enlarging smaller images, centers the raster output, and appends line feeds and the standard cut command. The initial paper width is fixed to the width already used by the receipt images; exposing paper-width configuration is deferred unless the physical test shows it is required.

### NetworkPrinter

Creates a TCP socket to the configured printer address and port, writes the encoded bytes, flushes them, and closes the socket for each job. Connection and write failures time out and retry up to three attempts. A completed socket write means the job was delivered to the printer connection; it is not proof that the printer physically produced paper.

## Data Flow

1. The user enters settings and selects **Save and Start**.
2. `MainActivity` validates and stores the settings, then starts `PrintForegroundService` while the application is visible.
3. The service connects to Pusher and subscribes to `orders` / `print`.
4. An event containing a non-empty HTTP or HTTPS `fullPath` is placed in `PrintQueue`.
5. The single queue consumer downloads and validates the image.
6. `EscPosImageEncoder` creates the printer byte stream.
7. `NetworkPrinter` sends the stream to the configured TCP endpoint.
8. The temporary image is deleted and status is returned to ready before the next job begins.

The **Test Print** action bypasses Pusher and the downloader. It generates a small local test bitmap containing identifying text and a pattern, then sends it through the same encoder, queue, and network printer path used by real jobs.

## Error Handling

- Invalid configuration prevents the service from starting and identifies the field to correct.
- Invalid Pusher payloads are logged and skipped without stopping the service.
- Pusher disconnects change the state to connecting/error while the SDK attempts to reconnect.
- Downloads and printer connections use finite timeouts and at most three attempts per job.
- A failed job is logged and discarded so that later queued jobs can proceed.
- Exceptions from one job cannot terminate the queue consumer or foreground service.
- Cached receipt images are deleted on both success and failure.
- The queue and UI log have fixed maximum sizes to prevent unbounded memory growth.

Because jobs are not durable in the MVP, stopping or killing the service loses queued work. The UI and documentation must state this limitation.

## Testing

Automated unit tests cover:

- acceptance and rejection of Pusher payloads;
- FIFO queue behavior and continuation after a failed job;
- bitmap scaling, raster dimensions, representative encoded bytes, and cut-command output;
- configuration validation;
- retry limits and cleanup behavior using fakes.

Manual acceptance testing on a physical Android device and printer covers:

1. Install and launch the APK on Android 8.0 or newer.
2. Save valid Pusher and printer settings and confirm the foreground notification and ready state.
3. Use **Test Print** and verify alignment, image clarity, paper feed, and cut behavior.
4. Trigger the existing `orders` / `print` event and verify the receipt image is printed.
5. Trigger several events in quick succession and verify serial FIFO output.
6. Disconnect and reconnect Wi-Fi and verify Pusher reconnects.
7. Enter an unreachable printer IP and verify bounded retries, a useful error, and continued service operation.
8. Minimize the application and verify a later event still prints while the foreground service notification remains present.

## Distribution

The first build is a debug APK installed directly on the test device, avoiding a Play Store release during validation. Signing, managed deployment, automatic startup, durable server-side print jobs, and kiosk operation are potential later phases and are not prerequisites for the MVP test.

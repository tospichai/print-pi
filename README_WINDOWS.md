# Mae Pra Nam Printer - Windows Setup

This guide allows you to run the printer service on your Windows PC instead of Raspberry Pi.

## Prerequisites

1.  **Install Node.js**: Download and install the "LTS" version from [nodejs.org](https://nodejs.org/).
2.  **Printer Connection**: Ensure your PC is connected to the same network as the Receipt Printer.

## Installation

1.  **Copy Folder**: Move this entire `print-pi` folder to a permanent location on your PC (e.g., `C:\mae-pra-nam\print-pi`).
2.  **Create Shortcut**:
    - Right-click on `setup_shortcut.ps1`.
    - Select **"Run with PowerShell"**.
    - You should see a new icon "Mae Pra Nam Printer" on your Desktop.

## Configuration

1.  Double-click the **Mae Pra Nam Printer** icon on your Desktop.
2.  If it's the first run, it might tell you that `.env` was created.
3.  Open the folder and edit the `.env` file with your configuration:
    ```env
    PUSHER_APP_KEY=your_key_here
    PUSHER_APP_CLUSTER=ap1
    PRINTER_IP=192.168.1.xxx
    PRINTER_PORT=9100
    ```
4.  Close and reopen the app to apply settings.

## How to Use

- **Start**: Double-click the "Mae Pra Nam Printer" icon.
- **Stop**: Click the `X` to close the window.
- **Status**: The black window will show green text with logs when orders are printed.

## Troubleshooting

- **"Script disabled" error**: If PowerShell prevents running the script, open PowerShell as Admin and run: `Set-ExecutionPolicy RemoteSigned`.
- **Printer not found**: Check your firewall or turn it off briefly to test. Ensure the IP address in `.env` is correct.

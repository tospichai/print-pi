'use strict';
const path = require('path');
const fs = require('fs');
const https = require('https');
const http = require('http');
const escpos = require('escpos');
escpos.Network = require('escpos-network');

const logger = require('./logger');
const Pusher = require('pusher-js');
require('dotenv').config();

const printQueue = [];
let isPrinting = false;

const pusher = new Pusher(process.env.PUSHER_APP_KEY, {
  cluster: process.env.PUSHER_APP_CLUSTER,
  encrypted: true,
});

pusher.subscribe('orders').bind('print', async (data) => {
  printQueue.push(data);
  processPrintQueue();
});

async function processPrintQueue() {
  if (isPrinting || printQueue.length === 0) return;

  isPrinting = true;
  const data = printQueue.shift();

  try {
    await handlePrint(data);
  } catch (err) {
    logger.error(`❌ Error in print job: ${err.message}`, { stack: err.stack });
  } finally {
    isPrinting = false;
    processPrintQueue();
  }
}

async function handlePrint(data) {
  const imageUrl = data.fullPath;
  logger.info(`📦 Order created: ${imageUrl}`);

  try {
    const filename = getFilenameFromUrl(imageUrl);
    const tempImagePath = path.join(__dirname, filename);

    await downloadImage(imageUrl, tempImagePath);
    logger.info('✅ Image downloaded successfully');

    const device = await createPrinterDeviceWithRetry();

    if (!device) {
      logger.warn('⚠️ Printer device is null. Reconnecting...');
      return;
    }

    const printer = new escpos.Printer(device);

    escpos.Image.load(tempImagePath, (image) => {
      device.open(() => {
        printer
          .align('ct')
          .image(image, 'D24')
          .then(() => {
            printer.cut().close();

            logger.info(`✅ Print Success`);
          })
          .catch((err) => {
            logger.error(`❌ Error during print job: ${err.message}`, {
              stack: err.stack,
            });
          })
          .finally(() => {
            fs.unlink(tempImagePath, (err) => {
              if (err) {
                logger.error(`❌ Failed to delete temp image: ${err.message}`, {
                  stack: err.stack,
                });
              }
            });

            if (device) {
              device.close();
              logger.info('🔌 Device connection closed');
            }
          });
      });
    });
  } catch (err) {
    logger.error(`❌ Print process failed: ${err.message}`, {
      stack: err.stack,
    });
  }
}

function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function createPrinterDeviceWithRetry(retries = 3, delay = 1000) {
  const ip = process.env.PRINTER_IP;
  const port = process.env.PRINTER_PORT || 9100;

  for (let i = 0; i < retries; i++) {
    try {
      const device = new escpos.Network(ip, port);

      await new Promise((resolve, reject) => {
        device.open((error) => {
          if (error) {
            reject(error);
          } else {
            resolve();
          }
        });
      });

      return device;
    } catch (err) {
      console.warn(
        `❌ Printer connection failed (attempt ${i + 1}/${retries}): ${
          err.message
        }`
      );
      if (i < retries - 1) {
        await sleep(delay);
      }
    }
  }

  console.error('❌ Failed to connect to printer after multiple attempts.');
  return null;
}

function getFilenameFromUrl(url) {
  try {
    const { pathname } = new URL(url);
    return path.basename(pathname);
  } catch (err) {
    logger.error(`❌ Invalid URL: ${url}`, { stack: err.stack });
    throw err;
  }
}

function downloadImage(url, destPath) {
  return new Promise((resolve, reject) => {
    const file = fs.createWriteStream(destPath);
    const client = url.startsWith('https') ? https : http;

    client
      .get(url, (response) => {
        response.pipe(file);
        file.on('finish', () => file.close(resolve));
      })
      .on('error', (err) => {
        fs.unlink(destPath, () => reject(err));
      });
  });
}

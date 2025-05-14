"use strict";
const path = require("path");
const fs = require("fs");
const https = require("https");
const http = require("http");
const escpos = require("escpos");
escpos.Network = require("escpos-network");

const logger = require("./logger");
const Pusher = require("pusher-js");
require("dotenv").config();

let device = createPrinterDevice();
let printer = new escpos.Printer(device);

const printQueue = [];
let isPrinting = false;

const pusher = new Pusher(process.env.PUSHER_APP_KEY, {
  cluster: process.env.PUSHER_APP_CLUSTER,
  encrypted: true,
});

pusher.subscribe("orders").bind("print", async (data) => {
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
    logger.info("✅ Image downloaded successfully");

    if (!device) {
      logger.warn("⚠️ Printer device is null. Reconnecting...");
      device = createPrinterDevice();
      printer = new escpos.Printer(device);
    }

    escpos.Image.load(tempImagePath, (image) => {
      device.open(() => {
        printer
          .align("ct")
          .image(image, "D24")
          .then(() => {
            printer.cut().close();

            logger.info(`✅ Print Success`);
          })
          .cacth((err) => {
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
          });
      });
    });
  } catch (err) {
    logger.error(`❌ Print process failed: ${err.message}`, {
      stack: err.stack,
    });
  }
}

function createPrinterDevice() {
  return new escpos.Network(process.env.PRINTER_IP, process.env.PRINTER_PORT);
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
    const client = url.startsWith("https") ? https : http;

    client
      .get(url, (response) => {
        response.pipe(file);
        file.on("finish", () => file.close(resolve));
      })
      .on("error", (err) => {
        fs.unlink(destPath, () => reject(err));
      });
  });
}

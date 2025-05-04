"use strict";
const path = require("path");
const fs = require("fs");
const https = require("https");
const escpos = require("escpos");
escpos.Network = require("escpos-network");
const logger = require("./logger");
const Pusher = require("pusher-js");
require("dotenv").config();

const device = new escpos.Network(
  process.env.PRINTER_IP,
  process.env.PRINTER_PORT
);
const printer = new escpos.Printer(device);

const pusher = new Pusher(process.env.PUSHER_APP_KEY, {
  cluster: process.env.PUSHER_APP_CLUSTER,
  encrypted: true,
});

const channel = pusher.subscribe("orders");

channel.bind("print", function (data) {
  logger.info(`print: ${data.fullPath}`);
  console.log("📦 Order created:", data.fullPath);

  const url = new URL(data.fullPath);
  const pathname = url.pathname;
  const filename = pathname.substring(pathname.lastIndexOf("/") + 1);

  const tempImagePath = path.join(__dirname, filename);

  downloadImage(data.fullPath, tempImagePath, function (err) {
    if (err) {
      logger.error(`❌ Error downloading image: : ${err.message}`, {
        stack: err.stack,
      });
      return;
    }

    console.log("success");

    escpos.Image.load(tempImagePath, function (image) {
      device.open(function () {
        printer
          .align("ct")
          .image(image, "D24")
          .then(() => {
            printer.cut().close();
            fs.unlink(tempImagePath, (unlinkErr) => {
              if (unlinkErr) {
                logger.error(
                  `❌ Error deleting temp image: : ${unlinkErr.message}`,
                  {
                    stack: unlinkErr.stack,
                  }
                );
              }
            });
          });
      });
    });
  });
});

function downloadImage(url, dest, callback) {
  const file = fs.createWriteStream(dest);
  const protocol = url.startsWith("https") ? https : http;

  protocol
    .get(url, function (response) {
      response.pipe(file);
      file.on("finish", function () {
        file.close(callback);
      });
    })
    .on("error", function (err) {
      fs.unlink(dest);
      if (callback) callback(err.message);
    });
}

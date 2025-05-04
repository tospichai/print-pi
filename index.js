
'use strict';
const path = require('path');
const fs = require('fs');
const https = require('https');
const escpos = require('escpos');
escpos.Network = require('escpos-network');

const device = new escpos.Network('192.168.1.103', 9100);
const printer = new escpos.Printer(device);

const imageUrl =
  'https://process.maepranam-order.com/receipts/R2024121800006.jpg';
const tempImagePath = path.join(__dirname, 'temp-receipt.jpg');

function downloadImage(url, dest, callback) {
  const file = fs.createWriteStream(dest);
  const protocol = url.startsWith('https') ? https : http;

  protocol
    .get(url, function (response) {
      response.pipe(file);
      file.on('finish', function () {
        file.close(callback);
      });
    })
    .on('error', function (err) {
      fs.unlink(dest);
      if (callback) callback(err.message);
    });
}

downloadImage(imageUrl, tempImagePath, function(err) {
  if (err) {
    console.error('Error downloading image:', err);
    return;
  }

  escpos.Image.load(tempImagePath, function(image) {
    device.open(function () {
      printer
        .align('ct')
        .image(image, 'D24')
        .then(() => {
          printer.cut().close();
          fs.unlink(tempImagePath, (unlinkErr) => {
            if (unlinkErr) {
              console.error('Error deleting temp image:', unlinkErr);
            }
          });
        });
    });
  });
});
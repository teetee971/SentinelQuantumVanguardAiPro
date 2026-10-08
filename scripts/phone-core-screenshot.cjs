const fs = require('node:fs');

const PNG_SIGNATURE = Buffer.from([137, 80, 78, 71, 13, 10, 26, 10]);
const MAX_PNG_BYTES = 20 * 1024 * 1024;

function crc32(buffer) {
  let crc = 0xffffffff;
  for (const byte of buffer) {
    crc ^= byte;
    for (let bit = 0; bit < 8; bit += 1) {
      crc = (crc >>> 1) ^ (0xedb88320 & -(crc & 1));
    }
  }
  return (crc ^ 0xffffffff) >>> 0;
}

function isValidPngFile(filePath) {
  let bytes;
  try {
    const stat = fs.statSync(filePath);
    if (!stat.isFile() || stat.size < PNG_SIGNATURE.length || stat.size > MAX_PNG_BYTES) return false;
    bytes = fs.readFileSync(filePath);
  } catch {
    return false;
  }

  if (!bytes.subarray(0, PNG_SIGNATURE.length).equals(PNG_SIGNATURE)) return false;

  let offset = PNG_SIGNATURE.length;
  let sawHeader = false;
  let sawData = false;
  let sawEnd = false;
  while (offset + 12 <= bytes.length) {
    const length = bytes.readUInt32BE(offset);
    const typeStart = offset + 4;
    const dataStart = offset + 8;
    const dataEnd = dataStart + length;
    const crcEnd = dataEnd + 4;
    if (dataEnd < dataStart || crcEnd > bytes.length) return false;

    const type = bytes.toString('ascii', typeStart, dataStart);
    const data = bytes.subarray(dataStart, dataEnd);
    const expectedCrc = bytes.readUInt32BE(dataEnd);
    if (crc32(bytes.subarray(typeStart, dataEnd)) !== expectedCrc) return false;

    if (!sawHeader) {
      if (type !== 'IHDR' || length !== 13) return false;
      const width = data.readUInt32BE(0);
      const height = data.readUInt32BE(4);
      if (width < 1 || height < 1) return false;
      sawHeader = true;
    } else if (type === 'IHDR') {
      return false;
    }

    if (type === 'IDAT') sawData = true;
    if (type === 'IEND') {
      if (length !== 0 || !sawHeader || !sawData || crcEnd !== bytes.length) return false;
      sawEnd = true;
      break;
    }
    offset = crcEnd;
  }

  return sawHeader && sawData && sawEnd;
}

module.exports = { isValidPngFile };

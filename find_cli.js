const fs = require('fs');
const path = require('path');

function findCli(dir, maxDepth = 4) {
  if (maxDepth <= 0 || !fs.existsSync(dir)) return [];
  let found = [];
  try {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const e of entries) {
      const full = path.join(dir, e.name);
      if (e.isDirectory()) {
        found = found.concat(findCli(full, maxDepth - 1));
      } else if (e.name.toLowerCase().includes('arduino-cli')) {
        found.push(full);
      }
    }
  } catch (err) {}
  return found;
}

const res = [
  ...findCli('C:\\Users\\navee\\AppData\\Local\\Programs\\Arduino IDE', 6),
  ...findCli('C:\\Users\\navee\\AppData\\Local\\Arduino15', 4),
  ...findCli('C:\\Users\\navee\\.local\\bin', 2)
];

fs.writeFileSync('B:/hackaton/found_cli.json', JSON.stringify(res, null, 2));
console.log('Found:', JSON.stringify(res, null, 2));

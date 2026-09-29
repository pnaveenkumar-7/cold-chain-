const fs = require('fs');

const possiblePaths = [
  'C:\\Users\\navee\\AppData\\Local\\Arduino15',
  'C:\\Users\\navee\\AppData\\Local\\Programs\\Arduino IDE',
  'C:\\Program Files\\Arduino IDE',
  'C:\\Program Files (x86)\\Arduino',
  'C:\\Users\\navee\\.espressif',
  'C:\\Espressif'
];

const results = {};
possiblePaths.forEach(p => {
  results[p] = fs.existsSync(p);
});

fs.writeFileSync('B:/hackaton/scratch_arduino_env.json', JSON.stringify(results, null, 2));
console.log('Done');

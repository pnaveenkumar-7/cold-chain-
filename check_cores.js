const { spawnSync } = require('child_process');
const fs = require('fs');

const cli = "C:\\Users\\navee\\AppData\\Local\\Programs\\Arduino IDE\\resources\\app\\lib\\backend\\resources\\arduino-cli.exe";

const res = spawnSync(cli, ['core', 'list'], { encoding: 'utf8' });
console.log('Core list output:');
console.log(res.stdout || res.stderr);

fs.writeFileSync('B:/hackaton/arduino_cores.txt', (res.stdout || '') + '\n' + (res.stderr || ''));

const { spawnSync } = require('child_process');
const fs = require('fs');

const cli = "C:\\Users\\navee\\AppData\\Local\\Programs\\Arduino IDE\\resources\\app\\lib\\backend\\resources\\arduino-cli.exe";

const res = spawnSync(cli, ['board', 'listall', 'esp32'], { encoding: 'utf8' });
console.log('Board listall:');
console.log(res.stdout);

fs.writeFileSync('B:/hackaton/board_list.txt', (res.stdout || '') + '\n' + (res.stderr || ''));

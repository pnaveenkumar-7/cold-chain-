const { spawnSync } = require('child_process');
const fs = require('fs');

const cli = "C:\\Users\\navee\\AppData\\Local\\Programs\\Arduino IDE\\resources\\app\\lib\\backend\\resources\\arduino-cli.exe";

console.log('Compiling B:\\hackaton\\firmware with arduino:esp32:nano_nora...');
const res = spawnSync(cli, ['compile', '--fqbn', 'arduino:esp32:nano_nora', 'B:\\hackaton\\firmware'], { encoding: 'utf8' });

console.log('STDOUT:');
console.log(res.stdout);
console.log('STDERR:');
console.log(res.stderr);
console.log('STATUS:', res.status);

fs.writeFileSync('B:/hackaton/compile_result.txt', `STATUS: ${res.status}\n\nSTDOUT:\n${res.stdout}\n\nSTDERR:\n${res.stderr}`);

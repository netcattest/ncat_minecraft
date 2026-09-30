import fs from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';

const project = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(project, 'src/main/resources/assets/ncat_minecraft/html/vendor/novnc');
const output = path.join(source, 'bundle.js');
const temporary = await fs.mkdtemp(path.join(os.tmpdir(), 'ncat-novnc-'));
try {
    await fs.cp(source, temporary, {recursive: true, filter: entry => entry !== output});
    const browser = path.join(temporary, 'core/util/browser.js');
    const original = await fs.readFile(browser, 'utf8');
    const before = 'supportsWebCodecsH264Decode = await _checkWebCodecsH264DecodeSupport();';
    const after = '_checkWebCodecsH264DecodeSupport().then(value => { supportsWebCodecsH264Decode = value; });';
    if (!original.includes(before)) throw new Error('Fonte noVNC inesperado');
    await fs.writeFile(browser, original.replace(before, after));
    const argumentsList = [
        '--yes', 'esbuild@0.25.12', path.join(temporary, 'core/rfb.js'),
        '--bundle', '--format=iife', '--global-name=NcatNoVNC', '--target=chrome108',
        '--legal-comments=inline', '--outfile=' + output
    ];
    if (process.platform === 'win32') {
        const npx = path.join(path.dirname(process.execPath), 'node_modules/npm/bin/npx-cli.js');
        execFileSync(process.execPath, [npx, ...argumentsList], {stdio: 'inherit'});
    } else {
        execFileSync('npx', argumentsList, {stdio: 'inherit'});
    }
} finally {
    const relative = path.relative(os.tmpdir(), temporary);
    if (relative.startsWith('ncat-novnc-') && !relative.includes('..') && !path.isAbsolute(relative))
        await fs.rm(temporary, {recursive: true, force: true});
}

#!/usr/bin/env python3
"""Capture the exact CI APK across viewports on disposable hosted Android only."""
import os
from pathlib import Path
import re
import struct
import subprocess
import time
import xml.etree.ElementTree as ET

assert os.environ.get('GITHUB_ACTIONS') == 'true', 'Run only on disposable hosted CI'
ADB = ['adb', '-s', 'emulator-5554']
PACKAGE = 'io.github.coreharbor.brewdoro'
OUT = Path('screenshots')
OUT.mkdir(exist_ok=True)

def adb(*args):
    return subprocess.check_output(ADB + list(args), stderr=subprocess.STDOUT)

assert adb('shell', 'getprop', 'ro.kernel.qemu').decode().strip() == '1', 'Not an emulator'
# Instrumentation builds can have a different signer: install the exact downloadable artifact.
if ('package:' + PACKAGE) in adb('shell', 'pm', 'list', 'packages', PACKAGE).decode().splitlines():
    adb('uninstall', PACKAGE)
adb('install', '--no-streaming', 'verified-apk/app-debug.apk')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
adb('shell', 'cmd', 'uimode', 'night', 'yes')
adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0')

def png():
    data = adb('exec-out', 'screencap', '-p')
    assert data.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])), 'Invalid screencap'
    return data

def rotate(landscape):
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '1' if landscape else '0')
    adb('shell', 'wm', 'user-rotation', 'lock', '1' if landscape else '0')
    deadline = time.monotonic() + 15
    while time.monotonic() < deadline:
        w, h = struct.unpack('>II', png()[16:24])
        if (w > h) == landscape:
            time.sleep(1)
            return
        time.sleep(.3)
    raise AssertionError('Emulator rotation did not settle')

def wait_main(language):
    """Wait for real controls; handle known emulator system dialogs, never application ANRs."""
    label = 'Start' if language == 'en' else 'Начать'
    deadline = time.monotonic() + 45
    system_ui_waits = 0
    while time.monotonic() < deadline:
        adb('shell', 'uiautomator', 'dump', '/sdcard/brewdoro-ready.xml')
        xml = adb('exec-out', 'cat', '/sdcard/brewdoro-ready.xml')
        tree = ET.fromstring(xml)
        texts = [n.get('text', '') for n in tree.iter('node')]
        if any(n.get('package') == PACKAGE and n.get('text') == label for n in tree.iter('node')):
            return
        system_dialog = next((t for t in texts if t in ("System UI isn't responding", "Pixel Launcher isn't responding")), None)
        if system_dialog is not None and system_ui_waits < 2:
            wait = next(n for n in tree.iter('node') if n.get('text') == 'Wait')
            x1, y1, x2, y2 = map(int, re.findall('[0-9]+', wait.attrib['bounds']))
            adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
            system_ui_waits += 1
            print('Hosted emulator dialog: ' + system_dialog + '; chose Wait. App ANRs are NOT dismissed.', flush=True)
            with (OUT / 'emulator-recovery.txt').open('a') as report:
                report.write(system_dialog + '; Wait selected on disposable emulator. App ANRs never dismissed.' + chr(10))
        elif any("isn't responding" in text for text in texts):
            (OUT / 'unrecovered-anr.xml').write_bytes(xml)
            (OUT / 'unrecovered-anr.png').write_bytes(png())
            raise AssertionError(('Unrecovered ANR', texts))
        time.sleep(.5)
    (OUT / 'not-ready.xml').write_bytes(xml)
    (OUT / 'not-ready.png').write_bytes(png())
    raise AssertionError(('Actual Activity never became ready', language, texts))

def launch():
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    time.sleep(2)

def capture(name):
    data = png()
    (OUT / (name + '.png')).write_bytes(data)
    adb('shell', 'uiautomator', 'dump', '/sdcard/brewdoro-window.xml')
    xml = adb('exec-out', 'cat', '/sdcard/brewdoro-window.xml')
    (OUT / (name + '.xml')).write_bytes(xml)
    return ET.fromstring(xml), struct.unpack('>II', data[16:24])

def assert_main_visible(tree, size, language):
    # These nodes must be onscreen BEFORE any scroll; Compose tests check full pane bounds too.
    labels = ('Start', 'Reset', '25 min', '5 min', '15 min') if language == 'en' else ('Начать', 'Сбросить', '25 мин', '5 мин', '15 мин')
    for label in labels:
        nodes = [n for n in tree.iter('node') if n.get('text') == label]
        assert nodes, f'Missing initial-viewport control: {label}'
        x1, y1, x2, y2 = map(int, re.findall('[0-9]+', nodes[0].attrib['bounds']))
        assert 0 <= x1 < x2 <= size[0] and 0 <= y1 < y2 <= size[1], (label, size, nodes[0].attrib['bounds'])
    assert any(n.get('content-desc', '').startswith(('Coffee cup,', 'Чашка кофе')) for n in tree.iter('node')), 'Cup missing'

def open_settings(tree):
    gear = next((n for n in tree.iter('node') if n.get('content-desc') in ('Settings', 'Настройки')), None)
    assert gear is not None, 'Settings gear missing'
    x1, y1, x2, y2 = map(int, re.findall('[0-9]+', gear.attrib['bounds']))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(1)

try:
    for language in ('en', 'ru'):
        adb('shell', 'cmd', 'locale', 'set-app-locales', PACKAGE, '--user', '0', '--locales', language)
        for landscape in (False, True):
            rotate(landscape)
            launch()
            # Locale/Activity relaunch can settle after the first device rotation.
            rotate(landscape)
            wait_main(language)
            name = ('main-landscape-' if landscape else 'main-') + language
            tree, size = capture(name)
            assert (size[0] > size[1]) == landscape, ('Wrong captured orientation', name, size)
            assert_main_visible(tree, size, language)
        if language == 'ru':
            open_settings(tree)
            capture('settings-landscape-ru')
            rotate(False)
            capture('settings-ru')

    # A small 640x360dp landscape display, not merely a big phone rotated.
    adb('shell', 'wm', 'size', '720x1280')
    adb('shell', 'wm', 'density', '320')
    rotate(True)
    launch()
    rotate(True)
    wait_main('ru')
    tree, size = capture('main-landscape-compact-ru')
    assert size[0] > size[1], ('Wrong compact orientation', size)
    assert_main_visible(tree, size, 'ru')
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.5')
    launch()
    rotate(True)
    wait_main('ru')
    capture('main-landscape-large-font-ru')
    (OUT / 'device.txt').write_text(
        adb('shell', 'getprop', 'ro.build.version.release').decode() +
        'API ' + adb('shell', 'getprop', 'ro.build.version.sdk').decode() +
        'Exact verified APK after instrumentation; portrait + landscape EN/RU; '
        'compact landscape 640x360dp and 1.5x font screenshot. Not a physical phone.' + chr(10))
finally:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'wm', 'density', 'reset')
    rotate(False)
print('Captured actual portrait/landscape/compact app; normal-font controls fit without scrolling.')

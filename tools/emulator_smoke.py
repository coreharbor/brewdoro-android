#!/usr/bin/env python3
"""Capture exact CI APK on disposable hosted Android emulator, never a real phone."""
import os
from pathlib import Path
import re
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
# Replace the instrumented test install with the exact verified release artifact.
if ('package:' + PACKAGE) in adb('shell', 'pm', 'list', 'packages', PACKAGE).decode().splitlines():
    adb('uninstall', PACKAGE)
adb('install', '--no-streaming', 'verified-apk/app-debug.apk')
adb('shell', 'input', 'keyevent', 'KEYCODE_WAKEUP')
adb('shell', 'wm', 'dismiss-keyguard')
adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
adb('shell', 'cmd', 'uimode', 'night', 'yes')

def launch():
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    time.sleep(2)

def capture(name):
    png = adb('exec-out', 'screencap', '-p')
    assert png.startswith(bytes([137, 80, 78, 71, 13, 10, 26, 10])), 'Invalid screencap'
    (OUT / (name + '.png')).write_bytes(png)
    adb('shell', 'uiautomator', 'dump', '/sdcard/brewdoro-window.xml')
    xml = adb('exec-out', 'cat', '/sdcard/brewdoro-window.xml')
    (OUT / (name + '.xml')).write_bytes(xml)
    return ET.fromstring(xml)

for language in ('en', 'ru'):
    adb('shell', 'cmd', 'locale', 'set-app-locales', PACKAGE, '--user', '0', '--locales', language)
    launch()
    tree = capture('main-' + language)

gear = next((n for n in tree.iter('node') if n.get('content-desc') in ('Settings', 'Настройки')), None)
assert gear is not None, 'Settings gear not found in actual app hierarchy'
x1, y1, x2, y2 = map(int, re.findall('[0-9]+', gear.attrib['bounds']))
adb('shell', 'input', 'tap', str((x1 + x2)//2), str((y1 + y2)//2))
time.sleep(1)
capture('settings-ru')
(OUT / 'device.txt').write_text(adb('shell', 'getprop', 'ro.build.version.release').decode() +
    'API ' + adb('shell', 'getprop', 'ro.build.version.sdk').decode() +
    'Exact verified APK installed after instrumentation tests; not a physical phone.' + chr(10))
print('Captured exact verified APK: EN/RU minimal main and RU settings.')

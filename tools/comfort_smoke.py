#!/usr/bin/env python3
"""Real installed-APK comfort screen and fluid surface evidence; disposable hosted CI only."""
import io
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from PIL import Image, ImageChops

assert os.environ.get('GITHUB_ACTIONS') == 'true'
ADB = ['adb', '-s', 'emulator-5554']
PACKAGE = 'io.github.coreharbor.brewdoro'
OUT = Path('screenshots')

def adb(*args):
    return subprocess.check_output(ADB + list(args), stderr=subprocess.STDOUT, timeout=40)

assert adb('shell', 'getprop', 'ro.kernel.qemu').decode().strip() == '1'

def screenshot(name=None):
    data = adb('exec-out', 'screencap', '-p')
    image = Image.open(io.BytesIO(data)).convert('RGB')
    if name:
        (OUT / (name + '.png')).write_bytes(data)
    return image

def tree():
    adb('shell', 'uiautomator', 'dump', '/sdcard/brewdoro-comfort.xml')
    data = adb('exec-out', 'cat', '/sdcard/brewdoro-comfort.xml')
    (OUT / 'comfort-latest.xml').write_bytes(data)
    return ET.fromstring(data)

def bounds(node):
    return tuple(map(int, re.findall('[0-9]+', node.attrib['bounds'])))

def tap(node):
    x1, y1, x2, y2 = bounds(node)
    assert x2 > x1 and y2 > y1, ('Not tappable', node.attrib)
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(.4)

def find(predicate, scroll=False, down=True):
    for _ in range(12 if scroll else 5):
        root = tree()
        node = next((n for n in root.iter('node') if n.get('package') == PACKAGE and predicate(n)
                     and bounds(n)[3] > bounds(n)[1]), None)
        if node is not None:
            return node
        if scroll:
            w, h = screenshot().size
            start, end = (3*h//4, h//3) if down else (h//3, 3*h//4)
            adb('shell', 'input', 'swipe', str(3*w//4), str(start), str(3*w//4), str(end), '350')
        time.sleep(.4)
    raise AssertionError('Expected app control missing; see comfort-latest.xml')

def text(label, **kwargs):
    return find(lambda n: n.get('text') == label, **kwargs)

def surface(crop):
    # Original136x86 cup coordinates: interior-only columns avoid rounded corners/handle.
    w, h = crop.size
    scale = min(w/136, h/86)
    x0 = (w - 136*scale)/2
    y0 = (h - 86*scale)/2
    values = []
    for x in range(round(x0+24*scale), round(x0+92*scale)):
        matches = [y for y in range(round(y0+10*scale), min(h, round(y0+74*scale)))
                   if max(abs(crop.getpixel((x,y))[i]-c) for i,c in enumerate((224,175,104))) <= 4]
        assert matches, ('No coffee surface in midlevel crop', x, crop.size)
        values.append(min(matches))
    mean = sum(values)/len(values)
    return [v-mean for v in values], max(values)-min(values)

old_scale = adb('shell', 'settings', 'get', 'global', 'animator_duration_scale').decode().strip()
recording = None
try:
    adb('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
    adb('shell', 'wm', 'size', 'reset')
    adb('shell', 'wm', 'density', 'reset')
    adb('shell', 'settings', 'put', 'system', 'user_rotation', '0')
    adb('shell', 'wm', 'user-rotation', 'lock', '0')
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    time.sleep(2)
    text('Начать')
    tap(find(lambda n: n.get('content-desc') == 'Настройки'))
    find(lambda n: n.get('resource-id', '').split('/')[-1] == 'keep_screen_awake', scroll=True)
    screenshot('settings-comfort-ru')
    (OUT / 'settings-comfort-ru.xml').write_bytes(ET.tostring(tree()))
    find(lambda n: n.get('resource-id', '').split('/')[-1] == 'vibration_DOUBLE', scroll=True)
    screenshot('settings-vibration-ru')
    (OUT / 'settings-vibration-ru.xml').write_bytes(ET.tostring(tree()))

    # Configure one-minute focus using the real settings UI, never inject app preferences.
    field = find(lambda n: n.get('class') == 'android.widget.EditText', scroll=True, down=False)
    # Scroll until the focus field specifically is visible; its tag is exported by the app.
    field = find(lambda n: n.get('resource-id', '').split('/')[-1] == 'duration_focus', scroll=True, down=False)
    tap(field)
    adb('shell', 'input', 'keyevent', 'KEYCODE_MOVE_END')
    for _ in range(4): adb('shell', 'input', 'keyevent', 'KEYCODE_DEL')
    adb('shell', 'input', 'text', '1')
    adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')  # Hide the numeric keyboard.
    # Cross-app picker test runs outside Compose/Espresso so the system owns accessibility normally.
    def private_state(name):
        return adb('exec-out', 'run-as', PACKAGE, 'cat', 'shared_prefs/' + name + '.xml')
    comfort_before = private_state('comfort')
    timer_before = private_state('timer')
    tap(find(lambda n: n.get('resource-id', '').split('/')[-1] == 'completion_sound', scroll=True))
    resolved = adb('shell', 'cmd', 'package', 'resolve-activity', '--brief', '-a', 'android.intent.action.RINGTONE_PICKER').decode()
    component = [line.strip() for line in resolved.splitlines() if '/' in line][-1]
    picker_package = component.split('/')[0]
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        picker_tree = tree()
        if any(n.get('package') == picker_package for n in picker_tree.iter('node')):
            break
        time.sleep(.4)
    else:
        raise AssertionError(('Actual system ringtone picker never appeared', component))
    screenshot('system-sound-picker-ru')
    # First Back may hide a system IME. Only issue another while the exact picker remains visible.
    for attempt in range(2):
        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
        time.sleep(1)
        returned = tree()
        if not any(n.get('package') == picker_package for n in returned.iter('node')):
            break
    else:
        raise AssertionError('System picker did not cancel after bounded Back presses')
    assert any(n.get('package') == PACKAGE for n in returned.iter('node')), 'Did not return to Brewdoro'
    assert private_state('comfort') == comfort_before, 'Cancelling picker changed comfort preferences'
    assert private_state('timer') == timer_before, 'Cancelling picker saved unfinished timer draft'
    draft = find(lambda n: n.get('resource-id', '').split('/')[-1] == 'duration_focus', scroll=True, down=False)
    assert draft.get('text') == '1', ('Duration draft did not survive picker', draft.attrib)
    picker_evidence = {'result':'PASS', 'picker':component, 'cancelPreservedComfort':True,
                       'cancelDidNotSaveTimer':True, 'unsavedFocusDraft':'1',
                       'source':'actual system picker opened from exact downloadable APK, outside instrumentation'}
    (OUT / 'sound-picker-evidence.json').write_text(json.dumps(picker_evidence,indent=2)+chr(10))
    tap(text('Сохранить настройки', scroll=True))
    text('Начать')
    # Normal CI tests use reduced motion. Enable system animation only for this actual-APK demo.
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', '1.0')
    # Recreate to observe scale on platforms caching it at composition creation.
    adb('shell', 'am', 'force-stop', PACKAGE)
    adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity')
    time.sleep(1)
    primary = text('Начать')
    cup = find(lambda n: n.get('resource-id', '').split('/')[-1] == 'coffee_cup')
    rect = bounds(cup)
    assert rect[2] > rect[0] and rect[3] > rect[1]
    # Cache geometry before starting: continuous drawing can delay UiAutomator idle waits.
    tap(primary)
    time.sleep(18)
    recording = subprocess.Popen(ADB + ['shell', 'screenrecord', '--time-limit', '6', '--bit-rate', '2000000', '/sdcard/brewdoro-coffee.mp4'], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    frames = []
    for i in range(8):
        frame = screenshot('coffee-motion-frame-' + str(i)).crop(rect)
        frames.append(frame)
        time.sleep(.22)
    output, _ = recording.communicate(timeout=20)
    assert recording.returncode == 0, output.decode(errors='replace')
    recording = None
    adb('pull', '/sdcard/brewdoro-coffee.mp4', str(OUT / 'coffee-motion.mp4'))
    assert (OUT / 'coffee-motion.mp4').stat().st_size > 10_000
    profiles = [surface(frame) for frame in frames]
    # Remove each frame's mean surface height: timer draining alone cannot pass this gate.
    residual = max(max(abs(x-y) for x,y in zip(profiles[0][0], p[0])) for p in profiles[1:])
    assert residual >= .5, ('No phase-dependent surface movement', residual)
    assert max(p[1] for p in profiles) >= 1, 'Surface must be visibly curved, not just vertical fill'
    # GIF is made exclusively from actual screencaps, cropped/zoomed, not a synthetic animation.
    previews = [f.resize((544,344), Image.Resampling.LANCZOS) for f in frames]
    previews[0].save(OUT / 'coffee-motion.gif', save_all=True, append_images=previews[1:], duration=420, loop=0)
    tap(primary)  # Same primary button geometry: now Pause, no accessibility idle wait.
    text('Продолжить')
    time.sleep(1)
    paused_a = screenshot('coffee-paused-a').crop(rect)
    time.sleep(.7)
    paused_b = screenshot('coffee-paused-b').crop(rect)
    assert ImageChops.difference(paused_a, paused_b).getbbox() is None, 'Paused coffee must stop animating'
    evidence = {'result':'PASS', 'source':'exact downloadable APK after instrumentation',
                'motionResidualPixels':round(residual,3), 'surfaceRangesPixels':[p[1] for p in profiles],
                'pausedCupPixelIdentical':True, 'frames':len(frames), 'videoSeconds':6,
                'note':'System animations enabled for demo only. GIF cropped from actual screenshots. No physical audio/vibration claim.'}
    (OUT / 'coffee-motion-evidence.json').write_text(json.dumps(evidence,indent=2)+chr(10))
    print(evidence)
finally:
    if recording is not None:
        recording.terminate()
        recording.wait(timeout=10)
    adb('shell', 'settings', 'put', 'global', 'animator_duration_scale', old_scale)
    screenshot('comfort-final')

#!/usr/bin/env python3
import datetime as dt
import io
import json
import re
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

from PIL import Image

PACKAGE = 'com.santiagorodriguez.countaway'
MAIN = f'{PACKAGE}/.ui.MainActivity'
OUT = Path('store-screenshots')
SIZE = (1080, 1920)
BOUNDS = re.compile(r'\[(\d+),(\d+)\]\[(\d+),(\d+)\]')


def adb(*args, payload=None, timeout=40):
    result = subprocess.run(
        ['adb', *args], input=payload, stdout=subprocess.PIPE,
        stderr=subprocess.PIPE, timeout=timeout, check=False,
    )
    if result.returncode:
        raise RuntimeError(f'adb {" ".join(args)}: {result.stderr.decode(errors="replace")}')
    return result.stdout


def put_private_file(path, data):
    adb('exec-in', 'run-as', PACKAGE, 'sh', '-c',
        f'mkdir -p {path.rsplit("/", 1)[0]} && cat > {path}', payload=data)


def seed_events():
    today = dt.date.today()

    def event(key, title, days, event_type, icon, repeat='none', mode='count_down'):
        return {
            'id': key, 'title': title, 'date': (today + dt.timedelta(days=days)).isoformat(),
            'type': event_type, 'iconKey': icon, 'reminderKey': 'off',
            'repeatRule': repeat, 'countMode': mode,
            'createdAt': '2026-01-01T00:00:00Z',
        }

    events = [
        event('trip', 'Trip to Barcelona', 59, 'trip', 'airplane'),
        event('birthday', "Sophia's birthday", 74, 'birthday', 'cake', 'yearly'),
        event('concert', 'Concert night', 21, 'concert', 'music'),
        event('deadline', 'Project milestone', 35, 'deadline', 'hourglass'),
        event('smokefree', 'Smoke-free', -120, 'custom', 'heart', mode='count_up'),
    ]
    put_private_file('files/countaways.json', json.dumps({
        'schemaVersion': 7, 'events': events,
    }, ensure_ascii=False, separators=(',', ':')).encode('utf-8'))


def hierarchy():
    for _ in range(5):
        try:
            adb('shell', 'uiautomator', 'dump', '/sdcard/store-ui.xml', timeout=20)
            raw = adb('exec-out', 'cat', '/sdcard/store-ui.xml').decode('utf-8')
            return list(ET.fromstring(raw).iter('node'))
        except (RuntimeError, ValueError, ET.ParseError):
            time.sleep(0.6)
    raise RuntimeError('Unable to read the current Android UI hierarchy')


def match(node, *, label=None, view_id=None, foreign=False):
    if foreign and node.get('package') == PACKAGE:
        return False
    if view_id and not node.get('resource-id', '').endswith('/' + view_id):
        return False
    if label:
        fields = (node.get('text', ''), node.get('content-desc', ''))
        if not any(label.casefold() == field.casefold() for field in fields):
            return False
    return True


def locate(*, label=None, view_id=None, foreign=False, timeout=20):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        for node in hierarchy():
            if match(node, label=label, view_id=view_id, foreign=foreign):
                coords = BOUNDS.fullmatch(node.get('bounds', ''))
                if coords:
                    x1, y1, x2, y2 = map(int, coords.groups())
                    if x2 > x1 and y2 > y1:
                        return ((x1 + x2) // 2, (y1 + y2) // 2)
        time.sleep(0.5)
    raise RuntimeError(f'Android control not found: {label or view_id}')


def tap(*, label=None, view_id=None, foreign=False, timeout=20):
    x, y = locate(label=label, view_id=view_id, foreign=foreign, timeout=timeout)
    adb('shell', 'input', 'tap', str(x), str(y))


def long_press(label):
    x, y = locate(label=label)
    adb('shell', 'input', 'swipe', str(x), str(y), str(x), str(y), '950')


def home():
    adb('shell', 'am', 'start', '-W', '-n', MAIN)
    locate(label='Trip to Barcelona')


def capture(number):
    time.sleep(0.9)
    raw = adb('exec-out', 'screencap', '-p')
    with Image.open(io.BytesIO(raw)) as source:
        if source.size != SIZE:
            raise RuntimeError(f'Screenshot {number}: got {source.size}, expected {SIZE}')
        image = source.convert('RGB')
        image.save(OUT / f'{number}.png', format='PNG', optimize=True)
        image.close()


def choose_light_theme():
    home()
    tap(view_id='themeButton')
    tap(label='Light')
    locate(label='Trip to Barcelona')


def pin_widget(title, background):
    home()
    if title == 'Smoke-free':
        adb('shell', 'input', 'swipe', '540', '1430', '540', '650', '420')
    long_press(title)
    locate(view_id='widgetPinSetupRoot')
    tap(label=background)
    tap(label='Dark')
    try:
        tap(label='Add to Home screen', timeout=4)
    except RuntimeError:
        adb('shell', 'input', 'swipe', '540', '1590', '540', '730', '420')
        tap(label='Add to Home screen')
    deadline = time.monotonic() + 22
    labels = {'add', 'add automatically', 'add to home screen', 'add widget'}
    while time.monotonic() < deadline:
        for node in hierarchy():
            if node.get('package') == PACKAGE:
                continue
            if node.get('text', '').casefold().strip() not in labels:
                continue
            coords = BOUNDS.fullmatch(node.get('bounds', ''))
            if coords:
                x1, y1, x2, y2 = map(int, coords.groups())
                adb('shell', 'input', 'tap', str((x1 + x2) // 2), str((y1 + y2) // 2))
                time.sleep(1)
                return
        time.sleep(0.5)
    raise RuntimeError(f'Launcher did not offer a pin confirmation for {title}')


def main():
    OUT.mkdir(exist_ok=True)
    adb('shell', 'wm', 'size', '1080x1920')
    adb('shell', 'wm', 'density', '360')
    adb('install', '-r', 'app/build/outputs/apk/debug/app-debug.apk', timeout=90)
    seed_events()
    adb('shell', 'cmd', 'locale', 'set-app-locales', PACKAGE, '--locales', 'en')
    adb('shell', 'pm', 'grant', PACKAGE, 'android.permission.POST_NOTIFICATIONS')
    home()
    capture(1)

    tap(label="Sophia's birthday")
    locate(view_id='editorRoot')
    capture(2)

    home()
    long_press('Trip to Barcelona')
    locate(view_id='widgetPinSetupRoot')
    tap(label='Ridge')
    tap(label='Dark')
    capture(3)

    home()
    tap(view_id='aboutButton')
    locate(view_id='aboutRoot')
    capture(4)

    choose_light_theme()
    capture(5)

    pin_widget('Trip to Barcelona', 'Ridge')
    pin_widget("Sophia's birthday", 'Sunset')
    pin_widget('Smoke-free', 'Forest')
    adb('shell', 'input', 'keyevent', 'KEYCODE_HOME')
    time.sleep(2.5)
    dump = adb('shell', 'dumpsys', 'appwidget').decode(errors='replace')
    if 'CountdownWidgetProvider' not in dump:
        raise RuntimeError('The emulator launcher has no CountAway widget provider')
    capture(6)

    expected = {f'{index}.png' for index in range(1, 7)}
    actual = {file.name for file in OUT.iterdir()}
    if actual != expected:
        raise RuntimeError(f'Unexpected screenshot set: {sorted(actual)}')
    for file in OUT.iterdir():
        with Image.open(file) as image:
            if image.size != SIZE or image.mode != 'RGB':
                raise RuntimeError(f'Invalid Play screenshot: {file}')
    print('Captured exactly six 1080x1920 RGB PNG screenshots')


if __name__ == '__main__':
    main()

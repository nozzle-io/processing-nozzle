#!/usr/bin/env python3
import os
import platform
import subprocess
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
TOOLS = BUILD / 'tools'
PROCESSING_CORE_VERSION = '4.5.2'
PROCESSING_CORE_URL = f'https://repo1.maven.org/maven2/org/processing/core/{PROCESSING_CORE_VERSION}/core-{PROCESSING_CORE_VERSION}.jar'
PROCESSING_CORE_JAR = TOOLS / f'processing-core-{PROCESSING_CORE_VERSION}.jar'
TEST_CLASSES = BUILD / 'test-classes'
PACKAGE_JAR = BUILD / 'package/processing-nozzle/library/processing-nozzle.jar'


def run(args, **kwargs):
    print('+', ' '.join(str(arg) for arg in args))
    subprocess.run([str(arg) for arg in args], cwd=ROOT, check=True, **kwargs)


def platform_folder_name():
    system = platform.system().lower()
    machine = platform.machine().lower()
    if system == 'darwin':
        os_name = 'macos'
    elif system == 'windows':
        os_name = 'windows'
    elif system == 'linux':
        os_name = 'linux'
    else:
        raise SystemExit(f'unsupported OS: {system}')
    if machine in ('x86_64', 'amd64'):
        arch = 'amd64' if os_name != 'macos' else 'x86_64'
    elif machine in ('arm64', 'aarch64'):
        arch = 'aarch64'
    else:
        raise SystemExit(f'unsupported arch: {machine}')
    return f'{os_name}-{arch}'


def fetch_processing_core():
    TOOLS.mkdir(parents=True, exist_ok=True)
    if PROCESSING_CORE_JAR.exists():
        return
    print(f'downloading {PROCESSING_CORE_URL}')
    urllib.request.urlretrieve(PROCESSING_CORE_URL, PROCESSING_CORE_JAR)


def classpath_separator():
    return ';' if platform.system().lower() == 'windows' else ':'


def main():
    if not PACKAGE_JAR.exists():
        raise SystemExit(f'missing packaged jar: {PACKAGE_JAR}; run scripts/build_package.py first')
    fetch_processing_core()
    TEST_CLASSES.mkdir(parents=True, exist_ok=True)
    separator = classpath_separator()
    compile_classpath = separator.join([str(PACKAGE_JAR), str(PROCESSING_CORE_JAR)])
    run(['javac', '--release', '17', '-cp', compile_classpath, '-d', TEST_CLASSES, ROOT / 'tests/ProcessingSketchSmoke.java'])
    runtime_classpath = separator.join([str(PACKAGE_JAR), str(TEST_CLASSES), str(PROCESSING_CORE_JAR)])
    native_path = BUILD / 'package/processing-nozzle/library' / platform_folder_name()
    env = os.environ.copy()
    env['JAVA_TOOL_OPTIONS'] = (env.get('JAVA_TOOL_OPTIONS', '') + ' -Djava.awt.headless=false').strip()
    run([
        'java',
        f'-Djava.library.path={native_path}',
        '-cp', runtime_classpath,
        'processing.nozzle.ProcessingSketchSmoke',
    ], env=env)


if __name__ == '__main__':
    main()

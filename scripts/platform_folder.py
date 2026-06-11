#!/usr/bin/env python3
import platform
import sys

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
print(f'build/package/processing-nozzle/library/{os_name}-{arch}')

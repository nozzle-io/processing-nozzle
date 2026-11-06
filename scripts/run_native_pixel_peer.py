#!/usr/bin/env python3
"""Run production Java/JNI against an independent native Metal pixel peer."""
import os
import platform
from pathlib import Path

from build_package import BUILD, CLASSES, LIBRARY, ROOT, platform_folder_name, run


def main():
    if platform.system() != 'Darwin':
        raise SystemExit('this native Metal evidence lane requires macOS')
    output = BUILD / 'pixel-peer'
    output.mkdir(parents=True, exist_ok=True)
    classes = output / 'classes'
    classes.mkdir(exist_ok=True)
    library = LIBRARY / platform_folder_name()
    java_home = Path(os.environ['JAVA_HOME'])
    peer = output / 'libnative_pixel_peer.dylib'
    run([os.environ.get('CXX', 'c++'), '-std=c++17', '-fPIC', '-shared',
         '-I', ROOT / 'deps/nozzle/include', '-I', java_home / 'include',
         '-I', java_home / 'include/darwin', ROOT / 'tests/native_pixel_peer.cpp',
         '-L', library, '-lnozzle', '-Wl,-rpath,' + str(library), '-o', peer])
    run(['javac', '--release', '17', '-cp', CLASSES, '-d', classes,
         ROOT / 'tests/NativePixelPeer.java'])
    run(['java', '-Djava.library.path=' + str(library), '-cp',
         os.pathsep.join([str(CLASSES), str(classes)]),
         'processing.nozzle.NativePixelPeer', peer])


if __name__ == '__main__':
    main()

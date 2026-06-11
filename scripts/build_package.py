#!/usr/bin/env python3
import os
import platform
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
BUILD = ROOT / 'build'
CLASSES = BUILD / 'classes'
PACKAGE = BUILD / 'package' / 'processing-nozzle'
LIBRARY = PACKAGE / 'library'


def run(args, **kwargs):
    print('+', ' '.join(str(a) for a in args))
    subprocess.run([str(a) for a in args], cwd=ROOT, check=True, **kwargs)


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


def native_name():
    if platform.system().lower() == 'windows':
        return 'processing_nozzle_jni.dll'
    if platform.system().lower() == 'darwin':
        return 'libprocessing_nozzle_jni.dylib'
    return 'libprocessing_nozzle_jni.so'


def nozzle_library_name():
    if platform.system().lower() == 'windows':
        return 'nozzle.dll'
    if platform.system().lower() == 'darwin':
        return 'libnozzle.dylib'
    return 'libnozzle.so'


def build_java():
    sources = sorted(str(p) for p in (ROOT / 'src/main/java').rglob('*.java'))
    if not sources:
        raise SystemExit('no Java sources')
    CLASSES.mkdir(parents=True, exist_ok=True)
    run(['javac', '--release', '17', '-d', CLASSES, *sources])
    LIBRARY.mkdir(parents=True, exist_ok=True)
    run(['jar', '--create', '--file', LIBRARY / 'processing-nozzle.jar', '-C', CLASSES, '.'])


def build_nozzle_library():
    build_dir = BUILD / 'nozzle-cmake'
    install_dir = BUILD / 'nozzle-install'
    build_dir.mkdir(parents=True, exist_ok=True)
    configure = [
        'cmake', '-S', ROOT / 'deps/nozzle', '-B', build_dir,
        '-DBUILD_SHARED_LIBS=ON',
        '-DNOZZLE_BUILD_TESTS=OFF',
        '-DNOZZLE_BUILD_EXAMPLES=OFF',
        '-DNOZZLE_INSTALL=ON',
        '-DCMAKE_INSTALL_PREFIX=' + str(install_dir),
    ]
    if platform.system().lower() == 'darwin':
        configure.append('-DCMAKE_INSTALL_RPATH=@loader_path')
    elif platform.system().lower() == 'linux':
        configure.append('-DCMAKE_INSTALL_RPATH=$ORIGIN')
    run(configure)
    run(['cmake', '--build', build_dir, '--config', 'Release', '--parallel', '2'])
    run(['cmake', '--install', build_dir, '--config', 'Release'])
    candidates = list(install_dir.rglob(nozzle_library_name()))
    if not candidates:
        raise SystemExit(f'missing built nozzle library {nozzle_library_name()} under {install_dir}')
    return candidates[0]


def build_native():
    native_dir = BUILD / 'native'
    native_dir.mkdir(parents=True, exist_ok=True)
    include_root = ROOT / 'deps/nozzle/include'
    nozzle_library = build_nozzle_library()
    java_home = Path(os.environ.get('JAVA_HOME', Path(sys.executable).anchor))
    if 'JAVA_HOME' not in os.environ:
        javac = shutil.which('javac')
        if not javac:
            raise SystemExit('javac not found and JAVA_HOME is unset')
        java_home = Path(javac).resolve().parents[1]
    jni_include = java_home / 'include'
    if platform.system().lower() == 'darwin':
        jni_platform = jni_include / 'darwin'
        compiler = os.environ.get('CXX', 'c++')
        output = native_dir / native_name()
        args = [compiler, '-std=c++17', '-fPIC', '-shared', '-I', include_root, '-I', jni_include, '-I', jni_platform, ROOT / 'src/main/native/processing_nozzle_jni.cpp', str(nozzle_library), '-Wl,-rpath,@loader_path', '-o', output]
    elif platform.system().lower() == 'linux':
        jni_platform = jni_include / 'linux'
        compiler = os.environ.get('CXX', 'c++')
        output = native_dir / native_name()
        args = [compiler, '-std=c++17', '-fPIC', '-shared', '-I', include_root, '-I', jni_include, '-I', jni_platform, ROOT / 'src/main/native/processing_nozzle_jni.cpp', str(nozzle_library), '-Wl,-rpath,$ORIGIN', '-o', output]
    elif platform.system().lower() == 'windows':
        jni_platform = jni_include / 'win32'
        output = native_dir / native_name()
        import_library = next(nozzle_library.parents[1].rglob('nozzle.lib'), None)
        if import_library is None:
            raise SystemExit('missing nozzle.lib for JNI link')
        args = ['cl', '/nologo', '/std:c++17', '/EHsc-', '/GR-', '/LD', '/I', include_root, '/I', jni_include, '/I', jni_platform, ROOT / 'src/main/native/processing_nozzle_jni.cpp', str(import_library), '/Fe:' + str(output)]
    else:
        raise SystemExit('unsupported native platform')
    run(args)
    platform_dir = LIBRARY / platform_folder_name()
    platform_dir.mkdir(parents=True, exist_ok=True)
    shutil.copy2(output, platform_dir / native_name())
    shutil.copy2(nozzle_library, platform_dir / nozzle_library_name())


def copy_package_files():
    shutil.copy2(ROOT / 'library.properties', PACKAGE / 'library.properties')
    shutil.copytree(ROOT / 'examples', PACKAGE / 'examples', dirs_exist_ok=True)
    shutil.copytree(ROOT / 'reference', PACKAGE / 'reference', dirs_exist_ok=True)


def write_zip():
    short = subprocess.check_output(['git', 'rev-parse', '--short', 'HEAD'], cwd=ROOT, text=True).strip()
    zip_path = BUILD / f'processing-nozzle-latest-{short}.zip'
    if zip_path.exists():
        zip_path.unlink()
    with zipfile.ZipFile(zip_path, 'w', zipfile.ZIP_DEFLATED) as zf:
        for path in sorted(PACKAGE.rglob('*')):
            if path.is_file():
                zf.write(path, path.relative_to(BUILD / 'package'))
    print(f'wrote {zip_path}')


def main():
    if BUILD.exists():
        shutil.rmtree(BUILD)
    PACKAGE.mkdir(parents=True, exist_ok=True)
    build_java()
    build_native()
    copy_package_files()
    write_zip()


if __name__ == '__main__':
    main()

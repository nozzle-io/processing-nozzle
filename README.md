# processing-nozzle

Initial Processing 4 library implementation package for nozzle diagnostics, source discovery API shape, deterministic CPU pixel-pattern tests, and a JNI native-load/control helper.

This is an implementation spike, not a GPU texture support claim.

## Target

- Processing release target: Processing 4.5.2 (`processing-1313-4.5.2`), tag commit `c5fec0554ab6292e5984ac3e202c6577c87a42fa`.
- Java target: JDK 17.
- nozzle submodule: `deps/nozzle` pinned to `a8efca3c847c39b76057a8e77f94b34146cc9125`.

## Scope

Implemented:

- Processing `libraries/` package layout under `build/package/processing-nozzle/`.
- Java API in package `processing.nozzle`.
- JNI native helper (`processing_nozzle_jni`) compiled per host platform and linked to the bundled `libnozzle`/`nozzle.dll`.
- Deterministic CPU pattern/oracle helpers for `320x240` and `641x479` that check orientation, R/B ordering, and alpha bytes.
- Examples: `NozzleDiagnostics`, `NozzleSenderCpu`, `NozzleReceiverCpu`.
- Package-shape validation for Processing library metadata, jar, native folder, bundled nozzle shared library, examples, reference, and zip top-level layout.
- Linux Processing runtime smoke attempts deterministic Processing ARGB -> nozzle CPU-copy sender -> independent nozzle receiver interop for `320x240` and `641x479`, and reports explicit `PASS` or `MISSING_HOST_SMOKE` markers instead of claiming GPU/PJOGL support.

Not claimed:

- No zero-copy GPU support.
- No Processing PJOGL texture send/receive support.
- No npm/Maven/Processing contribution publication.
- No proof that `nozzle.java` is sufficient Processing/PJOGL evidence.
- Linux CI runs a real Processing `PApplet` sketch smoke through `org.processing:core:4.5.2`, using the packaged `processing-nozzle.jar` and native helper.
- Processing PJOGL texture paths remain `MISSING_HOST_SMOKE`; zero-copy/GPU-copy cost remains `UNPROVEN`. Any passing frame path is CPU-copy only.

## Build

```bash
python3 scripts/build_package.py
python3 scripts/check_package.py
java -Djava.library.path="$(python3 scripts/platform_folder.py)" -cp build/classes processing.nozzle.NozzleSelfTest
```

The build writes:

```text
build/package/processing-nozzle/
build/processing-nozzle-latest-<short_sha>.zip
```

The zip unpacks directly to `processing-nozzle/` with no extra wrapper directory.

## Evidence table

| Path | Status | Evidence boundary |
| --- | --- | --- |
| Java API compile | PASS in CI | `javac --release 17` |
| JNI native load | PASS in CI | `NozzleSelfTest` loads `processing_nozzle_jni` and the bundled nozzle shared library from the Processing 4 platform folder |
| Real Processing sketch runtime | PASS on Linux CI | `xvfb-run -a python3 scripts/run_processing_sketch_smoke.py` starts a `PApplet`, runs `setup()`/`draw()`, loads packaged jar/native helper, and exits deterministically |
| CPU pattern oracle 320x240 | PASS in Java self-test and Processing sketch smoke | deterministic RGBA bytes; runtime smoke verifies no y-flip, no R/B swap, alpha, and byte-size mismatch detection |
| CPU pattern oracle 641x479 | PASS in Java self-test and Processing sketch smoke | odd-size deterministic RGBA bytes; runtime smoke verifies no y-flip, no R/B swap, alpha, and byte-size mismatch detection |
| Processing package layout | PASS in CI | `scripts/check_package.py` validates `library.properties`, jar, native folder, bundled nozzle shared library, examples, reference, zip root |
| Processing CPU pixels -> nozzle sender -> independent nozzle receiver | PASS or MISSING_HOST_SMOKE from Linux runtime marker | real Processing `PApplet` attempts deterministic ARGB publish via JNI/nozzle writable frame and independent receiver validation for `320x240` and `641x479`; if PASS, copy cost is `cpu-copy`; backend/device blockers remain explicit MISSING evidence |
| PJOGL/OpenGL texture path | MISSING_HOST_SMOKE | no render-thread PJOGL texture evidence; zero-copy/GPU-copy remains `UNPROVEN` |

## License

MIT

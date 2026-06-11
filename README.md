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
- JNI native helper load check (`processing_nozzle_jni`) compiled per host platform.
- Deterministic CPU pattern/oracle helpers for `320x240` and `641x479` that check orientation, R/B ordering, and alpha bytes.
- Examples: `NozzleDiagnostics`, `NozzleSenderCpu`, `NozzleReceiverCpu`.
- Package-shape validation for Processing library metadata, jar, native folder, examples, reference, and zip top-level layout.

Not claimed:

- No zero-copy GPU support.
- No Processing PJOGL texture send/receive support.
- No npm/Maven/Processing contribution publication.
- No proof that `nozzle.java` is sufficient Processing/PJOGL evidence.
- Linux CI runs a real Processing `PApplet` sketch smoke through `org.processing:core:4.5.2`, using the packaged `processing-nozzle.jar` and native helper.
- No real nozzle sender/frame interop proof yet; runtime smoke classifies sender/receiver and PJOGL texture paths as `MISSING_HOST_SMOKE` with copy cost `UNPROVEN`.

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
| JNI native load | PASS in CI | `NozzleSelfTest` loads `processing_nozzle_jni` from Processing 4 platform folder |
| Real Processing sketch runtime | PASS on Linux CI | `xvfb-run -a python3 scripts/run_processing_sketch_smoke.py` starts a `PApplet`, runs `setup()`/`draw()`, loads packaged jar/native helper, and exits deterministically |
| CPU pattern oracle 320x240 | PASS in Java self-test and Processing sketch smoke | deterministic RGBA bytes; runtime smoke verifies no y-flip, no R/B swap, alpha, and byte-size mismatch detection |
| CPU pattern oracle 641x479 | PASS in Java self-test and Processing sketch smoke | odd-size deterministic RGBA bytes; runtime smoke verifies no y-flip, no R/B swap, alpha, and byte-size mismatch detection |
| Processing package layout | PASS in CI | `scripts/check_package.py` validates `library.properties`, jar, native folder, examples, reference, zip root |
| Actual nozzle sender/receiver runtime | MISSING_HOST_SMOKE | Processing sketch constructs sender/receiver and reports explicit missing status; no frame interop attempted/proven |
| PJOGL/OpenGL texture path | MISSING_HOST_SMOKE | no zero-copy/GPU claim without render-thread texture evidence; copy cost remains `UNPROVEN` |

## License

MIT

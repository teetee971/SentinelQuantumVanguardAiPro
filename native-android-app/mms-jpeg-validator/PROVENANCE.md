# Strict JPEG decoder dependency

libjpeg-turbo 3.2.1, upstream commit `7b4993786d856cbccabfc0ed2f923ba000e326cc`.
Source: https://codeload.github.com/libjpeg-turbo/libjpeg-turbo/tar.gz/refs/tags/3.2.1
Downloaded archive SHA-256: `6f5819c51649bce2571a23b1b0cd41eae2f57fc3f8908c7ec9e7047efb65ca13`.
The GitHub release asset is a different archive; its published digest must not be used for this archive.
Sources and licenses are vendored unchanged. No network resolution during CMake builds.
SIMD, TurboJPEG, tools and upstream tests are disabled for the Android library.
The validator promotes all decoder warnings to errors and fully consumes entropy at 1/8 pixel resolution.
It is independent of the Android decoder and used before pass-through as well as transcoding.
Native allocations have per-image dimension and progressive coefficient estimates; the decoder's no-backing-store memory setting is 64 MiB.
This is not a process-wide memory quota. Each concurrent call owns its decoder.

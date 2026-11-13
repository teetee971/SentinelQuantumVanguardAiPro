#!/usr/bin/env bash
set -euo pipefail

if [ "$#" -lt 1 ] || [ "$#" -gt 2 ]; then
  echo "usage: $0 /absolute/path/to/llama.cpp [build-dir]" >&2
  exit 2
fi

source_dir=$1
build_dir=${2:-"$source_dir/build-sentinel-termux"}
expected_commit='b79696f1f556cb30b0c184838115007d929baca8'

if [ ! -d "$source_dir/.git" ]; then
  echo 'LLAMA_SOURCE_MUST_BE_A_GIT_CHECKOUT' >&2
  exit 2
fi
actual_commit=$(git -C "$source_dir" rev-parse HEAD)
if [ "$actual_commit" != "$expected_commit" ]; then
  echo "LLAMA_COMMIT_MISMATCH expected=$expected_commit actual=$actual_commit" >&2
  exit 2
fi
case "$(uname -m)" in
  aarch64|arm64) ;;
  *) echo 'UNSUPPORTED_ARCH_EXPECTED_ARM64' >&2; exit 2 ;;
esac

cmake -S "$source_dir" -B "$build_dir" \
  -DCMAKE_BUILD_TYPE=Release \
  -DGGML_NATIVE=ON \
  -DGGML_OPENMP=OFF \
  -DGGML_LLAMAFILE=OFF \
  -DLLAMA_OPENSSL=OFF \
  -DLLAMA_BUILD_SERVER=OFF \
  -DLLAMA_BUILD_TESTS=OFF \
  -DLLAMA_BUILD_EXAMPLES=ON
cmake --build "$build_dir" --config Release --target llama-cli -j "${JOBS:-2}"
printf 'LLAMA_BUILD_OK commit=%s binary=%s\n' "$actual_commit" "$build_dir/bin/llama-cli"

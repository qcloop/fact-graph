#!/usr/bin/env bash
set -euo pipefail

demo_dir="$(cd -- "$(dirname -- "$0")" && pwd)"
modules=("$demo_dir"/../js/target/scala-*/factgraph-fastopt/main.mjs)
if [[ ${#modules[@]} -ne 1 || ! -f "${modules[0]}" ]]; then
  echo "Run a clean sbt 'factGraphJS/fastLinkJS' build first." >&2
  exit 1
fi
cp "${modules[0]}" "$demo_dir/fg.generated.mjs"
cp "${modules[0]}.map" "$demo_dir/main.mjs.map"

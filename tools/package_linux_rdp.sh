#!/usr/bin/env bash
set -euo pipefail

prefix=${1:?FreeRDP install prefix required}
native=${2:?libncatrdp.so path required}
output=${3:?output ZIP path required}
freerdp_source=${4:?FreeRDP source directory required}

stage=$(mktemp -d)
temporary=
trap 'rm -rf "$stage"; if [[ -n "$temporary" ]]; then rm -f "$temporary"; fi' EXIT

cp -L "$native" "$stage/libncatrdp.so"
for name in libfreerdp-client3.so.3 libfreerdp3.so.3 libwinpr3.so.3; do
    cp -L "$prefix/lib/$name" "$stage/$name"
done
cp "$freerdp_source/LICENSE" "$stage/FreeRDP-LICENSE.txt"
cp -L /usr/lib/x86_64-linux-gnu/ossl-modules/legacy.so "$stage/legacy.so"

printf 'Bundled Linux libraries and their distribution copyright notices\n' > "$stage/THIRD-PARTY-NOTICES.txt"
LD_LIBRARY_PATH="$prefix/lib" ldd "$native" |
    awk '$2 == "=>" && $3 ~ /^\// { print $1 "|" $3 }' > "$stage/dependencies.list"

while IFS='|' read -r name path; do
    case "$name" in
        libfreerdp*|libwinpr*|libc.so.*|libm.so.*|libgcc_s.so.*|libstdc++.so.*|libresolv.so.*)
            continue
            ;;
    esac
    cp -L "$path" "$stage/$name"
    package=$(dpkg-query -S "$path" 2>/dev/null | head -n 1 | cut -d: -f1 || true)
    if [[ -z "$package" ]]; then
        package=$(dpkg-query -S "$(readlink -f "$path")" 2>/dev/null | head -n 1 | cut -d: -f1 || true)
    fi
    printf '\n===== %s (%s) =====\n' "$name" "${package:-unknown package}" >> "$stage/THIRD-PARTY-NOTICES.txt"
    if [[ -n "$package" && -f "/usr/share/doc/$package/copyright" ]]; then
        cat "/usr/share/doc/$package/copyright" >> "$stage/THIRD-PARTY-NOTICES.txt"
    else
        printf 'Copyright file not installed in build image.\n' >> "$stage/THIRD-PARTY-NOTICES.txt"
    fi
done < "$stage/dependencies.list"

rm "$stage/dependencies.list"
for library in "$stage"/*.so*; do
    patchelf --set-rpath '$ORIGIN' "$library"
done

mkdir -p "$(dirname "$output")"
output=$(realpath -m "$output")
temporary="$output.$$.$RANDOM.tmp"
(cd "$stage" && zip -q -X -j "$temporary" ./*)
mv -f "$temporary" "$output"
temporary=

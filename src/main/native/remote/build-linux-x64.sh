#!/usr/bin/env bash
set -euo pipefail

source_dir=${1:-/source}
bridge_dir=${2:-/bridge}
output_dir=${3:-/out}
build_dir=/tmp/ncat-rdp-linux-build
install_dir=$build_dir/install
bundle_dir=$build_dir/bundle

test -f "$source_dir/CMakeLists.txt"
test -f "$bridge_dir/rdp_jni.c"
test -d "$output_dir"

cmake -S "$source_dir" -B "$build_dir/freerdp" -G Ninja \
    -DCMAKE_BUILD_TYPE=Release \
    -DCMAKE_INSTALL_PREFIX="$install_dir" \
    -DWITH_CLIENT=OFF \
    -DWITH_CLIENT_COMMON=ON \
    -DWITH_CLIENT_CHANNELS=ON \
    -DWITH_SERVER=OFF \
    -DWITH_X11=OFF \
    -DWITH_WAYLAND=OFF \
    -DWITH_MANPAGES=OFF \
    -DWITH_SAMPLE=OFF \
    -DBUILD_TESTING=OFF \
    -DWITH_FFMPEG=OFF \
    -DWITH_SWSCALE=OFF \
    -DWITH_ALSA=OFF \
    -DWITH_PULSE=OFF \
    -DWITH_CUPS=OFF \
    -DWITH_FUSE=OFF \
    -DWITH_PCSC=OFF \
    -DWITH_JSON_DISABLED=ON \
    -DWITH_ABSOLUTE_PLUGIN_LOAD_PATHS=OFF \
    -DWITH_CCACHE=OFF

cmake --build "$build_dir/freerdp" --parallel 6
cmake --install "$build_dir/freerdp"

export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PKG_CONFIG_PATH="$install_dir/lib/pkgconfig"
cmake -S "$bridge_dir" -B "$build_dir/bridge" -G Ninja -DCMAKE_BUILD_TYPE=Release
cmake --build "$build_dir/bridge" --parallel 4

mkdir -p "$bundle_dir"
cp "$build_dir/bridge/ncatrdp.so" "$bundle_dir/libncatrdp.so"
cp -L "$install_dir/lib/libfreerdp-client3.so.3" "$bundle_dir/libfreerdp-client3.so.3"
cp -L "$install_dir/lib/libfreerdp3.so.3" "$bundle_dir/libfreerdp3.so.3"
cp -L "$install_dir/lib/libwinpr3.so.3" "$bundle_dir/libwinpr3.so.3"

copy_dependencies() {
    local library=$1
    local name
    local path
    while read -r name path; do
        case "$name" in
            libc.so.6|libm.so.6|libdl.so.2|libpthread.so.0|librt.so.1|libresolv.so.2) continue ;;
        esac
        if test ! -f "$bundle_dir/$name"; then
            cp -L "$path" "$bundle_dir/$name"
        fi
    done < <(ldd "$library" | awk '$2 == "=>" && $3 ~ /^\// { print $1, $3 }')
}

export LD_LIBRARY_PATH="$bundle_dir"
previous_count=0
while true; do
    for library in "$bundle_dir"/*.so*; do
        copy_dependencies "$library"
    done
    current_count=$(find "$bundle_dir" -maxdepth 1 -type f | wc -l)
    test "$current_count" -eq "$previous_count" && break
    previous_count=$current_count
done

for library in "$bundle_dir"/*.so*; do
    patchelf --set-rpath '$ORIGIN' "$library"
done

cp "$source_dir/LICENSE" "$bundle_dir/LICENSE-FreeRDP.txt"

for library in "$bundle_dir"/*.so*; do
    if ldd "$library" | grep -q 'not found'; then
        echo "Dependência ausente em $library" >&2
        exit 1
    fi
done

(
    cd "$bundle_dir"
    zip -q -9 "$output_dir/linux-x64.zip" ./*
)

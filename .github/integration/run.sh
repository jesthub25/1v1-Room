#!/usr/bin/env bash
# Gercek Paper sunucusunda 1v1Arena entegrasyon testi.
# Kullanim: run.sh <paper-surumu|latest> <plugin-jar>
set -euo pipefail

SPEC="$1"
PLUGIN_JAR="$(realpath "$2")"
HERE="$(cd "$(dirname "$0")" && pwd)"
SERVER="$(pwd)/server-$SPEC"
rm -rf "$SERVER"
mkdir -p "$SERVER/plugins/1v1Arena"

VERSION="$(python3 "$HERE/fetch.py" "$SPEC" "$SERVER")"
echo "Test edilen Paper surumu: $VERSION"
echo "PAPER_VERSION=$VERSION" >> "${GITHUB_ENV:-/dev/null}"

MAJOR="${VERSION%%.*}"
if [ "$MAJOR" -ge 26 ] && [ -n "${JAVA_HOME_25_X64:-}" ]; then
  JAVA="$JAVA_HOME_25_X64/bin/java"
else
  JAVA="${JAVA_HOME_21_X64:-$JAVA_HOME}/bin/java"
fi
"$JAVA" -version

cp "$PLUGIN_JAR" "$SERVER/plugins/1v1Arena.jar"

# Test icin kisa sureler
sed -e 's/^  mac-suresi: .*/  mac-suresi: 15/' \
    -e 's/^  bitis-bekleme-suresi: .*/  bitis-bekleme-suresi: 8/' \
    -e 's/^  kapi-silme-suresi: .*/  kapi-silme-suresi: 1/' \
    "$HERE/../../src/main/resources/config.yml" > "$SERVER/plugins/1v1Arena/config.yml"

cat > "$SERVER/eula.txt" <<EOF
eula=true
EOF
cat > "$SERVER/server.properties" <<EOF
online-mode=false
enable-rcon=true
rcon.password=test
rcon.port=25575
server-port=25565
level-type=minecraft\:flat
generate-structures=false
spawn-protection=0
difficulty=peaceful
gamemode=survival
pvp=true
view-distance=4
simulation-distance=4
max-players=10
spawn-monsters=false
spawn-animals=false
enforce-secure-profile=false
EOF
cat > "$SERVER/bukkit.yml" <<EOF
settings:
  connection-throttle: -1
EOF

cd "$SERVER"
"$JAVA" -Xms1G -Xmx2G -Dcom.mojang.eula.agree=true -jar paper.jar --nogui > console.log 2>&1 &
PID=$!
cleanup() { kill "$PID" 2>/dev/null || true; }
trap cleanup EXIT

for i in $(seq 1 300); do
  if grep -q "Done (" console.log 2>/dev/null; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then echo "Sunucu kapandi!"; tail -100 console.log; exit 1; fi
  sleep 1
done
grep -q "Done (" console.log || { echo "Sunucu 300 sn icinde acilmadi"; tail -100 console.log; exit 1; }
echo "Sunucu hazir."

set +e
node "$HERE/test.js" "$VERSION" "$(cat worldedit.txt)"
RESULT=$?
set -e

node -e "
const {Rcon}=require('$HERE/node_modules/rcon-client');
Rcon.connect({host:'127.0.0.1',port:25575,password:'test'}).then(async r=>{await r.send('stop');r.end();}).catch(()=>{});
" || true
for i in $(seq 1 60); do kill -0 "$PID" 2>/dev/null || break; sleep 1; done

echo "================ 1v1Arena log satirlari ================"
grep -E "1v1Arena|org\.jestron" console.log || true
echo "========================================================"
echo "================ WorldEdit log satirlari ==============="
grep -iE "worldedit" console.log | head -40 || true
echo "========================================================"

if ! grep -q "1v1Arena v.* aktif edildi" console.log; then
  echo "HATA: Plugin aktif edilemedi"; RESULT=1
fi
if grep -nE "at org\.jestron|Could not pass event .* to 1v1Arena|Error occurred while enabling 1v1Arena|Exception.*1v1Arena|\[1v1Arena\].*(WARN|ERROR)|(WARN|ERROR)\]: \[1v1Arena\]" console.log; then
  echo "HATA: Sunucu logunda plugin hatasi/uyarisi var"; RESULT=1
fi
exit $RESULT

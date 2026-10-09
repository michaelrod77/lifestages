#!/usr/bin/env bash
# Look up Minecraft / Fabric API classes without decompiling the whole game.
#   mc.sh find <regex>        list matching class names
#   mc.sh sig <class>         javap signatures (public + private)
#   mc.sh src <class>         decompile one class (and its inner classes) with Vineflower
set -e
JAVA_HOME="${JAVA_HOME:-/c/Program Files/Microsoft/jdk-25.0.4.101-hotspot}"
LOOM=/c/dev/lifestages/.gradle/loom-cache/minecraftMaven/net/minecraft
M2=/c/Users/micha/.gradle/caches/modules-2/files-2.1
JARS=$(ls $LOOM/minecraft-common-*/26.3/*.jar $LOOM/minecraft-clientOnly-*/26.3/*.jar 2>/dev/null; find $M2/net.fabricmc.fabric-api -name "*.jar" ! -name "*sources*"; find $M2/net.fabricmc/fabric-loader -name "*.jar" ! -name "*sources*")
CP=$(for j in $JARS; do cygpath -w "$j"; done | tr "
" ";")
VF=$(find $M2/org.vineflower -name "vineflower-*.jar" | head -1)
case "$1" in
  find) for j in $JARS; do unzip -Z1 "$j" 2>/dev/null | grep '\.class$' | grep -v '\$' | sed 's/\.class$//; s#/#.#g' ; done | grep -E "$2" | sort -u ;;
  sig) "$JAVA_HOME/bin/javap" -p -cp "$CP" "$2" ;;
  src)
    path=$(echo "$2" | tr . /)
    tmp=$(mktemp -d)
    for j in $JARS; do if unzip -Z1 "$j" 2>/dev/null | grep -q "^$path\.class$"; then (cd $tmp && unzip -q -o "$j" "$path.class" "$path\$*.class" 2>/dev/null || true); break; fi; done
    "$JAVA_HOME/bin/java" -jar "$(cygpath -w "$VF")" -log=ERROR "$(cygpath -w "$tmp")" "$(cygpath -w "$tmp/out")" >/dev/null 2>&1
    find "$tmp/out" -name "*.java" -exec cat {} \;
    rm -rf "$tmp" ;;
esac

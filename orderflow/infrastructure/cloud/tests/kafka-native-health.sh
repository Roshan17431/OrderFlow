#!/bin/sh
# LOCAL NATIVE IMAGE ONLY: apache/kafka-native has no JVM CLI scripts.
# Read-only Kafka ApiVersions v0 request, correlation ID123; validate actual
# protocol response/errorcode/API count instead of accepting an open TCP socket.
set -eu
printf '\000\000\000\012\000\022\000\000\000\000\000\173\000\000' |
  nc -w 3 127.0.0.1 29092 |
  od -An -v -t u1 -N14 |
  awk '{for(i=1;i<=NF;i++)b[++n]=$i} END {exit !(n==14 && b[5]==0 && b[6]==0 && b[7]==0 && b[8]==123 && b[9]==0 && b[10]==0 && (b[11]+b[12]+b[13]+b[14])>0)}'

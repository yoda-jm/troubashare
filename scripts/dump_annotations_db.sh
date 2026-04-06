#!/usr/bin/env bash
# Dumps the TroubaShare annotation tables from the on-device Room database.
# Usage:  ./scripts/dump_annotations_db.sh
# Requires: device connected via USB with USB debugging enabled (adb devices should show it).

set -euo pipefail

ADB=~/Android/Sdk/platform-tools/adb
SQLITE=~/Android/Sdk/platform-tools/sqlite3
PKG="com.troubashare"
DB_NAME="troubashare_database"
TMP_DIR="/tmp/troubashare_debug"

mkdir -p "$TMP_DIR"
LOCAL_DB="$TMP_DIR/${DB_NAME}.db"

echo "=== TroubaShare Annotation DB Dump ==="
echo

# Check device
DEVICE=$($ADB get-state 2>/dev/null || echo "unknown")
if [[ "$DEVICE" != "device" ]]; then
  echo "ERROR: No device connected (state=$DEVICE). Connect your phone/emulator and enable USB debugging."
  exit 1
fi

echo "Device: $($ADB shell getprop ro.product.model 2>/dev/null | tr -d '\r')"
echo "App version: $($ADB shell dumpsys package $PKG 2>/dev/null | grep versionName | head -1 | tr -d ' \r')"
echo

# Pull the database by streaming via run-as cat (works on MIUI without root or sdcard access).
# exec-out is used (not shell) so the binary file is not mangled by CRLF conversion.
DB_PATH="/data/data/${PKG}/databases/${DB_NAME}"
echo "Streaming database from $DB_PATH ..."
$ADB exec-out "run-as $PKG cat '$DB_PATH'" > "$LOCAL_DB"

if [[ ! -s "$LOCAL_DB" ]]; then
  echo "ERROR: Could not pull database (file empty or missing). Make sure the app has been run at least once."
  exit 1
fi

echo "Database pulled to $LOCAL_DB ($(du -h "$LOCAL_DB" | cut -f1))"
echo

run_query() {
  local label="$1"
  local sql="$2"
  echo "── $label ──"
  $SQLITE "$LOCAL_DB" "$sql" 2>/dev/null || echo "(query failed)"
  echo
}

run_query "DB schema version (user_version)" \
  "PRAGMA user_version;"

run_query "All annotation rows" \
  "SELECT fileId, memberId, pageNumber, id FROM annotations ORDER BY fileId, memberId, pageNumber;"

run_query "Stroke counts per annotation" \
  "SELECT a.fileId, a.memberId, a.pageNumber, a.id as annId, COUNT(s.id) as strokeCount
   FROM annotations a
   LEFT JOIN annotation_strokes s ON s.annotationId = a.id
   GROUP BY a.id
   ORDER BY a.fileId, a.memberId, a.pageNumber;"

run_query "Stroke counts per memberId (summary)" \
  "SELECT a.memberId, COUNT(DISTINCT a.id) as annotationRows, COUNT(s.id) as totalStrokes
   FROM annotations a
   LEFT JOIN annotation_strokes s ON s.annotationId = a.id
   GROUP BY a.memberId;"

run_query "All song files (PDF/IMAGE only — checking IDs)" \
  "SELECT id, songId, fileName, fileType, uploadedBy FROM song_files WHERE fileType IN ('PDF','IMAGE') ORDER BY songId;"

run_query "File selections (member assignments)" \
  "SELECT songFileId, memberId, selectionType FROM file_selections ORDER BY songFileId, memberId;"

run_query "Members" \
  "SELECT id, name FROM members ORDER BY name;"

echo "=== Done. Full DB at: $LOCAL_DB ==="
echo "You can query it interactively with:"
echo "  ~/Android/Sdk/platform-tools/sqlite3 $LOCAL_DB"
echo ""
echo "Quick re-pull + verify command:"
echo "  $0 && ~/Android/Sdk/platform-tools/sqlite3 $LOCAL_DB 'SELECT memberId, COUNT(*) FROM annotations GROUP BY memberId;'"

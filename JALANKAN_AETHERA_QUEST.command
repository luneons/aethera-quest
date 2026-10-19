#!/usr/bin/env bash
# Launcher klik-dua-kali untuk macOS (Finder).
#
# Finder menjalankan file .command di Terminal; script ini memakai launcher
# Unix bersama, lalu menahan jendela tetap terbuka bila bot berhenti karena
# error — perilaku yang sama dengan JALANKAN_AETHERA_QUEST.bat di Windows.
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

if "$SCRIPT_DIR/JALANKAN_AETHERA_QUEST.sh" "$@"; then
    exit 0
else
    status=$?
fi

echo
echo "[ERROR] aethera-quest berhenti dengan kode $status."
read -r -p "Tekan Enter untuk menutup..." _ || true
exit "$status"

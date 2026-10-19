"""Uji live-server khusus untuk membuktikan quest 7968 -> pickup drop -> turn-in."""
from __future__ import annotations

import os
import sys
import time

from aethera_quest import config, credentials, farming, login, runner, servers, sfs
from aethera_quest.mode import RunMode


def main() -> int:
    store = credentials.MultiAccountStore()
    usernames = store.list_usernames()
    if not usernames:
        print("[PROBE] tidak ada akun tersimpan")
        return 1

    user = usernames[0]
    _u, password = store.load(user)
    print(f"[PROBE] login akun: {user} ...")

    tok = login.aqw_login(user, password)
    srv_list = servers.fetch_server_list()
    server = servers.pick(srv_list, config.DEFAULT_SERVER)

    slot_store = credentials.CredentialStore(base_dir=store.base_dir / "slots" / user)
    slot_store.save(user, password)

    orch = runner.Orchestrator(
        server_name=server.name,
        target_map="battleon-100000",
        store=slot_store,
        mode=RunMode.FARMING,
    )
    orch.auto_relogin = False

    events: list[str] = []

    def _logged(msg: str) -> None:
        sanitized = msg
        if user in sanitized:
            sanitized = sanitized.replace(user, "[USER]")
        print(sanitized, flush=True)
        if any(k in msg for k in ("[DROP]", "[QUEST]", "turn-in", "7968", "7969", "Indulgence", "ccqr")):
            events.append(sanitized)

    orch.log = _logged
    orch.connect(user, password)
    bot = orch.bot
    f = orch.farming
    if bot is None or f is None:
        print("[PROBE] bot / farming null")
        return 1

    orch.packet_log_enabled = True  # lihat setiap paket inbound

    time.sleep(2.0)
    print("[PROBE] join sevencircles ...")
    f.join("sevencircles", private=True)
    time.sleep(3.0)

    print("[PROBE] mulai leveling...")
    f.start_leveling(target=80, private=True)

    start_t = time.monotonic()
    success = False
    try:
        while time.monotonic() - start_t < 90.0:
            time.sleep(1.0)
            status_7968 = f.quest_state.status(7968)
            if status_7968.complete or f.quest_state.completed(7968):
                print(f"[PROBE-SUCCESS] Quest 7968 selesai! Progress beralih: {status_7968}")
                success = True
                break
            if f.quest_state.accepted(7969):
                print(f"[PROBE-SUCCESS] Quest 7969 sudah ter-accept!")
                success = True
                break
    finally:
        print("[PROBE] shutdown bot...")
        orch.shutdown()

    print("=== SUMMARY EVENTS ===")
    for e in events[-20:]:
        print(e)
    return 0 if success else 2


if __name__ == "__main__":
    sys.exit(main())

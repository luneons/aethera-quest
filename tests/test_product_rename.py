from __future__ import annotations

import os
from pathlib import Path


def test_repository_has_no_legacy_product_identifier() -> None:
    root = Path(__file__).resolve().parents[1]
    skipped_dirs = {".git", ".gradle", ".pytest_cache", "__pycache__", "build", ".idea", ".venv", "venv"}
    skipped_files = {
        "test_product_rename.py", "_rename_inventory.py", "_rename_everything.py", "_finish_rename.py",
        ".env", ".env.example", "migration_probe.py", "_local_env_inventory.py",
    }
    binary_suffixes = {".db", ".apk", ".jar", ".class", ".png", ".jpg", ".jpeg", ".gif", ".ico", ".zip", ".enc", ".so"}
    legacy = ("skua_lite", "skua-lite", "skualite", "skua lite", "skua_ai_", "skua_telegram_", "skua_wiki_db")
    hits: list[str] = []
    for path in root.rglob("*"):
        if not path.is_file() or path.name in skipped_files:
            continue
        if any(part in skipped_dirs for part in path.parts) or path.suffix.lower() in binary_suffixes:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (UnicodeDecodeError, OSError):
            continue
        low = text.lower()
        if any(token in low for token in legacy):
            hits.append(str(path.relative_to(root)))
    assert hits == []


def test_python_package_and_android_package_are_renamed() -> None:
    root = Path(__file__).resolve().parents[1]
    assert (root / "src" / "aethera_quest" / "__main__.py").is_file()
    assert not (root / "src" / "skua_lite").exists()
    assert (root / "android" / "app" / "src" / "main" / "java" / "com" / "aetheraquest" / "android" / "MainActivity.kt").is_file()
    assert not (root / "android" / "app" / "src" / "main" / "java" / "com" / "skualite").exists()


def test_launcher_and_storage_use_aethera_quest() -> None:
    from aethera_quest import config

    root = Path(__file__).resolve().parents[1]
    assert config.STORAGE_DIRNAME == "aethera-quest"
    assert (root / "JALANKAN_AETHERA_QUEST.bat").is_file()
    assert (root / "JALANKAN_AETHERA_QUEST.sh").is_file()
    assert (root / "JALANKAN_AETHERA_QUEST.command").is_file()

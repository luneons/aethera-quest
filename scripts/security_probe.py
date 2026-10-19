"""Probe cepat untuk memverifikasi celah keamanan yang dicurigai."""
import sys
sys.path.insert(0, 'src')

from aethera_quest.agent_tools import is_allowed_command, AgentTools

print("=== CELAH 1: single ampersand (cmd.exe chaining) ===")
print("echo hello & calc.exe ->", is_allowed_command("echo hello & calc.exe"))
print("echo hello && calc.exe ->", is_allowed_command("echo hello && calc.exe"))

print()
print("=== CELAH 2: python -c arbitrary code ===")
print('python -c "import os; os.system(\'calc\')" ->',
      is_allowed_command('python -c "import os; os.system(\'calc\')"'))

print()
print("=== CELAH 3: echo env var (kebocoran secret via output) ===")
print("echo %AETHERA_QUEST_AI_API_KEY% ->", is_allowed_command("echo %AETHERA_QUEST_AI_API_KEY%"))

print()
print("=== CELAH 4: nama pemain owner spoof ===")
from aethera_quest.ai_router import is_owner_account, is_owner_id
print("is_owner_account('MELE') ->", is_owner_account("MELE"))
print("is_owner_account('mele') ->", is_owner_account("mele"))
print("is_owner_account('MeLe') ->", is_owner_account("MeLe"))
print("is_owner_id(21623) ->", is_owner_id(21623))

print()
print("=== CELAH 5: fernet key derivation (salt == key material) ===")
import base64, os
from cryptography.fernet import Fernet
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
salt = os.urandom(32)
kdf = PBKDF2HMAC(algorithm=hashes.SHA256(), length=32, salt=salt, iterations=100_000)
key = base64.urlsafe_b64encode(kdf.derive(salt))
print("key diturunkan dari salt itu sendiri (salt==input, tanpa password):", key is not None)

print()
print("=== CELAH 6: SERVER_LIST_URL pakai HTTP (bukan HTTPS) ===")
from aethera_quest import config
print("SERVER_LIST_URL =", config.SERVER_LIST_URL)

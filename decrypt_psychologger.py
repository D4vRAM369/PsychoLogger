#!/usr/bin/env python3
"""
Desencriptador de audios de PsychoLogger
Uso: python decrypt_psychologger.py audios_encrypted_2025-01-15.zip password
"""

import sys
import json
import zipfile
from io import BytesIO
from getpass import getpass
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.pbkdf2 import PBKDF2HMAC
from cryptography.hazmat.primitives import hashes

def hex_to_bytes(hex_string):
    """Convierte string hexadecimal a bytes"""
    return bytes.fromhex(hex_string)

def derive_key(password, salt, iterations=120000, key_length=32):
    """
    Deriva clave AES-256 desde contraseña usando PBKDF2

    Parámetros:
    - password: Contraseña en string
    - salt: Salt en bytes (16 bytes)
    - iterations: Iteraciones PBKDF2 (default: 120,000)
    - key_length: Longitud de clave en bytes (32 = 256 bits)
    """
    kdf = PBKDF2HMAC(
        algorithm=hashes.SHA256(),
        length=key_length,
        salt=salt,
        iterations=iterations
    )
    return kdf.derive(password.encode('utf-8'))

def decrypt_chunked(aesgcm, iv, data):
    """
    Formato "chunked-v2" (backups/audios/fotos desde 1.4):
    data.enc = registros [len:4 bytes BE][ciphertext+tag(16)]
    - nonce_i = iv con sus últimos 4 bytes XOR contador i (BE)
    - AAD = b"\x01" si es el último bloque, b"\x00" si no (detecta truncado)
    """
    out, pos, i = [], 0, 0
    while pos < len(data):
        n = int.from_bytes(data[pos:pos + 4], 'big')
        pos += 4
        ct = data[pos:pos + n]
        pos += n
        last = pos >= len(data)
        nonce = iv[:-4] + (int.from_bytes(iv[-4:], 'big') ^ i).to_bytes(4, 'big')
        out.append(aesgcm.decrypt(nonce, ct, b"\x01" if last else b"\x00"))
        i += 1
    return b"".join(out)

def decrypt_zip(encrypted_zip_path, password, output_dir="decrypted_audios"):
    """
    Desencripta un ZIP de audios cifrado

    PROCESO:
    1. Abrir ZIP externo
    2. Leer metadata.json → extraer salt, iv, iterations
    3. Leer data.enc → bytes cifrados
    4. Derivar clave AES desde password + salt
    5. Desencriptar data.enc con AES-256-GCM
    6. Extraer ZIP interno con los audios
    """
    print(f"🔓 Desencriptando: {encrypted_zip_path}")

    # 1. Abrir ZIP externo
    with zipfile.ZipFile(encrypted_zip_path, 'r') as outer_zip:
        # 2. Leer metadata.json
        with outer_zip.open('metadata.json') as f:
            metadata = json.load(f)

        print(f"📄 Metadata:")
        print(f"   - Algoritmo: {metadata['algorithm']}")
        print(f"   - Iteraciones: {metadata['iterations']}")
        print(f"   - Timestamp: {metadata.get('timestamp', 'N/A')}")

        # Extraer salt e IV
        salt = hex_to_bytes(metadata['salt'])
        iv = hex_to_bytes(metadata['iv'])
        iterations = metadata['iterations']

        print(f"   - Salt: {len(salt)} bytes")
        print(f"   - IV: {len(iv)} bytes")

        # 3. Leer data.enc (bytes cifrados)
        with outer_zip.open('data.enc') as f:
            encrypted_data = f.read()

        print(f"📦 Datos cifrados: {len(encrypted_data)} bytes")

    # 4. Derivar clave AES desde contraseña
    print(f"🔑 Derivando clave AES-256 con PBKDF2 ({iterations} iteraciones)...")
    key = derive_key(password, salt, iterations)

    # 5. Desencriptar con AES-256-GCM
    print(f"🔐 Desencriptando con AES-256-GCM...")
    try:
        aesgcm = AESGCM(key)
        if metadata.get('format') == 'chunked-v2':
            decrypted_zip_bytes = decrypt_chunked(aesgcm, iv, encrypted_data)
        else:  # formato antiguo: un único bloque GCM
            decrypted_zip_bytes = aesgcm.decrypt(iv, encrypted_data, None)
    except Exception as e:
        print(f"❌ ERROR: Contraseña incorrecta o datos corruptos")
        print(f"   Detalle: {e}")
        return False

    print(f"✅ Desencriptado exitoso: {len(decrypted_zip_bytes)} bytes")

    # 6. Extraer ZIP interno (audios/ y/o photos/)
    print(f"📂 Extrayendo archivos a: {output_dir}/")
    import os
    os.makedirs(output_dir, exist_ok=True)

    with zipfile.ZipFile(BytesIO(decrypted_zip_bytes)) as inner_zip:
        media_files = [f for f in inner_zip.namelist()
                       if f.startswith(('audios/', 'photos/')) and not f.endswith('/')]
        print(f"🎵📷 Archivos encontrados: {len(media_files)}")

        for media_file in media_files:
            filename = os.path.basename(media_file)
            output_path = os.path.join(output_dir, filename)

            with inner_zip.open(media_file) as source:
                with open(output_path, 'wb') as target:
                    target.write(source.read())

            print(f"   ✓ {filename}")

    print(f"\n✅ ¡Desencriptado completado!")
    return True

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Uso: python3 decrypt_psychologger.py <archivo.zip> [password]")
        print("Si no se proporciona password, se pedirá de forma segura")
        sys.exit(1)

    zip_path = sys.argv[1]

    # Pedir contraseña de forma segura (no visible en terminal)
    if len(sys.argv) >= 3:
        password = sys.argv[2]
    else:
        password = getpass("🔒 Contraseña: ")

    decrypt_zip(zip_path, password)

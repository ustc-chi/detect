# Ransomware Encryption Behaviors — Comparative Analysis

> Research document summarizing encryption architectures, algorithms, and operational behaviors of major ransomware families. For defensive/security research purposes only.

---

## Executive Summary

Modern ransomware universally employs **hybrid encryption**: a symmetric cipher encrypts file contents (speed), while an asymmetric cipher wraps the symmetric key (key management). However, the specific algorithms, key-exchange mechanisms, encryption granularity, and performance strategies vary significantly across families. This document compares 7 of the most impactful ransomware families across these dimensions.

---

## Quick-Reference Comparison Matrix

| Feature | WannaCry | Ryuk | Conti | REvil (Sodinokibi) | LockBit | BlackCat (ALPHV) | Cl0p |
|---|---|---|---|---|---|---|---|
| **First Seen** | 2017 | 2018 | 2020 | 2019 | 2019 | 2021 | 2019 |
| **Language** | C | C/C++ | C++ | C/C++ | C/C++ | Rust | C/C++ |
| **Symmetric Cipher** | AES-128-CBC | AES-256 | ChaCha20 / Salsa20 | Salsa20 | XSalsa20 / XChaCha20 / ChaCha20-Poly1305 | AES / ChaCha20 | RC4 |
| **Asymmetric / Key Exchange** | RSA-2048 | RSA-2048 (3-layer) | RSA-4096 | Curve25519 (ECDH) | Curve25519 / X25519+BLAKE2b | RSA | RSA-1024 (Win) / RC4 master key (Linux) |
| **Key Scope** | Per-file AES | Per-file AES | Per-file ChaCha20 | Per-file Salsa20 | Per-file XSalsa20/XChaCha20 | Per-file AES/ChaCha20 | Per-file RC4 |
| **Encryption Mode** | Full file | Full file (1 MB chunks) | Full / Partial / Header (size-based) | Full / configurable | Partial (4 KB per file v2) / configurable | Full / Fast / DotPattern / SmartPattern / Auto | Full / partial (size-based) |
| **Multi-threaded** | No | Yes (1 thread/file) | Yes (32 workers via I/O completion ports) | Yes | Yes | Yes | Yes |
| **Cross-platform** | No (Windows) | No (Windows) | No (Windows) | No (Windows) | Yes (Windows, ESXi) | Yes (Windows, Linux, ESXi) | Yes (Windows, Linux — flawed) |
| **Self-propagation** | Yes (EternalBlue SMB worm) | No (relies on TrickBot/Emotet) | Yes (PsExec lateral movement) | No (RaaS, affiliate-driven) | Yes (self-propagation, PsExec) | Yes (PsExec) | No |
| **Double Extortion** | No | No | Yes | Yes | Yes | Yes | Yes |
| **File Marker** | `WANACRY!` | `HERMES` | `.KCWTT` extension | Random 8-char prefix | `.lockbit` extension | 4-byte border (head+tail) | `Clop^_-` |
| **File Extension** | `.WNCRY` | `.RYK` (some: none) | `.KCWTT` | Random 8-char | `.lockbit` | Varies | `.C_l_0P` / `.clop` |
| **RaaS Model** | No | No | Yes | Yes | Yes | Yes | Yes |

---

## Detailed Family Profiles

---

### 1. WannaCry (2017)

**Origin**: North Korea-linked (Lazarus Group). Notable for the EternalBlue SMB worm that enabled global self-propagation.

#### Encryption Architecture

```
[Embedded RSA-2048 Public Key] → encrypts → [Per-infection RSA-2048 Key Pair]
[Per-infection RSA-2048 Public Key] → encrypts → [Per-file AES-128-CBC Key]
[Per-file AES-128-CBC Key] → encrypts → [File Contents]
```

- **Symmetric**: AES-128-CBC with NULL IV — generates a unique 128-bit key per file via Windows Crypto API (`CryptGenRandom`).
- **Asymmetric**: RSA-2048. A per-infection RSA key pair is generated; the private half is stored locally encrypted by a hardcoded master public key. Each file's AES key is encrypted by the per-infection RSA public key.
- **Encryption scope**: Full file contents. Files are staged as `.WNCRYT` temp files, then renamed to `.WNCRY`.
- **File marker**: `WANACRY!` magic bytes prepended to each encrypted file, followed by the RSA-encrypted AES key.
- **Worm behavior**: Spreads via EternalBlue (CVE-2017-0144) exploiting SMBv1 — the only major ransomware with true autonomous worm capability.
- **Weaknesses**: No flaw in the crypto itself; recovery requires the per-infection RSA private key. However, the kill-switch domain registration stopped propagation. Tools like Wanakiwi could extract keys from memory pre-reboot.

---

### 2. Ryuk (2018)

**Origin**: Derived from Hermes ransomware source code. Distributed via TrickBot and Emotet botnets. Operated by Wizard Spider (Russia-linked).

#### Encryption Architecture

```
[Master RSA-2048 Key Pair (attacker-held)] → encrypts → [Session RSA-2048 Private Key]
[Session RSA-2048 Public Key (hardcoded)] → encrypts → [Per-file AES-256 Key]
[Per-file AES-256 Key] → encrypts → [File Contents (1 MB chunks)]
```

- **Three-layer encryption**:
  1. **Layer 1 (symmetric)**: AES-256 per-file key generated via `CryptGenKey` (algorithm ID `0x6610` = CALG_AES_256).
  2. **Layer 2 (session asymmetric)**: RSA-2048 session key pair embedded per sample; private key is pre-encrypted by master public key and stored in `UNIQUE_ID_DO_NOT_REMOVE` file.
  3. **Layer 3 (master asymmetric)**: RSA-2048 master key pair held exclusively by attacker.
- **Encryption scope**: Full file encrypted in 1,000,000-byte (1 MB) chunks. Six rounds of drive enumeration (A: to Z:) targeting local and mapped network drives.
- **File marker**: `HERMES` (6 bytes) appended at end of file, followed by 274-byte metadata block containing the RSA-encrypted AES key.
- **Multi-threading**: One thread per file — creates separate threads for parallel encryption.
- **Network behavior**: Continuously scans network via ping to discover new hosts; attempts to encrypt network shares via UNC paths (`\\HOST\X$\...`).
- **Speed evolution**: 2018 version took ~1 hour for local disk; 2020 version reduced to <10 minutes through self-duplication and process injection.
- **Weaknesses**: Per-sample RSA key pair means different samples have different keys — no single master key decrypts all victims. No known cryptographic flaws.

---

### 3. Conti (2020)

**Origin**: Closely linked to Ryuk/Wizard Spider. Source code leaked in 2022, spawning numerous derivatives. Defunct since 2022 but descendants persist.

#### Encryption Architecture

```
[Hardcoded RSA-4096 Public Key] → encrypts → [Per-file ChaCha20 Key]
[Per-file ChaCha20 Key] → encrypts → [File Contents]
```

- **Symmetric**: ChaCha20 (statically linked). Per-file key generated via `CryptGenRandom`.
- **Asymmetric**: RSA-4096 public key hardcoded in the binary (found via builder).
- **Encryption modes** (3 strategies based on file type and size):

  | File Condition | Mode | Behavior |
  |---|---|---|
  | Size < 1 MB | `FULL_ENCRYPT` | Entire file encrypted |
  | Size 1–5 MB | `HEADER_ENCRYPT` | Only file header encrypted |
  | Size > 5 MB | `PARTLY_ENCRYPT` | Encrypts 20% or 50% of data in stepped blocks |
  | VM files (any size) | `PARTLY_ENCRYPT` | Partial encrypt for speed |
  | Database files | `FULL_ENCRYPT` | Full encrypt for maximum damage |

- **Multi-threading**: Up to 32 concurrent encryption worker threads using Windows I/O Completion Ports (`CreateIoCompletionPort` / `PostQueuedCompletionStatus`) — among the fastest ransomware observed.
- **File footer**: RSA-encrypted ChaCha20 key + encryption mode constant + data percent value appended.
- **Scope modes**: `all` (local + network), `local`, `network` — configurable via `--encrypt-mode` CLI flag.
- **Obfuscation**: Unique per-string decryption routines (~277 unique algorithms), ADVObfuscator macro for inline string obfuscation.
- **Weaknesses**: Source code leak (2022) allowed full analysis and derivative detection. No cryptographic flaw in the encryption itself.

---

### 4. REvil / Sodinokibi (2019)

**Origin**: Emerged after GandCrab shutdown. Believed to be GandCrab successors. RaaS model. Disrupted by law enforcement (2021 FBI operation).

#### Encryption Architecture

```
[Curve25519 ECDH Key Exchange] → derives → [Shared Secret]
[Shared Secret + Nonce] → feeds → [Per-file Salsa20 Key]
[Per-file Salsa20 Key] → encrypts → [File Contents]
```

- **Key exchange**: Elliptic-Curve Diffie-Hellman (Curve25519) — uniquely avoids RSA entirely for key exchange. Shorter keys, high efficiency, near-impossible to crack.
- **Symmetric**: Salsa20 for file encryption. Chosen for performance over AES.
- **Configuration**: RC4-encrypted configuration embedded in binary, containing folder/file exclusion lists and process kill lists.
- **Encryption scope**: Full file encryption by default. Supports `-fast` and `-full` CLI flags to control behavior.
- **Safe Mode encryption**: Can operate in Windows Safe Mode via `-smode` flag for stealth, establishing persistence via `RunOnce` registry key.
- **Other CLI flags**: `-nolan` (skip network), `-nolocal` (skip local), `-path` (target directory), `-silent` (no output).
- **File extension**: Random 8-character string appended to filenames.
- **Multi-threading**: Yes — aggressive multi-threaded encryption.
- **Weaknesses**: A bug in some samples caused files to be renamed but not encrypted. Tor-based C2 infrastructure was seized by law enforcement.

---

### 5. LockBit (2019–present)

**Origin**: LockBit group (Bitwise Spider). Multiple major versions. Disrupted by Operation Cronos (2024) but resurfaces. **Most evolved encryption across versions.**

#### Encryption Architecture (Evolution by Version)

| Version | Symmetric | Asymmetric / Key Exchange | Key Derivation |
|---|---|---|---|
| **LockBit 2.0** | XSalsa20-Poly1305 + AES-128-CBC | Curve25519 (Libsodium) | Libsodium's `crypto_secretbox` |
| **LockBit 3.0 (Black)** | Similar to 2.0 | Curve25519 | Enhanced obfuscation |
| **LockBit 4.0 (Green)** | XChaCha20 | Curve25519 | SHA512-based key derivation; double-layer XChaCha20 wrapping |
| **LockBit 5.0** | ChaCha20-Poly1305 | X25519 + BLAKE2b | BLAKE2b hash of random values → ChaCha20 key stream |

#### LockBit 2.0 (Most Analyzed)

```
[Curve25519 Key Exchange] → [Shared Secret]
[Shared Secret] → [XSalsa20-Poly1305 Key + AES-128-CBC Key]
[XSalsa20 Key] → encrypts → [File Contents (partial: 4 KB per file)]
```

- **Partial encryption**: Only **4 KB** of data encrypted per file — claims fastest encryption on the market.
- **Multi-threading**: Shared structure architecture divides encryption work into multiple states across child threads.
- **Configuration**: XOR-encrypted (`0x5F` key) stored in static memory.

#### LockBit 4.0 (Green)

```
[Curve25519 ECDH] → [Shared Secret]
[Random 32-byte XChaCha20 File Key] → encrypts → [File Contents]
[SHA512(SharedSecret || PubKey)] → "Outer" XChaCha20 Key → wraps → [File Key]
[SHA512(OuterKey)] → Outer Nonce
[Wrapped File Key + Victim PubKey] → appended as lb_file_footer
```

- **Double-layer encryption**: File key encrypted by an outer XChaCha20 key derived from ECDH shared secret.
- **Nonce derivation**: XChaCha20 nonce derived by XOR-ing internal bytes of the file key together.

#### LockBit 5.0

```
[X25519 Key Exchange + BLAKE2b Hash] → [Shared Secret]
[2 × 32-byte random values (system time + memory)] → [Victim Private Key]
[BLAKE2b(First Random Value)] → [ChaCha20 Key Stream]
[ChaCha20 Key Stream (top 16 + bottom 16 bytes XOR)] → 32-byte Key → [File Encryption Key Stream]
```

- **Size-dependent encryption**: Files ≤ 0x5000000 bytes encrypted with derived key stream; larger files encrypted in 0x800000 (8 MB) chunks.
- **File footer**: File size, custom hash, ChaCha20-encrypted values, Poly1305 MAC, victim's public key.
- **Weaknesses**: Builder leaked (2022), allowing extensive analysis. No fundamental crypto flaw, but operational security failures.

---

### 6. BlackCat / ALPHV / Noberus (2021)

**Origin**: First major Rust-based ransomware. Highly customizable RaaS. Active through 2024. Possible links to DarkSide/Babuk actors.

#### Encryption Architecture

```
[Per-execution AES Private Key] → generated fresh
[Config RSA Public Key] → RSA-encrypts → [AES Private Key]
[RSA-encrypted AES Key] → embedded alongside → [Encrypted File]
[Per-file AES or ChaCha20 Key] → encrypts → [File Contents]
```

- **Dual symmetric options**: AES (default when hardware AES-NI available) or ChaCha20 (fallback).
- **Asymmetric**: RSA for key wrapping (config-embedded public key).
- **Five configurable encryption modes**:

  | Mode | Strategy | Speed vs Strength |
  |---|---|---|
  | **Full** | Encrypt entire file | Slowest, strongest |
  | **Fast** | Encrypt first N MB only | Fastest, weakest |
  | **DotPattern** | Encrypt N MB every M steps | Moderate |
  | **SmartPattern** | Encrypt 10 MB every 10% of file from header | Optimal balance (recommended) |
  | **Auto** | Dynamically selects mode based on file type + size | Adaptive |

- **Cross-platform**: Targets Windows, Linux (Debian, Ubuntu, ReadyNAS, Synology), and VMware ESXi — broadest platform support.
- **Self-propagation**: Embeds compressed PsExec for lateral movement via supplied credentials.
- **Configuration protection**: Config encrypted with AES-128-CTR, decryption key derived from command-line `--access-token`.
- **File marker**: 4-byte border (`19 47 B7 4D`) appended at both head and tail of encrypted files.
- **Checkpoint files**: Created during encryption for resume-after-interruption capability.
- **Weaknesses**: Access token recoverable from drag-and-drop BAT files left on disk. No crypto flaw; recovery requires attacker's RSA private key.

---

### 7. Cl0p (2019)

**Origin**: Operated by TA505. Known for mass exploitation of Accellion FTA, GoAnywhere MFT, MOVEit Transfer. Active and evolving.

#### Encryption Architecture (Windows)

```
[RSA-1024 Public Key] → encrypts → [Per-file RC4 Key (0x75 bytes)]
[Per-file RC4 Key] → encrypts → [File Contents]
[RC4 Key generated via Mersenne Twister PRNG (MT19937)]
```

- **Symmetric**: RC4 — unusually weak compared to peers. 117-byte (0x75) keys generated using Mersenne Twister PRNG.
- **Asymmetric**: RSA-1024 public key (weaker than the RSA-2048/4096 used by peers).
- **Key generation**: MT19937 PRNG seeded to generate RC4 key bytes; validates first 5 bytes are NULL before use.
- **Encryption scope**: File-size-based strategy:
  - Small files: **not encrypted** (skipped)
  - Medium files: Encrypted using `ReadFile`/`WriteFile` API (data starting at offset `4000h`)
  - Large files: Partial encryption
- **Execution modes**:
  - No parameters: Encrypt all local + network drives (installs as service)
  - `temp.ocx` parameter: Encrypt specific file list
  - `runrun` parameter: Two threads — one for local drives, one for network shares
- **File marker**: `Clop^_-` prepended to encrypted content.
- **File extension**: `.C_l_0P` or `.clop`.

#### Linux Variant (Critically Flawed)

```
[Hardcoded RC4 "Master Key" (0x64 bytes)] → RC4-encrypts → [Per-file RC4 Key]
[Per-file RC4 Key] → encrypts → [File Contents]
```

- **Critical flaw**: Uses symmetric RC4 to "protect" per-file keys instead of RSA. Since RC4 is symmetric, the hardcoded master key can directly decrypt all file keys → **full recovery possible without attacker's key**.
- **Additional weakness**: RC4 key is never validated (unlike Windows variant).
- **Decryptor published**: SentinelLabs released a free decryptor for the Linux variant.
- **Missing features vs Windows**: No drive enumeration, no file-size differentiation, no hashing-based exclusion.

---

## Filesystem Interaction Behaviors — Deep Comparison

This section focuses specifically on **how each ransomware physically modifies the filesystem**: whether files are overwritten in-place, staged through temp files, renamed before/after encryption, and how different file sizes are handled. These behaviors directly impact forensic recoverability and detection opportunities.

### Filesystem Behavior Matrix

| Behavior | WannaCry | Ryuk | Conti | REvil | LockBit | BlackCat | Cl0p |
|---|---|---|---|---|---|---|---|
| **Write strategy** | Staged (temp → rename) | In-place overwrite | In-place overwrite | In-place overwrite then rename | In-place overwrite then rename | In-place overwrite then rename | In-place overwrite |
| **Rename timing** | After encryption | After encryption | After encryption | Before encryption ⚠️ | After encryption | After encryption | No rename |
| **Original file** | Moved to $RECYCLE or %TEMP% | Deleted | Deleted | Overwritten | Overwritten | Overwritten | Overwritten |
| **Temp files** | Yes (`.WNCRYT` staging) | No | No | No | No | Yes (checkpoints) | No |
| **Original recoverable?** | Sometimes (see below) | No | No | No | No | No | No |
| **Locked file handling** | Kill processes | Kill processes | Restart Manager API | Restart Manager API | Restart Manager API | Restart Manager / kill | Kill processes |
| **Persistence if interrupted** | No | No | No | RunOnce (safe mode) | Registry Run key | Checkpoint files | Installs as service |

---

### 1. WannaCry — Staged Encryption with Original File Recovery

WannaCry is **unique** among these families in its staging approach. It does not overwrite files in-place.

```
BEFORE:  C:\Users\Alice\Documents\report.docx
DURING:  C:\Users\Alice\Documents\report.docx.WNCRYT   ← temp staged file (encrypted content)
AFTER:   C:\Users\Alice\Documents\report.docx.WNCRY     ← final encrypted file (renamed from .WNCRYT)
         Original moved to $RECYCLE or %TEMP%
```

**Step-by-step filesystem interaction:**

1. **Read** original file content into memory.
2. **Create** a new temp file with `.WNCRYT` extension in the same directory.
3. **Write** encrypted content to the `.WNCRYT` file: `WANACRY!` header (8 bytes) + encrypted AES key + encrypted file data.
4. **Rename** `.WNCRYT` → `.WNCRY` (final extension).
5. **Delete** the original file.

**Original file handling varies by location:**

| File Location | What Happens to Original | Recoverable? |
|---|---|---|
| "Important" folders (Desktop, Documents) | Overwritten with random data, then deleted | **No** — data destroyed |
| Other folders | Moved to `%TEMP%\%d.WNCRYT` (simply deleted) | **Yes** — data recovery tools can restore |
| Read-only files | **Bug**: not encrypted at all; only get `hidden` attribute set | **Yes** — just unhide |

**Additional details:**
- Creates a hidden `$RECYCLE` folder with system attributes — intended to move originals there, but synchronization bugs cause many originals to remain in-place.
- The `taskdl.exe` process periodically deletes remaining `.WNCRYT` temp files.
- Up to 10 files may use the embedded RSA key (for "demonstration decryption"); file paths are logged to `f.wnry`.
- Scans all drives + mapped network drives; targets specific extensions (176+ file types).

---

### 2. Ryuk — In-Place Overwrite with Chunked Full Encryption

Ryuk takes a **brute-force approach**: full file encryption in-place with no staging.

```
BEFORE:  D:\Data\database.mdf              (original file)
DURING:  D:\Data\database.mdf              (being overwritten in 1 MB chunks)
AFTER:   D:\Data\database.mdf.ryk          (renamed after encryption)
         Footer: [HERMES][RSA-encrypted AES key (268 bytes)][counter if partial]
```

**Step-by-step filesystem interaction:**

1. **Enumerate** drives A: through Z: in **6 rounds**, targeting different drive types per round (fixed, mapped, removable).
2. **Check** for `HERMES` marker at end of file — skip if already encrypted (prevents double encryption).
3. **Generate** per-file AES-256 key via `CryptGenKey()`.
4. **Encrypt** file contents in-place in **1,000,000 byte (1 MB) chunks**: read chunk → encrypt → write back at same offset.
5. **Append** footer: 6-byte `HERMES` magic + 268-byte RSA-encrypted AES key (`CryptExportKey` produces 12 bytes blob info + 256 bytes encrypted key).
6. **Rename** file: append `.RYK` extension (some samples don't rename at all).

**Large file optimization (files > 54.4 MB):**

Ryuk partially encrypts large files to save time:
- Only encrypts **certain parts** of the file in 1 MB chunks.
- Appends a **counter** in the footer indicating how many 1 MB blocks were encrypted.
- The official Ryuk decryptor had a **bug** that truncated the last byte of large files during decryption, corrupting VHD/VHDX and Oracle database files.

**Exclusions:**
- **Folders**: `Windows`, `Mozilla`, `Chrome`, `Recycle Bin`, `Ahnlab`
- **Extensions**: `.dll`, `.lnk`, `.hrmlog`, `.ini`, `.exe`
- Continuously scans network via ping for new hosts; encrypts via UNC paths `\\HOST\X$\...`

---

### 3. Conti — Size-Tiered Encryption with Process Killing

Conti encrypts **in-place** with a sophisticated three-tier strategy based on file size and extension.

```
BEFORE:  E:\Reports\quarterly.xlsx
AFTER:   E:\Reports\quarterly.xlsx.LSNWX       (extension varies: .CONTI → .YZXXX → .LSNWX)
         Footer: [RSA-encrypted ChaCha key][encryption mode byte][percent byte]
```

**Step-by-step filesystem interaction:**

1. **Enumerate** files via `FindFirstFile`/`FindNextFile` on local drives and remote SMB shares (`NetShareEnum`).
2. **Kill processes** holding file locks using Windows **Restart Manager API** (`RmStartSession` → `RmRegisterResources` → `RmGetList` → terminate processes).
3. **Classify** each file by size and extension to determine encryption mode:

| Condition | Mode | What Happens to File Data |
|---|---|---|
| Size < 1 MB **OR** database extension (171 extensions) | `FULL_ENCRYPT` (0x24) | Entire file encrypted in-place |
| Size 1–5 MB, non-special extension | `HEADER_ENCRYPT` (0x26) | Only first 1 MB encrypted |
| Size > 5 MB, non-special extension | `PARTLY_ENCRYPT` (0x25) | 5 chunks of `(filesize/100 * 10)` bytes each, with gaps between |
| Size > 5 MB, special extension (20 extensions) | `PARTLY_ENCRYPT` (0x25) | 3 chunks of `(filesize/100 * 7)` bytes each |
| VM files (any size) | `PARTLY_ENCRYPT` | Partial for speed |

4. **Encrypt** in-place using custom ChaCha8 implementation: read → encrypt → write back at same offset.
5. **Append** footer: RSA-encrypted ChaCha key + 1-byte encryption mode constant + 1-byte percent value.
6. **Rename** file: append extension (varies by version: `.CONTI`, `.YZXXX`, `.LSNWX`, `.KCWTT`).

**Exclusions:** `.exe`, `.dll`, `.sys`, `.lnk`, and the ransomware's own extension.

**Threading architecture:**
- 32 worker threads via `CreateIoCompletionPort` — files queued via `PostQueuedCompletionStatus`, workers receive via `GetQueuedCompletionStatus`.
- Later versions replaced I/O completion ports with C++ queues and mutex locks.

---

### 4. REvil / Sodinokibi — Configurable Encryption with IOCP Threading

REvil encrypts **in-place**, then renames. Notable for its config-driven flexibility.

```
BEFORE:  C:\Finance\invoices.sql
DURING:  C:\Finance\invoices.sql          (content overwritten with encrypted data)
AFTER:   C:\Finance\invoices.9781xsd4     (random extension appended)
         Footer: [metadata blob with encrypted key + encryption type]
```

**Step-by-step filesystem interaction:**

1. **Parse** RC4-encrypted configuration from binary (exclusion lists, process kill lists).
2. **Generate** random extension (5-10 chars, a-z/0-9) → store in registry `SOFTWARE\recfg\rnd_ext`.
3. **Enumerate** local fixed drives; compare against whitelist (`fld`, `fls`, `ext` config keys).
4. **Queue** non-whitelisted files to I/O completion port thread pool.
5. **For each file, select encryption type:**

| Type Code | Strategy | Detail |
|---|---|---|
| `et=0` | Full encryption | Entire file contents encrypted |
| `et=1` | First 1 MB | Only first 1,048,576 bytes encrypted |
| `et=2` | Intermittent | Encrypt 1 MB, skip `spsize` MBs, repeat |

6. **Encrypt in-place**: read file into buffer → encrypt with Salsa20 → overwrite original content → append metadata blob.
7. **Rename**: append the random extension via `MoveFileExW`.
8. **Drop** ransom note `{EXT}-readme.txt` in every directory.

**Process handling:**
- Uses Windows **Restart Manager API** to terminate processes/services holding file locks.
- Also uses Restart Manager in its decryptor (v2.2+) to handle locked files during recovery.

**Known bugs:**
- **Last version**: Files were renamed but never encrypted due to a logic error — the rename completed, then the code tried to find the original filename (which no longer existed) and failed silently.
- The `MoveFileExW` rename succeeded, but the second attempt raised `ERROR_FILE_NOT_FOUND`.

**Network encryption:**
- If running with SYSTEM privileges or can impersonate `explorer.exe` → encrypts mapped network shares.
- `-nolan` flag: skip network shares entirely.

---

### 5. LockBit — Most Evolved Filesystem Strategy (Version-Dependent)

LockBit's filesystem interaction changed significantly across versions. All versions encrypt **in-place** then rename.

```
BEFORE:  C:\Work\budget.xlsx
AFTER:   C:\Work\budget.xlsx.IzYqBW5pa    (random per-victim extension)
         Footer: [encrypted key material][original filename if ChangeFilename=true]
```

#### LockBit 3.0 (Black) — Most Detailed Analysis Available

**Step-by-step filesystem interaction:**

1. **Parse** configuration from binary (XOR-encrypted). Contains:
   - `DirectoryList`, `FileSet`, `NoneSet` → files/folders/extensions to skip
   - `IntermittentSet` → extensions for partial encryption (with `Percent` value)
   - `FastSet` → extensions for fast-mode encryption
   - `ChangeFilename` → whether to randomize filenames
   - `EnableNetworkShares` → encrypt network shares

2. **Traverse** filesystem with one thread, queue files to I/O completion port.

3. **For each file:**
   - Verify filename against hash lists of excluded files.
   - **Rename** immediately: append random extension (e.g., `.IzYqBW5pa`).
   - **Classify** encryption mode by extension config:

| Mode | Behavior | Config Target |
|---|---|---|
| **Fast** | Encrypt only first 0x1000 (4 KB) bytes | Files in `FastSet` |
| **Intermittent** | Encrypt a percentage of chunks in 0x20000 (128 KB) blocks | Files in `IntermittentSet`, density = `Percent` field |
| **Full** | Encrypt entire file | Everything else |

4. **Chunk processing** (intermittent mode):
   - File divided into 0x20000-byte (128 KB) chunks.
   - Chunks organized into alternating groups: **before** (encrypt), **skip** (leave), **after** (encrypt).
   - Only "before" and "after" group chunks are encrypted with modified Salsa20.

5. **Key management bug (v3)**: `key_encryption_key` is reused every 1,000 files. This creates a **key stream reuse vulnerability** that makes decryption possible without paying (if the pattern is detected and exploited).

6. **Write footer**: Encrypted key material + original filename (if `ChangeFilename=true`).

7. **Change desktop icon**: Modifies `HKEY_CLASSES_ROOT` registry to associate the random extension with a LockBit icon (`.ico` file dropped to `C:\ProgramData\`).

#### LockBit 4.0 (Green) — Smart Chunking

| File Size | Strategy | Detail |
|---|---|---|
| < 1 MB (0x100000) | Full encryption | Entire file encrypted with XChaCha20 |
| > 1 MB | Partial: 3 chunks | Each chunk ≈ 9% of file, max 1 MB per chunk. Two skip regions of ≈ 36.5% each between chunks |

**Chunk positioning calculation:**
```
chunk_size = min(file_size * 0.09, 0x100000)
skip_size = (file_size - 3 * chunk_size) / 2
```
Result: 3 encrypted chunks with ~36.5% gaps → file is ~27% encrypted but completely unusable.

#### LockBit 5.0 — Size-Dependent Streams

| File Size | Strategy |
|---|---|
| ≤ 0x5000000 (~83 MB) | Single-pass ChaCha20-Poly1305 key stream |
| > 0x5000000 | Chunked: 0x800000 (8 MB) blocks |

**Footer contains:** file size, custom hash, ChaCha20-encrypted values, Poly1305 MAC, victim's public key.

**ChangeFilename feature**: When enabled, encrypted files are renamed to random strings. The original filename is preserved inside the file footer so the decryptor can restore it.

---

### 6. BlackCat / ALPHV — Most Configurable Filesystem Strategy

BlackCat is the most sophisticated in terms of **configurable encryption granularity**. Encrypts **in-place**, then renames.

```
BEFORE:  /var/lib/database/pg_data.db
DURING:  /var/lib/database/pg_data.db              (content being overwritten in selected mode)
AFTER:   /var/lib/database/pg_data.db.abc1234      (random 7-char extension appended)
         Structure: [encrypted data][4-byte border 19 47 B2 CE][RSA-encrypted AES key][key size]
```

**Step-by-step filesystem interaction:**

1. **Parse** JSON configuration (protected by `--access-token` AES-128-CTR encryption):
   - `default_file_mode`: Auto / Full / Fast / DotPattern / SmartPattern
   - `default_file_cipher`: Best (AES-NI if available, else ChaCha20)
   - Exclusion lists: folders, files, extensions
   - Processes/services to terminate

2. **Spawn** a 4-worker file worker pool.

3. **For each file, select mode based on config:**

| Mode | File Interaction | Example (100 MB file) |
|---|---|---|
| **Full** | Entire file read, encrypted, written back | 100 MB encrypted |
| **Fast/HeadOnly** | First N MB read, encrypted, written back | First 10 MB encrypted |
| **DotPattern** | N MB encrypted every M bytes | 1 MB encrypted every 10 MB |
| **SmartPattern** | 10 MB encrypted at every 10% offset from start | 10 MB at 0%, 10 MB at 10%, 10 MB at 20%, ... |
| **Auto** | Mode selected dynamically by file extension + size | Large .sql → Full; large .log → SmartPattern |

4. **Encrypt in-place**: `SetFilePointerEx` to beginning → `ReadFile` → encrypt → `WriteFile` back.
5. **Append key material**: 4-byte border → RSA-encrypted AES key → key size.
6. **Rename** via `MoveFileExW`: append random 7-character extension.
7. **Create checkpoint files** (`checkpoint-<id>`) during encryption — allows resuming if interrupted.

**Additional filesystem behaviors:**
- **Process termination**: Kills database servers, mail servers, backup agents to release file locks.
- **ESXi targeting**: Stops VMs on ESXi hosts before encrypting VMDK/flat files.
- **Self-propagation**: Drops embedded PsExec to `%TEMP%`, copies itself to remote hosts (`-c`), overwrites existing (`-f`), runs as SYSTEM (`-s`), non-interactive (`-d`).

---

### 7. Cl0p — Size-Selective with Separate Key Files

Cl0p has a unique approach where it creates **separate companion files** for encrypted keys instead of embedding them in the encrypted file itself.

```
BEFORE:  F:\Backups\server.bkf
DURING:  F:\Backups\server.bkf              (content partially/fully encrypted in-place)
AFTER:   F:\Backups\server.bkf              (original filename preserved!)
         F:\Backups\server.bkf.C_l_0P       (separate file containing RSA-encrypted RC4 key)
         Marker: "Clop^_-" prepended to encrypted content
```

**Step-by-step filesystem interaction:**

1. **Determine execution mode:**

| Mode | Trigger | Behavior |
|---|---|---|
| **Full scan** | No parameters | Installs as service, encrypts all local + network drives |
| **Targeted** | `temp.ocx` parameter | Encrypts only files listed in temp.ocx |
| **Dual-thread** | `runrun` parameter | Thread 1: local drives; Thread 2: network shares via MPR.DLL |

2. **For each file, classify by size:**

| File Size | Action |
|---|---|
| Small files | **Skipped entirely** — not encrypted |
| Medium files | Encrypted starting at offset `4000h` (leaves first 16 KB intact) |
| Large files | Partial encryption |

3. **Encrypt in-place** with per-file RC4 key (117 bytes, generated via Mersenne Twister PRNG).
4. **Prepend** `Clop^_-` marker to encrypted content.
5. **Create companion file**: `filename.C_l_0P` containing the RSA-encrypted RC4 key + metadata (file size, encryption time).
6. **Original filename is NOT changed** — Cl0p relies on the companion file and marker to identify encrypted files.

**Linux variant differences:**
- No separate key files — key is embedded but "protected" with symmetric RC4 (broken).
- No file-size differentiation — all sizes treated the same.
- No drive enumeration — encrypts from working directory recursively.
- No hashing-based file exclusion — encrypts more indiscriminately.

---

### Comparative Filesystem Interaction Diagram

```
IN-PLACE OVERWRITE (most common):
  Ryuk, Conti, REvil, LockBit, BlackCat, Cl0p
  ┌─────────────────┐
  │  original.dat    │──read──→ [memory buffer]
  └─────────────────┘              │
       ↑                           ↓ encrypt
       │                    [encrypted buffer]
       │                           │
       └──────write back───────────┘
       └──append footer (key + marker)
       └──rename to .extension

STAGED APPROACH (WannaCry only):
  ┌─────────────────┐
  │  original.dat    │──read──→ [memory buffer] ──encrypt──→ write to original.dat.WNCRYT
  └─────────────────┘                                                    │
       │                                                                 ↓
       └──move to $RECYCLE/%%TEMP%%                    rename to original.dat.WNCRY
       └──OR──overwrite with random data (important folders)

COMPANION FILE (Cl0p only):
  ┌─────────────────┐
  │  original.dat    │──read──→ [memory buffer] ──encrypt──→ write back in-place
  └─────────────────┘              │
       (NOT renamed)               └──→ create original.dat.C_l_0P (encrypted key)
       └──prepend "Clop^_-" marker
```

### Encryption Coverage Per File — Visual Comparison

For a hypothetical **100 MB file**:

```
                    0%                                              100%
                    ├──────────────────────────────────────────────────┤
WannaCry:           ████████████████████████████████████████████████████  100% (full)
Ryuk:               ████████████████████████████████████████████████████  100% (full, 1MB chunks)
Conti (DB ext):     ████████████████████████████████████████████████████  100% (full)
Conti (>5MB):       ██████░░░░░░░██████░░░░░░░██████░░░░░░░██████░░░░░  ~35% (5 chunks × 7%)
LockBit v2:         ████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  0.004% (4 KB only)
LockBit v4 (>1MB):  █████░░░░░░░░░░░░░░░░░░░█████░░░░░░░░░░░░░░░░█████  ~27% (3 × 9%)
BlackCat Smart:     ████████░░░░░░████████░░░░░░████████░░░░░░████████  ~40% (10MB per 10%)
BlackCat Fast:      ████████░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  ~10% (first N MB)
REvil (et=2):       ██████░░░░░░██████░░░░░░██████░░░░░░██████░░░░░░░░  ~25% (1MB encrypt, skip, repeat)
Cl0p (medium):      ░░░░░░░░░░░░░░░███████████████████████████████████  ~98% (from offset 4000h)
Cl0p (small):       ░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░  0% (skipped!)
```

█ = encrypted bytes, ░ = unencrypted bytes

---

### File Exclusion & Targeting Strategies

| Family | Targeting Approach | Excludes System Files | Extension Whitelist/Blacklist | Folder Exclusions |
|---|---|---|---|---|
| **WannaCry** | Extension blacklist (176 types) | Implicit (doesn't target system dirs) | Blacklist: scans for specific extensions | Scans all drives + network |
| **Ryuk** | Allowlist approach | Yes (Windows, Mozilla, Chrome, Recycle Bin) | Skips `.dll`, `.lnk`, `.hrmlog`, `.ini`, `.exe` | Explicit allowlist |
| **Conti** | Size + extension hybrid | Yes (.exe, .dll, .sys, .lnk, own extension) | 171 extensions → full; 20 extensions → partial; rest → size-based | Config-driven |
| **REvil** | Config-driven whitelist | Yes (whitelist in `wht` config key) | Full config: `fld` (folders), `fls` (files), `ext` (extensions) | Full config |
| **LockBit** | Hash-based + config | Yes (DirectoryList, FileSet, NoneSet + regex) | Hash lists for excluded filenames | Config + regex patterns |
| **BlackCat** | JSON config | Yes (config exclusion lists) | Full JSON config with exclusion lists | Full JSON config |
| **Cl0p** | Size-based only (Win) | No explicit system exclusion on Linux | Extension-based hashing (Win only) | Limited |

---

### Forensic Recovery Implications

| Family | Original File Recoverable? | Method | Caveats |
|---|---|---|---|
| **WannaCry** | **Partially** | Data recovery tools for files in `%TEMP%`; no recovery for Desktop/Documents | Sync bugs leave some originals in-place |
| **Ryuk** | **No** | Original overwritten in-place | Decryptor bug truncates last byte of large files |
| **Conti** | **No** | Original overwritten in-place | Header-only mode leaves most data intact but file is unusable |
| **REvil** | **No** | Original overwritten in-place | Last version bug: files renamed but not encrypted (recoverable) |
| **LockBit** | **No** | Original overwritten in-place | Key reuse every 1000 files (v3) — potential decryption without key |
| **BlackCat** | **No** | Original overwritten in-place | Checkpoint files may reveal encryption progress |
| **Cl0p (Win)** | **No** | Original overwritten; small files untouched | Small files skipped = intact but identified as encrypted |
| **Cl0p (Linux)** | **Yes** | Free decryptor available; symmetric key flaw | Only Linux variant |

---

## Encryption Campaign Strategy — Mass vs. Gradual, Full vs. Partial

This section answers a specific question: **when ransomware hits a host or network, how does it sweep the filesystem?** Does it encrypt everything at once in a blitz, or spread gradually? Does it hit local drives first then network, or both simultaneously? Does it encrypt every file entirely, or selectively target only portions?

### Campaign Pace Matrix

| Family | Campaign Style | Real-World Timeline | Scope Control | Can Operator Target Specific Paths? |
|---|---|---|---|---|
| **WannaCry** | **Blitzkrieg — autonomous mass** | Minutes per host | None — encrypts everything reachable | No — fully autonomous worm |
| **Ryuk** | **Staged gradual — local then continuous network** | ~10 min local disk; continuous network scanning | Allowlist only (specific folders excluded) | No — runs fixed pattern |
| **Conti** | **Operator-directed mass** | Minutes (32 threads) | `--encrypt-mode all/local/network` + `-h` host list | Yes — CLI args for scope |
| **REvil** | **Config-driven mass** | Minutes (IOCP pool) | `-nolan`, `-nolocal`, `-path`, `-fast`, `-full` | Yes — path/target flags |
| **LockBit** | **Blitzkrieg — fastest on market** | **<2 minutes** for full host | Config (FastSet, IntermittentSet, NetworkShares) | Yes — config-driven |
| **BlackCat** | **Config-driven mass** | Minutes (4-worker pool) | JSON config (modes, exclusions, cipher) | Yes — extensive JSON config |
| **Cl0p** | **Mass or precision-targeted** | Minutes to hours | `temp.ocx` file list / `runrun` dual-thread | Yes — file list or full sweep |

### Encryption Sweep Sequence — What Gets Hit First?

```
WannaCry (autonomous worm):
  ┌──────────────────────────────────────────────────────────────┐
  │  Phase 1: SMB worm propagation (EternalBlue)                │
  │    → Infects new hosts autonomously                         │
  │    → Each host starts encrypting immediately                │
  │                                                              │
  │  Phase 2: Local encryption (all drives, A:-Z:)              │
  │    → Single-threaded: file-by-file, sequential              │
  │    → No network targeting — worm handles spread              │
  │    → Full file encryption for every target file             │
  │                                                              │
  │  NO prioritization. NO operator input. Pure autonomous mass. │
  └──────────────────────────────────────────────────────────────┘

Ryuk (staged: local → continuous network):
  ┌──────────────────────────────────────────────────────────────┐
  │  Phase 1: Local drives (6 sequential rounds, A:-Z:)         │
  │    → Round 1-6 target different drive types per round        │
  │    → Full file encryption for most files                    │
  │    → 1 thread per file, unbounded thread creation            │
  │                                                              │
  │  Phase 2: Network discovery (CONTINUOUS, runs in parallel)  │
  │    → ARP cache scan → identify live subnets                 │
  │    → Ping sweep 192.168.x.x, 172.16-31.x.x, 10.x.x.x      │
  │    → Wake-On-LAN packets to sleeping hosts                   │
  │    → SMB mount via UNC: \\HOST\X$\                          │
  │    → Encrypt mounted shares using same process               │
  │    → NEVER STOPS scanning — continuous loop                  │
  │                                                              │
  │  Takes ~10 min for local. Network scanning is indefinite.    │
  └──────────────────────────────────────────────────────────────┘

Conti (operator-directed, 32 parallel workers):
  ┌──────────────────────────────────────────────────────────────┐
  │  Phase 0: Operator selects mode via CLI:                     │
  │    --encrypt-mode all | local | network                      │
  │    -h <hostfile> (list of targets/exceptions)                │
  │                                                              │
  │  Phase 1: Enumerate targets                                  │
  │    → Local: FindFirstFile/FindNextFile on all drives         │
  │    → Network: NetShareEnum for SMB shares                    │
  │                                                              │
  │  Phase 2: Parallel encryption (32 IOCP workers)              │
  │    → All targets encrypted simultaneously                    │
  │    → Per-file mode selected by size + extension tier         │
  │    → Kill processes via Restart Manager before each file     │
  │                                                              │
  │  Phase 3: Append footer + rename + drop ransom note          │
  │                                                              │
  │  Fully operator-controlled. Can target specific networks.     │
  └──────────────────────────────────────────────────────────────┘

LockBit (blitzkrieg with domain-wide GPO):
  ┌──────────────────────────────────────────────────────────────┐
  │  Phase 0 (pre-encryption, operator-driven):                  │
  │    → GPO pushes NetworkShares.xml to domain hosts            │
  │    → Shares ALL drives (C:-Z:) on every host via SMB         │
  │    → This makes every drive accessible for encryption         │
  │                                                              │
  │  Phase 1: Traverse + queue (1 producer thread)               │
  │    → Single thread walks directory tree                      │
  │    → Queues files to IOCP (FIFO)                             │
  │                                                              │
  │  Phase 2: Encrypt (N consumer threads, N = 2× or 3× CPUs)   │
  │    → File processing pool: 3× logical processor count        │
  │    → Each file: fast mode by default (4 KB only!)            │
  │    → Registry Run key ensures restart if interrupted         │
  │                                                              │
  │  Phase 3: Self-destruct when complete                        │
  │    → fsutil setZeroData + Del (wipes own binary)             │
  │                                                              │
  │  <2 minutes for full host encryption. Fastest in the market.  │
  └──────────────────────────────────────────────────────────────┘

BlackCat (config-driven, cross-platform):
  ┌──────────────────────────────────────────────────────────────┐
  │  Phase 0: Parse JSON config (--access-token protected)       │
  │    → Encryption mode, cipher, exclusions, propagation creds  │
  │                                                              │
  │  Phase 1: Enumerate + encrypt (4-worker pool)                │
  │    → Walk filesystem, classify each file by config rules     │
  │    → Auto mode: dynamically picks strategy per file          │
  │    → Creates checkpoint files for resume-after-interrupt     │
  │                                                              │
  │  Phase 2 (optional): Self-propagate via PsExec               │
  │    → Embedded PsExec dropped to %TEMP%                       │
  │    → Copy self to remote (-c), overwrite (-f), run as SYSTEM │
  │    → Repeat Phase 1 on each new host                         │
  │                                                              │
  │  Phase 3: Target ESXi (if on VMware host)                    │
  │    → Stop VMs, encrypt VMDK/flat.vmdk files                  │
  │                                                              │
  │  Highly configurable. Operator controls every aspect.         │
  └──────────────────────────────────────────────────────────────┘

Cl0p (mass OR precision):
  ┌──────────────────────────────────────────────────────────────┐
  │  Mode A: Mass sweep (no params)                              │
  │    → Installs as Windows service for persistence              │
  │    → Enumerates all local + network drives                   │
  │    → Size-based: skip small, encrypt medium+, partial large  │
  │                                                              │
  │  Mode B: Precision targeting (temp.ocx param)                │
  │    → Only encrypts files listed in temp.ocx                  │
  │    → Used in targeted attacks (MOVEit, GoAnywhere victims)   │
  │                                                              │
  │  Mode C: Dual-thread (runrun param)                          │
  │    → Thread 1: local drives (ReadFile/WriteFile)             │
  │    → Thread 2: network shares (via MPR.DLL)                  │
  │    → Both run simultaneously                                 │
  │                                                              │
  │  Most flexible: mass or surgical, operator's choice.          │
  └──────────────────────────────────────────────────────────────┘
```

### Full File Encryption vs. Partial — Per-File Coverage

This is about **what happens inside each individual file**. Does the ransomware encrypt the entire file contents, or only a strategic portion?

| Family | Small Files (<1 MB) | Medium Files (1-5 MB) | Large Files (>5 MB) | Very Large Files (>50 MB) |
|---|---|---|---|---|
| **WannaCry** | **100%** — full | **100%** — full | **100%** — full | **100%** — full |
| **Ryuk** | **100%** — full | **100%** — full | **100%** — full | **Partial** — only certain 1 MB chunks |
| **Conti** | **100%** — full | **~20%** — header only (first 1 MB) | **20-50%** — stepped chunks | **20-50%** — stepped chunks |
| **Conti (DB ext)** | **100%** — full | **100%** — full | **100%** — full | **100%** — full |
| **REvil (et=0)** | **100%** — full | **100%** — full | **100%** — full | **100%** — full |
| **REvil (et=1)** | **100%** — full | **~20%** — first 1 MB | **~1-5%** — first 1 MB | **<1%** — first 1 MB |
| **REvil (et=2)** | **100%** — full | **~25%** — 1MB on/off | **~10-25%** — 1MB + skip | **~5-25%** — 1MB + skip |
| **LockBit v2** | **0.004%** — 4 KB | **0.004%** — 4 KB | **0.004%** — 4 KB | **0.004%** — 4 KB |
| **LockBit v3 (fast)** | **0.4%** — 4 KB | **0.04%** — 4 KB | **<0.01%** — 4 KB | **<0.001%** — 4 KB |
| **LockBit v3 (intermittent)** | **100%** — full | **Configurable %** | **Configurable %** | **Configurable %** |
| **LockBit v4** | **100%** — full | **100%** — full | **~27%** — 3 chunks × 9% | **~27%** — 3 chunks × 9% (capped 1MB) |
| **LockBit v5** | **100%** — full | **100%** — full | **100%** — full (≤83 MB) | **Chunked** — 8 MB blocks |
| **BlackCat (Full)** | **100%** — full | **100%** — full | **100%** — full | **100%** — full |
| **BlackCat (Fast)** | **100%** — first N MB | **~20-50%** — first N MB | **~5-20%** — first N MB | **~1-5%** — first N MB |
| **BlackCat (SmartPattern)** | **100%** — full | **~30-40%** — 10MB per 10% | **~40%** — 10MB per 10% | **~40%** — 10MB per 10% |
| **BlackCat (Auto)** | **100%** — full | **Varies** by ext+size | **Varies** by ext+size | **Varies** by ext+size |
| **Cl0p (Win)** | **0%** — skipped! | **~98%** — from offset 4000h | **Partial** | **Partial** |
| **Cl0p (Linux)** | **100%** — full | **100%** — full | **100%** — full | **100%** — full |

**Key insight**: LockBit v2/v3's "fast" mode encrypts only 4 KB per file regardless of size — at **0.004%** coverage for a 100 MB file, it's the most aggressive speed-vs-damage tradeoff. The file is rendered unusable (most file formats become unrecoverable if their header is destroyed) while achieving blazing speed.

### Full Filesystem Sweep vs. Targeted Selection

```
                    FULL SWEEP                              TARGETED
                    (encrypt everything reachable)           (operator selects what to hit)
                    ─────────────────────────                ─────────────────────────────
                    │                                        │
  WannaCry ●───────┤                                        │
  (autonomous)      │                                        │
                    │                                        │
  Ryuk ●────────────┤                                        │
  (6 rounds A:-Z:,  │                                        │
   continuous net)   │                                        │
                    │                                        │
  Conti ●───────────┤──●                                     │
  (all by default,  │  (with -h hostfile)                    │
   configurable)    │                                        │
                    │                                        │
  REvil ●───────────┤───────────────────────●                │
  (all by default,  │  (-path for specific dirs)             │
   -nolan/-nolocal) │                                        │
                    │                                        │
  LockBit ●─────────┤───────────────────────────────────●    │
  (all by default,  │  (config-driven exclusion,             │
   fastest speed)   │   GPO domain-wide shares)              │
                    │                                        │
  BlackCat ●────────┤────────────────────────────────────●   │
  (all by default,  │  (JSON config, most granular           │
   most configurable)│   control of any family)              │
                    │                                        │
  Cl0p ●────────────┤───────────────────────────────────●───●
  (mass or targeted) │  (temp.ocx file list,                  │
                    │   runrun dual-thread)                   │
```

### Interruption Resilience — What Happens If You Pull the Plug?

| Family | Resists Interruption? | Mechanism | What If Power-Cut Mid-Encryption? |
|---|---|---|---|
| **WannaCry** | **No** | No persistence mechanism | Partially encrypted files with `.WNCRYT` remain; some originals in `%TEMP` |
| **Ryuk** | **No** | No auto-restart | Partially encrypted file remains with incomplete footer; HERMES marker may be absent |
| **Conti** | **No** | No persistence | Partially encrypted files remain; no resume capability |
| **REvil** | **Partial** | `RunOnce` registry key (safe mode) | Re-runs in safe mode but starts from scratch |
| **LockBit** | **Yes** | `HKCU\...\Run` registry key | **Auto-restarts** on boot; picks up where it left off. Self-destructs ONLY after full completion |
| **BlackCat** | **Yes** | Checkpoint files (`checkpoint-<id>`) | Can **resume** encryption from checkpoint — tracks progress per file |
| **Cl0p** | **Partial** | Installs as Windows service | Service restarts on boot; but no per-file resume |

### Encryption Speed Benchmarks (Real-World)

| Family | Local Disk (typical) | Full Enterprise Deployment | Encryption Coverage Per File | Throughput Strategy |
|---|---|---|---|---|
| **WannaCry** | ~30-60 min | Hours (worm propagation) | 100% | Single-threaded, full file |
| **Ryuk (2018)** | ~60 min | Hours (network scan loop) | 100% (partial for >54 MB) | 1 thread/file, full chunks |
| **Ryuk (2020)** | **<10 min** | Hours (network scan loop) | 100% (partial for >54 MB) | Multi-process + injection |
| **Conti** | **<15 min** | Minutes (operator-driven) | 20-100% (size-tiered) | 32 IOCP workers |
| **REvil** | **<10 min** | Minutes (IOCP pool) | 1-100% (config-driven) | IOCP thread pool |
| **LockBit v2** | **<5 min** | **<2 hours** (domain-wide) | **0.004%** (4 KB) | IOCP, minimal I/O per file |
| **LockBit v3** | **<2 min** | **<2 hours** (domain-wide) | 0.004-100% (mode-based) | IOCP + hidden threads |
| **LockBit v4** | **<5 min** | Hours (domain-wide) | 27-100% (size-based) | 2×/3× CPU thread pools |
| **BlackCat** | **<10 min** | Minutes-hours (PsExec) | 1-100% (5 modes) | 4-worker pool |
| **Cl0p** | **<15 min** | Minutes (targeted or mass) | 0-100% (size-based) | 1-2 threads |

### The Two Strategic Philosophies

```
┌─────────────────────────────────────────────────────────────────────┐
│                     PHILOSOPHY 1: "BRUTE FORCE"                     │
│                                                                     │
│  Encrypt everything, every byte, everywhere.                        │
│  Families: WannaCry, Ryuk, REvil (et=0), BlackCat (Full mode)      │
│                                                                     │
│  ✓ Maximum damage per file — no forensic recovery possible          │
│  ✓ Simpler code, fewer bugs                                        │
│  ✗ Slow — limited by disk I/O and CPU                               │
│  ✗ Gives defenders more time to detect and respond                  │
│  ✗ Higher chance of partial encryption if interrupted               │
│                                                                     │
│  Typical timeline: 10-60 minutes per host                           │
│  When operator has: already spent weeks on recon, wants certainty   │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                   PHILOSOPHY 2: "SURGICAL SPEED"                    │
│                                                                     │
│  Encrypt the minimum needed to destroy file usability.              │
│  Families: LockBit, Conti, BlackCat (Smart/Auto), REvil (et=1/2)   │
│                                                                     │
│  ✓ Extremely fast — LockBit completes in <2 min                     │
│  ✓ Less I/O = less noise for EDR to detect                          │
│  ✓ LockBit v2: only 4KB per file → encrypts 10,000s of files/min   │
│  ✗ Unencrypted portions may be forensically extractable             │
│  ✗ Some file formats can survive partial encryption                 │
│  ✗ More complex code = more bugs (LockBit v3 key reuse)             │
│                                                                     │
│  Typical timeline: 2-10 minutes per host                            │
│  When operator wants: maximum hosts encrypted before detection      │
└─────────────────────────────────────────────────────────────────────┘

                    TREND OVER TIME:
    2017 ──────────────────────────────────────────→ 2026
    
    Full-file encryption          Partial/adaptive encryption
    (WannaCry, early Ryuk)        (LockBit, BlackCat, modern variants)
    
    "Encrypt every byte"    ──→   "Encrypt just enough to destroy usability"
    Single-threaded         ──→   32+ parallel workers
    Hours per host          ──→   Minutes per host
    No persistence          ──→   Auto-restart + checkpoint resume
    Fixed algorithm         ──→   Configurable per-file strategy
```

---

## Encryption Strategy Comparison

### Encryption Granularity

| Strategy | Families | Trade-off |
|---|---|---|
| **Full file encryption** | WannaCry, Ryuk, REvil | Maximum damage, slowest |
| **Fixed-size partial (4 KB)** | LockBit 2.0 | Fastest, some data recoverable from unencrypted portions |
| **Size-tiered (full/header/partial)** | Conti, Cl0p | Balances speed and damage based on file importance |
| **Percentage-based (10% steps)** | BlackCat SmartPattern, LockBit 5.0 | Optimal balance — renders files unusable while preserving speed |
| **Adaptive (auto)** | BlackCat Auto | Dynamically optimizes per-file |
| **Chunk-based (1 MB)** | Ryuk | Predictable throughput |

### Key Exchange Evolution

| Generation | Mechanism | Families | Key Size |
|---|---|---|---|
| **1st Gen (2017)** | Hardcoded RSA | WannaCry | RSA-2048 |
| **2nd Gen (2018–2020)** | Per-sample RSA + master RSA | Ryuk, Conti, Cl0p | RSA-1024 to RSA-4096 |
| **3rd Gen (2019+)** | Elliptic Curve (ECDH) | REvil, LockBit, BlackCat | Curve25519 / X25519 |

The shift from RSA to Curve25519/ECDH provides equivalent security with dramatically smaller key sizes (32 bytes vs 256+ bytes) and faster key generation — important for per-file operations.

### Performance Optimization Techniques

1. **Multi-threading**: All modern families (post-2019) use multi-threaded encryption.
   - Conti: Most sophisticated — 32 I/O completion port workers.
   - Ryuk: One thread per file (simpler but effective).
   - LockBit: Shared state architecture across child threads.

2. **Partial encryption**: Encrypting only a portion of each file dramatically speeds up operations while still rendering files unusable.
   - LockBit 2.0: 4 KB per file (extreme)
   - Conti: 20–50% of file data
   - BlackCat SmartPattern: 10 MB per 10% of file

3. **Stream ciphers over block ciphers**: Salsa20/ChaCha20 are stream ciphers that don't require padding and can encrypt arbitrary byte ranges efficiently — favored by REvil, Conti, LockBit, BlackCat over AES's block-based approach.

---

## Threat Landscape Trends

| Trend | Evidence |
|---|---|
| **Rust adoption** | BlackCat (2021), more RE variants likely — harder to reverse-engineer, cross-platform |
| **ECDH over RSA** | REvil → LockBit → BlackCat all use Curve25519 — smaller keys, faster ops |
| **ChaCha20 over AES** | Conti, REvil, LockBit all favor ChaCha20/Salsa20 — no hardware dependency, stream-friendly |
| **Adaptive encryption** | BlackCat Auto mode, LockBit size-dependent — maximize damage per second |
| **Double extortion** | All post-2020 families — encryption alone is no longer enough; data exfiltration is standard |
| **Cross-platform** | BlackCat (Win/Linux/ESXi), LockBit (Win/ESXi), Cl0p (Win/Linux) — targeting infrastructure, not just desktops |
| **RaaS professionalization** | All post-2019 families — builders, configs, affiliate portals, negotiation platforms |
| **Exploitation-driven** | Cl0p (MOVEit, GoAnywhere), WannaCry (EternalBlue) — targeting vulnerabilities for mass deployment |

---

## Cryptographic Summary Table

| Family | Symmetric | Key Size | Asymmetric / Kx | Key Size | Key Scope | File Recovery Without Attacker Key? |
|---|---|---|---|---|---|---|
| WannaCry | AES-128-CBC | 128-bit | RSA | 2048-bit | Per-file AES | No (memory forensics only) |
| Ryuk | AES-256 | 256-bit | RSA (3-layer) | 2048-bit | Per-file AES | No |
| Conti | ChaCha20 | 256-bit | RSA | 4096-bit | Per-file ChaCha | No |
| REvil | Salsa20 | 256-bit | Curve25519 ECDH | 256-bit | Per-file Salsa | No |
| LockBit 2.0 | XSalsa20-Poly1305 | 256-bit | Curve25519 | 256-bit | Per-file XSalsa | No |
| LockBit 4.0 | XChaCha20 | 256-bit | Curve25519 | 256-bit | Per-file XChaCha | No |
| LockBit 5.0 | ChaCha20-Poly1305 | 256-bit | X25519+BLAKE2b | 256-bit | Per-file ChaCha | No |
| BlackCat | AES / ChaCha20 | 128/256-bit | RSA | 2048-bit | Per-file | No |
| Cl0p (Win) | RC4 | ~117 bytes | RSA | 1024-bit | Per-file RC4 | No (but weakest RSA) |
| Cl0p (Linux) | RC4 | ~117 bytes | RC4 (symmetric!) | 100 bytes | Per-file RC4 | **Yes** (flawed — free decryptor available) |

---

## Sources & References

- HHS HC3: BlackCat Analyst Note (2022-12)
- Chuong Dong: LockBit v2.0 Analysis (2022-03), LockBit v4.0 Analysis (2025-03)
- ASEC/AhnLab: LockBit 5.0 In-Depth Analysis (2026-01)
- NioGuard Security Lab: Ryuk Ransomware Analysis (2019-12)
- SentinelLabs: Ryuk Encryption Evolution (2020-10), Cl0p Linux Decryptor (2023-02)
- OALABS Research: Conti V2 Source Code Leak (2022-03), BlackCat Ransomware (2022-03)
- Qualys: Conti Ransomware Analysis (2021-11)
- VMware TAU: Conti Threat Discovery (2020-07)
- Sophos: Relentless REvil Revealed (2021-06)
- SecurityScorecard: REvil Last Version Analysis
- Mandiant/Google Cloud: WannaCry Malware Profile (2017-05)
- Secureworks: WCry Ransomware Analysis (2017-05)
- EY: BlackCat Technical Analysis (2024)
- Cyble: LockBit 2.0 Deep Dive (2021-08), Cl0p Analysis (2023-04)
- Truesec: LockBit Analysis (2024-08)
- lldre: Conti Blog / Encryption Loop Analysis (2021-07)
- Fraunhofer FKIE Malpedia: BlackCat Entry

---

*Document generated: April 30, 2026 | For security research and defensive purposes only.*

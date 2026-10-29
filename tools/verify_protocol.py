"""
verify_protocol.py — proves the codec matches the APK's own hard-coded frames.

The APK builds three frames from `fill-array-data` constants plus a checksum
byte. If our encoder reproduces those bytes exactly, the frame layout and the
checksum rule are confirmed against real firmware traffic.
"""

import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

import lamp_protocol as lp

EXPECTED = [
    # (description, encoder call, bytes taken from the APK's own constants)
    ("get device info (on connect)", lp.get_info(), "55 01 00 FE AA"),
    ("builtin-mode shortcut", lp.system_builtin_mode(), "55 02 16 01 E6 AA"),
    ("RF pairing", lp.system_rf_pair(), "55 02 16 03 E4 AA"),
]

failures = 0
print("=" * 70)
print("A. frames rebuilt from APK constants")
print("=" * 70)
for desc, produced, want in EXPECTED:
    got = " ".join(f"{b:02X}" for b in produced)
    ok = got == want
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'}  {desc:32s} {got}  (want {want})")

print()
print("=" * 70)
print("B. request encoders (derived from disassembly immediates)")
print("=" * 70)
# total frame length = 5 + payload_len   (SOF, L, CMD, payload, CK, EOF)
cases = [
    ("brightness 100/100", lp.set_brightness(100, 100), 7),
    ("brightness 64/0", lp.set_brightness(64, 0), 7),
    ("rgb 255,0,0 / 0,0,255", lp.set_rgb((255, 0, 0), (0, 0, 255)), 11),
    ("power on", lp.set_power(2), 6),
    ("power off", lp.set_power(0), 6),
    ("power follow", lp.set_power(1), 6),
    ("speed 3", lp.set_speed(3), 6),
    ("direction reverse", lp.set_direction(True), 6),
    ("rot follow on", lp.set_rot_follow(True), 6),
    ("led count 150", lp.set_led_count(150), 6),
    ("mic sens 50", lp.set_mic_sensitivity(50), 6),
    ("mode 1,2", lp.set_mode(1, 2), 7),
    ("flow family0 step3", lp.set_flow(0, 3), 6),
    ("flow family4 step1", lp.set_flow(4, 1), 6),
    ("get state", lp.get_state(), 6),
]
for desc, raw, nbytes in cases:
    try:
        f = lp.decode(raw)
        assert len(raw) == nbytes, f"length {len(raw)} != {nbytes}"
        assert bytes(f) == raw
        ok = True
        note = f"{' '.join(f'{b:02X}' for b in raw)}  -> cmd={f.cmd:#04x} payload={f.payload.hex()}"
    except Exception as exc:                       # noqa: BLE001
        ok = False
        note = f"ERROR {exc}"
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'}  {desc:26s} {note}")

print()
print("=" * 70)
print("C. status-frame parse + stream reassembly")
print("=" * 70)

# Build the 18-byte state frame the firmware sends: L = 14, CMD = 0x00,
# 13 payload bytes.
payload = bytes([
    80, 3, 255, 0, 0,      # zone1: brightness 80, mode 3, red
    40, 5, 0, 0, 255,      # zone2: brightness 40, mode 5, blue
    1,                     # power on
    32,                    # mic level
    0,                     # unused
])
state_frame = lp.encode(lp.CMD_GET_INFO, payload)
assert len(state_frame) == 18, len(state_frame)
assert state_frame[1] == 14, state_frame[1]
print("state frame:", " ".join(f"{b:02X}" for b in state_frame))

dec = lp.StreamDecoder()
# split into 20-byte MTU chunks (BLE notifications arrive fragmented)
chunks = [state_frame[i:i + 20] for i in range(0, len(state_frame), 20)]
chunks = [c for c in chunks]
frames = []
for c in chunks:
    frames += dec.feed(c)
assert len(frames) == 1, frames
st = lp.LampState.from_payload(frames[0].payload)
print("parsed     :", st.summary())
assert st.is_on and st.brightness1 == 80 and st.red1 == 255 and st.blue2 == 255
assert st.mic_value == 32 and st.mode2 == 5
print("PASS  state fields match")

# mixed garbage + frame must resync
frames2 = dec.feed(b"\x00\x13" + state_frame + b"\xff")
assert len(frames2) == 1, frames2
print("PASS  stream resync past noise")

# a stray 0x55 inside the noise must NOT be mistaken for a frame start
# (0x55 would be read as an 85-byte length and stall a naive parser)
frames3 = lp.StreamDecoder().feed(b"\x00\x55\x13" + state_frame + b"\xff")
assert len(frames3) == 1, frames3
assert lp.LampState.from_payload(frames3[0].payload).brightness1 == 80
print("PASS  stray 0x55 in noise does not stall the parser")

# frame split across 5-byte chunks (small MTU)
small = lp.StreamDecoder()
reassembled = []
for k in range(0, len(state_frame), 5):
    reassembled += small.feed(state_frame[k:k + 5])
assert len(reassembled) == 1, reassembled
print("PASS  fragmented frame reassembled")

# corrupted checksum must be rejected
bad = bytearray(state_frame)
bad[-2] ^= 0xFF
dec2 = lp.StreamDecoder()
try:
    got = dec2.feed(bytes(bad))
    # decoder drops bytes one at a time until it can't parse: expect no frames
    assert got == [], got
    print("PASS  corrupted checksum rejected")
except Exception as exc:                            # noqa: BLE001
    failures += 1
    print("FAIL  corrupted checksum", exc)

print()
print("=" * 70)
print("D. known plaintext check of checksum rule")
print("=" * 70)
# 2 + 0x16 + 1 = 0x19 ; ~0x19 = 0xE6  (matches APK constant)
print(f"~(2+0x16+1)&0xFF = {lp.checksum(bytes([2, 0x16, 1])):#04x}  (APK used 0xE6)")
# 2 + 0x16 + 3 = 0x1B ; ~0x1B = 0xE4
print(f"~(2+0x16+3)&0xFF = {lp.checksum(bytes([2, 0x16, 3])):#04x}  (APK used 0xE4)")
# 1 + 0 = 1 ; ~1 = 0xFE
print(f"~(1+0)&0xFF      = {lp.checksum(bytes([1, 0x00])):#04x}  (APK used 0xFE)")

print()
print("=" * 70)
print("E. golden frames shared with the Kotlin unit tests")
print("=" * 70)
GOLDEN = [
    ("flow family0 step3", lp.set_flow(0, 3), "55 02 22 2A B1 AA"),
    ("flow family4 step1", lp.set_flow(4, 1), "55 02 22 0E CD AA"),
    ("single zone brightness", lp.set_brightness(100, 40), "55 03 02 64 28 6E AA"),
    ("rgb red/black", lp.set_rgb((255, 0, 0), (0, 0, 0)), "55 07 04 FF 00 00 00 00 00 F5 AA"),
    ("rgb white/all", lp.set_rgb((255, 255, 255), (255, 255, 255)),
     "55 07 04 FF FF FF FF FF FF FA AA"),
]
for desc, raw, want in GOLDEN:
    got = " ".join(f"{b:02X}" for b in raw)
    ok = got == want
    failures += 0 if ok else 1
    print(f"{'PASS' if ok else 'FAIL'}  {desc:26s} {got}  (want {want})")

print()
print("RESULT:", "ALL CHECKS PASSED" if failures == 0 else f"{failures} FAILURE(S)")
sys.exit(1 if failures else 0)

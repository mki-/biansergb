"""
lamp_protocol.py — reference codec for the "氛围灯" (ambient lamp) BLE protocol
reverse-engineered from com.yl.milight v1.0.0 (ColorfulLED).

Frame layout (all multi-byte fields are single bytes):

    +------+------+------+---------------------+------+------+
    | 0x55 |  L   | CMD  | PAYLOAD (L-1 bytes) |  CK  | 0xAA |
    +------+------+------+---------------------+------+------+

    L  = 1 + len(PAYLOAD)          # 1 for CMD itself
    CK = (~(L + CMD + sum(PAYLOAD))) & 0xFF

This module is dependency-free and is used both as the written spec's
executable form and as a cross-check for the Kotlin implementation.
"""

from __future__ import annotations

from dataclasses import dataclass, field
from typing import List, Optional, Sequence, Tuple

SOF = 0x55
EOF = 0xAA

# ----------------------------------------------------------------- commands
# request command bytes (byte index 2)
CMD_GET_INFO = 0x00        # payload: empty        -> 55 01 00 FE AA
CMD_BRIGHTNESS = 0x02      # payload: [b1, b2]     0..100 each
CMD_MODE = 0x03            # payload: [p1, p2]
CMD_RGB = 0x04             # payload: [r1,g1,b1, r2,g2,b2]
CMD_POWER = 0x05           # payload: [0=off, 1=follow, 2=on]
CMD_MIC_SENS = 0x06        # payload: [0..100]
CMD_GET_STATE = 0x11       # payload: [0x00]
CMD_SYSTEM = 0x16          # payload: [0x01] builtin mode / [0x03] RF pair
CMD_FLOW = 0x22            # payload: [((idx-1)<<4) | (mode+10)]
CMD_SPEED = 0x23           # payload: [0..5]
CMD_DIRECTION = 0x24       # payload: [0=forward, 1=reverse]
CMD_ROT_FOLLOW = 0x25      # payload: [0=off, 1=on]
CMD_LED_COUNT = 0x26       # payload: [0..150]


# ------------------------------------------------------------------ framing
def checksum(body: bytes) -> int:
    """body = L | CMD | PAYLOAD ...  -> two's-complement style XOR-ones sum."""
    return (~sum(body)) & 0xFF


def encode(cmd: int, payload: Sequence[int] = ()) -> bytes:
    payload = bytes(payload)
    assert 0 <= cmd <= 0xFF, "command out of range"
    assert len(payload) <= 0xFD, "payload too long"
    body = bytes([1 + len(payload), cmd]) + payload
    return bytes([SOF]) + body + bytes([checksum(body), EOF])


@dataclass
class Frame:
    cmd: int
    payload: bytes

    def __bytes__(self) -> bytes:
        return encode(self.cmd, self.payload)

    def hex(self) -> str:
        return " ".join(f"{b:02X}" for b in bytes(self))


def decode(raw: bytes) -> Frame:
    """Validate and decode a single complete frame; raises ValueError."""
    if len(raw) < 5:
        raise ValueError(f"frame too short: {len(raw)} bytes")
    if raw[0] != SOF:
        raise ValueError(f"bad SOF: {raw[0]:#04x}")
    if raw[-1] != EOF:
        raise ValueError(f"bad EOF: {raw[-1]:#04x}")
    length = raw[1]
    if len(raw) != length + 4:
        raise ValueError(f"length byte {length} implies {length + 4} bytes, got {len(raw)}")
    body = raw[1:-2]
    if checksum(body) != raw[-2]:
        raise ValueError(f"bad checksum: got {raw[-2]:#04x}, want {checksum(body):#04x}")
    return Frame(cmd=raw[2], payload=bytes(raw[3:-2]))


class StreamDecoder:
    """Feed arbitrary notification chunks, get frames back.

    Mirrors the Kotlin implementation: scans for the earliest offset at which a
    *complete, checksum-valid* frame is fully buffered. Trusting the first 0x55
    would make a stray 0x55 in noise look like the start of an 85-byte frame and
    stall the parser.
    """

    def __init__(self) -> None:
        self._buf = bytearray()

    def _try_parse(self, start: int) -> Optional[Tuple[Frame, int]]:
        if start + 1 >= len(self._buf):
            return None
        need = self._buf[start + 1] + 4
        if start + need > len(self._buf):
            return None
        candidate = bytes(self._buf[start:start + need])
        try:
            return decode(candidate), need
        except ValueError:
            return None

    def feed(self, data: bytes) -> List[Frame]:
        self._buf += data
        out: List[Frame] = []
        while True:
            hit: Optional[Frame] = None
            hit_at = -1
            consumed = 0
            for i, byte in enumerate(self._buf):
                if byte != SOF:
                    continue
                parsed = self._try_parse(i)
                if parsed is not None:
                    hit, consumed = parsed
                    hit_at = i
                    break
            if hit is None:
                break
            out.append(hit)
            del self._buf[:hit_at + consumed]

        # Drop leading noise, but keep an incomplete tail that may still become a
        # frame: resume at the last plausible start-of-frame.
        keep_from = len(self._buf)
        for i, byte in enumerate(self._buf):
            if byte != SOF:
                continue
            if i + 1 == len(self._buf):        # header not received yet
                keep_from = i
                break
            if 1 <= self._buf[i + 1] <= 0xFE:  # plausible length byte
                keep_from = i
                break
        if keep_from > 0:
            del self._buf[:keep_from]

        if len(self._buf) > (0xFE + 4) * 2:
            self._buf.clear()
        return out

    def reset(self) -> None:
        self._buf.clear()


# --------------------------------------------------------------- requests
def get_info() -> bytes:
    return encode(CMD_GET_INFO)


def get_state() -> bytes:
    return encode(CMD_GET_STATE, [0x00])


def set_brightness(zone1: int, zone2: int) -> bytes:
    for v in (zone1, zone2):
        if not 0 <= v <= 100:
            raise ValueError("brightness must be 0..100")
    return encode(CMD_BRIGHTNESS, [zone1, zone2])


def set_rgb(zone1: Sequence[int], zone2: Sequence[int]) -> bytes:
    for zone, name in ((zone1, "zone1"), (zone2, "zone2")):
        if len(zone) != 3:
            raise ValueError(f"{name} must be 3 components")
        for v in zone:
            if not 0 <= v <= 255:
                raise ValueError(f"{name} component out of range")
    return encode(CMD_RGB, list(zone1) + list(zone2))


def set_power(mode: int) -> bytes:
    if mode not in (0, 1, 2):
        raise ValueError("power mode must be 0 (off), 1 (follow) or 2 (on)")
    return encode(CMD_POWER, [mode])


def set_speed(level: int) -> bytes:
    if not 0 <= level <= 5:
        raise ValueError("speed must be 0..5")
    return encode(CMD_SPEED, [level])


def set_direction(reverse: bool) -> bytes:
    return encode(CMD_DIRECTION, [1 if reverse else 0])


def set_rot_follow(on: bool) -> bytes:
    return encode(CMD_ROT_FOLLOW, [1 if on else 0])


def set_led_count(count: int) -> bytes:
    if not 0 <= count <= 150:
        raise ValueError("led count must be 0..150")
    return encode(CMD_LED_COUNT, [count])


def set_mic_sensitivity(value: int) -> bytes:
    if not 0 <= value <= 100:
        raise ValueError("mic sensitivity must be 0..100")
    return encode(CMD_MIC_SENS, [value])


def set_mode(p1: int, p2: int) -> bytes:
    return encode(CMD_MODE, [p1, p2])


def system_builtin_mode() -> bytes:
    return encode(CMD_SYSTEM, [0x01])


def system_rf_pair() -> bytes:
    return encode(CMD_SYSTEM, [0x03])


def set_flow(family_index: int, step: int) -> bytes:
    """family_index 0..4 (七彩/幻彩/魔幻/律动/跟随), step 1..16 (1-based)."""
    if not 0 <= family_index <= 4:
        raise ValueError("family_index must be 0..4")
    if not 1 <= step <= 16:
        raise ValueError("step must be 1..16")
    return encode(CMD_FLOW, [((step - 1) << 4) | (family_index + 10)])


# ---------------------------------------------------------------- responses
@dataclass
class LampState:
    """Decoded payload of a 55 0E 00 ... state frame (13 payload bytes)."""
    brightness1: int = 0
    mode1: int = 0
    red1: int = 0
    green1: int = 0
    blue1: int = 0
    brightness2: int = 0
    mode2: int = 0
    red2: int = 0
    green2: int = 0
    blue2: int = 0
    is_on: bool = False
    mic_value: int = 0
    unknown: int = 0
    raw: bytes = field(default=b"", repr=False)

    @classmethod
    def from_payload(cls, payload: bytes) -> "LampState":
        if len(payload) < 12:
            raise ValueError(f"state payload too short: {len(payload)}")
        p = payload
        return cls(
            brightness1=p[0], mode1=p[1],
            red1=p[2], green1=p[3], blue1=p[4],
            brightness2=p[5], mode2=p[6],
            red2=p[7], green2=p[8], blue2=p[9],
            is_on=p[10] != 0,
            mic_value=p[11],
            unknown=p[12] if len(p) > 12 else 0,
            raw=payload,
        )

    def summary(self) -> str:
        return (f"on={self.is_on} zone1=(b{self.brightness1},m{self.mode1},"
                f"rgb{self.red1},{self.green1},{self.blue1}) "
                f"zone2=(b{self.brightness2},m{self.mode2},"
                f"rgb{self.red2},{self.green2},{self.blue2}) mic={self.mic_value}")


# ASCII payloads (firmware version / model / working state) -----------------
def ascii_payload(frame: Frame) -> str:
    """The app builds String(bytes) then drops the last byte; mirror that."""
    text = frame.payload.decode("utf-8", "replace")
    return text[:-1] if text else text

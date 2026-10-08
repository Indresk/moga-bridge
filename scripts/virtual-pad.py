#!/usr/bin/env python3
"""Drive the virtual-gamepad helper from a PC, without a MOGA controller.

It registers its own pad (a second device next to the app's) through the helper and sends
button, D-pad and stick events, so you can check how a game or emulator reacts, or develop
the bridge, without hardware. The helper must be running (`pnpm android:uinput`).

Usage:
    python3 scripts/virtual-pad.py A down down right START
    python3 scripts/virtual-pad.py stick:l:100:0 wait:1 stick:l:0:0
    python3 scripts/virtual-pad.py --name "Test pad" A

Steps:
    A B X Y L R SELECT START     press and release a button
    up down left right           press and release the D-pad (hat)
    stick:<l|r>:<x>:<y>          hold a stick at x,y (-127..127, y down-positive) for --hold
    wait:<seconds>               pause

Needs `adb` in PATH (or ADB=/path/to/adb) and a USB-debugging phone.
"""
import argparse
import json
import os
import socket
import subprocess
import sys
import time

PACKAGE = os.environ.get("APP_PACKAGE", "dev.mogabridge.app")
ADB = os.environ.get("ADB", "adb")
PORT = int(os.environ.get("UINPUT_PORT", "7777"))
TOKEN_PATH = f"/sdcard/Android/data/{PACKAGE}/files/helper.token"

EV_KEY, EV_ABS = 1, 3
BUTTONS = {"A": 304, "B": 305, "X": 307, "Y": 308, "L": 310, "R": 311, "SELECT": 314, "START": 315}
HAT = {"left": (16, -1), "right": (16, 1), "up": (17, -1), "down": (17, 1)}
STICK_AXES = {"l": (0, 1), "r": (3, 4)}


def adb(*args):
    return subprocess.run([ADB, *args], capture_output=True, text=True, check=True).stdout.strip()


def register_command(name):
    axes = [(code, -127, 127) for code in (0, 1, 3, 4)] + [(16, -1, 1), (17, -1, 1)]
    return {
        "id": 1, "command": "register", "name": name, "vid": 0x20D6, "pid": 0x89E5, "bus": "bluetooth",
        "configuration": [
            {"type": 100, "data": [EV_KEY, EV_ABS]},
            {"type": 101, "data": list(BUTTONS.values())},
            {"type": 103, "data": [a[0] for a in axes]},
        ],
        "abs_info": [
            {"code": c, "info": {"value": 0, "minimum": lo, "maximum": hi, "fuzz": 0, "flat": 0, "resolution": 0}}
            for c, lo, hi in axes
        ],
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("steps", nargs="+")
    parser.add_argument("--name", default="MOGA Pocket (PC test)")
    parser.add_argument("--hold", type=float, default=0.12, help="seconds a press is held")
    parser.add_argument("--gap", type=float, default=0.4, help="seconds between steps")
    args = parser.parse_args()

    token = adb("shell", f"cat {TOKEN_PATH}")
    if not token:
        sys.exit("Could not read the helper token: open MOGA Bridge once on the phone.")
    adb("forward", f"tcp:{PORT}", f"tcp:{PORT}")
    try:
        sock = socket.create_connection(("127.0.0.1", PORT), timeout=3)
    except OSError:
        sys.exit("The helper is not running. Start it with: pnpm android:uinput")

    def send(command):
        sock.sendall((json.dumps(command) + "\n").encode())

    def inject(*triples):
        events = [v for t in triples for v in t] + [0, 0, 0]  # EV_SYN
        send({"id": 1, "command": "inject", "events": events})

    sock.sendall((token + "\n").encode())
    send(register_command(args.name))
    time.sleep(1.5)  # let Android notice the new device

    try:
        for step in args.steps:
            key = step.upper()
            if key in BUTTONS:
                inject((EV_KEY, BUTTONS[key], 1)); time.sleep(args.hold); inject((EV_KEY, BUTTONS[key], 0))
            elif step.lower() in HAT:
                code, value = HAT[step.lower()]
                inject((EV_ABS, code, value)); time.sleep(args.hold); inject((EV_ABS, code, 0))
            elif step.lower().startswith("stick:"):
                _, side, x, y = step.lower().split(":")
                ax, ay = STICK_AXES[side]
                inject((EV_ABS, ax, int(x)), (EV_ABS, ay, int(y))); time.sleep(args.hold)
                inject((EV_ABS, ax, 0), (EV_ABS, ay, 0))
            elif step.lower().startswith("wait:"):
                time.sleep(float(step.split(":")[1]))
            else:
                sys.exit(f"Unknown step: {step}")
            time.sleep(args.gap)
    finally:
        time.sleep(0.5)
        sock.close()  # closing the connection removes the virtual device


if __name__ == "__main__":
    main()

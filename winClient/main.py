import datetime
import hmac
import json
import logging
import os
import re
import secrets
import socket
import sys
import threading
import time
from urllib.parse import urlencode

import eel
import uvicorn
from fastapi import FastAPI, HTTPException, Request
from pynput.keyboard import Controller, Key


def get_base_path():
    if getattr(sys, "frozen", False):
        return os.path.dirname(sys.executable)
    return os.path.dirname(os.path.abspath(__file__))


def get_web_path():
    if getattr(sys, "frozen", False):
        return os.path.join(sys._MEIPASS, "web")
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")


def init_logging():
    log_dir = os.path.join(get_base_path(), "log")
    os.makedirs(log_dir, exist_ok=True)

    filename = f"qrdata_{datetime.datetime.now().strftime('%Y-%m-%d')}.log"
    log_file = os.path.join(log_dir, filename)

    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s-%(levelname)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(log_file, encoding="utf-8"),
        ],
        force=True,
    )

    if getattr(sys, "frozen", False):
        fastapi_log_path = os.path.join(log_dir, "fastapi.log")
        try:
            sys.stdout = open(fastapi_log_path, "a", encoding="utf-8")
            sys.stderr = open(fastapi_log_path, "a", encoding="utf-8")
        except Exception as exc:
            logging.error("failed to redirect stdout/stderr: %s", exc)


init_logging()


CLIENT_STALE_SECONDS = 15
CLIENT_RETENTION_SECONDS = 24 * 60 * 60
KICK_RETENTION_SECONDS = 0
PAIRING_TOKEN = os.environ.get("CATSCAN_PAIRING_TOKEN") or secrets.token_urlsafe(24)

_connections_lock = threading.Lock()
_client_connections = {}
_blocked_clients = set()
_kicked_clients = {}


app = FastAPI()


def _request_is_authorized(request):
    if request is None:
        return False
    supplied_token = request.query_params.get("token", "")
    if not supplied_token:
        supplied_token = request.headers.get("X-CatScan-Token", "")
    return bool(supplied_token) and hmac.compare_digest(supplied_token, PAIRING_TOKEN)


def _require_authorized(request):
    if not _request_is_authorized(request):
        raise HTTPException(status_code=401, detail="invalid pairing token")


def _build_server_url(ip):
    query = urlencode({"token": PAIRING_TOKEN})
    return f"http://{ip}:29027/postqrdata?{query}"


def _type_chinese_with_pynput_impl(text):
    try:
        raw_text = str(text)
        submit_after_typing = raw_text.endswith(("\r", "\n"))
        content = raw_text.rstrip("\r\n")
        safe_content = "".join(char for char in content if char.isprintable())[:4096]
        if not safe_content:
            return

        keyboard = Controller()
        keyboard.type(safe_content)
        if submit_after_typing:
            keyboard.press(Key.enter)
            keyboard.release(Key.enter)
    except Exception as exc:
        logging.error("keyboard input failed: %s", exc)


def getTime():
    return time.strftime("%F %R", time.localtime())


def get_local_ips():
    ip_addresses = []
    hostname = socket.gethostname()
    try:
        for addr_info in socket.getaddrinfo(hostname, None):
            ip = addr_info[4][0]
            if not ip:
                continue
            if ":" in ip:
                continue
            if ip.startswith("127."):
                continue
            ip_addresses.append(_build_server_url(ip))

        ip_addresses = list(dict.fromkeys(ip_addresses))
        logging.info("local endpoints available: %s", len(ip_addresses))
        return ip_addresses
    except socket.gaierror as exc:
        logging.error("get local ips failed: %s", exc)
        return []
    except Exception as exc:
        logging.error("get local ips failed: %s", exc)
        return []


def readJsonFile(file_path):
    try:
        with open(file_path, "r", encoding="utf-8") as file:
            return json.load(file)
    except FileNotFoundError:
        logging.warning("file not found: %s", file_path)
    except json.JSONDecodeError:
        logging.error("invalid json: %s", file_path)
    except Exception as exc:
        logging.error("read file failed: %s (%s)", file_path, exc)
    return None


def _normalize_ip(value):
    if value is None:
        return ""
    ip = str(value).strip().replace("[", "").replace("]", "")
    if "%" in ip:
        ip = ip.split("%", 1)[0]
    return ip


def _normalize_mac(value):
    if value is None:
        return ""
    mac = str(value).strip().lower().replace("-", ":")
    if not mac:
        return ""
    if re.fullmatch(r"[0-9a-f]{12}", mac):
        mac = ":".join([mac[i : i + 2] for i in range(0, 12, 2)])
    return mac


def _extract_client_network_info(payload, fallback_ip=""):
    if not isinstance(payload, dict):
        return _normalize_ip(fallback_ip), ""

    ip = ""
    for key in ("clientIp", "ip", "sourceIp", "remoteIp", "deviceIp", "senderIp"):
        if payload.get(key):
            ip = _normalize_ip(payload.get(key))
            break
    if not ip:
        ip = _normalize_ip(fallback_ip)

    mac = ""
    for key in ("clientMac", "mac", "deviceMac", "macAddress"):
        if payload.get(key):
            mac = _normalize_mac(payload.get(key))
            break

    return ip, mac


def _connection_key(ip, mac):
    if mac:
        return f"mac:{mac}"
    if ip:
        return f"ip:{ip}"
    return ""


def _client_blocked(ip, mac):
    if ip and f"ip:{ip}" in _blocked_clients:
        return True
    if mac and f"mac:{mac}" in _blocked_clients:
        return True
    return False


def _blocked_response():
    return {
        "status": "forbidden",
        "msg": "client blocked",
        "reason": "kicked",
        "code": 403,
        "timestamp": getTime(),
    }


def _prune_old_connections(now_ts):
    stale_keys = []
    for key, info in _client_connections.items():
        if now_ts - info.get("last_seen_ts", 0) > CLIENT_RETENTION_SECONDS:
            stale_keys.append(key)
    for key in stale_keys:
        _client_connections.pop(key, None)


def _prune_old_kicked(now_ts):
    if KICK_RETENTION_SECONDS <= 0:
        return
    stale_keys = []
    for key, info in _kicked_clients.items():
        if now_ts - info.get("kicked_ts", 0) > KICK_RETENTION_SECONDS:
            stale_keys.append(key)
    for key in stale_keys:
        _kicked_clients.pop(key, None)


def _touch_client_connection(payload, fallback_ip=""):
    ip, mac = _extract_client_network_info(payload, fallback_ip=fallback_ip)
    if not ip and not mac:
        return True, ip, mac

    key = _connection_key(ip, mac)
    if not key:
        return True, ip, mac

    now_ts = time.time()
    last_seen = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")

    with _connections_lock:
        if _client_blocked(ip, mac):
            return False, ip, mac

        existing = _client_connections.get(key, {})
        if mac and ip and key.startswith("mac:"):
            legacy_ip_key = f"ip:{ip}"
            if legacy_ip_key != key and legacy_ip_key in _client_connections:
                legacy = _client_connections.pop(legacy_ip_key, {})
                existing = {**legacy, **existing}

        _client_connections[key] = {
            "key": key,
            "ip": ip or existing.get("ip", ""),
            "mac": mac or existing.get("mac", ""),
            "last_seen": last_seen,
            "last_seen_ts": now_ts,
        }
        _prune_old_connections(now_ts)

    return True, ip, mac


def _build_client_connection_rows():
    now_ts = time.time()
    with _connections_lock:
        rows = []
        for key, info in _client_connections.items():
            idle_seconds = max(0, int(now_ts - info.get("last_seen_ts", 0)))
            rows.append(
                {
                    "key": key,
                    "ip": info.get("ip") or "--",
                    "mac": info.get("mac") or "--",
                    "last_seen": info.get("last_seen") or "",
                    "idle_seconds": idle_seconds,
                    "online": idle_seconds <= CLIENT_STALE_SECONDS,
                }
            )

    rows.sort(key=lambda row: (row["online"], -row["idle_seconds"]), reverse=True)
    return rows


def _build_kicked_client_rows():
    now_ts = time.time()
    with _connections_lock:
        rows = []
        for key, info in _kicked_clients.items():
            rows.append(
                {
                    "key": key,
                    "ip": info.get("ip") or "--",
                    "mac": info.get("mac") or "--",
                    "kicked_at": info.get("kicked_at") or "",
                    "kicked_ts": info.get("kicked_ts") or 0,
                }
            )
        _prune_old_kicked(now_ts)

    rows.sort(key=lambda row: row.get("kicked_ts", 0), reverse=True)
    return rows


@app.post("/postqrdata")
async def receive_json(data: dict, request: Request):
    _require_authorized(request)
    client_ip = _normalize_ip(request.client.host if request and request.client else "")
    base_ip, base_mac = _extract_client_network_info(data, fallback_ip=client_ip)

    if _client_blocked(base_ip, base_mac):
        logging.info("Blocked client request rejected: ip=%s mac=%s", base_ip, base_mac)
        return _blocked_response()

    if "batch" in data and data["batch"] is True and "data" in data:
        batch_data = data["data"]
        if not isinstance(batch_data, list):
            logging.error("Invalid batch payload: %s", data)
            return {"status": "error", "msg": "batch data format error", "data": data, "code": 500, "timestamp": getTime()}

        accepted_count = 0
        pushed_count = 0

        for item in batch_data:
            if not isinstance(item, dict):
                continue

            reported_ip = _normalize_ip(item.get("clientIp") or base_ip)
            if reported_ip and reported_ip != client_ip:
                item["reportedClientIp"] = reported_ip

            item["clientIp"] = client_ip or reported_ip
            item["clientMac"] = item.get("clientMac") or item.get("mac") or base_mac

            allowed, resolved_ip, resolved_mac = _touch_client_connection(item, fallback_ip=client_ip)
            if not allowed:
                logging.info("Blocked client payload ignored: ip=%s mac=%s", resolved_ip, resolved_mac)
                continue

            accepted_count += 1
            action = str(item.get("action", "add")).lower()
            if item.get("heartbeat") is True or action == "heartbeat":
                continue
            if "qrdata" not in item:
                continue

            payload = {
                **item,
                "clientIp": client_ip or item.get("clientIp") or resolved_ip,
                "clientMac": item.get("clientMac") or item.get("mac") or resolved_mac or "",
                "status": "received",
                "code": 200,
                "timestamp": getTime(),
            }
            pushqrdata(payload)
            pushed_count += 1

        return {
            "status": "received",
            "code": 200,
            "timestamp": getTime(),
            "msg": f"accepted={accepted_count}, pushed={pushed_count}",
        }

    reported_ip = _normalize_ip(data.get("clientIp"))
    if reported_ip and reported_ip != client_ip:
        data["reportedClientIp"] = reported_ip
    data["clientIp"] = client_ip or reported_ip

    allowed, resolved_ip, resolved_mac = _touch_client_connection(data, fallback_ip=client_ip)
    if not allowed:
        logging.info("Blocked client payload ignored: ip=%s mac=%s", resolved_ip, resolved_mac)
        return _blocked_response()

    action = str(data.get("action", "add")).lower()
    if data.get("heartbeat") is True or action == "heartbeat":
        return {"status": "heartbeat", "code": 200, "timestamp": getTime()}

    if "qrdata" in data:
        qrdata = data["qrdata"]
        payload = {
            **data,
            "clientIp": client_ip or data.get("clientIp") or resolved_ip,
            "clientMac": data.get("clientMac") or data.get("mac") or resolved_mac or "",
            "status": "received",
            "code": 200,
            "timestamp": getTime(),
        }
        pushqrdata(payload)
        if action == "delete":
            logging.info("Delete data: %s", qrdata)
        else:
            logging.info("Received qrdata: %s", qrdata)
        return payload

    if resolved_ip or resolved_mac:
        return {
            "status": "received",
            "code": 200,
            "timestamp": getTime(),
            "clientIp": resolved_ip,
            "clientMac": resolved_mac,
        }

    logging.error("Invalid payload: %s", data)
    return {"status": "error", "msg": "missing qrdata field", "data": data, "code": 500, "timestamp": getTime()}


@app.get("/postqrdata")
async def health_check(request: Request):
    _require_authorized(request)
    return {"status": "received", "code": 200, "timestamp": getTime()}


def run_fastapi():
    max_retries = 3
    retry_count = 0

    while retry_count < max_retries:
        try:
            uvicorn.run(
                app,
                host="0.0.0.0",
                port=29027,
                log_level="info",
                access_log=False,
            )
            break
        except OSError as exc:
            if "Address already in use" in str(exc) or "10048" in str(exc):
                retry_count += 1
                if retry_count < max_retries:
                    logging.warning("port 29027 is in use, retry %s/%s", retry_count, max_retries)
                    time.sleep(2)
                else:
                    logging.error("port 29027 is in use, please free it and restart")
                    try:
                        import ctypes

                        ctypes.windll.user32.MessageBoxW(
                            0,
                            "Port 29027 is already in use.\nPlease close the process using this port and restart.",
                            "CatScan - Port In Use",
                            0x10,
                        )
                    except Exception:
                        pass
            else:
                logging.error("FastAPI start failed: %s", exc)
                break
        except Exception as exc:
            logging.error("FastAPI start failed: %s", exc)
            break


@eel.expose
def getLocalIps():
    return get_local_ips()


@eel.expose
def getClientConnections():
    return {
        "active": _build_client_connection_rows(),
        "kicked": _build_kicked_client_rows(),
    }


@eel.expose
def kickClient(client):
    ip = ""
    mac = ""

    if isinstance(client, dict):
        ip = _normalize_ip(client.get("ip"))
        mac = _normalize_mac(client.get("mac"))
    elif isinstance(client, str):
        ip = _normalize_ip(client)

    if not ip and not mac:
        return {"status": "error", "msg": "invalid client", "code": 400}

    with _connections_lock:
        now_ts = time.time()
        kicked_at = datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        if ip:
            _blocked_clients.add(f"ip:{ip}")
        if mac:
            _blocked_clients.add(f"mac:{mac}")

        remove_keys = []
        last_seen = ""
        for key, info in _client_connections.items():
            if ip and info.get("ip") == ip:
                remove_keys.append(key)
                last_seen = info.get("last_seen") or last_seen
                continue
            if mac and info.get("mac") == mac:
                remove_keys.append(key)
                last_seen = info.get("last_seen") or last_seen

        for key in remove_keys:
            _client_connections.pop(key, None)

        kicked_key = _connection_key(ip, mac)
        if kicked_key:
            existing = _kicked_clients.get(kicked_key, {})
            _kicked_clients[kicked_key] = {
                "key": kicked_key,
                "ip": ip or existing.get("ip", ""),
                "mac": mac or existing.get("mac", ""),
                "kicked_at": kicked_at,
                "kicked_ts": now_ts,
                "last_seen": last_seen or existing.get("last_seen", ""),
            }

    logging.info("Client kicked: ip=%s mac=%s", ip or "-", mac or "-")
    return {"status": "ok", "code": 200}


@eel.expose
def restoreClient(client):
    ip = ""
    mac = ""

    if isinstance(client, dict):
        ip = _normalize_ip(client.get("ip"))
        mac = _normalize_mac(client.get("mac"))
    elif isinstance(client, str):
        ip = _normalize_ip(client)

    if not ip and not mac:
        return {"status": "error", "msg": "invalid client", "code": 400}

    with _connections_lock:
        if ip:
            _blocked_clients.discard(f"ip:{ip}")
        if mac:
            _blocked_clients.discard(f"mac:{mac}")

        kicked_key = _connection_key(ip, mac)
        if kicked_key:
            _kicked_clients.pop(kicked_key, None)

        remove_keys = []
        for key, info in _kicked_clients.items():
            if ip and info.get("ip") == ip:
                remove_keys.append(key)
                continue
            if mac and info.get("mac") == mac:
                remove_keys.append(key)

        for key in remove_keys:
            _kicked_clients.pop(key, None)

    logging.info("Client restored: ip=%s mac=%s", ip or "-", mac or "-")
    return {"status": "ok", "code": 200}


@eel.expose
def pushqrdata(qrdata):
    eel.updateQrData(qrdata)()


@eel.expose
def type_chinese_with_pynput(text):
    _type_chinese_with_pynput_impl(text)


def maximize_window():
    try:
        import win32con
        import win32gui

        hwnd = win32gui.GetForegroundWindow()
        if hwnd:
            win32gui.ShowWindow(hwnd, win32con.SW_MAXIMIZE)
    except ImportError:
        logging.warning("win32gui not installed, skip maximize")
    except Exception as exc:
        logging.warning("maximize window failed: %s", exc)


if __name__ == "__main__":
    try:
        fastapi_thread = threading.Thread(target=run_fastapi, daemon=True)
        fastapi_thread.start()
        logging.info("FastAPI started on port 29027")

        try:
            from udp_discovery import run_discovery_in_thread

            run_discovery_in_thread(PAIRING_TOKEN)
            logging.info("UDP discovery started on port 29028")
        except ImportError:
            logging.warning("UDP discovery module not found, skip")
        except Exception as exc:
            logging.error("failed to start UDP discovery: %s", exc)

        logging.info("Starting Eel UI...")
        web_path = get_web_path()
        if not os.path.exists(web_path):
            logging.error("web directory not found: %s", web_path)
            sys.exit(1)

        eel.init(web_path)

        import platform

        if platform.system() == "Windows":
            threading.Timer(0.5, maximize_window).start()

        eel.start("index.html", size=(1400, 900), position=(50, 50), disable_cache=True)
    except KeyboardInterrupt:
        logging.info("program interrupted by user")
    except Exception as exc:
        logging.error("startup failed: %s", exc, exc_info=True)
        sys.exit(1)

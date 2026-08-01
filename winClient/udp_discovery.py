"""UDP discovery service for CatScan PC client."""

import logging
import socket
import threading
import time
from urllib.parse import urlencode

DISCOVERY_PORT = 29028
DISCOVERY_REQUEST = "CATSCAN_DISCOVERY_REQUEST"
DISCOVERY_RESPONSE_PREFIX = "CATSCAN_DISCOVERY_RESPONSE:"
SERVER_PORT = 29027


def get_local_ips():
    """Return usable local IPv4 addresses."""
    ips = []
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
            if ip not in ips:
                ips.append(ip)
    except Exception as exc:
        logging.error("get_local_ips failed: %s", exc)
    return ips


def get_routed_local_ip(peer_ip):
    """Return the local IPv4 selected by routing table for a peer."""
    if not peer_ip:
        return None
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as probe:
            probe.connect((peer_ip, 80))
            local_ip = probe.getsockname()[0]
            if local_ip and not local_ip.startswith("127."):
                return local_ip
    except Exception as exc:
        logging.debug("get_routed_local_ip failed (%s): %s", peer_ip, exc)
    return None


def build_response_ips(peer_ip):
    routed_ip = get_routed_local_ip(peer_ip)
    if routed_ip:
        return [routed_ip]
    return get_local_ips()


def build_server_url(ip, pairing_token):
    query = urlencode({"token": pairing_token})
    return f"http://{ip}:{SERVER_PORT}/postqrdata?{query}"


def start_udp_discovery(pairing_token):
    """Start UDP discovery responder loop with retry."""
    sock = None
    max_retries = 3
    retry_count = 0
    retry_delay_sec = 5

    while retry_count < max_retries:
        try:
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.bind(("", DISCOVERY_PORT))
            sock.settimeout(1.0)

            logging.info("UDP discovery started on port %s", DISCOVERY_PORT)
            retry_count = 0

            while True:
                try:
                    data, addr = sock.recvfrom(1024)
                    request = data.decode("utf-8", errors="ignore").strip()
                    if request != DISCOVERY_REQUEST:
                        continue

                    response_ips = build_response_ips(addr[0])
                    for ip in response_ips:
                        server_url = build_server_url(ip, pairing_token)
                        response = f"{DISCOVERY_RESPONSE_PREFIX}{server_url}"
                        sock.sendto(response.encode("utf-8"), addr)
                        logging.info("Discovery response sent to %s via %s", addr[0], ip)
                except socket.timeout:
                    continue
                except Exception as exc:
                    logging.error("UDP discovery loop error: %s", exc)

        except OSError as exc:
            retry_count += 1
            if "10048" in str(exc) or "Address already in use" in str(exc):
                if retry_count < max_retries:
                    logging.warning(
                        "UDP port %s is in use, retry %s/%s in %ss",
                        DISCOVERY_PORT,
                        retry_count,
                        max_retries,
                        retry_delay_sec,
                    )
                    time.sleep(retry_delay_sec)
                else:
                    logging.error("UDP discovery failed: port %s is still in use", DISCOVERY_PORT)
            else:
                logging.error("UDP discovery failed to start: %s", exc)
                break
        except Exception as exc:
            logging.error("UDP discovery failed to start: %s", exc)
            break
        finally:
            if sock:
                try:
                    sock.close()
                    logging.info("UDP discovery socket closed")
                except Exception:
                    pass
            sock = None


def run_discovery_in_thread(pairing_token):
    """Run UDP discovery in a daemon thread."""
    thread = threading.Thread(
        target=start_udp_discovery,
        args=(pairing_token,),
        daemon=True,
    )
    thread.start()
    logging.info("UDP discovery thread started")
    return thread


if __name__ == "__main__":
    logging.basicConfig(level=logging.INFO)
    raise SystemExit("Run winClient/main.py so UDP discovery shares its pairing token.")

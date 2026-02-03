"""
UDP网络发现服务
用于响应Android客户端的网络发现请求
"""
import socket
import threading
import logging

DISCOVERY_PORT = 29028
DISCOVERY_REQUEST = "CATSCAN_DISCOVERY_REQUEST"
DISCOVERY_RESPONSE_PREFIX = "CATSCAN_DISCOVERY_RESPONSE:"
SERVER_PORT = 29027


def get_local_ips():
    """获取本机所有IP地址"""
    ips = []
    hostname = socket.gethostname()
    try:
        for addr_info in socket.getaddrinfo(hostname, None):
            ip = addr_info[4][0]
            if ip and not ip.startswith("127."):
                ips.append(ip)
    except Exception as e:
        logging.error(f"获取IP地址失败: {e}")
    return ips


def start_udp_discovery():
    """启动UDP发现服务（长期运行），带自动重试机制"""
    sock = None
    max_retries = 3
    retry_count = 0
    retry_delay = 5  # 秒
    
    while retry_count < max_retries:
        try:
            # 创建UDP socket
            sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
            sock.bind(("", DISCOVERY_PORT))
            sock.settimeout(1.0)  # 1秒超时，用于定期检查线程状态
            
            logging.info(f"UDP发现服务已启动，监听端口 {DISCOVERY_PORT}")
            retry_count = 0  # 成功启动后重置重试计数
            
            while True:
                try:
                    # 接收UDP广播
                    data, addr = sock.recvfrom(1024)
                    request = data.decode('utf-8')
                    
                    if request == DISCOVERY_REQUEST:
                        # 获取本机IP地址
                        local_ips = get_local_ips()
                        
                        # 向请求方发送响应
                        for ip in local_ips:
                            server_url = f"http://{ip}:{SERVER_PORT}/postqrdata"
                            response = f"{DISCOVERY_RESPONSE_PREFIX}{server_url}"
                            sock.sendto(response.encode('utf-8'), addr)
                            logging.info(f"响应发现请求: {addr[0]} -> {server_url}")
                            
                except socket.timeout:
                    # 超时是正常的，继续循环
                    continue
                except Exception as e:
                    logging.error(f"UDP发现服务错误: {e}")
                    
        except OSError as e:
            retry_count += 1
            if "10048" in str(e) or "Address already in use" in str(e):
                if retry_count < max_retries:
                    logging.warning(f"UDP端口 {DISCOVERY_PORT} 已被占用，{retry_count}/{max_retries} 次重试中，等待 {retry_delay} 秒...")
                    import time
                    time.sleep(retry_delay)
                else:
                    logging.error(f"UDP发现服务启动失败：端口 {DISCOVERY_PORT} 持续被占用")
            else:
                logging.error(f"启动UDP发现服务失败: {e}")
                break
        except Exception as e:
            logging.error(f"启动UDP发现服务失败: {e}")
            break
        finally:
            # 程序退出时关闭socket（daemon线程会在主程序退出时自动终止）
            if sock:
                try:
                    sock.close()
                    logging.info("UDP发现服务已关闭")
                except Exception:
                    pass
            sock = None


def run_discovery_in_thread():
    """在后台线程中运行UDP发现服务"""
    thread = threading.Thread(target=start_udp_discovery, daemon=True)
    thread.start()
    logging.info("UDP发现服务已在后台线程启动")
    return thread


if __name__ == "__main__":
    # 测试模式
    logging.basicConfig(level=logging.INFO)
    print("启动UDP发现服务...")
    start_udp_discovery()

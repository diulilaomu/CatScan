import eel
import os
import sys
import json
from fastapi import FastAPI
import threading
import uvicorn
import time, datetime
import logging
import socket
from pynput.keyboard import Controller




# ---------------------
# config 路径使用 get_base_path()
# ---------------------
def get_base_path():
    if getattr(sys, "frozen", False):
        # ✅ 打包后，返回 exe 所在目录（不是临时解压目录）
        return os.path.dirname(sys.executable)
    else:
        # ✅ 开发环境下，返回当前脚本目录
        return os.path.dirname(os.path.abspath(__file__))




# ---------------------
# eel 初始化 web 页面
# ---------------------
def get_web_path():
    if getattr(sys, "frozen", False):
        # web 被打包在 _MEIPASS 里
        return os.path.join(sys._MEIPASS, "web")
    else:
        return os.path.join(os.path.dirname(os.path.abspath(__file__)), "web")



# ---------------------
# 日志服务初始化
# ---------------------
def init_logging():
    """初始化日志服务"""
    log_dir = os.path.join(get_base_path(), "log")
    os.makedirs(log_dir, exist_ok=True)
    
    filename = f'qrdata_{datetime.datetime.now().strftime("%Y-%m-%d")}.log'
    log_file = os.path.join(log_dir, filename)
    
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s-%(levelname)s: %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
        handlers=[
            logging.StreamHandler(),
            logging.FileHandler(log_file, encoding="utf-8")
        ],
        force=True  # 强制重新配置，避免重复初始化
    )
    
    # 在无控制台模式中创建一个日志文件用来承接所有 print
    if getattr(sys, "frozen", False):
        fastapi_log_path = os.path.join(log_dir, "fastapi.log")
        try:
            sys.stdout = open(fastapi_log_path, "a", encoding="utf-8")
            sys.stderr = open(fastapi_log_path, "a", encoding="utf-8")
        except Exception as e:
            logging.error(f"无法重定向stdout/stderr: {e}")

# 初始化日志
init_logging()

# ---------------------
# 键盘输入服务
# ---------------------
def _type_chinese_with_pynput_impl(text):
    """使用pynput模拟键盘输入中文（内部实现）"""
    try:
        keyboard = Controller()
        keyboard.type(str(text))
    except Exception as e:
        logging.error(f"键盘输入失败: {e}")

def getTime():
    return time.strftime('%F %R', time.localtime())

def get_local_ips():
    """获取本机所有IP地址"""
    ipAddresses = []
    hostname = socket.gethostname()
    try:
        for addr_info in socket.getaddrinfo(hostname, None):
            ip = addr_info[4][0]
            # 过滤掉回环地址
            if ip and not ip.startswith("127."):
                ipAddresses.append(f"http://{ip}:29027/postqrdata")
        
        # 去重
        ipAddresses = list(dict.fromkeys(ipAddresses))
        
        for ip in ipAddresses:
            logging.info(f"本机地址：{ip}")
        return ipAddresses
    except socket.gaierror as e:
        logging.error(f"获取地址信息错误: {e}")
        return []
    except Exception as e:
        logging.error(f"获取IP地址失败: {e}")
        return []


def readJsonFile(file_path):
    """读取JSON文件"""
    try:
        with open(file_path, "r", encoding="utf-8") as file:
            data = json.load(file)
            return data
    except FileNotFoundError:
        logging.warning(f"文件 {file_path} 不存在！")
    except json.JSONDecodeError:
        logging.error(f"文件 {file_path} 不是有效的 JSON 格式！")
    except Exception as e:
        logging.error(f"读取文件 {file_path} 失败：{e}")
    return None


# FastAPI服务 接收29027端口数据
app = FastAPI()


@app.post("/postqrdata")
async def receive_json(data: dict):
    if "qrdata" in data:
        qrdata = data["qrdata"]
        # 合并请求体全部字段，并补充 status/code/timestamp（后者优先）
        payload = {**data, "status": "received", "code": 200, "timestamp": getTime()}
        pushqrdata(payload)
        logging.info("接收到的 qrdata 数据: %s", qrdata)
        return payload
    else:
        logging.error("接收到的数据: %s", data)
        return {"status": "error", "msg": "缺少 qrdata 字段", "data": data, "code": 500, "timestamp": getTime()}


@app.get("/postqrdata")
async def health_check():
    return {"status": "received", "code": 200, "timestamp": getTime()}


def run_fastapi():
    """在后台线程中运行FastAPI服务"""
    try:
        uvicorn.run(
            app, 
            host="0.0.0.0", 
            port=29027, 
            log_level="info",
            access_log=False  # 禁用访问日志以减少输出
        )
    except OSError as e:
        if "Address already in use" in str(e):
            logging.error(f"端口 29027 已被占用，请关闭占用该端口的程序")
        else:
            logging.error(f"FastAPI 服务启动失败: {e}")
    except Exception as e:
        logging.error(f"FastAPI 服务启动失败: {e}")


# 获取IP地址
@eel.expose
def getLocalIps():
    return get_local_ips()

# 推送历史记录列表
@eel.expose
def pushqrdata(qrdata):
    eel.updateQrData(qrdata)()

# 键盘输入服务（暴露给前端）
@eel.expose
def type_chinese_with_pynput(text):
    """前端调用的键盘输入服务"""
    _type_chinese_with_pynput_impl(text)

def maximize_window():
    """最大化窗口（Windows系统）"""
    try:
        import win32gui
        import win32con
        hwnd = win32gui.GetForegroundWindow()
        if hwnd:
            win32gui.ShowWindow(hwnd, win32con.SW_MAXIMIZE)
    except ImportError:
        logging.warning("win32gui模块未安装，无法自动最大化窗口")
    except Exception as e:
        logging.warning(f"最大化窗口失败: {e}")


if __name__ == "__main__":
    try:
        # 启动 FastAPI 在后台线程
        fastapi_thread = threading.Thread(target=run_fastapi, daemon=True)
        fastapi_thread.start()
        logging.info("FastAPI 服务已在后台启动，端口 29027")
        
        # 启动 UDP 发现服务
        try:
            from udp_discovery import run_discovery_in_thread
            run_discovery_in_thread()
            logging.info("UDP 发现服务已启动，端口 29028")
        except ImportError:
            logging.warning("UDP 发现服务模块未找到，跳过启动")
        except Exception as e:
            logging.error(f"启动 UDP 发现服务失败: {e}")

        # 启动 Eel 在主线程
        logging.info("启动 Eel 界面...")
        web_path = get_web_path()
        if not os.path.exists(web_path):
            logging.error(f"Web目录不存在: {web_path}")
            sys.exit(1)
        
        eel.init(web_path)
        
        # Windows系统下尝试最大化窗口
        import platform
        if platform.system() == 'Windows':
            # 延迟执行最大化，确保窗口已创建
            threading.Timer(0.5, maximize_window).start()
        
        eel.start("index.html", size=(1400, 900), position=(50, 50), disable_cache=True)
    except KeyboardInterrupt:
        logging.info("程序被用户中断")
    except Exception as e:
        logging.error(f"程序启动失败: {e}", exc_info=True)
        sys.exit(1)

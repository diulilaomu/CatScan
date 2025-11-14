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



# log服务
os.makedirs("log", exist_ok=True)
filename = f'{datetime.datetime.now().strftime("%Y-%m-%d")}.log'
logging.basicConfig(  # 设置日志记录器和处理器
    # level=logging.DEBUG,
    format="%(asctime)s-%(levelname)s: %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler(), logging.FileHandler(os.path.join(get_base_path(), "log", filename), encoding="utf-8")],
)



# 键盘输入服务
def type_chinese_with_pynput(text):
    keyboard = Controller()
    keyboard.type(str(text))

def getTime():
    return time.strftime('%F %R', time.localtime())

def get_local_ips():
    hostname = socket.gethostname()
    try:
        # 获取所有IP地址
        ipAddresses = []
        for addr_info in socket.getaddrinfo(hostname, None):
            ip = addr_info[4][0]
            ipAddresses.append(f"http://{ip}:29027/postqrdata")
        for ip in ipAddresses:
            print(f"本机地址：{ip}")
        return ipAddresses
    except socket.gaierror as e:
        print(f"获取地址信息错误: {e}")
        return []


def readJsonFile(file_path):  # 读json文件
    try:
        with open(file_path, "r", encoding="utf-8") as file:
            data = json.load(file)
            return data
    except FileNotFoundError:
        print(f"文件 {file_path} 不存在！")
    except json.JSONDecodeError:
        print(f"文件 {file_path} 不是有效的 JSON 格式！")
    except Exception as e:
        print(f"读取文件 {file_path} 失败：{e}")
    return None


# FastAPI服务 接收29027端口数据
app = FastAPI()


@app.post("/postqrdata")
async def receive_json(data: dict):
    if "qrdata" in data:
        qrdata = data["qrdata"]
        # type_chinese_with_pynput(qrdata+"\n")
        pushqrdata({"status": "received", "qrdata": qrdata, "code": 200, "timestamp": getTime()})
        logging.info("接收到的 qrdata 数据: %s", qrdata)
        return {"status": "received", "qrdata": qrdata, "code": 200, "timestamp": getTime()}
    else:
        logging.error("接收到的数据: %s", data)
        return {"status": "error", "msg": "缺少 qrdata 字段", "data": data, "code": 500, "timestamp": getTime()}


@app.get("/postqrdata")
async def health_check():
    return {"status": "received", "code": 200, "timestamp": getTime()}


def run_fastapi():
    try:
        uvicorn.run(app, host="0.0.0.0", port=29027, log_level="info")
    except Exception as e:
        print(f"FastAPI 服务启动失败: {e}")


# 获取IP地址
@eel.expose
def getLocalIps():
    return get_local_ips()

# 推送历史记录列表
@eel.expose
def pushqrdata(qrdata):
    eel.updateQrData(qrdata)()

# 键盘输入服务
@eel.expose
def type_chinese_with_pynput(text):
    keyboard = Controller()
    keyboard.type(str(text))

if __name__ == "__main__":
    # 启动 FastAPI 在后台线程
    fastapi_thread = threading.Thread(target=run_fastapi, daemon=True)
    fastapi_thread.start()
    print("FastAPI 服务已在后台启动，端口 29027")

    # 启动 Eel 在主线程
    print("启动 Eel 界面...")
    eel.init(os.path.join(get_web_path()))
    eel.start("index.html", size=(1000, 700))

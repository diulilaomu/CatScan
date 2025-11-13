import eel
import os
import tools, time, threading


data = [{"roomName": "海研B114-单相单路导轨电表", "command": "68 53 01 01 04 05 11 68 11 04 33 33 34 33 21 16", "comPort": 10008, "status": True}]


@eel.expose
def start_send_data():
    for i in range(13):
        print("发送给前端:", data)
        data.append(
            {
                "roomName": "海研B114-单相单路导轨电表",
                "address": "110407012803",
                "command": "68 53 01 01 04 05 12 68 11 04 33 33 34 33 21 16",
                "status": False,
            }
        )
        eel.updateAddressList(data)()  # 🔥 主动调用前端函数
        # time.sleep(2)  # 模拟延时


eel.init("web")


if __name__ == "__main__":
    threading.Thread(target=start_send_data, daemon=True).start()
    eel.start("index.html", size=(700, 500), mode="chrome", cmdline_args=["--auto-open-devtools-for-tabs"])

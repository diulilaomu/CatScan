import eel
import os
import tools

eel.init('web')

@eel.expose
def readJsonlist():
    config_dir = os.path.join(os.path.dirname(__file__), 'config')   # 定位到当前脚本同级的 ./config
    json_files_list = [f for f in os.listdir(config_dir)
                if f.lower().endswith('.json')]                    # 只保留 .json 后缀
    print(f"返回jsonlist")
    return json_files_list

@eel.expose
def sendConfigJson(filename):
    print(os.path.join(os.getcwd(), "config", filename))
    devlist = tools.readJsonFile(os.path.join(os.getcwd(), "config", filename))
    # devlist = os.path.join(os.getcwd(), "config", filename)
    return devlist

eel.start('index.html', size=(1000, 700))



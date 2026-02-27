#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
生成测试模板数据的脚本
"""

import json
import uuid
import time
from pathlib import Path

def generate_test_template():
    """生成包含100条扫描数据的测试模板"""
    
    template_id = str(uuid.uuid4())
    
    # 生成100条扫描数据
    scans = []
    for i in range(1, 101):
        floor = (i - 1) // 24 + 1  # 每楼层24个房间
        if floor > 6:
            floor = 6
        room = (i - 1) % 24 + 1
        
        scan = {
            "id": str(uuid.uuid4()),
            "text": f"TEST_BARCODE_{i:03d}",
            "timestamp": int(time.time() * 1000) - (100 - i) * 60000,  # 时间递增
            "operator": "测试员",
            "campus": "猫头校区",
            "building": "2号猫屋",
            "floor": str(floor),
            "room": str(room),
            "templateId": template_id,
            "templateName": "猫头枪",
            "uploaded": False
        }
        scans.append(scan)
    
    # 生成房间列表
    selected_rooms = []
    for floor in range(1, 7):
        for room in range(1, 25):
            selected_rooms.append(f"F{floor}R{room:02d}")
    
    # 创建模板对象
    template = {
        "id": template_id,
        "name": "猫头枪",
        "operator": "测试员",
        "campus": "猫头校区",
        "building": "2号猫屋",
        "maxFloor": 6,
        "roomCountPerFloor": 24,
        "selectedRooms": selected_rooms,
        "scans": scans
    }
    
    # 创建完整的 templates.json 结构
    templates_data = {
        "activeTemplateId": template_id,
        "templates": [template]
    }
    
    return templates_data, template_id

def main():
    # 生成数据
    templates_data, template_id = generate_test_template()
    
    # Android 数据目录
    android_data_dir = Path(r"C:\Users\pink\Desktop\CatScan\app\src\main\res\raw")
    
    # 输出 templates.json
    templates_file = Path(r"C:\Users\pink\Desktop\CatScan\templates.json")
    with open(templates_file, 'w', encoding='utf-8') as f:
        json.dump(templates_data, f, ensure_ascii=False, indent=2)
    print(f"✅ 已生成 templates.json: {templates_file}")
    
    # 输出 scan_history_[templateId].json
    scan_history_data = {
        "items": templates_data["templates"][0]["scans"]
    }
    scan_history_file = Path(r"C:\Users\pink\Desktop\CatScan\scan_history.json")
    with open(scan_history_file, 'w', encoding='utf-8') as f:
        json.dump(scan_history_data, f, ensure_ascii=False, indent=2)
    print(f"✅ 已生成 scan_history.json: {scan_history_file}")
    
    # 打印统计信息
    print("\n" + "="*50)
    print("📊 测试模板信息:")
    print("="*50)
    print(f"模板ID: {template_id}")
    print(f"模板名称: 猫头枪")
    print(f"校区: 猫头校区")
    print(f"楼栋: 2号猫屋")
    print(f"楼层: 6层")
    print(f"房间/层: 24个")
    print(f"扫描数据: 100条")
    print(f"房间总数: {len(templates_data['templates'][0]['selectedRooms'])}")
    print("="*50)
    
    # 打印前5条和最后5条数据示例
    scans = templates_data["templates"][0]["scans"]
    print("\n📋 前5条扫描数据:")
    for i, scan in enumerate(scans[:5], 1):
        print(f"  {i}. {scan['text']} - F{scan['floor']}R{scan['room']} @ {scan['campus']}/{scan['building']}")
    
    print("\n📋 最后5条扫描数据:")
    for i, scan in enumerate(scans[-5:], 96):
        print(f"  {i}. {scan['text']} - F{scan['floor']}R{scan['room']} @ {scan['campus']}/{scan['building']}")

if __name__ == "__main__":
    main()

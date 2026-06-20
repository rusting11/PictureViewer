import os
import sys

IMAGE_EXTS = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'}

def scan_folder(path, depth=0):
    """扫描文件夹，找出既有图片又有子文件夹的目录"""
    try:
        items = os.listdir(path)
    except PermissionError:
        print(f"{'  ' * depth}[权限不足] {path}")
        return
    except Exception as e:
        print(f"{'  ' * depth}[错误] {path}: {e}")
        return
    
    subfolders = []
    images = []
    
    for item in items:
        full_path = os.path.join(path, item)
        if os.path.isdir(full_path):
            subfolders.append(item)
        elif os.path.splitext(item)[1].lower() in IMAGE_EXTS:
            images.append(item)
    
    # 打印当前目录信息
    indent = '  ' * depth
    if images and subfolders:
        print(f"{indent}[!] {os.path.basename(path)}: {len(images)}张图片, {len(subfolders)}个子文件夹")
    elif images:
        print(f"{indent}[漫画] {os.path.basename(path)}: {len(images)}张图片")
    elif subfolders:
        print(f"{indent}[目录] {os.path.basename(path)}: {len(subfolders)}个子文件夹")
    
    # 递归扫描子文件夹
    for sub in subfolders:
        scan_folder(os.path.join(path, sub), depth + 1)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        target = sys.argv[1]
    else:
        target = os.getcwd()
    
    if not os.path.exists(target):
        print(f"路径不存在: {target}")
        sys.exit(1)
    
    print(f"\n扫描目录: {target}\n")
    scan_folder(target)
    
    print("\n说明:")
    print("[漫画] = 只有图片，没有子文件夹（会显示为漫画卡片）")
    print("[!] = 既有图片又有子文件夹（当前不会显示为漫画卡片）")
    print("[目录] = 只有子文件夹")

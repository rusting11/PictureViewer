import subprocess
import sys

IMAGE_EXTS = {'.jpg', '.jpeg', '.png', '.gif', '.webp', '.bmp'}

def adb_ls(path):
    """通过 adb 列出目录内容"""
    try:
        result = subprocess.run(
            ['adb', 'shell', f'ls -la "{path}"'],
            capture_output=True, text=True, encoding='utf-8', errors='replace'
        )
        return result.stdout.strip().split('\n')
    except Exception as e:
        return []

stats = {
    'comic': 0,      # 只有图片，无子文件夹
    'mixed': 0,      # 既有图片又有子文件夹
    'folder': 0,     # 只有子文件夹
    'total_images': 0
}

def scan_adb_folder(path, depth=0):
    """通过 adb 扫描文件夹，只统计"""
    lines = adb_ls(path)
    
    subfolders = []
    images = []
    
    for line in lines:
        if not line or line.startswith('total'):
            continue
        if 'Permission denied' in line or 'No such file' in line:
            return
        
        parts = line.split()
        if len(parts) < 8:
            continue
        
        name = ' '.join(parts[7:])
        if name in ['.', '..']:
            continue
        
        is_dir = line.startswith('d')
        
        if is_dir:
            subfolders.append(name)
        elif any(name.lower().endswith(ext) for ext in IMAGE_EXTS):
            images.append(name)
    
    if images and subfolders:
        stats['mixed'] += 1
        stats['total_images'] += len(images)
        print(f"[!] {path}: {len(images)}图 + {len(subfolders)}子文件夹")
    elif images:
        stats['comic'] += 1
        stats['total_images'] += len(images)
    elif subfolders:
        stats['folder'] += 1
    
    for sub in subfolders:
        scan_adb_folder(f"{path}/{sub}", depth + 1)

if __name__ == "__main__":
    if len(sys.argv) > 1:
        target = sys.argv[1]
    else:
        target = "/storage/emulated/0/Alarms"
    
    print(f"扫描: {target}\n")
    scan_adb_folder(target)
    
    print(f"\n=== 统计 ===")
    print(f"[漫画] 只有图片的文件夹: {stats['comic']}个（会显示为卡片）")
    print(f"[!] 既有图片又有子文件夹: {stats['mixed']}个（不会显示为卡片！）")
    print(f"[目录] 只有子文件夹: {stats['folder']}个")
    print(f"总图片数: {stats['total_images']}张")

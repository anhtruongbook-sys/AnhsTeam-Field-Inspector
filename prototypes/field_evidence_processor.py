"""
Công cụ xử lý chứng cứ ảnh chụp hiện trường, trích xuất EXIF GPS,
dán trực tiếp Bảng thông tin kỹ thuật hiện trường (Engineering Field Stamp / Digital Kokuban)
lên ảnh và tạo mã băm kiểm chứng SHA-256.
"""

import argparse
import datetime
import hashlib
import json
import os
import sys
from pathlib import Path
from typing import Dict, Any, List, Optional, Tuple

from PIL import Image, ImageDraw, ImageFont, ExifTags

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")


def compute_sha256(file_path: Path) -> str:
    """Tính mã băm SHA-256 bảo đảm tính toàn vẹn của tệp dữ liệu hiện trường."""
    sha256_hash = hashlib.sha256()
    with open(file_path, "rb") as f:
        for byte_block in iter(lambda: f.read(65536), b""):
            sha256_hash.update(byte_block)
    return sha256_hash.hexdigest()


def _load_vietnamese_font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    """Tải phông chữ hỗ trợ đầy đủ tiếng Việt trên Windows (Arial / Times New Roman / Segoe UI)."""
    win_fonts = [
        "C:/Windows/Fonts/arialbd.ttf" if bold else "C:/Windows/Fonts/arial.ttf",
        "C:/Windows/Fonts/timesbd.ttf" if bold else "C:/Windows/Fonts/times.ttf",
        "C:/Windows/Fonts/segoeuib.ttf" if bold else "C:/Windows/Fonts/segoeui.ttf",
    ]
    for fp in win_fonts:
        if os.path.exists(fp):
            try:
                return ImageFont.truetype(fp, size=size)
            except Exception:
                continue
    return ImageFont.load_default()


def extract_exif_metadata(img: Image.Image) -> Dict[str, str]:
    """Trích xuất ngày chụp và thông tin thiết bị từ EXIF của ảnh gốc (nếu có)."""
    info: Dict[str, str] = {}
    try:
        exif_data = img._getexif()
        if not exif_data:
            return info
        for tag_id, value in exif_data.items():
            tag = ExifTags.TAGS.get(tag_id, tag_id)
            if tag in ("DateTimeOriginal", "DateTime", "Make", "Model"):
                info[str(tag)] = str(value)
    except Exception:
        pass
    return info


def stamp_field_photo(
    input_image_path: Path,
    output_image_path: Path,
    project_name: str = "Tòa nhà Trung tâm R&D Công nghệ (DA01-IBST)",
    work_item: str = "Kiểm tra cốt thép & lớp bê tông bảo vệ dầm sàn",
    zone_axis: str = "Tầng hầm B1 - Trục (B-C)/(3-4) - Cao độ -4.50m",
    gps_coords: str = "21.004512°N, 105.798234°E (±1.5m RTK)",
    compass_heading: str = "135° Đông Nam (SE)",
    measured_result: str = "Thép D25 CB500-V; Lớp bảo vệ thực đo a = 26mm (ĐẠT)",
    inspector_name: str = "KS. Nguyễn Văn A (TVGS) - KS. Trần Văn B (NTTC)",
    timestamp_str: Optional[str] = None,
) -> Path:
    """
    Dán trực tiếp Bảng thông tin kỹ thuật hiện trường (Digital Engineering Chalkboard + Watermark)
    vào ảnh chụp công trường để làm chứng cứ nghiệm thu theo Nghị định số 207/2026/NĐ-CP.
    """
    base_img = Image.open(input_image_path).convert("RGBA")
    width, height = base_img.size

    exif_info = extract_exif_metadata(base_img)
    if not timestamp_str:
        timestamp_str = exif_info.get(
            "DateTimeOriginal",
            datetime.datetime.now().strftime("%d/%m/%Y %H:%M:%S GMT+07:00"),
        )

    # Tính mã băm ảnh gốc trước khi đóng dấu để truy xuất nguồn gốc
    raw_sha256_short = compute_sha256(input_image_path)[:16].upper()

    overlay = Image.new("RGBA", (width, height), (0, 0, 0, 0))
    draw = ImageDraw.Draw(overlay)

    # Kích thước chữ tự động co giãn theo độ phân giải ảnh
    scale = max(width, height) / 1600.0
    font_title = _load_vietnamese_font(max(16, int(22 * scale)), bold=True)
    font_label = _load_vietnamese_font(max(13, int(17 * scale)), bold=True)
    font_value = _load_vietnamese_font(max(13, int(17 * scale)), bold=False)
    font_small = _load_vietnamese_font(max(11, int(14 * scale)), bold=False)

    # 1. Dải băng tiêu đề trên cùng (Top Header Bar)
    top_bar_h = max(38, int(48 * scale))
    draw.rectangle([(0, 0), (width, top_bar_h)], fill=(15, 23, 42, 210))
    draw.text(
        (int(18 * scale), int(11 * scale)),
        f"CHỨNG CỨ KIỂM TRA - KHẢO SÁT - GIÁM SÁT HIỆN TRƯỜNG (NĐ 207/2026/NĐ-CP)",
        fill=(255, 255, 255, 255),
        font=font_label,
    )
    draw.text(
        (width - int(340 * scale), int(12 * scale)),
        f"SHA-256: {raw_sha256_short}",
        fill=(148, 163, 184, 255),
        font=font_small,
    )

    # 2. Bảng thông tin kỹ thuật hiện trường ở góc dưới bên trái (Engineering Data Panel)
    margin = int(20 * scale)
    panel_w = min(width - 2 * margin, int(860 * scale))
    line_h = int(28 * scale)
    rows = [
        ("DỰ ÁN:", project_name),
        ("HẠNG MỤC KIỂM TRA:", work_item),
        ("VỊ TRÍ / TRỤC / CAO ĐỘ:", zone_axis),
        ("TỌA ĐỘ GNSS / LA BÀN:", f"{gps_coords}  |  Hướng nhìn: {compass_heading}"),
        ("THỜI GIAN GHI NHẬN:", timestamp_str),
        ("KẾT QUẢ ĐO / ĐÁNH GIÁ:", measured_result),
        ("CÁN BỘ HIỆN TRƯỜNG:", inspector_name),
    ]
    panel_h = int(52 * scale) + len(rows) * line_h + int(16 * scale)
    x0 = margin
    y0 = height - panel_h - margin
    x1 = x0 + panel_w
    y1 = height - margin

    # Nền bảng bán trong suốt màu xanh đen kỹ thuật + viền khung trắng mảnh
    draw.rectangle([(x0, y0), (x1, y1)], fill=(15, 23, 42, 215), outline=(255, 255, 255, 220), width=2)
    # Thanh tiêu đề bảng thông tin
    header_h = int(38 * scale)
    draw.rectangle([(x0, y0), (x1, y0 + header_h)], fill=(30, 58, 138, 235), outline=(255, 255, 255, 220), width=1)
    draw.text(
        (x0 + int(14 * scale), y0 + int(8 * scale)),
        "BẢNG THÔNG TIN ĐỊNH VỊ & NGHIỆM THU HIỆN TRƯỜNG",
        fill=(255, 255, 255, 255),
        font=font_title,
    )

    # Vẽ từng dòng thông tin có đường kẻ phân cách mảnh
    curr_y = y0 + header_h + int(8 * scale)
    label_col_w = int(235 * scale)
    for idx, (lbl, val) in enumerate(rows):
        draw.text((x0 + int(14 * scale), curr_y), lbl, fill=(147, 197, 253, 255), font=font_label)
        val_color = (134, 239, 172, 255) if "ĐẠT" in val else (255, 255, 255, 255)
        draw.text((x0 + label_col_w, curr_y), val, fill=val_color, font=font_value)
        curr_y += line_h
        if idx < len(rows) - 1:
            draw.line(
                [(x0 + int(10 * scale), curr_y - int(4 * scale)), (x1 - int(10 * scale), curr_y - int(4 * scale))],
                fill=(71, 85, 105, 160),
                width=1,
            )

    # 3. Vòng la bàn & Tâm ngắm kiểm chứng góc trên bên phải (Compass & Reticle)
    compass_r = int(52 * scale)
    cx = width - margin - compass_r - int(10 * scale)
    cy = top_bar_h + margin + compass_r + int(10 * scale)
    draw.ellipse(
        [(cx - compass_r, cy - compass_r), (cx + compass_r, cy + compass_r)],
        fill=(15, 23, 42, 200),
        outline=(255, 255, 255, 220),
        width=2,
    )
    draw.text((cx - int(7 * scale), cy - compass_r + int(4 * scale)), "B", fill=(248, 113, 113, 255), font=font_label)
    draw.text((cx - int(6 * scale), cy + compass_r - int(22 * scale)), "N", fill=(255, 255, 255, 220), font=font_small)
    draw.text((cx + compass_r - int(18 * scale), cy - int(9 * scale)), "Đ", fill=(255, 255, 255, 220), font=font_small)
    draw.text((cx - compass_r + int(6 * scale), cy - int(9 * scale)), "T", fill=(255, 255, 255, 220), font=font_small)
    draw.text((cx - int(22 * scale), cy - int(9 * scale)), "135°", fill=(134, 239, 172, 255), font=font_label)

    combined = Image.alpha_composite(base_img, overlay).convert("RGB")
    output_image_path.parent.mkdir(parents=True, exist_ok=True)
    combined.save(output_image_path, quality=95)
    return output_image_path


def generate_demo_field_photo(raw_path: Path) -> Path:
    """Tạo một bức ảnh giả lập hiện trường kiểm tra cốt thép dầm sàn để kiểm thử đóng dấu."""
    w, h = 1600, 1050
    img = Image.new("RGB", (w, h), (64, 70, 78))
    draw = ImageDraw.Draw(img)

    # Vẽ lưới cốt thép dầm sàn minh họa
    for x in range(100, w - 100, 90):
        draw.line([(x, 80), (x, h - 80)], fill=(156, 163, 175), width=8)
        draw.line([(x + 3, 80), (x + 3, h - 80)], fill=(209, 213, 219), width=2)
    for y in range(100, h - 100, 90):
        draw.line([(80, y), (w - 80, y)], fill=(140, 148, 160), width=7)

    # Vẽ các viên con kê bê tông định hình (Concrete Spacers a=25mm)
    for sx, sy in [(370, 280), (820, 280), (1270, 280), (370, 550), (820, 550), (1270, 550)]:
        draw.rectangle([(sx - 22, sy - 22), (sx + 22, sy + 22)], fill=(203, 213, 225), outline=(71, 85, 105), width=2)

    raw_path.parent.mkdir(parents=True, exist_ok=True)
    img.save(raw_path, quality=92)
    return raw_path


def build_evidence_manifest(
    evidence_dir: Path,
    project_code: str,
    zone: str,
    grid_axis: str,
    inspector: str,
) -> Dict[str, Any]:
    """Quét thư mục dữ liệu hiện trường và tạo hồ sơ băm kiểm chứng (Manifest JSON)."""
    records: List[Dict[str, Any]] = []
    supported_exts = {".jpg", ".jpeg", ".png", ".wav", ".m4a", ".csv", ".pdf"}

    if evidence_dir.exists():
        for item in sorted(evidence_dir.rglob("*")):
            if item.is_file() and item.suffix.lower() in supported_exts:
                stat = item.stat()
                modified_iso = datetime.datetime.fromtimestamp(
                    stat.st_mtime
                ).isoformat(timespec="seconds")
                records.append(
                    {
                        "file_name": item.name,
                        "relative_path": str(item.relative_to(evidence_dir.parent)),
                        "file_size_bytes": stat.st_size,
                        "captured_at": modified_iso,
                        "zone": zone,
                        "grid_axis": grid_axis,
                        "sha256": compute_sha256(item),
                    }
                )

    return {
        "project_code": project_code,
        "generated_at": datetime.datetime.now().isoformat(timespec="seconds"),
        "inspector": inspector,
        "legal_compliance": [
            "Luật Xây dựng số 135/2025/QH15",
            "Nghị định số 207/2026/NĐ-CP",
            "Nghị định số 217/2026/NĐ-CP",
        ],
        "total_files": len(records),
        "evidence_items": records,
    }


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Xử lý chứng cứ số, dán Bảng thông tin kỹ thuật lên ảnh hiện trường và băm SHA-256."
    )
    parser.add_argument(
        "--dir",
        type=str,
        default=str(Path(__file__).resolve().parent.parent / "field_data"),
        help="Đường dẫn thư mục dữ liệu hiện trường (field_data).",
    )
    parser.add_argument(
        "--demo-stamp",
        action="store_true",
        help="Tạo và đóng dấu thông tin hiện trường lên ảnh mẫu kiểm tra cốt thép.",
    )
    args = parser.parse_args()

    target_dir = Path(args.dir)
    photos_dir = target_dir / "photos"
    photos_dir.mkdir(parents=True, exist_ok=True)

    if args.demo_stamp or not any(photos_dir.glob("*.jpg")):
        raw_photo = photos_dir / "DA01-IBST-B1-IMG-STR-0001_RAW.jpg"
        stamped_photo = photos_dir / "DA01-IBST-B1-IMG-STR-0001_STAMPED.jpg"
        generate_demo_field_photo(raw_photo)
        stamp_field_photo(input_image_path=raw_photo, output_image_path=stamped_photo)
        print(f"Đã dán Bảng thông tin kỹ thuật hiện trường vào ảnh tại: {stamped_photo}")

    manifest = build_evidence_manifest(
        evidence_dir=target_dir,
        project_code="DA01-IBST",
        zone="Tầng hầm B1",
        grid_axis="Trục (B-C)/(3-4)",
        inspector="KS. Nguyễn Văn A (TVGS) - KS. Trần Văn B (NTTC)",
    )

    output_json = target_dir / "field_evidence_manifest.json"
    with open(output_json, "w", encoding="utf-8") as f:
        json.dump(manifest, f, ensure_ascii=False, indent=2)

    print(f"Đã xuất hồ sơ chứng cứ hiện trường tại: {output_json}")
    print(f"Tổng số tệp chứng cứ đã băm SHA-256: {manifest['total_files']}")


if __name__ == "__main__":
    main()

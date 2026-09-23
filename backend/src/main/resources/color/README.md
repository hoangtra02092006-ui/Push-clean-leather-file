# Hồ sơ màu ICC kèm sẵn

## `USWebCoatedSWOP.icc` — 557 KB

Hồ sơ **U.S. Web Coated (SWOP) v2**, dùng làm không gian CMYK đích mặc định khi xuất TIF.

### Lấy từ đâu

Đọc ra từ tag `34675` của file mẫu `samples/IN6 2209.tif` — bản do thợ tự dựng trong Photoshop. Nói cách khác, **đây đúng là hồ sơ xưởng đang dùng**.

### Vì sao chọn nó

| | |
|---|---|
| **Khớp màu** | Cùng hồ sơ với quy trình Photoshop của xưởng, nên không thêm một lần lệch màu nào nữa |
| **Nhẹ** | 557 KB, trong khi các hồ sơ CGATS21 đều khoảng 3,5 MB — mà hồ sơ được **nhúng vào từng file** xuất ra, nên chênh lệch đó cộng thẳng vào mỗi tấm |

> Java ghi **lại** hồ sơ sau khi dùng nó để chuyển màu, nên bản nằm trong file xuất ra là 702 KB chứ không phải 557 KB. Đừng viết test đòi hai số này bằng nhau.

### ⚠️ Về bản quyền — cần người quyết

Đây là hồ sơ do **Adobe** phát hành, không phải hồ sơ tự do như các bản CGATS21. Nó nằm sẵn trên mọi máy có phần mềm Adobe.

Dùng nội bộ trong xưởng thì không có gì phải bàn. Nhưng **repo này công khai trên GitHub**, nên việc kèm file vào đây là chuyện khác. Nếu thấy không ổn, có hai đường:

1. Xoá file này, đặt `APP_TIFF_CMYK_PROFILE` trỏ tới hồ sơ nằm trên máy chủ.
2. Quay lại hồ sơ CGATS21 do ICC phát hành công khai — đổi lại file xuất ra nặng thêm khoảng 2,8 MB mỗi tấm, và màu lệch đi chút ít so với bản thợ làm tay.

### Nên thay bằng gì

Nếu xin được hồ sơ `.icc` của **chính máy in** từ nhà cung cấp RIP thì nên dùng bản đó: nó mô tả đúng đặc tính mực và giấy của máy, nên màu sát hơn cả SWOP. Chỉ cần đổi `APP_TIFF_CMYK_PROFILE`, không phải sửa code.

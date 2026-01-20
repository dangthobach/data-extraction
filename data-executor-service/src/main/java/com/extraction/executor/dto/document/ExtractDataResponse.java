package com.extraction.executor.dto.document;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Response DTO for Extract Data API
 * Contains all extracted structured data from disbursement documents
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExtractDataResponse {

    @JsonProperty("transaction_id")
    private String transactionId;

    @JsonProperty("status")
    private String status;

    @JsonProperty("extracted_details")
    private ExtractedDetails extractedDetails;

    /**
     * Main container for all extracted data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExtractedDetails {

        @JsonProperty("to_trinh")
        private ToTrinhData toTrinh;

        @JsonProperty("nghi_quyet")
        private NghiQuyetData nghiQuyet;

        @JsonProperty("khe_uoc")
        private KheUocData kheUoc;

        @JsonProperty("hop_dong_mua_ban")
        private HopDongMuaBanData hopDongMuaBan;

        @JsonProperty("de_nghi_thanh_toan")
        private DeNghiThanhToanData deNghiThanhToan;

        @JsonProperty("hoa_don")
        private HoaDonData hoaDon;

        @JsonProperty("bien_ban_ban_giao")
        private BienBanBanGiaoData bienBanBanGiao;
    }

    /**
     * Tờ trình (Proposal) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToTrinhData {

        @JsonProperty("ten_khach_hang")
        private String tenKhachHang;

        @JsonProperty("cif_khach_hang")
        private String cifKhachHang;

        @JsonProperty("msdn_dkkd")
        private String msdnDkkd;

        @JsonProperty("so_tien_giai_ngan")
        private String soTienGiaiNgan;

        @JsonProperty("loai_tien")
        private String loaiTien;

        @JsonProperty("thoi_han_vay")
        private String thoiHanVay;

        @JsonProperty("lai_suat")
        private String laiSuat;

        @JsonProperty("loai_lai_suat")
        private String loaiLaiSuat;

        @JsonProperty("bien_do")
        private String bienDo;

        @JsonProperty("muc_dich_giai_ngan")
        private String mucDichGiaiNgan;

        @JsonProperty("ten_ben_thu_huong")
        private String tenBenThuHuong;

        @JsonProperty("so_hop_dong")
        private List<String> soHopDong;

        @JsonProperty("ngay_ky_hop_dong")
        private List<String> ngayKyHopDong;
    }

    /**
     * Nghị quyết (Resolution) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NghiQuyetData {

        @JsonProperty("ten_khach_hang")
        private String tenKhachHang;

        @JsonProperty("cif_khach_hang")
        private String cifKhachHang;

        @JsonProperty("msdn_dkkd")
        private String msdnDkkd;

        @JsonProperty("gia_tri_han_muc_vay")
        private String giaTriHanMucVay;

        @JsonProperty("loai_tien")
        private String loaiTien;

        @JsonProperty("thoi_han_khe_uoc")
        private String thoiHanKheUoc;

        @JsonProperty("muc_dich_cho_vay")
        private String mucDichChoVay;

        @JsonProperty("hinh_thuc_giai_ngan")
        private String hinhThucGiaiNgan;

        @JsonProperty("phuong_thuc_tra_no_goc")
        private String phuongThucTraNoGoc;

        @JsonProperty("phuong_thuc_tra_no_lai")
        private String phuongThucTraNoLai;

        @JsonProperty("ty_le_cho_vay")
        private String tyLeChoVay;

        @JsonProperty("chung_tu_giai_ngan")
        private String chungTuGiaiNgan;
    }

    /**
     * Khế ước (Commitment/Loan Agreement) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class KheUocData {

        @JsonProperty("so_hop_dong_han_muc")
        private String soHopDongHanMuc;

        @JsonProperty("ten_khach_hang")
        private String tenKhachHang;

        @JsonProperty("so_tien_de_nghi_giai_ngan")
        private String soTienDeNghiGiaiNgan;

        @JsonProperty("thong_tin_ben_thu_huong")
        private List<BenThuHuongInfo> thongTinBenThuHuong;
    }

    /**
     * Beneficiary information
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BenThuHuongInfo {

        @JsonProperty("ten_ben_thu_huong")
        private String tenBenThuHuong;

        @JsonProperty("so_tai_khoan")
        private String soTaiKhoan;

        @JsonProperty("ngan_hang_thu_huong")
        private String nganHangThuHuong;

        @JsonProperty("noi_dung_thanh_toan")
        private String noiDungThanhToan;

        @JsonProperty("so_tien_thanh_toan")
        private String soTienThanhToan;
    }

    /**
     * Hợp đồng mua bán (Purchase Contract) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HopDongMuaBanData {

        @JsonProperty("ten_hop_dong")
        private String tenHopDong;

        @JsonProperty("so_hop_dong")
        private String soHopDong;

        @JsonProperty("ngay_ky_hop_dong")
        private String ngayKyHopDong;

        @JsonProperty("ten_ben_ban")
        private String tenBenBan;

        @JsonProperty("msdn_dkkd_ben_ban")
        private String msdnDkkdBenBan;

        @JsonProperty("ten_ben_mua")
        private String tenBenMua;

        @JsonProperty("msdn_dkkd_ben_mua")
        private String msdnDkkdBenMua;

        @JsonProperty("ten_hang_hoa_dich_vu")
        private String tenHangHoaDichVu;

        @JsonProperty("so_luong_hang_hoa")
        private String soLuongHangHoa;

        @JsonProperty("tong_so_tien_mua_ban")
        private String tongSoTienMuaBan;

        @JsonProperty("loai_tien")
        private String loaiTien;

        @JsonProperty("phuong_thuc_thanh_toan")
        private String phuongThucThanhToan;

        @JsonProperty("so_tien_tra_truoc_tam_ung")
        private String soTienTraTruocTamUng;

        @JsonProperty("ngay_het_hieu_luc")
        private String ngayHetHieuLuc;
    }

    /**
     * Đề nghị thanh toán (Payment Request) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeNghiThanhToanData {

        @JsonProperty("ngay_thang_de_nghi_thanh_toan")
        private String ngayThangDeNghiThanhToan;

        @JsonProperty("ten_ben_de_nghi_thanh_toan")
        private String tenBenDeNghiThanhToan;

        @JsonProperty("ten_ben_nhan_de_nghi_thanh_toan")
        private String tenBenNhanDeNghiThanhToan;

        @JsonProperty("so_tien_de_nghi_thanh_toan")
        private String soTienDeNghiThanhToan;

        @JsonProperty("loai_tien")
        private String loaiTien;

        @JsonProperty("so_hoa_don")
        private String soHoaDon;

        @JsonProperty("ngay_hoa_don")
        private String ngayHoaDon;

        @JsonProperty("so_hop_dong")
        private String soHopDong;

        @JsonProperty("ngay_ky_hop_dong")
        private String ngayKyHopDong;
    }

    /**
     * Hóa đơn (Invoice) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HoaDonData {

        @JsonProperty("so_hoa_don")
        private String soHoaDon;

        @JsonProperty("ngay_hoa_don")
        private String ngayHoaDon;

        @JsonProperty("ten_don_vi_ban_hang")
        private String tenDonViBanHang;

        @JsonProperty("ten_don_vi_mua_hang")
        private String tenDonViMuaHang;

        @JsonProperty("tong_tien_thanh_toan")
        private String tongTienThanhToan;

        @JsonProperty("loai_tien")
        private String loaiTien;
    }

    /**
     * Biên bản bàn giao (Handover Minutes) data
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BienBanBanGiaoData {

        @JsonProperty("ngay_ban_giao")
        private String ngayBanGiao;

        @JsonProperty("ben_giao")
        private String benGiao;

        @JsonProperty("ben_nhan")
        private String benNhan;

        @JsonProperty("noi_dung_ban_giao")
        private String noiDungBanGiao;
    }
}

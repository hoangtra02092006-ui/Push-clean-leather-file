package vn.printnest.nesting.model;

import java.util.List;

/**
 * Ket qua day du cua mot job ghep file.
 *
 * @param sheets danh sach tam da dan khuon
 * @param stats  so lieu tong hop
 */
public record NestResult(List<Sheet> sheets, NestStats stats) {
}

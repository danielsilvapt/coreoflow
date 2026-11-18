package pt.studioflow.util;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Geração de folhas Excel simples (título, cabeçalho colorido, linhas e rodapé
 * com a data de geração), partilhada pelos relatórios e pela lista de alunos.
 */
public final class ExcelUtil {

    private ExcelUtil() {
    }

    public static byte[] gerarTabela(String titulo, Color corCabecalho, String[] headers, List<String[]> rows) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            // Nome da folha limitado a 31 caracteres e sem caracteres proibidos pelo Excel
            String sheetName = titulo.replaceAll("[\\\\/?*\\[\\]:]", " ");
            if (sheetName.length() > 30) {
                sheetName = sheetName.substring(0, 30);
            }
            Sheet sheet = workbook.createSheet(sheetName);
            int rowNum = 0;

            // 1. Título
            Row titleRow = sheet.createRow(rowNum++);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue(titulo);
            CellStyle titleStyle = workbook.createCellStyle();
            Font titleFont = workbook.createFont();
            titleFont.setFontName("Helvetica");
            titleFont.setFontHeightInPoints((short) 16);
            titleFont.setBold(true);
            titleStyle.setFont(titleFont);
            titleCell.setCellStyle(titleStyle);
            rowNum++;

            // 2. Cabeçalho
            Row headerRow = sheet.createRow(rowNum++);
            CellStyle headerStyle = workbook.createCellStyle();
            if (headerStyle instanceof XSSFCellStyle xssf) {
                xssf.setFillForegroundColor(new XSSFColor(corCabecalho, null));
                xssf.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            }
            Font headerFont = workbook.createFont();
            headerFont.setFontName("Helvetica");
            headerFont.setFontHeightInPoints((short) 10);
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            bordas(headerStyle);
            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            // 3. Dados
            CellStyle rowStyle = workbook.createCellStyle();
            Font rowFont = workbook.createFont();
            rowFont.setFontName("Helvetica");
            rowFont.setFontHeightInPoints((short) 9);
            rowStyle.setFont(rowFont);
            bordas(rowStyle);
            for (String[] r : rows) {
                Row row = sheet.createRow(rowNum++);
                for (int i = 0; i < r.length; i++) {
                    Cell cell = row.createCell(i);
                    cell.setCellValue(r[i] != null ? r[i] : "");
                    cell.setCellStyle(rowStyle);
                }
            }
            rowNum++;

            // 4. Rodapé
            Row footerRow = sheet.createRow(rowNum);
            Cell footerCell = footerRow.createCell(Math.max(0, headers.length - 1));
            footerCell.setCellValue("Gerado em: " + DataUtil.formatar(LocalDate.now()));
            CellStyle footerStyle = workbook.createCellStyle();
            Font footerFont = workbook.createFont();
            footerFont.setFontName("Helvetica");
            footerFont.setFontHeightInPoints((short) 8);
            footerFont.setItalic(true);
            footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            footerStyle.setFont(footerFont);
            footerStyle.setAlignment(HorizontalAlignment.RIGHT);
            footerCell.setCellStyle(footerStyle);

            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Erro a gerar Excel: " + titulo, e);
        }
    }

    private static void bordas(CellStyle style) {
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
    }
}

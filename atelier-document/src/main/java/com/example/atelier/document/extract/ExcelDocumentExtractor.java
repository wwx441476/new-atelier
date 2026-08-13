package com.example.atelier.document.extract;

import com.example.atelier.document.model.BlockMeta;
import com.example.atelier.document.model.BlockType;
import com.example.atelier.document.model.DocumentBlock;
import com.example.atelier.document.model.DocumentModel;
import com.example.atelier.document.model.TableData;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
@Order(30)
public class ExcelDocumentExtractor implements DocumentExtractor {

    private final DataFormatter formatter = new DataFormatter();

    @Override
    public boolean supports(String mimeType, String fileName) {
        String mime = mimeType == null ? "" : mimeType.toLowerCase(Locale.ROOT);
        String name = fileName == null ? "" : fileName.toLowerCase(Locale.ROOT);
        return mime.contains("spreadsheetml") || name.endsWith(".xlsx");
    }

    @Override
    public DocumentModel extract(InputStream input, ExtractContext context) throws Exception {
        BlockIds ids = new BlockIds("xlsx");
        List<DocumentBlock> blocks = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        int maxSheets = context.getMaxSheets() > 0 ? context.getMaxSheets() : 20;
        try (Workbook workbook = new XSSFWorkbook(input)) {
            int sheetCount = Math.min(workbook.getNumberOfSheets(), maxSheets);
            if (workbook.getNumberOfSheets() > maxSheets) {
                warnings.add("Excel sheet 数量超过上限 " + maxSheets + "，已截断");
            }
            for (int i = 0; i < sheetCount; i++) {
                Sheet sheet = workbook.getSheetAt(i);
                String sheetName = sheet.getSheetName();
                List<List<String>> rows = new ArrayList<>();
                int lastRow = Math.min(sheet.getLastRowNum(), 2000);
                for (int r = 0; r <= lastRow; r++) {
                    Row row = sheet.getRow(r);
                    if (row == null) {
                        continue;
                    }
                    List<String> cells = new ArrayList<>();
                    short lastCell = row.getLastCellNum();
                    if (lastCell < 0) {
                        continue;
                    }
                    int maxCol = Math.min(lastCell, 100);
                    boolean any = false;
                    for (int c = 0; c < maxCol; c++) {
                        Cell cell = row.getCell(c);
                        String value = cell == null ? "" : formatter.formatCellValue(cell).trim();
                        if (!value.isEmpty()) {
                            any = true;
                        }
                        cells.add(value);
                    }
                    if (any) {
                        rows.add(cells);
                    }
                }
                blocks.add(DocumentBlock.builder()
                        .id(ids.next())
                        .type(BlockType.SHEET)
                        .text("[Sheet: " + sheetName + "]")
                        .meta(BlockMeta.builder().sheet(sheetName).build())
                        .table(TableData.builder().sheetName(sheetName).rows(rows).build())
                        .build());
            }
        }
        return DocumentModel.builder()
                .fileName(context.getFileName())
                .mimeType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .blocks(blocks)
                .warnings(warnings)
                .build();
    }
}

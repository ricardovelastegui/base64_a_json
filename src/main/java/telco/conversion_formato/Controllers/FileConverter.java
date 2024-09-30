package telco.conversion_formato.Controllers;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.opencsv.CSVReader;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/apitel/convert")
public class FileConverter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    
    @PostMapping("/xlsx-to-json")
    public ResponseEntity<?> convertXlsxToJson(@RequestBody Map<String, String> body) {
        try {
            String base64File = body.get("file");
            if (base64File == null || base64File.isEmpty()) {
                return ResponseEntity.badRequest().body("Archivo Base64 no proporcionado");
            }
            
    
            byte[] fileBytes = Base64.getDecoder().decode(base64File);
            InputStream inputStream = new ByteArrayInputStream(fileBytes);
    
            Workbook workbook = new XSSFWorkbook(inputStream);
            Map<String, List<Map<String, String>>> allSheetData = new HashMap<>();
            boolean hasData = false;
    
            // Recorrer todas las hojas del archivo
            for (int sheetIndex = 0; sheetIndex < workbook.getNumberOfSheets(); sheetIndex++) {
                Sheet sheet = workbook.getSheetAt(sheetIndex);
                String sheetName = workbook.getSheetName(sheetIndex);
                List<Map<String, String>> jsonData = new ArrayList<>();
    
                boolean sheetHasData = false;
                String[] headers = null; // Almacenar encabezados para la hoja
    
                // Detectar automáticamente dónde empieza la tabla (primera fila con datos)
                int startRow = -1;
                for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row != null && row.getPhysicalNumberOfCells() > 0) {
                        // Detectar si la fila tiene datos y usarla como encabezado
                        startRow = rowIndex;
                        headers = new String[row.getLastCellNum()];  // Crear array de encabezados
                        for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                            Cell cell = row.getCell(cellIndex);
                            headers[cellIndex] = (cell != null) ? cell.toString().trim() : "Columna" + (cellIndex + 1);
                        }
                        break;
                    }
                }
    
                // Si no hay encabezados detectados, pasar a la siguiente hoja
                if (headers == null) {
                    continue;
                }
    
                // Procesar los datos desde la fila siguiente a la que se detectó como encabezado
                for (int rowIndex = startRow + 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                    Row row = sheet.getRow(rowIndex);
                    if (row == null || row.getPhysicalNumberOfCells() == 0) {
                        continue;  // Saltar filas vacías
                    }
    
                    Map<String, String> rowData = new HashMap<>();
                    boolean rowHasData = false;
    
                    for (int cellIndex = 0; cellIndex < headers.length; cellIndex++) {
                        Cell cell = row.getCell(cellIndex);
                        String cellValue = (cell != null) ? cell.toString().trim() : "";
                        if (!cellValue.isEmpty()) {
                            rowHasData = true;
                        }
                        rowData.put(headers[cellIndex], cellValue);  // Asignar valor a la clave del encabezado
                    }
    
                    if (rowHasData) {
                        jsonData.add(rowData);
                        sheetHasData = true;
                    }
                }
    
                if (sheetHasData) {
                    allSheetData.put(sheetName, jsonData);
                    hasData = true;
                }
            }
    
            if (!hasData) {
                return ResponseEntity.status(HttpStatus.NO_CONTENT).body("El archivo no contiene datos procesables");
            }
    
            ObjectMapper objectMapper = new ObjectMapper();
            String json = objectMapper.writeValueAsString(allSheetData);
            return ResponseEntity.ok(json);
    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Archivo Base64 inválido: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al convertir XLSX a JSON: " + e.getMessage());
        }
    }



    




    @PostMapping("/csv-to-json")
    public ResponseEntity<?> convertCsvToJson(@RequestBody Map<String, String> body) {
        try {
            String base64File = body.get("base64File");
            byte[] fileBytes = Base64.getDecoder().decode(base64File);
            InputStream inputStream = new ByteArrayInputStream(fileBytes);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));
    
            String line;
            List<String[]> csvData = new ArrayList<>();
            while ((line = reader.readLine()) != null) {
                if (!line.isEmpty()) {
                    csvData.add(line.split(","));
                }
            }
    
            if (csvData.isEmpty()) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El archivo CSV está vacío.");
            }
    
            // Validación del encabezado
            String[] expectedHeaders = {"nombre", "apellido", "ciudad", "codigo_postal", "ruc", "fecha_nacimiento", "telefono", "correo_electronico", "genero"};
            String[] headers = csvData.get(0);
    
            if (headers.length != expectedHeaders.length) {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La cantidad de elementos en el encabezado no coincide con lo esperado.");
            }
    
            for (int i = 0; i < headers.length; i++) {
                if (!headers[i].trim().equalsIgnoreCase(expectedHeaders[i])) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El encabezado del CSV no tiene el formato correcto. Se esperaba: " + Arrays.toString(expectedHeaders));
                }
            }
    
            List<Map<String, String>> jsonData = new ArrayList<>();
            for (int i = 1; i < csvData.size(); i++) {
                String[] row = csvData.get(i);
                if (row.length != headers.length) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("La fila " + (i + 1) + " no tiene la misma cantidad de elementos que el encabezado.");
                }
    
                Map<String, String> rowData = new HashMap<>();
                for (int j = 0; j < row.length; j++) {
                    rowData.put(headers[j], row[j]);
                }
                jsonData.add(rowData);
            }
    
            String json = objectMapper.writeValueAsString(jsonData);
            return ResponseEntity.ok(json);
    
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Error al decodificar el archivo Base64: " + e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al convertir CSV a JSON: " + e.getMessage());
        }
    }
    



    
    @PostMapping("/json-to-xlsx")
public ResponseEntity<?> convertJsonToXlsx(@RequestBody List<Map<String, String>> jsonData) {
    try {
        //crear un nuevo libro de excel
        Workbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Sheet1");

        // Obtener encabezados desde el primer objeto del JSON
        if (!jsonData.isEmpty()) {
            Map<String, String> firstRow = jsonData.get(0);
            Row headerRow = sheet.createRow(0);

            int headerCellIndex = 0;
            for (String key : firstRow.keySet()) {
                Cell cell = headerRow.createCell(headerCellIndex++);
                cell.setCellValue(key);
            }

            //reellenar las filas con los datos
            for (int i = 0; i < jsonData.size(); i++) {
                Map<String, String> rowData = jsonData.get(i);
                Row row = sheet.createRow(i + 1);

                int cellIndex = 0;
                for (String value : rowData.values()) {
                    Cell cell = row.createCell(cellIndex++);
                    cell.setCellValue(value);
                }
            }
        }

        //convertir el archivo XLSX a Base64
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        workbook.write(outputStream);
        byte[] xlsxBytes = outputStream.toByteArray();
        String base64Xlsx = Base64.getEncoder().encodeToString(xlsxBytes);

        workbook.close();
        return ResponseEntity.ok(base64Xlsx);

    } catch (Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error al convertir JSON a XLSX: " + e.getMessage());
    }
}


}

package telco.conversion_formato.Controllers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
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
import com.opencsv.CSVReader;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/apitel/convert")
public class FileConverter {
    private final ObjectMapper objectMapper = new ObjectMapper();

    
    @PostMapping("/xlsx-to-json")
public ResponseEntity<?> convertXlsxToJson(@RequestBody Map<String, String> body) {
    try {
        //obtener el base64 del request body, pilas usar el 'file' como clave
        String base64File = body.get("file");
        if (base64File == null || base64File.isEmpty()) {
            return ResponseEntity.badRequest().body("Archivo Base64 no proporcionado");
        }

        //decodificar el base64 a bytes
        byte[] fileBytes = Base64.getDecoder().decode(base64File);
        InputStream inputStream = new ByteArrayInputStream(fileBytes);
        
        //abrir el archivo Excel
        Workbook workbook = new XSSFWorkbook(inputStream);
        Sheet sheet = workbook.getSheetAt(0);  // Toma la primera hoja del Excel
        List<Map<String, String>> jsonData = new ArrayList<>();

        //leer la primera fila como encabezado
        // Row headerRow = sheet.getRow(0);
        // for (int i = 1; i < sheet.getPhysicalNumberOfRows(); i++) {
        //     Row row = sheet.getRow(i);
        //     Map<String, String> rowData = new HashMap<>();
        //     for (int j = 0; j < row.getPhysicalNumberOfCells(); j++) {
        //         rowData.put(headerRow.getCell(j).getStringCellValue(), row.getCell(j).toString());
        //     }
        //     jsonData.add(rowData);
        // }

        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);

            
            if (row == null || row.getPhysicalNumberOfCells() == 0) {
                continue;  //Saltar filas vacias
            }

            //crear un map para almacenar los datos de la fila
            Map<String, String> rowData = new HashMap<>();

            //recorrer todas las celdas de la fila
            for (int cellIndex = 0; cellIndex < row.getLastCellNum(); cellIndex++) {
                Cell cell = row.getCell(cellIndex);

                
                if (cell == null) {
                    continue; //saltar celdas vacias
                }

                //obtener el valor de la celda como strin
                String cellValue = cell.toString();
                rowData.put("Columna" + (cellIndex + 1), cellValue); // Ajusta los nombres de columna según sea necesario
            }

            //agregar los datos de la fila al jsondata
            jsonData.add(rowData);
        }
        //Convierte los datos a json
        ObjectMapper objectMapper = new ObjectMapper();
        String json = objectMapper.writeValueAsString(jsonData);
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
        // Extraer el contenido del base64 que viene en el request body
        String base64File = body.get("base64File");
        System.out.println("Longitud de la cadena Base64: " + base64File.length());

        // Decodificar el base64
        byte[] fileBytes = Base64.getDecoder().decode(base64File);
        System.out.println("Longitud de bytes decodificados: " + fileBytes.length);

        // Pasar los bytes decodificados al InputStream para procesar el CSV
        InputStream inputStream = new ByteArrayInputStream(fileBytes);
        CSVReader reader = new CSVReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8));

        // Leer el csv
        List<String[]> csvData = reader.readAll();
        List<Map<String, String>> jsonData = new ArrayList<>();
        
        // Validar que tengamos suficientes datos
        if (csvData.size() < 2) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El archivo CSV no contiene suficientes datos.");
        }

        // Usamos la fila 2 (índice 1) como encabezados, ignorando las primeras filas
        String[] headers = csvData.get(1); 

        // Convertir cada fila del CSV a un objeto JSON, ignorando celdas vacías
        for (int i = 2; i < csvData.size(); i++) {
            if (csvData.get(i).length != headers.length) {
                // Si una fila tiene un número diferente de columnas, lo ignoramos
                System.out.println("Fila ignorada por tener un número diferente de columnas: " + i);
                continue;
            }

            Map<String, String> rowData = new HashMap<>();
            boolean hasData = false;

            for (int j = 0; j < headers.length; j++) {
                String header = headers[j].trim();
                String value = csvData.get(i)[j] != null ? csvData.get(i)[j].trim() : "";

                if (!value.isEmpty()) {
                    hasData = true;
                }

                rowData.put(header, value);
            }

            if (hasData) {
                jsonData.add(rowData);
            }
        }

        // Convertir los datos a JSON
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

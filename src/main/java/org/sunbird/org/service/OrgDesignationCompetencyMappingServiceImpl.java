package org.sunbird.org.service;

import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddressList;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.usermodel.XSSFDataValidation;
import org.apache.poi.xssf.usermodel.XSSFDataValidationHelper;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.sunbird.common.service.OutboundRequestHandlerServiceImpl;
import org.sunbird.common.util.Constants;

import java.io.ByteArrayOutputStream;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class OrgDesignationCompetencyMappingServiceImpl implements OrgDesignationCompetencyMappingService {

    @Autowired
    private OutboundRequestHandlerServiceImpl outboundRequestHandler;

    @Override
    public ResponseEntity<ByteArrayResource> bulkUploadOrganisationCompetencyMapping(String rootOrgId, String userAuthToken, String frameworkId) {
        try {
            Workbook workbook = new XSSFWorkbook();

            // Create sheets with safe names
            Sheet yourWorkspaceSheet = workbook.createSheet(WorkbookUtil.createSafeSheetName("Your Workspace"));
            Sheet referenceSheetCompetency = workbook.createSheet(WorkbookUtil.createSafeSheetName("Reference Sheet Competency"));
            Sheet orgDesignationMasterSheet = workbook.createSheet(WorkbookUtil.createSafeSheetName("Org Designation master"));

            // Headers for all sheets
            String[] headersWorksheet = {"Designation", "Competency Area", "Competency Theme", "Competency SubTheme"};
            String[] headersCompetency = {"Competency Area", "Competency Theme", "Competency SubTheme"};
            String[] headersDesignation = {"Designation"};
            // Create header rows in each sheet

            createHeaderRow(yourWorkspaceSheet, headersWorksheet);
            createHeaderRow(referenceSheetCompetency, headersCompetency);
            createHeaderRow(orgDesignationMasterSheet, headersDesignation);

            // Example data (can be replaced with actual data)
            populateReferenceSheetCompetency(referenceSheetCompetency);
            populateOrgDesignationMaster(orgDesignationMasterSheet, frameworkId);

            makeSheetReadOnly(orgDesignationMasterSheet);
            makeSheetReadOnly(referenceSheetCompetency);

            // Set up the dropdowns in "Your Workspace" sheet
            setUpDropdowns(workbook, yourWorkspaceSheet, orgDesignationMasterSheet, referenceSheetCompetency);

            // Set column widths to avoid cell overlap
            setColumnWidths(yourWorkspaceSheet);
            setColumnWidths(referenceSheetCompetency);
            setColumnWidths(orgDesignationMasterSheet);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            workbook.write(outputStream);
            workbook.close();

            // Convert the output stream to a byte array and return as a downloadable file
            ByteArrayResource resource = new ByteArrayResource(outputStream.toByteArray());

            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=updated_framework_hierarchy.xlsx");

            return ResponseEntity.ok()
                    .headers(headers)
                    .contentLength(resource.contentLength())
                    .contentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM)
                    .body(resource);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private List<Map<String, Object>> populateDataFromFrameworkTerm(String frameworkId) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Authorization", "bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJNNWVldkxVTUV6TTRmTUU0QUU0MUIzOTJmTzYzWVFwMSJ9.eP6JnGE69Q2KxrdtD0FVBK8gSZ37jK1HpyME7rJE8ZU");
        String url = "https://mdo.dev.karmayogibharat.net/" + "/api/framework/v1/read/" + frameworkId;
        Map<String, Object> termFrameworkCompetencies = (Map<String, Object>) outboundRequestHandler.fetchUsingGetWithHeaders(
                url, headers);
        if (MapUtils.isNotEmpty(termFrameworkCompetencies)) {
            Map<String, Object> result = ((Map<String, Object>) termFrameworkCompetencies.get(Constants.RESULT));
            if (MapUtils.isNotEmpty(result)) {
                Map<String, Object> frameworkObject = ((Map<String, Object>) result.get("framework"));
                if (MapUtils.isNotEmpty(frameworkObject)) {
                    return (List<Map<String, Object>>) frameworkObject.get("categories");
                }
            }
        }
        return null;
    }

    private static void createHeaderRow(Sheet sheet, String[] headers) {
        Row headerRow = sheet.createRow(0);
        for (int i = 0; i < headers.length; i++) {
            Cell cell = headerRow.createCell(i);
            cell.setCellValue(headers[i]);
        }
    }

    private void populateReferenceSheetCompetency(Sheet sheet) {
        Set<String> competencyAreaSet = new LinkedHashSet<>();
        Set<String> competencyThemeSet = new LinkedHashSet<>();
        Set<String> competencySubThemeSet = new LinkedHashSet<>();
        int rowIndex = 1; // Start after header row
        List<Map<String, Object>> getAllCompetenciesMapping = populateDataFromFrameworkTerm("kcmfinal_fw");

        if (CollectionUtils.isNotEmpty(getAllCompetenciesMapping)) {
            Map<String, Object> competencyAreaFrameworkObject = getAllCompetenciesMapping.stream().filter(n -> ((String) (n.get("code")))
                    .equalsIgnoreCase("competencyarea")).findFirst().orElse(null);
            Map<String, Object> competencyThemeFrameworkObject = getAllCompetenciesMapping.stream().filter(n -> ((String) (n.get("code")))
                    .equalsIgnoreCase("theme")).findFirst().orElse(null);
            if (MapUtils.isNotEmpty(competencyAreaFrameworkObject) && MapUtils.isNotEmpty(competencyThemeFrameworkObject)) {
                List<Map<String, Object>> competencyAreaTerms = (List<Map<String, Object>>) competencyAreaFrameworkObject.get("terms");
                List<Map<String, Object>> competencyThemeTerms = (List<Map<String, Object>>) competencyThemeFrameworkObject.get("terms");
                if (CollectionUtils.isNotEmpty(competencyAreaTerms) && CollectionUtils.isNotEmpty(competencyThemeTerms)) {
                    for (Map<String, Object> competencyAreaTerm : competencyAreaTerms) {
                        String competencyArea = (String) competencyAreaTerm.get("name");
                        List<Map<String, Object>> competencyAreaAssociations = (List<Map<String, Object>>) competencyAreaTerm.get("associations");
                        for (Map<String, Object> competencyAreaAssociation : competencyAreaAssociations) {
                            String competencySubTheme = (String) competencyAreaAssociation.get("name");
                            String identifier = (String) competencyAreaAssociation.get("identifier");
                            Map<String, Object> themeObject = competencyThemeTerms.stream().filter(theme -> ((String) theme.get("identifier")).equalsIgnoreCase(identifier))
                                    .findFirst().orElse(null);
                            if (MapUtils.isNotEmpty(themeObject)) {
                                List<Map<String, Object>> themeObjectAssociations = (List<Map<String, Object>>) themeObject.get("associations");
                                if (CollectionUtils.isNotEmpty(themeObjectAssociations)) {
                                    for (Map<String, Object> themeAssociation : themeObjectAssociations) {
                                        String subTheme = (String) themeAssociation.get("name");
                                        if (competencyAreaSet.add(competencyArea) || competencyThemeSet.add(competencySubTheme) || competencySubThemeSet.add(subTheme)) {
                                            Row row = sheet.createRow(rowIndex++);
                                            row.createCell(0).setCellValue(competencyArea);
                                            row.createCell(1).setCellValue(competencySubTheme);
                                            row.createCell(2).setCellValue(subTheme);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void populateOrgDesignationMaster(Sheet sheet, String frameworkId) {
        List<String> designation = new ArrayList<>();
        List<Map<String, Object>> getAllDesignationForOrg = populateDataFromFrameworkTerm(frameworkId);
        if (CollectionUtils.isNotEmpty(getAllDesignationForOrg)) {
            Map<String, Object> designationFrameworkObject = getAllDesignationForOrg.stream().filter(n -> ((String) (n.get("code")))
                    .equalsIgnoreCase("designation")).findFirst().orElse(null);
            if (MapUtils.isNotEmpty(designationFrameworkObject)) {
                List<Map<String, Object>> designationFrameworkTerms = (List<Map<String, Object>>) designationFrameworkObject.get("terms");
                if (CollectionUtils.isNotEmpty(designationFrameworkTerms)) {
                    designation = designationFrameworkTerms.stream()
                            .map(map -> (String) map.get("name"))
                            .distinct()  // Ensure unique values
                            .collect(Collectors.toList());
                }
            }
        }
        for (int i = 0; i < designation.size(); i++) {
            Row row = sheet.createRow(i + 1);  // Create a new row starting from row 2 (index 1)
            Cell cell = row.createCell(0);  // Create a new cell in the first column
            cell.setCellValue(designation.get(i));
        }
    }

    private void makeSheetReadOnly(Sheet sheet) {
        sheet.protectSheet("password");  // Protect the sheet with a random UUID as the password
    }

    public void setUpDropdowns(Workbook workbook, Sheet yourWorkspaceSheet, Sheet orgDesignationMasterSheet, Sheet referenceSheetCompetency) {
        XSSFDataValidationHelper validationHelper = new XSSFDataValidationHelper((XSSFSheet) yourWorkspaceSheet);

        // Create or update the "NamedRanges" sheet
        createOrUpdateNamedRangesSheet(workbook, referenceSheetCompetency);

        // Dropdown for "Designation" column from "Org Designation master"
        String designationRange = "'" + orgDesignationMasterSheet.getSheetName() + "'!$A$2:$A$" + (orgDesignationMasterSheet.getLastRowNum() + 1);
        setDropdownForColumn(validationHelper, yourWorkspaceSheet, 0, designationRange);

        // Dropdown for "Competency Area" from named range
        String competencyAreaRange = "Competency_Areas";
        setDropdownForColumn(validationHelper, yourWorkspaceSheet, 1, competencyAreaRange);

        // Dropdown for "Competency Theme" depending on Competency Area
        setDependentDropdownForColumn(validationHelper, yourWorkspaceSheet, 2, "Themes_");

        // Dropdown for "Competency SubTheme" depending on Competency Theme
        setDependentDropdownForColumn(validationHelper, yourWorkspaceSheet, 3, "SubThemes_");
    }

    private void createOrUpdateNamedRangesSheet(Workbook workbook, Sheet referenceSheetCompetency) {
        Sheet namedRangesSheet = workbook.getSheet("NamedRanges");
        if (namedRangesSheet == null) {
            namedRangesSheet = workbook.createSheet("NamedRanges");
        } else {
            // Clear existing content
            int lastRowNum = namedRangesSheet.getLastRowNum();
            if (lastRowNum > 0) {
                for (int i = 0; i <= lastRowNum; i++) {
                    Row row = namedRangesSheet.getRow(i);
                    if (row != null) {
                        namedRangesSheet.removeRow(row);
                    }
                }
            }
        }

        // Create named ranges
        Map<String, Set<String>> themesMap = new LinkedHashMap<>();
        Map<String, Set<String>> subThemesMap = new LinkedHashMap<>();

        int lastRow = referenceSheetCompetency.getLastRowNum();
        for (int i = 1; i <= lastRow; i++) {
            Row row = referenceSheetCompetency.getRow(i);
            if (row == null) continue;

            String area = row.getCell(0).getStringCellValue();
            String theme = row.getCell(1).getStringCellValue();
            String subTheme = row.getCell(2).getStringCellValue();

            // Create named ranges for themes and subthemes
            themesMap.computeIfAbsent(area, k -> new LinkedHashSet<>()).add(theme);
            subThemesMap.computeIfAbsent(theme, k -> new LinkedHashSet<>()).add(subTheme);
        }

        // Write named ranges for Competency Area
        writeNamedRange(namedRangesSheet, "Competency_Areas", themesMap.keySet());

        // Write named ranges for Themes
        for (Map.Entry<String, Set<String>> entry : themesMap.entrySet()) {
            String area = entry.getKey();
            Set<String> themes = entry.getValue();
            writeNamedRange(namedRangesSheet, "Themes_" + makeNameSafe(area), themes);
        }

        // Write named ranges for SubThemes
        for (Map.Entry<String, Set<String>> entry : subThemesMap.entrySet()) {
            String theme = entry.getKey();
            Set<String> subThemes = entry.getValue();
            writeNamedRange(namedRangesSheet, "SubThemes_" + makeNameSafe(theme), subThemes);
        }

        makeSheetReadOnly(namedRangesSheet);
        workbook.setSheetHidden(workbook.getSheetIndex(namedRangesSheet), true);
    }

    private void writeNamedRange(Sheet namedRangesSheet, String name, Set<String> values) {
        int startRow = namedRangesSheet.getLastRowNum() + 1;
        for (String value : values) {
            Row row = namedRangesSheet.createRow(startRow++);
            row.createCell(0).setCellValue(value);
        }

        // Define named range
        Name namedRange = namedRangesSheet.getWorkbook().createName();
        namedRange.setNameName(name);
        namedRange.setRefersToFormula("'" + "NamedRanges" + "'!$A$" + (startRow - values.size() + 1) + ":$A$" + startRow);
    }

    private void setDropdownForColumn(XSSFDataValidationHelper validationHelper, Sheet targetSheet, int targetColumn, String range) {
        int firstRow = 1;
        int lastRow = 1000;
        if (lastRow < firstRow) {
            lastRow = firstRow; // Avoid invalid range if the sheet has no data
        }

        DataValidationConstraint constraint = null;
        CellRangeAddressList addressList = new CellRangeAddressList(firstRow, lastRow, targetColumn, targetColumn);
            constraint = validationHelper.createFormulaListConstraint("INDIRECT(\"" + range + "\")");
        DataValidation dataValidation = validationHelper.createValidation(constraint, addressList);

        if (dataValidation instanceof XSSFDataValidation) {
            dataValidation.setSuppressDropDownArrow(true);
            dataValidation.setShowErrorBox(true);
        }
        targetSheet.addValidationData(dataValidation);
    }

    private void setDependentDropdownForColumn(XSSFDataValidationHelper validationHelper, Sheet targetSheet, int targetColumn, String dependentRangePrefix) {
        // Get the number of rows in the sheet
        int lastRow = targetSheet.getLastRowNum();

        // Optionally set a minimum number of rows to apply validation if the sheet is initially empty
        if (lastRow < 1) {
            lastRow = 1000; // or another reasonable default value
        }

        // Iterate over rows to apply data validation
        for (int rowIdx = 1; rowIdx <= lastRow; rowIdx++) {
            // Construct the formula for the data validation constraint using the prefix
            String formula = "INDIRECT(\"" + dependentRangePrefix + "\" & SUBSTITUTE($B" + (rowIdx + 1) + ", \" \", \"_\"))";
            if (dependentRangePrefix.equalsIgnoreCase("SubThemes_")) {
                formula = "INDIRECT(\"" + dependentRangePrefix + "\" & SUBSTITUTE($C" + (rowIdx + 1) + ", \" \", \"_\"))";
            }
            DataValidationConstraint constraint = validationHelper.createFormulaListConstraint(formula);

            // Define the range where the data validation should apply
            CellRangeAddressList addressList = new CellRangeAddressList(rowIdx, rowIdx, targetColumn, targetColumn);
            DataValidation dataValidation = validationHelper.createValidation(constraint, addressList);

            // Customize data validation settings if needed
            if (dataValidation instanceof XSSFDataValidation) {
                dataValidation.setSuppressDropDownArrow(true);
                dataValidation.setShowErrorBox(true);
            }

            // Add validation to the sheet
            targetSheet.addValidationData(dataValidation);
        }
    }


    private String makeNameSafe(String name) {
        return name.replaceAll("[^A-Za-z0-9_]", "_");
    }

    private void setColumnWidths(Sheet sheet) {
        for (int i = 0; i < sheet.getRow(0).getPhysicalNumberOfCells(); i++) {
            sheet.autoSizeColumn(i);
        }
    }
}

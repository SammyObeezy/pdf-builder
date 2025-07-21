import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.logging.Level;

public class Main {

    private static final String OUTPUT_DIRECTORY = "out";
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public static void main(String[] args) {
        try {
            Main generator = new Main();

            String p9Output = generator.generateP9PDF(
                    "src/main/resources/p-nine-report.html",
                    "src/main/resources/data/p9-data.json"
            );
            System.out.println("P9 PDF generated: " + p9Output);

            String statementOutput = generator.generateAccountStatementPDF(
                    "src/main/resources/account-statements.html",
                    "src/main/resources/data/account-statements-data.json"
            );
            System.out.println("Account Statement PDF generated: " + statementOutput);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public String generateP9PDF(String templatePath, String dataPath) throws IOException {
        String htmlContent = readTemplate(templatePath);
        JsonNode data = objectMapper.readTree(Paths.get(dataPath).toFile());

        htmlContent = htmlContent.replace("{{YEAR}}", data.get("year").asText());
        htmlContent = htmlContent.replace("{{EMPLOYER_NAME}}", data.get("employer").get("name").asText());
        htmlContent = htmlContent.replace("{{EMPLOYER_PIN}}", data.get("employer").get("pin").asText());
        htmlContent = htmlContent.replace("{{EMPLOYEE_LASTNAME}}", data.get("employee").get("lastName").asText());
        htmlContent = htmlContent.replace("{{EMPLOYEE_FIRSTNAME}}", data.get("employee").get("firstName").asText());
        htmlContent = htmlContent.replace("{{EMPLOYEE_PIN}}", data.get("employee").get("pin").asText());
        htmlContent = htmlContent.replace("{{TOTAL_CHARGEABLE_PAY}}", formatCurrency(data.get("totals").get("chargeablePay").asDouble()));
        htmlContent = htmlContent.replace("{{TOTAL_TAX}}", formatCurrency(data.get("totals").get("payeTax").asDouble()));

        String monthlyRows = generateMonthlyRows(data.get("monthlyData"));
        htmlContent = htmlContent.replace("{{MONTHLY_DATA_ROWS}}", monthlyRows);

        String outputPath = generateOutputPath("P9_Report");
        generatePdfFromHtml(htmlContent, outputPath);
        return outputPath;
    }

    public String generateAccountStatementPDF(String templatePath, String dataPath) throws IOException {
        String htmlContent = readTemplate(templatePath);
        JsonNode data = objectMapper.readTree(Paths.get(dataPath).toFile());

        String base64Logo = getBase64Logo("src/main/resources/assets/ukulima-sacco-logo.png");
        if (base64Logo != null) {
            htmlContent = htmlContent.replace("assets/ukulima-sacco-logo.png", base64Logo);
        }

        htmlContent = htmlContent.replace("{{DATE_ISSUED}}", data.get("dateIssued").asText());
        htmlContent = htmlContent.replace("{{CUSTOMER_NAME}}", data.get("customer").get("name").asText());
        htmlContent = htmlContent.replace("{{CUSTOMER_PHONE}}", data.get("customer").get("phone").asText());
        htmlContent = htmlContent.replace("{{STATEMENT_PERIOD}}", data.get("statementPeriod").asText());
        htmlContent = htmlContent.replace("{{ACCOUNT_NUMBER}}", data.get("account").get("number").asText());
        htmlContent = htmlContent.replace("{{OPENING_BALANCE}}", data.get("balances").get("opening").asText());
        htmlContent = htmlContent.replace("{{TOTAL_CREDITS}}", data.get("balances").get("totalCredits").asText());
        htmlContent = htmlContent.replace("{{TOTAL_DEBITS}}", data.get("balances").get("totalDebits").asText());
        htmlContent = htmlContent.replace("{{CLOSING_BALANCE}}", data.get("balances").get("closing").asText());
        htmlContent = htmlContent.replace("{{PAGE_NUMBER}}", data.get("pageNumber").asText());

        String transactionRows = generateTransactionRows(data.get("transactions"));
        htmlContent = htmlContent.replace("{{TRANSACTIONS}}", transactionRows);

        String outputPath = generateOutputPath("Account_Statement");
        generatePdfFromHtml(htmlContent, outputPath);
        return outputPath;
    }

    private String generateMonthlyRows(JsonNode monthlyData) {
        StringBuilder rows = new StringBuilder();

        for (JsonNode month : monthlyData) {
            rows.append("<tr>");
            rows.append("<td>").append(month.get("month").asText()).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("basicSalary").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("benefitsNonCash").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("valueOfQuarters").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("totalGrossPay").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("retirementContribution30Percent").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("retirementContributionActual").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("retirementContributionFixed").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("ownerOccupiedInterest").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("totalRetirementAndInterest").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("chargeablePay").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("taxCharged").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("personalRelief").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("insuranceRelief").asDouble())).append("</td>");
            rows.append("<td>").append(formatCurrency(month.get("payeTax").asDouble())).append("</td>");
            rows.append("</tr>");
        }

        JsonNode totals = objectMapper.createObjectNode();
        double totalBasicSalary = 0, totalBenefits = 0, totalQuarters = 0, totalGross = 0;
        double totalRetirement30 = 0, totalRetirementActual = 0, totalRetirementFixed = 0;
        double totalOwnerOccupied = 0, totalRetirementInterest = 0, totalChargeable = 0;
        double totalTaxCharged = 0, totalPersonalRelief = 0, totalInsuranceRelief = 0, totalPaye = 0;

        for (JsonNode month : monthlyData) {
            totalBasicSalary += month.get("basicSalary").asDouble();
            totalBenefits += month.get("benefitsNonCash").asDouble();
            totalQuarters += month.get("valueOfQuarters").asDouble();
            totalGross += month.get("totalGrossPay").asDouble();
            totalRetirement30 += month.get("retirementContribution30Percent").asDouble();
            totalRetirementActual += month.get("retirementContributionActual").asDouble();
            totalRetirementFixed += month.get("retirementContributionFixed").asDouble();
            totalOwnerOccupied += month.get("ownerOccupiedInterest").asDouble();
            totalRetirementInterest += month.get("totalRetirementAndInterest").asDouble();
            totalChargeable += month.get("chargeablePay").asDouble();
            totalTaxCharged += month.get("taxCharged").asDouble();
            totalPersonalRelief += month.get("personalRelief").asDouble();
            totalInsuranceRelief += month.get("insuranceRelief").asDouble();
            totalPaye += month.get("payeTax").asDouble();
        }

        rows.append("<tr style='font-weight: bold; background-color: #f0f0f0;'>");
        rows.append("<td>TOTAL</td>");
        rows.append("<td>").append(formatCurrency(totalBasicSalary)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalBenefits)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalQuarters)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalGross)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalRetirement30)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalRetirementActual)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalRetirementFixed)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalOwnerOccupied)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalRetirementInterest)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalChargeable)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalTaxCharged)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalPersonalRelief)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalInsuranceRelief)).append("</td>");
        rows.append("<td>").append(formatCurrency(totalPaye)).append("</td>");
        rows.append("</tr>");

        return rows.toString();
    }

    private String generateTransactionRows(JsonNode transactions) {
        StringBuilder rows = new StringBuilder();

        for (JsonNode transaction : transactions) {
            rows.append("<tr>");
            rows.append("<td class='date-col'>").append(transaction.get("date").asText()).append("</td>");
            rows.append("<td class='doc-col'>").append(transaction.get("docNo").asText()).append("</td>");
            rows.append("<td class='description-col'>").append(transaction.get("description").asText()).append("</td>");

            String credit = transaction.get("credit").asText();
            String debit = transaction.get("debit").asText();

            if (!credit.isEmpty()) {
                rows.append("<td class='amount-col table-credit'>").append(credit).append("</td>");
                rows.append("<td class='amount-col'></td>");
            } else {
                rows.append("<td class='amount-col'></td>");
                rows.append("<td class='amount-col table-debit'>").append(debit).append("</td>");
            }

            rows.append("<td class='balance-col balance-amount'>").append(transaction.get("balance").asText()).append("</td>");
            rows.append("</tr>");
        }

        return rows.toString();
    }

    private String readTemplate(String templatePath) throws IOException {
        Path path = Paths.get(templatePath);
        if (!Files.exists(path)) {
            throw new IOException("Template not found: " + templatePath);
        }
        return Files.readString(path);
    }

    private void generatePdfFromHtml(String htmlContent, String outputPath) throws IOException {
        ensureOutputDirectoryExists();
        XRLog.setLevel(XRLog.EXCEPTION, Level.WARNING);

        try (OutputStream os = new FileOutputStream(outputPath)) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            builder.withHtmlContent(htmlContent, null);
            builder.toStream(os);
            builder.withProducer("KRA Document Generator");
            builder.run();
        } catch (Exception e) {
            throw new IOException("PDF generation failed: " + e.getMessage(), e);
        }
    }

    private void ensureOutputDirectoryExists() throws IOException {
        Path outputDir = Paths.get(OUTPUT_DIRECTORY);
        if (!Files.exists(outputDir)) {
            Files.createDirectories(outputDir);
        }
    }

    private String generateOutputPath(String prefix) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String fileName = String.format("%s_%s.pdf", prefix, timestamp);
        return Paths.get(OUTPUT_DIRECTORY, fileName).toString();
    }

    private String formatCurrency(double value) {
        return String.format("%.2f", value);
    }

    private String getBase64Logo(String logoPath) {
        try {
            Path path = Paths.get(logoPath);
            if (!Files.exists(path)) {
                System.out.println("Logo file not found: " + logoPath + ". PDF will generate without logo.");
                return null;
            }

            byte[] logoBytes = Files.readAllBytes(path);
            String base64String = Base64.getEncoder().encodeToString(logoBytes);
            String fileExtension = getFileExtension(logoPath).toLowerCase();

            String mimeType;
            switch (fileExtension) {
                case "png":
                    mimeType = "image/png";
                    break;
                case "jpg":
                case "jpeg":
                    mimeType = "image/jpeg";
                    break;
                case "gif":
                    mimeType = "image/gif";
                    break;
                case "svg":
                    mimeType = "image/svg+xml";
                    break;
                default:
                    mimeType = "image/png";
            }

            return "data:" + mimeType + ";base64," + base64String;

        } catch (IOException e) {
            System.err.println("Error reading logo file: " + e.getMessage());
            return null;
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex == -1 ? "" : fileName.substring(dotIndex + 1);
    }
}

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

/** Loads and saves local CSV/properties data. No database or network is used. */
public class DataStore {
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");
    private final Path dataDirectory = Path.of("data");
    private final Path transactionsFile = dataDirectory.resolve("transactions.csv");
    private final Path falseAlarmsFile = dataDirectory.resolve("false-alarms.csv");
    private final Path settingsFile = dataDirectory.resolve("settings.properties");

    public List<Transaction> loadTransactions() throws IOException {
        List<Transaction> transactions = new ArrayList<>();
        if (!Files.exists(transactionsFile)) return transactions;
        List<String> lines = Files.readAllLines(transactionsFile, StandardCharsets.UTF_8);
        Set<String> ids = new HashSet<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.isBlank()) continue;
            try {
                List<String> fields = parseCsv(line);
                if (fields.size() != 5) throw new IllegalArgumentException("expected five columns");
                String id = fields.get(0).trim();
                if (id.isEmpty() || !ids.add(id.toLowerCase(java.util.Locale.ROOT))) {
                    throw new IllegalArgumentException("empty or duplicate transaction ID");
                }
                transactions.add(new Transaction(
                        id,
                        fields.get(1).trim(),
                        new BigDecimal(fields.get(2).trim()),
                        LocalTime.parse(fields.get(3).trim(), TIME_FORMAT),
                        fields.get(4).trim()));
            } catch (RuntimeException e) {
                System.out.println("Warning: skipped invalid transaction row " + (i + 1) + ".");
            }
        }
        return transactions;
    }

    public void saveTransactions(List<Transaction> transactions) throws IOException {
        StringBuilder csv = new StringBuilder("transactionId,userId,amount,time,city\n");
        for (Transaction transaction : transactions) {
            csv.append(csv(transaction.getTransactionId())).append(',')
                    .append(csv(transaction.getUserId())).append(',')
                    .append(csv(transaction.getAmount().toPlainString())).append(',')
                    .append(csv(transaction.getTime().format(TIME_FORMAT))).append(',')
                    .append(csv(transaction.getCity())).append('\n');
        }
        writeFile(transactionsFile, csv.toString());
    }

    public Map<String, Set<FraudRule>> loadFalseAlarms() throws IOException {
        Map<String, Set<FraudRule>> reviews = new LinkedHashMap<>();
        if (!Files.exists(falseAlarmsFile)) return reviews;
        List<String> lines = Files.readAllLines(falseAlarmsFile, StandardCharsets.UTF_8);
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            try {
                List<String> fields = parseCsv(lines.get(i));
                if (fields.size() != 2) throw new IllegalArgumentException("expected two columns");
                String transactionId = fields.get(0);
                FraudRule rule = FraudRule.valueOf(fields.get(1));
                reviews.computeIfAbsent(transactionId, ignored -> new TreeSet<>()).add(rule);
            } catch (RuntimeException e) {
                System.out.println("Warning: skipped invalid false-alarm row " + (i + 1) + ".");
            }
        }
        return reviews;
    }

    public void saveFalseAlarms(Map<String, Set<FraudRule>> reviews) throws IOException {
        StringBuilder csv = new StringBuilder("transactionId,rule\n");
        for (Map.Entry<String, Set<FraudRule>> entry : reviews.entrySet()) {
            for (FraudRule rule : entry.getValue()) {
                csv.append(csv(entry.getKey())).append(',').append(csv(rule.name())).append('\n');
            }
        }
        writeFile(falseAlarmsFile, csv.toString());
    }

    public FraudSettings loadSettings() throws IOException {
        FraudSettings settings = new FraudSettings();
        if (!Files.exists(settingsFile)) return settings;
        Properties values = new Properties();
        try (var reader = Files.newBufferedReader(settingsFile, StandardCharsets.UTF_8)) {
            values.load(reader);
        }
        try {
            settings.setAmountMultiplier(new BigDecimal(values.getProperty("amountMultiplier", "5")));
            settings.setMaxTransactionsInWindow(Integer.parseInt(values.getProperty("maxTransactionsInWindow", "3")));
            settings.setWindowMinutes(Integer.parseInt(values.getProperty("windowMinutes", "2")));
        } catch (RuntimeException e) {
            System.out.println("Warning: invalid rule settings; using the CP004 defaults.");
            return new FraudSettings();
        }
        return settings;
    }

    public void saveSettings(FraudSettings settings) throws IOException {
        Properties values = new Properties();
        values.setProperty("amountMultiplier", settings.getAmountMultiplier().toPlainString());
        values.setProperty("maxTransactionsInWindow", Integer.toString(settings.getMaxTransactionsInWindow()));
        values.setProperty("windowMinutes", Integer.toString(settings.getWindowMinutes()));
        StringBuilder text = new StringBuilder();
        for (String key : List.of("amountMultiplier", "maxTransactionsInWindow", "windowMinutes")) {
            text.append(key).append('=').append(values.getProperty(key)).append('\n');
        }
        writeFile(settingsFile, text.toString());
    }

    private void writeFile(Path path, String contents) throws IOException {
        Files.createDirectories(dataDirectory);
        Path temporary = path.resolveSibling(path.getFileName() + ".tmp");
        Files.writeString(temporary, contents, StandardCharsets.UTF_8);
        try {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private String csv(String value) {
        String safe = value == null ? "" : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private List<String> parseCsv(String line) {
        List<String> values = new ArrayList<>();
        StringBuilder value = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char current = line.charAt(i);
            if (current == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    value.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (current == ',' && !quoted) {
                values.add(value.toString());
                value.setLength(0);
            } else {
                value.append(current);
            }
        }
        if (quoted) throw new IllegalArgumentException("unclosed CSV quote");
        values.add(value.toString());
        return values;
    }
}

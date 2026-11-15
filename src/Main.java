import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Console application for recording UPI transactions and explaining risk flags. */
public class Main {
    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm").withResolverStyle(ResolverStyle.STRICT);

    private final Scanner scanner = new Scanner(System.in);
    private final DataStore dataStore = new DataStore();
    private final List<Transaction> transactions = new ArrayList<>();
    private final Map<String, Set<FraudRule>> falseAlarms = new LinkedHashMap<>();
    private FraudSettings settings = new FraudSettings();
    private FraudDetector detector = new FraudDetector(settings);
    private Map<String, List<FraudFlag>> flags = new LinkedHashMap<>();
    private boolean detectionCurrent;

    public static void main(String[] args) {
        new Main().run();
    }

    private void run() {
        loadSavedData();
        System.out.println("========================================");
        System.out.println("  FRAUDSHIELD - UPI RISK ENGINE");
        System.out.println("========================================");
        System.out.println("Data is saved locally in the project's data folder.");

        boolean running = true;
        while (running) {
            showMenu();
            switch (readNonEmpty("Choose an option: ")) {
                case "1" -> recordTransaction();
                case "2" -> viewTransactions();
                case "3" -> detectFraud();
                case "4" -> viewFlaggedTransactions();
                case "5" -> viewUserRiskCounts();
                case "6" -> configureRules();
                case "7" -> markFalseAlarm();
                case "8" -> showFalseAlarmReport();
                case "9" -> {
                    saveAllData();
                    System.out.println("Your data is saved. Goodbye!");
                    running = false;
                }
                default -> System.out.println("Please choose a number from 1 to 9.");
            }
        }
    }

    private void showMenu() {
        System.out.println("\n--------------- MENU ---------------");
        System.out.println("1. Record a transaction");
        System.out.println("2. View all transactions");
        System.out.println("3. Run fraud detection");
        System.out.println("4. View flagged transactions and reasons");
        System.out.println("5. View risk counts per user");
        System.out.println("6. Change fraud-rule thresholds");
        System.out.println("7. Mark a rule flag as a false alarm");
        System.out.println("8. View false-alarm rates by rule");
        System.out.println("9. Save and exit");
        System.out.println("------------------------------------");
    }

    private void loadSavedData() {
        try {
            transactions.addAll(dataStore.loadTransactions());
            falseAlarms.putAll(dataStore.loadFalseAlarms());
            settings = dataStore.loadSettings();
            detector = new FraudDetector(settings);
            if (!transactions.isEmpty()) {
                System.out.println("Loaded " + transactions.size() + " saved transaction(s).");
            }
        } catch (Exception e) {
            System.out.println("Could not load saved data: " + e.getMessage());
            System.out.println("The app will continue with the data it could read.");
        }
    }

    private void recordTransaction() {
        System.out.println("\n--- Record transaction ---");
        String id = readNonEmpty("Transaction ID: ");
        if (findTransaction(id) != null) {
            System.out.println("That transaction ID is already in use.");
            return;
        }

        String user = readNonEmpty("User ID: ");
        BigDecimal amount = readPositiveAmount("Amount (Rs.): ");
        LocalTime time = readTime("Time (HH:mm): ");
        String city = readNonEmpty("City: ");
        transactions.add(new Transaction(id, user, amount, time, city));
        detectionCurrent = false;
        saveTransactions();
        System.out.println("Transaction saved.");
    }

    private void viewTransactions() {
        System.out.println("\n--- All transactions ---");
        if (transactions.isEmpty()) {
            System.out.println("No transactions yet. Choose 1 to record one.");
            return;
        }
        for (Transaction transaction : transactions) {
            transaction.display();
        }
    }

    private void detectFraud() {
        refreshDetection();
        int flaggedTransactions = 0;
        int totalFlags = 0;
        for (List<FraudFlag> transactionFlags : flags.values()) {
            if (!transactionFlags.isEmpty()) flaggedTransactions++;
            totalFlags += transactionFlags.size();
        }
        System.out.println("\n--- Detection complete ---");
        System.out.println("Transactions checked: " + transactions.size());
        System.out.println("Flagged transactions: " + flaggedTransactions);
        System.out.println("Rule flags raised: " + totalFlags);
        System.out.println("Choose 4 to see the explanation for each flag.");
    }

    private void viewFlaggedTransactions() {
        refreshDetection();
        System.out.println("\n--- Flagged transactions ---");
        boolean found = false;
        for (Transaction transaction : transactions) {
            List<FraudFlag> transactionFlags = flags.get(transaction.getTransactionId());
            if (transactionFlags == null || transactionFlags.isEmpty()) continue;
            found = true;
            transaction.display();
            for (FraudFlag flag : transactionFlags) {
                boolean falseAlarm = isFalseAlarm(transaction.getTransactionId(), flag.getRule());
                System.out.println("  - " + flag.getReason()
                        + (falseAlarm ? " [marked false alarm]" : ""));
            }
        }
        if (!found) System.out.println("No transactions are currently flagged.");
    }

    private void viewUserRiskCounts() {
        refreshDetection();
        Map<String, Integer> activeCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, Integer> falseAlarmCounts = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Transaction transaction : transactions) {
            activeCounts.putIfAbsent(transaction.getUserId(), 0);
            falseAlarmCounts.putIfAbsent(transaction.getUserId(), 0);
            List<FraudFlag> transactionFlags = flags.get(transaction.getTransactionId());
            if (transactionFlags == null || transactionFlags.isEmpty()) continue;
            boolean hasActiveFlag = false;
            for (FraudFlag flag : transactionFlags) {
                if (!isFalseAlarm(transaction.getTransactionId(), flag.getRule())) {
                    hasActiveFlag = true;
                }
            }
            if (hasActiveFlag) {
                activeCounts.put(transaction.getUserId(), activeCounts.get(transaction.getUserId()) + 1);
            } else {
                falseAlarmCounts.put(transaction.getUserId(), falseAlarmCounts.get(transaction.getUserId()) + 1);
            }
        }

        System.out.println("\n--- User risk report ---");
        if (activeCounts.isEmpty()) {
            System.out.println("No user records yet.");
            return;
        }
        for (String user : activeCounts.keySet()) {
            System.out.println(user + " | Active flagged transactions: " + activeCounts.get(user)
                    + " | Marked false alarm: " + falseAlarmCounts.get(user));
        }
    }

    private void configureRules() {
        System.out.println("\n--- Rule thresholds ---");
        System.out.println("Current amount multiplier: " + settings.getAmountMultiplier());
        System.out.println("Current rapid-transaction limit: more than "
                + settings.getMaxTransactionsInWindow() + " in "
                + settings.getWindowMinutes() + " minute(s)");
        System.out.println("Press Enter to keep any current value.");

        BigDecimal multiplier = readDecimalOrKeep(
                "Amount multiplier (must be positive): ", settings.getAmountMultiplier());
        int maxTransactions = readIntOrKeep(
                "Maximum transactions allowed in the window: ", settings.getMaxTransactionsInWindow());
        int windowMinutes = readIntOrKeep(
                "Window length in minutes (1-1440): ", settings.getWindowMinutes());
        try {
            FraudSettings updatedSettings = new FraudSettings();
            updatedSettings.setAmountMultiplier(multiplier);
            updatedSettings.setMaxTransactionsInWindow(maxTransactions);
            updatedSettings.setWindowMinutes(windowMinutes);
            settings = updatedSettings;
            detector = new FraudDetector(settings);
            detectionCurrent = false;
            saveSettings();
            System.out.println("Thresholds saved. Detection will use them next time it runs.");
        } catch (IllegalArgumentException e) {
            System.out.println("No changes saved: " + e.getMessage());
        }
    }

    private void markFalseAlarm() {
        refreshDetection();
        System.out.println("\n--- Mark a false alarm ---");
        if (flags.values().stream().allMatch(List::isEmpty)) {
            System.out.println("There are no flags to review. Run detection after recording transactions.");
            return;
        }
        viewFlaggedTransactions();
        String id = readNonEmpty("Enter the transaction ID to review: ");
        Transaction transaction = findTransaction(id);
        if (transaction == null) {
            System.out.println("Transaction ID not found.");
            return;
        }
        List<FraudFlag> transactionFlags = flags.get(transaction.getTransactionId());
        if (transactionFlags == null || transactionFlags.isEmpty()) {
            System.out.println("That transaction has no current fraud flags.");
            return;
        }
        for (int i = 0; i < transactionFlags.size(); i++) {
            FraudFlag flag = transactionFlags.get(i);
            String state = isFalseAlarm(transaction.getTransactionId(), flag.getRule())
                    ? " (already marked false alarm)" : "";
            System.out.println((i + 1) + ". " + flag.getRule().getLabel() + ": " + flag.getReason() + state);
        }
        int choice = readIntInRange("Choose the rule flag to mark (0 to cancel): ",
                0, transactionFlags.size());
        if (choice == 0) return;
        FraudRule rule = transactionFlags.get(choice - 1).getRule();
        if (isFalseAlarm(transaction.getTransactionId(), rule)) {
            System.out.println("That flag is already marked as a false alarm.");
            return;
        }
        falseAlarms.computeIfAbsent(transaction.getTransactionId(), ignored -> new TreeSet<>()).add(rule);
        saveFalseAlarms();
        System.out.println("False alarm recorded. Choose 8 to view the updated rate for this rule.");
    }

    private void showFalseAlarmReport() {
        refreshDetection();
        Map<FraudRule, Integer> totals = new EnumMap<>(FraudRule.class);
        Map<FraudRule, Integer> falseCounts = new EnumMap<>(FraudRule.class);
        for (FraudRule rule : FraudRule.values()) {
            totals.put(rule, 0);
            falseCounts.put(rule, 0);
        }
        for (Map.Entry<String, List<FraudFlag>> entry : flags.entrySet()) {
            for (FraudFlag flag : entry.getValue()) {
                FraudRule rule = flag.getRule();
                totals.put(rule, totals.get(rule) + 1);
                if (isFalseAlarm(entry.getKey(), rule)) {
                    falseCounts.put(rule, falseCounts.get(rule) + 1);
                }
            }
        }
        System.out.println("\n--- False-alarm rate by rule ---");
        for (FraudRule rule : FraudRule.values()) {
            int total = totals.get(rule);
            int falseCount = falseCounts.get(rule);
            String rate = total == 0 ? "N/A" : String.format(Locale.ROOT, "%.1f%%", 100.0 * falseCount / total);
            System.out.println(rule.getLabel() + ": " + falseCount + "/" + total + " false alarms (" + rate + ")");
        }
        System.out.println("The rate uses current rule flags as its denominator.");
    }

    private void refreshDetection() {
        if (!detectionCurrent) {
            flags = detector.detect(transactions);
            detectionCurrent = true;
        }
    }

    private boolean isFalseAlarm(String transactionId, FraudRule rule) {
        Set<FraudRule> reviewedRules = falseAlarms.get(transactionId);
        return reviewedRules != null && reviewedRules.contains(rule);
    }

    private Transaction findTransaction(String id) {
        for (Transaction transaction : transactions) {
            if (transaction.getTransactionId().equalsIgnoreCase(id.trim())) return transaction;
        }
        return null;
    }

    private String readNonEmpty(String prompt) {
        while (true) {
            System.out.print(prompt);
            String value = scanner.nextLine().trim();
            if (!value.isEmpty()) return value;
            System.out.println("This value cannot be empty. Please try again.");
        }
    }

    private BigDecimal readPositiveAmount(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                BigDecimal value = new BigDecimal(scanner.nextLine().trim());
                if (value.compareTo(BigDecimal.ZERO) > 0) return value;
                System.out.println("Amount must be greater than zero.");
            } catch (NumberFormatException e) {
                System.out.println("Enter a valid number, for example 250 or 250.50.");
            }
        }
    }

    private LocalTime readTime(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return LocalTime.parse(scanner.nextLine().trim(), TIME_FORMAT);
            } catch (DateTimeParseException e) {
                System.out.println("Use 24-hour time in HH:mm format, for example 14:30.");
            }
        }
    }

    private BigDecimal readDecimalOrKeep(String prompt, BigDecimal current) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return current;
            try {
                BigDecimal value = new BigDecimal(input);
                if (value.compareTo(BigDecimal.ZERO) > 0) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a positive number or press Enter to keep the current value.");
        }
    }

    private int readIntOrKeep(String prompt, int current) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            if (input.isEmpty()) return current;
            try {
                int value = Integer.parseInt(input);
                if (value > 0) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a positive whole number or press Enter to keep the current value.");
        }
    }

    private int readIntInRange(String prompt, int min, int max) {
        while (true) {
            System.out.print(prompt);
            try {
                int value = Integer.parseInt(scanner.nextLine().trim());
                if (value >= min && value <= max) return value;
            } catch (NumberFormatException ignored) { }
            System.out.println("Enter a number from " + min + " to " + max + ".");
        }
    }

    private void saveTransactions() {
        try { dataStore.saveTransactions(transactions); }
        catch (Exception e) { System.out.println("Could not save transactions: " + e.getMessage()); }
    }

    private void saveSettings() {
        try { dataStore.saveSettings(settings); }
        catch (Exception e) { System.out.println("Could not save settings: " + e.getMessage()); }
    }

    private void saveFalseAlarms() {
        try { dataStore.saveFalseAlarms(falseAlarms); }
        catch (Exception e) { System.out.println("Could not save reviews: " + e.getMessage()); }
    }

    private void saveAllData() {
        saveTransactions();
        saveSettings();
        saveFalseAlarms();
    }
}

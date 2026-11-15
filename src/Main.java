
import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.format.DateTimeParseException;
import java.util.*;

public class Main {

    private static final Scanner scanner = new Scanner(System.in);

    private static final DateTimeFormatter TIME_FORMAT =
            DateTimeFormatter.ofPattern("HH:mm")
                    .withResolverStyle(ResolverStyle.STRICT);

    private static final List<Transaction> transactions =
            new ArrayList<>();

    private static final FraudDetector detector =
            new FraudDetector();

    private static Map<String, List<String>> fraudFlags =
            new LinkedHashMap<>();

    private static boolean detectionCompleted = false;

    public static void main(String[] args) {

        System.out.println("====================================");
        System.out.println("       WELCOME TO FRAUDSHIELD");
        System.out.println("     UPI TRANSACTION RISK ENGINE");
        System.out.println("====================================");

        while (true) {

            showMenu();

            System.out.print("Enter your choice: ");

            String choice = scanner.nextLine().trim();

            switch (choice) {

                case "1":
                    recordTransaction();
                    break;

                case "2":
                    viewTransactions();
                    break;

                case "3":
                    detectFraud();
                    break;

                case "4":
                    viewFlaggedTransactions();
                    break;

                case "5":
                    viewUserRiskCounts();
                    break;

                case "6":
                    System.out.println("Thank you for using FraudShield!");
                    return;

                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private static void showMenu() {

        System.out.println("\n========== MAIN MENU ==========");
        System.out.println("1. Record Transaction");
        System.out.println("2. View All Transactions");
        System.out.println("3. Detect Suspicious Transactions");
        System.out.println("4. View Flagged Transactions and Reasons");
        System.out.println("5. View Risk Count Per User");
        System.out.println("6. Exit");
        System.out.println("================================");
    }

    // FEATURE 1: RECORD TRANSACTIONS

    private static void recordTransaction() {

        System.out.println("\n--- RECORD NEW TRANSACTION ---");

        String id = readNonEmpty("Enter Transaction ID: ");

        if (findTransaction(id) != null) {
            System.out.println("Error: Transaction ID already exists.");
            return;
        }

        String user = readNonEmpty("Enter User ID: ");

        BigDecimal amount = readAmount();

        LocalTime time = readTime();

        String city = readNonEmpty("Enter City: ");

        Transaction transaction =
                new Transaction(id, user, amount, time, city);

        transactions.add(transaction);

        // Previous fraud results are now outdated
        detectionCompleted = false;

        System.out.println("\nTransaction recorded successfully!");
        transaction.display();
    }

    private static String readNonEmpty(String message) {

        while (true) {

            System.out.print(message);

            String value = scanner.nextLine().trim();

            if (!value.isEmpty()) {
                return value;
            }

            System.out.println("Input cannot be empty.");
        }
    }

    private static BigDecimal readAmount() {

        while (true) {

            System.out.print("Enter Amount (Rs.): ");

            try {

                BigDecimal amount =
                        new BigDecimal(scanner.nextLine().trim());

                if (amount.compareTo(BigDecimal.ZERO) > 0) {
                    return amount;
                }

                System.out.println("Amount must be greater than zero.");

            } catch (NumberFormatException e) {

                System.out.println("Please enter a valid amount.");
            }
        }
    }

    private static LocalTime readTime() {

        while (true) {

            System.out.print("Enter Time (HH:mm): ");

            String input = scanner.nextLine().trim();

            try {

                return LocalTime.parse(input, TIME_FORMAT);

            } catch (DateTimeParseException e) {

                System.out.println("Invalid time. Example: 14:30");
            }
        }
    }

    // FEATURE 2: VIEW ALL TRANSACTIONS

    private static void viewTransactions() {

        System.out.println("\n--- ALL TRANSACTIONS ---");

        if (transactions.isEmpty()) {
            System.out.println("No transactions recorded yet.");
            return;
        }

        for (Transaction t : transactions) {
            t.display();
        }
    }

    // FEATURE 3: DETECT SUSPICIOUS TRANSACTIONS

    private static void detectFraud() {

        fraudFlags = detector.detect(transactions);

        detectionCompleted = true;

        int flaggedCount = 0;

        for (List<String> reasons : fraudFlags.values()) {

            if (!reasons.isEmpty()) {
                flaggedCount++;
            }
        }

        System.out.println("\n--- FRAUD DETECTION RESULT ---");

        System.out.println("Total Transactions: " + transactions.size());

        System.out.println("Flagged Transactions: " + flaggedCount);

        System.out.println("Normal Transactions: "
                + (transactions.size() - flaggedCount));

        System.out.println("Detection completed successfully.");
    }

    // FEATURE 4: VIEW FLAGGED TRANSACTIONS

    private static void viewFlaggedTransactions() {

        ensureDetection();

        System.out.println("\n--- FLAGGED TRANSACTIONS ---");

        boolean found = false;

        for (Transaction t : transactions) {

            List<String> reasons =
                    fraudFlags.get(t.getTransactionId());

            if (reasons != null && !reasons.isEmpty()) {

                found = true;

                System.out.println("\n------------------------------");

                t.display();

                System.out.println("Fraud Reasons:");

                for (String reason : reasons) {
                    System.out.println(" - " + reason);
                }
            }
        }

        if (!found) {
            System.out.println("No suspicious transactions found.");
        }
    }

    // FEATURE 5: RISK COUNT PER USER

    private static void viewUserRiskCounts() {

        ensureDetection();

        Map<String, Integer> riskCounts = new LinkedHashMap<>();

        // Initialize every user with zero flagged transactions
        for (Transaction t : transactions) {

            riskCounts.putIfAbsent(t.getUserId(), 0);
        }

        // Count each flagged transaction only once
        for (Transaction t : transactions) {

            List<String> reasons =
                    fraudFlags.get(t.getTransactionId());

            if (reasons != null && !reasons.isEmpty()) {

                riskCounts.put(
                        t.getUserId(),
                        riskCounts.get(t.getUserId()) + 1
                );
            }
        }

        System.out.println("\n--- USER RISK REPORT ---");

        if (riskCounts.isEmpty()) {
            System.out.println("No user records available.");
            return;
        }

        for (Map.Entry<String, Integer> entry :
                riskCounts.entrySet()) {

            System.out.println(
                    "User: " + entry.getKey()
                            + " | Flagged Transactions: "
                            + entry.getValue()
            );
        }
    }

    private static void ensureDetection() {

        if (!detectionCompleted) {

            fraudFlags = detector.detect(transactions);

            detectionCompleted = true;
        }
    }

    private static Transaction findTransaction(String id) {

        for (Transaction t : transactions) {

            if (t.getTransactionId().equalsIgnoreCase(id)) {
                return t;
            }
        }

        return null;
    }
}

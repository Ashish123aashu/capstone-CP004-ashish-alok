
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.time.LocalTime;

public class FraudDetector {

    public Map<String, List<String>> detect(
            List<Transaction> transactions) {

        Map<String, List<String>> flags = new LinkedHashMap<>();

        // Prepare an empty reason list for every transaction
        for (Transaction t : transactions) {
            flags.put(t.getTransactionId(), new ArrayList<>());
        }

        // Previous transaction history for each user
        Map<String, BigDecimal> totalAmounts = new HashMap<>();
        Map<String, Integer> transactionCounts = new HashMap<>();
        Map<String, Set<String>> userCities = new HashMap<>();

        // RULE 1: High transaction amount
        // RULE 2: Transaction from a new city

        for (Transaction t : transactions) {

            String user = t.getUserId();
            String city = t.getCity();

            BigDecimal total = totalAmounts.getOrDefault(
                    user, BigDecimal.ZERO);

            int count = transactionCounts.getOrDefault(user, 0);

            Set<String> cities = userCities.computeIfAbsent(
                    user, k -> new HashSet<>());

            // High amount: compare against previous transactions only
            if (count > 0) {

                BigDecimal average = total.divide(
                        BigDecimal.valueOf(count),
                        2,
                        RoundingMode.HALF_UP);

                BigDecimal limit = average.multiply(
                        BigDecimal.valueOf(5));

                if (t.getAmount().compareTo(limit) > 0) {

                    addReason(flags, t.getTransactionId(),
                            "High amount: exceeds 5 times previous average (Rs. "
                                    + average.toPlainString() + ")");
                }
            }

            // New city detection
            // First transaction establishes the user's baseline city
            if (!cities.isEmpty() && !cities.contains(city)) {

                addReason(flags, t.getTransactionId(),
                        "Transaction from a new city: " + city);
            }

            // Update user history after checking the current transaction
            totalAmounts.put(user, total.add(t.getAmount()));

            transactionCounts.put(user, count + 1);

            cities.add(city);
        }

        // RULE 3: Rapid transactions
        detectRapidTransactions(transactions, flags);

        return flags;
    }

    private void detectRapidTransactions(
            List<Transaction> transactions,
            Map<String, List<String>> flags) {

        Map<String, List<Transaction>> userTransactions =
                new HashMap<>();

        // Group transactions by user
        for (Transaction t : transactions) {

            userTransactions.computeIfAbsent(
                    t.getUserId(), k -> new ArrayList<>()
            ).add(t);
        }

        for (List<Transaction> list : userTransactions.values()) {

            // Sort transactions by time
            list.sort(Comparator.comparing(Transaction::getTime));

            int n = list.size();

            // Duplicate the time list with +24 hours.
            // This handles transactions crossing midnight.
            List<Transaction> doubled = new ArrayList<>(list);
            doubled.addAll(list);

            int[] minutes = new int[2 * n];

            for (int i = 0; i < 2 * n; i++) {

                int baseMinute = doubled.get(i % n)
                        .getTime().getHour() * 60
                        + doubled.get(i % n).getTime().getMinute();

                minutes[i] = baseMinute + (i >= n ? 1440 : 0);
            }

            // Find groups of more than 3 transactions
            // within a 2-minute window
            for (int start = 0; start < n; start++) {

                int end = start;

                while (end < start + n &&
                        minutes[end] - minutes[start] <= 2) {

                    end++;
                }

                if (end - start > 3) {

                    for (int j = start; j < end; j++) {

                        Transaction t = doubled.get(j % n);

                        addReason(flags, t.getTransactionId(),
                                "Rapid transactions: more than 3 transactions within 2 minutes");
                    }
                }
            }
        }
    }

    private void addReason(
            Map<String, List<String>> flags,
            String transactionId,
            String reason) {

        List<String> reasons = flags.get(transactionId);

        if (reasons != null && !reasons.contains(reason)) {
            reasons.add(reason);
        }
    }
}

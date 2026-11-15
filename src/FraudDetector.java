import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Applies the explainable, configurable CP004 rules to recorded transactions. */
public class FraudDetector {
    private final FraudSettings settings;

    public FraudDetector(FraudSettings settings) {
        this.settings = settings;
    }

    public Map<String, List<FraudFlag>> detect(List<Transaction> transactions) {
        Map<String, List<FraudFlag>> flags = new LinkedHashMap<>();
        for (Transaction transaction : transactions) {
            flags.put(transaction.getTransactionId(), new ArrayList<>());
        }

        Map<String, BigDecimal> totals = new HashMap<>();
        Map<String, Integer> counts = new HashMap<>();
        Map<String, Set<String>> knownCities = new HashMap<>();

        for (Transaction transaction : transactions) {
            String user = transaction.getUserId().trim().toLowerCase(Locale.ROOT);
            String cityKey = transaction.getCity().trim().toLowerCase(Locale.ROOT);
            BigDecimal previousTotal = totals.getOrDefault(user, BigDecimal.ZERO);
            int previousCount = counts.getOrDefault(user, 0);
            Set<String> userCities = knownCities.computeIfAbsent(user, ignored -> new HashSet<>());

            if (previousCount > 0) {
                BigDecimal average = previousTotal.divide(
                        BigDecimal.valueOf(previousCount), 8, RoundingMode.HALF_UP);
                BigDecimal limit = average.multiply(settings.getAmountMultiplier());
                if (transaction.getAmount().compareTo(limit) > 0) {
                    addFlag(flags, transaction.getTransactionId(), FraudRule.HIGH_AMOUNT,
                            "Amount Rs. " + transaction.getAmount().toPlainString()
                                    + " is above " + settings.getAmountMultiplier().toPlainString()
                                    + "x the user's previous average of Rs. " + average.stripTrailingZeros().toPlainString());
                }
            }

            if (!userCities.isEmpty() && !userCities.contains(cityKey)) {
                addFlag(flags, transaction.getTransactionId(), FraudRule.NEW_CITY,
                        "City '" + transaction.getCity() + "' has not been used by this user before");
            }

            totals.put(user, previousTotal.add(transaction.getAmount()));
            counts.put(user, previousCount + 1);
            userCities.add(cityKey);
        }

        detectRapidTransactions(transactions, flags);
        return flags;
    }

    private void detectRapidTransactions(List<Transaction> transactions,
                                         Map<String, List<FraudFlag>> flags) {
        Map<String, List<Transaction>> byUser = new HashMap<>();
        for (Transaction transaction : transactions) {
            String userKey = transaction.getUserId().trim().toLowerCase(Locale.ROOT);
            byUser.computeIfAbsent(userKey, ignored -> new ArrayList<>()).add(transaction);
        }

        for (List<Transaction> userTransactions : byUser.values()) {
            userTransactions.sort(Comparator.comparing(Transaction::getTime)
                    .thenComparing(Transaction::getTransactionId));
            int size = userTransactions.size();
            if (size == 0) continue;

            // Duplicating the sorted day with +24 hours lets the same window cross midnight.
            int[] minuteOfDay = new int[size * 2];
            for (int i = 0; i < size * 2; i++) {
                LocalTime time = userTransactions.get(i % size).getTime();
                minuteOfDay[i] = time.getHour() * 60 + time.getMinute() + (i >= size ? 1440 : 0);
            }

            for (int start = 0; start < size; start++) {
                int end = start;
                while (end < start + size
                        && minuteOfDay[end] - minuteOfDay[start] <= settings.getWindowMinutes()) {
                    end++;
                }
                if (end - start > settings.getMaxTransactionsInWindow()) {
                    String reason = "More than " + settings.getMaxTransactionsInWindow()
                            + " transactions within " + settings.getWindowMinutes() + " minute(s)";
                    for (int i = start; i < end; i++) {
                        Transaction transaction = userTransactions.get(i % size);
                        addFlag(flags, transaction.getTransactionId(), FraudRule.RAPID_ACTIVITY, reason);
                    }
                }
            }
        }
    }

    private void addFlag(Map<String, List<FraudFlag>> flags, String transactionId,
                         FraudRule rule, String reason) {
        List<FraudFlag> transactionFlags = flags.get(transactionId);
        if (transactionFlags == null) return;
        for (FraudFlag existing : transactionFlags) {
            if (existing.getRule() == rule) return;
        }
        transactionFlags.add(new FraudFlag(rule, reason));
    }
}

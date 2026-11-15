import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.Objects;

/** One recorded UPI transaction. */
public class Transaction {
    private final String transactionId;
    private final String userId;
    private final BigDecimal amount;
    private final LocalTime time;
    private final String city;

    public Transaction(String transactionId, String userId, BigDecimal amount,
                       LocalTime time, String city) {
        this.transactionId = requireText(transactionId, "transaction ID");
        this.userId = requireText(userId, "user ID");
        this.amount = Objects.requireNonNull(amount, "amount");
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amount must be greater than zero");
        }
        this.time = Objects.requireNonNull(time, "time");
        this.city = requireText(city, "city");
    }

    private String requireText(String value, String fieldName) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(fieldName + " cannot be empty");
        }
        return value.trim();
    }

    public String getTransactionId() { return transactionId; }
    public String getUserId() { return userId; }
    public BigDecimal getAmount() { return amount; }
    public LocalTime getTime() { return time; }
    public String getCity() { return city; }

    public void display() {
        System.out.println("Transaction ID: " + transactionId
                + " | User: " + userId
                + " | Amount: Rs. " + amount.toPlainString()
                + " | Time: " + time
                + " | City: " + city);
    }
}

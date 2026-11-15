
import java.math.BigDecimal;
import java.time.LocalTime;

public class Transaction {

    private String transactionId;
    private String userId;
    private BigDecimal amount;
    private LocalTime time;
    private String city;

    public Transaction(String transactionId,
                       String userId,
                       BigDecimal amount,
                       LocalTime time,
                       String city) {

        this.transactionId = transactionId;
        this.userId = userId;
        this.amount = amount;
        this.time = time;
        this.city = city;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getUserId() {
        return userId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public LocalTime getTime() {
        return time;
    }

    public String getCity() {
        return city;
    }

    public void display() {
        System.out.println(
                "Transaction ID: " + transactionId +
                        " | User: " + userId +
                        " | Amount: Rs. " + amount.toPlainString() +
                        " | Time: " + time +
                        " | City: " + city
        );
    }
}

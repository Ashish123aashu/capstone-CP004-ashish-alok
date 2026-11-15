import java.math.BigDecimal;

/** Default rule values match CP004. Settings can be changed in the console. */
public class FraudSettings {
    private BigDecimal amountMultiplier = BigDecimal.valueOf(5);
    private int maxTransactionsInWindow = 3;
    private int windowMinutes = 2;

    public BigDecimal getAmountMultiplier() {
        return amountMultiplier;
    }

    public void setAmountMultiplier(BigDecimal amountMultiplier) {
        if (amountMultiplier == null || amountMultiplier.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("the amount multiplier must be greater than zero");
        }
        this.amountMultiplier = amountMultiplier.stripTrailingZeros();
    }

    public int getMaxTransactionsInWindow() {
        return maxTransactionsInWindow;
    }

    public void setMaxTransactionsInWindow(int maxTransactionsInWindow) {
        if (maxTransactionsInWindow < 1) {
            throw new IllegalArgumentException("the transaction limit must be at least 1");
        }
        this.maxTransactionsInWindow = maxTransactionsInWindow;
    }

    public int getWindowMinutes() {
        return windowMinutes;
    }

    public void setWindowMinutes(int windowMinutes) {
        if (windowMinutes < 1 || windowMinutes > 1440) {
            throw new IllegalArgumentException("the window must be between 1 and 1440 minutes");
        }
        this.windowMinutes = windowMinutes;
    }
}

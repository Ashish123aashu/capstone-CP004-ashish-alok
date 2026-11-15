public class FraudFlag {
    private final FraudRule rule;
    private final String reason;

    public FraudFlag(FraudRule rule, String reason) {
        this.rule = rule;
        this.reason = reason;
    }

    public FraudRule getRule() {
        return rule;
    }

    public String getReason() {
        return reason;
    }
}

public enum FraudRule {
    HIGH_AMOUNT("High amount"),
    RAPID_ACTIVITY("Rapid transactions"),
    NEW_CITY("New city");

    private final String label;

    FraudRule(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}

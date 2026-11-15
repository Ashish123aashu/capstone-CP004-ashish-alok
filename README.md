# Problem Title: FraudShield - Rule-Based UPI Transaction Risk Engine

**Problem ID:** CP-004

## Team Members

1. **Name:** Alok Shah | **USN:** 1AT24CS015
2. **Name:** Ashish Chauhan | **USN:** 1AT24CS028

## How to Run the Program

**Prerequisites:**

- Java Development Kit (JDK) 17 or newer.
- IntelliJ IDEA is optional; the program can also be compiled from a terminal.

**Steps:**

1. Clone or download this repository.
2. Open the project folder in IntelliJ IDEA, or open a terminal in the project folder.
3. Compile the program:

   **PowerShell with the standard JDK 17 installation path:**

   ```powershell
   & "C:\Program Files\Java\jdk-17\bin\javac.exe" -d out src\*.java
   ```

   **If `javac` is on your PATH:**

   ```bash
   javac -d out src/*.java
   ```

4. Execute the program:

   **PowerShell with the standard JDK 17 installation path:**

   ```powershell
   & "C:\Program Files\Java\jdk-17\bin\java.exe" -cp out Main
   ```

   **If `java` is on your PATH:**

   ```bash
   java -cp out Main
   ```

   In IntelliJ, open `src/Main.java` and click the green Run icon next to `main`.

## Features Implemented

- Record transactions with a unique transaction ID, user ID, positive amount, 24-hour time (`HH:mm`), and city.
- Flag amounts greater than the configured multiple of that user's previous transaction average. The default is 5 times.
- Flag users with more than 3 transactions in a 2-minute window by default, including a window that crosses midnight.
- Flag a user's first transaction from a city they have not previously used; city matching ignores letter case.
- Display every flagged transaction with its reasons, then show active flagged-transaction counts per user.
- Change rule thresholds from the menu without editing source code.
- Mark an individual rule flag as a false alarm and report false-alarm rates by rule.
- Save transactions, reviews, and thresholds locally so they remain available after restarting the program.
- Validate empty input, duplicate IDs, nonnumeric or nonpositive amounts, and invalid time values.

## Sample Run

**Input:**

```text
1
TX1001
U100
100
09:00
Mysuru
1
TX1002
U100
600
09:01
Bengaluru
3
4
5
9
```

**Output:**

```text
--- Detection complete ---
Transactions checked: 2
Flagged transactions: 1
Rule flags raised: 2

Transaction ID: TX1002 | User: U100 | Amount: Rs. 600 | Time: 09:01 | City: Bengaluru
  - Amount Rs. 600 is above 5x the user's previous average of Rs. 100
  - City 'Bengaluru' has not been used by this user before

--- User risk report ---
U100 | Active flagged transactions: 1 | Marked false alarm: 0
```

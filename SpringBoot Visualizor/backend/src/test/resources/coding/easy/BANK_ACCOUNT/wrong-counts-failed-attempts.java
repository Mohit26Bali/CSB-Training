public class BankAccount {

    private final String owner;
    private double balance;
    private int transactions;

    public BankAccount(String owner, double openingBalance) {
        if (owner == null || owner.trim().isEmpty() || openingBalance < 0) {
            throw new IllegalArgumentException("invalid account");
        }
        this.owner = owner;
        this.balance = openingBalance;
    }

    public void deposit(double amount) {
        if (amount <= 0) {
            throw new IllegalArgumentException("deposit must be positive");
        }
        balance += amount;
        transactions++;
    }

    public void withdraw(double amount) throws InsufficientBalanceException {
        transactions++;
        if (amount <= 0) {
            throw new IllegalArgumentException("withdrawal must be positive");
        }
        if (amount > balance) {
            throw new InsufficientBalanceException("balance is " + balance);
        }
        balance -= amount;
        transactions++;
    }

    public double getBalance() {
        return balance;
    }

    public String getOwner() {
        return owner;
    }

    public int transactionCount() {
        return transactions;
    }
}

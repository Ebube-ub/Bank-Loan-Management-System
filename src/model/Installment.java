package src.model;
import java.time.LocalDate;

public class Installment implements java.io.Serializable {

    private LocalDate dueDate;
    private double amountDue;
    private double amountPaid;
    private LocalDate paidDate;
    private InstallmentStatus status;

    public Installment(LocalDate dueDate, double amountDue) {
        this.dueDate = dueDate;
        this.amountDue = amountDue;
        this.amountPaid = 0.0;
        this.paidDate = null;
        this.status = InstallmentStatus.PENDING;
    }

    public LocalDate getDueDate() {
    return dueDate;
    }

    public double getAmountDue() {
    return amountDue;
    }

    public double getAmountPaid() {
    return amountPaid;
    }

    public LocalDate getPaidDate() {
    return paidDate;
    }

    public InstallmentStatus getStatus() {
    return status;
    }

    public void makePayment(double paymentAmount) {

    amountPaid += paymentAmount;

    if (amountPaid >= amountDue) {
        amountPaid = amountDue;
        status = InstallmentStatus.PAID;
        paidDate = LocalDate.now();
    }
    }

    public double getRemainingAmount() {
    return amountDue - amountPaid;
    }
}
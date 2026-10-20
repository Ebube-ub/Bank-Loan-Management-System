package bank.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import bank.model.Bank;

// Saves/loads the whole Bank object to a file using Java's built-in
// serialization. try-with-resources closes the file for us automatically,
// even if something goes wrong while reading/writing.
//
// The web version saves after every change, so saving must never leave a
// half-written file behind (for example if the power goes off mid-save).
// So we write to a temporary file first, and only swap it in once it is complete.
public class BankFileRepository {

    public void save(Bank bank, String fileName) throws IOException {

        Path target = Path.of(fileName);
        Path temporary = Path.of(fileName + ".tmp");

        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(temporary.toFile()))) {
            output.writeObject(bank);
        }

        try {
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            // some file systems can't swap in one step; a normal replace is the next best thing
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public Bank load(String fileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(fileName))) {
            return (Bank) input.readObject();
        }
    }
}

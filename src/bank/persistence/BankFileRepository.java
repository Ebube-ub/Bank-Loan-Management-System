package bank.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import bank.model.Bank;

// Saves/loads the whole Bank object to a file using Java's built-in
// serialization. try-with-resources closes the file for us automatically,
// even if something goes wrong while reading/writing.
public class BankFileRepository {

    public void save(Bank bank, String fileName) throws IOException {
        try (ObjectOutputStream output = new ObjectOutputStream(new FileOutputStream(fileName))) {
            output.writeObject(bank);
        }
    }

    public Bank load(String fileName) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new FileInputStream(fileName))) {
            return (Bank) input.readObject();
        }
    }
}

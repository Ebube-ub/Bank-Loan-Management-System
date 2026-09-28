package src.persistence;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.IOException;

import src.model.Bank;

public class BankFileRepository {

    public void save(Bank bank, String fileName) throws IOException {

        ObjectOutputStream output =
                new ObjectOutputStream(
                        new FileOutputStream(fileName)
                );

        output.writeObject(bank);

        output.close();
    }

    public Bank load(String fileName) throws IOException, ClassNotFoundException {

    ObjectInputStream input =
            new ObjectInputStream(
                    new FileInputStream(fileName)
            );

    Bank bank = (Bank) input.readObject();

    input.close();

    return bank;
    }
}
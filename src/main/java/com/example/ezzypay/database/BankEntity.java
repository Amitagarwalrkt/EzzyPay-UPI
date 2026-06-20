package com.example.ezzypay.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "banks")
public class BankEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String bankName;
    public String accountNumber;
    public double balance;

    public BankEntity(String bankName, String accountNumber, double balance) {
        this.bankName = bankName;
        this.accountNumber = accountNumber;
        this.balance = balance;
    }
}

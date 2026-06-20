package com.example.ezzypay.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;
import java.util.Date;

@Entity(tableName = "transactions")
public class TransactionEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String firestoreId; // Unique ID from Firebase
    public String name;
    public String upiId;
    public double amount;
    public Date date;
    public boolean isCredit;
    public String status;

    public TransactionEntity(String name, String upiId, double amount, Date date, boolean isCredit, String status) {
        this.name = name;
        this.upiId = upiId;
        this.amount = amount;
        this.date = date;
        this.isCredit = isCredit;
        this.status = status;
    }
}

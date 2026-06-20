package com.example.ezzypay.database;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "users")
public class UserEntity {
    @PrimaryKey(autoGenerate = true)
    public int id;
    public String name;
    public String upiId;
    public String phone;

    public UserEntity(String name, String upiId, String phone) {
        this.name = name;
        this.upiId = upiId;
        this.phone = phone;
    }
}

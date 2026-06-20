package com.example.ezzypay.database;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface AppDao {
    // User operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertUser(UserEntity user);

    @Query("SELECT * FROM users")
    List<UserEntity> getAllUsers();

    @Query("SELECT * FROM users WHERE upiId = :upi LIMIT 1")
    UserEntity getUserByUpi(String upi);

    // Bank operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertBank(BankEntity bank);

    @Query("SELECT * FROM banks")
    List<BankEntity> getAllBanks();

    @Update
    void updateBank(BankEntity bank);

    @Query("SELECT * FROM banks WHERE bankName = :name LIMIT 1")
    BankEntity getBankByName(String name);

    // Transaction operations
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insertTransaction(TransactionEntity transaction);

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<TransactionEntity> getAllTransactions();

    @Query("SELECT * FROM transactions WHERE firestoreId = :fId LIMIT 1")
    TransactionEntity getTransactionByFirestoreId(String fId);

    @Query("SELECT * FROM transactions WHERE upiId = :upiId ORDER BY date DESC")
    List<TransactionEntity> getTransactionsByUpi(String upiId);

    @Query("DELETE FROM transactions WHERE firestoreId IS NULL AND (name = 'Priya Singh' OR name = 'Zomato')")
    void deleteDemoTransactions();
    
    @Query("DELETE FROM transactions")
    void deleteAllTransactions();
}

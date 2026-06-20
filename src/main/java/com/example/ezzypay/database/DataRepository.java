package com.example.ezzypay.database;

import android.content.Context;
import android.util.Log;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.SetOptions;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Query;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.function.Consumer;

public class DataRepository {
    private static final String TAG = "DataRepository";
    private final FirebaseFirestore db;
    private ListenerRegistration balanceListener;
    private ListenerRegistration transactionsListener;

    public DataRepository(Context context) {
        db = FirebaseFirestore.getInstance();
    }

    private String getCleanPhone(String phone) {
        if (phone == null) return "";
        return phone.replace("+91", "").replace(" ", "").trim();
    }

    // --- Profile & Balance Section (Pure Firebase) ---
    public void saveProfileToFirebase(UserEntity user) {
        String cleanPhone = getCleanPhone(user.phone);
        Map<String, Object> profile = new HashMap<>();
        profile.put("name", user.name);
        profile.put("upiId", user.upiId);
        profile.put("phone", cleanPhone);
        profile.put("balance", 10000.00); // Practical Demo initial balance
        db.collection("users_profile").document(cleanPhone).set(profile, SetOptions.merge())
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Profile saved for " + cleanPhone));
    }

    public void updateBalanceInFirebase(String phone, double balance) {
        String cleanPhone = getCleanPhone(phone);
        if (cleanPhone.isEmpty()) return;
        
        Map<String, Object> data = new HashMap<>();
        data.put("balance", balance);
        db.collection("users_profile").document(cleanPhone).set(data, SetOptions.merge())
            .addOnSuccessListener(aVoid -> Log.d(TAG, "Balance updated in Firebase: " + balance))
            .addOnFailureListener(e -> Log.e(TAG, "Error updating balance", e));
    }

    public void syncBalanceFromFirebase(String phone, Consumer<Double> onBalanceUpdate) {
        String cleanPhone = getCleanPhone(phone);
        if (cleanPhone.isEmpty()) return;
        
        if (balanceListener != null) balanceListener.remove();

        balanceListener = db.collection("users_profile").document(cleanPhone)
            .addSnapshotListener((snapshot, e) -> {
                if (e != null) {
                    Log.w(TAG, "Balance listen failed.", e);
                    return;
                }

                if (snapshot != null && snapshot.exists()) {
                    Object balObj = snapshot.get("balance");
                    if (balObj instanceof Number) {
                        double balance = ((Number) balObj).doubleValue();
                        if (onBalanceUpdate != null) onBalanceUpdate.accept(balance);
                    }
                }
            });
    }

    // --- Transactions Section (Pure Firebase) ---
    public void syncTransactionsFromFirebase(String userUpiId, Consumer<List<TransactionEntity>> onUpdate) {
        if (userUpiId == null || userUpiId.isEmpty()) return;

        if (transactionsListener != null) transactionsListener.remove();

        // Removed orderBy to avoid index requirement, sorting locally
        transactionsListener = db.collection("transactions")
            .whereEqualTo("ownerUpi", userUpiId)
            .addSnapshotListener((snapshots, e) -> {
                if (e != null) {
                    Log.w(TAG, "Transactions listen failed.", e);
                    return;
                }

                if (snapshots != null) {
                    List<TransactionEntity> transactions = new ArrayList<>();
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        Date date = null;
                        Object dateObj = doc.get("date");
                        if (dateObj instanceof Timestamp) {
                            date = ((Timestamp) dateObj).toDate();
                        } else if (dateObj instanceof Long) {
                            date = new Date((Long) dateObj);
                        } else if (dateObj instanceof Date) {
                            date = (Date) dateObj;
                        }

                        TransactionEntity entity = new TransactionEntity(
                            doc.getString("name"),
                            doc.getString("upiId"),
                            doc.getDouble("amount") != null ? doc.getDouble("amount") : 0.0,
                            date != null ? date : new Date(),
                            doc.getBoolean("isCredit") != null ? doc.getBoolean("isCredit") : false,
                            doc.getString("status")
                        );
                        entity.firestoreId = doc.getId();
                        transactions.add(entity);
                    }
                    
                    // Local sort: Newest first
                    Collections.sort(transactions, (t1, t2) -> t2.date.compareTo(t1.date));

                    if (onUpdate != null) onUpdate.accept(transactions);
                }
            });
    }

    public void insertTransaction(TransactionEntity transaction, String ownerUpi) {
        Map<String, Object> txnData = new HashMap<>();
        txnData.put("name", transaction.name);
        txnData.put("upiId", transaction.upiId);
        txnData.put("amount", transaction.amount);
        txnData.put("date", transaction.date);
        txnData.put("isCredit", transaction.isCredit);
        txnData.put("status", transaction.status);
        txnData.put("ownerUpi", ownerUpi);
        
        db.collection("transactions").add(txnData);
    }

    public void cleanup() {
        if (balanceListener != null) balanceListener.remove();
        if (transactionsListener != null) transactionsListener.remove();
    }
}

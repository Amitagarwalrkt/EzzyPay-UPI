package com.example.ezzypay;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.NetworkRequest;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.ScaleAnimation;
import android.view.animation.TranslateAnimation;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.ezzypay.database.DataRepository;
import com.example.ezzypay.database.TransactionEntity;
import com.example.ezzypay.database.UserEntity;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthOptions;
import com.google.firebase.auth.PhoneAuthProvider;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String DEFAULT_PIN = "123456";

    private View screenLogin, screenOtp, screenHome, screenSearch, screenConfirm, screenAmount, screenPin, screenProcessing, screenSuccess, screenFail, screenScanner;
    private View screenWallet, screenHistory, screenProfile, screenBankSelect, screenUssdConfirm;
    private View screenRequest, screenTxnDetail, screenWpinSetup, screenWpinEntry, screenSyncQueue;
    private final List<View> allScreens = new ArrayList<>();
    
    private View btnSendTrigger, btnBackSearch, btnBackConfirm, btnProceedConfirm, btnConfirmPin, btnBackBank, btnConfirmBank, btnScanTrigger, btnBackScanner;
    private View btnConfirmUssd, btnBackHistory, btnGetOtp, btnVerifyOtp;
    
    private TextView tvAmountVal, tvBalance, tvPinAmount, tvUssdAmount, tvUssdReceiverName, tvUssdReceiverUpi;
    private TextView tvProfileName, tvProfileUpi, tvProfileBalance, tvWalletBalance;
    private Button btnPayAmount, btnLogout;
    private LinearLayout recentTxnContainer, pinDotsContainer, otpDotsContainer, searchResultsContainer;
    private EditText etSearchInput, etMobile;
    
    private final StringBuilder amountBuilder = new StringBuilder("0");
    private final StringBuilder pinBuilder = new StringBuilder();
    private final StringBuilder enteredOtp = new StringBuilder();
    private double currentBalance = 0.0;
    
    private DataRepository repository;
    private FirebaseAuth mAuth;
    private FirebaseFirestore db;

    private User selectedUser;
    private String currentUserName = "";
    private String currentUserUpi = ""; 
    private String currentUserPhone = "";
    private String lastSyncedUpi = "";

    private BarcodeScanner barcodeScanner;
    private PreviewView previewView;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private boolean isScanning = false;
    private String mVerificationId;

    // Practical Demo Dummy Data
    private final List<User> dummyUsers = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        repository = new DataRepository(this);
        mAuth = FirebaseAuth.getInstance();
        db = FirebaseFirestore.getInstance();

        initDummyData();

        BarcodeScannerOptions barcodeOptions = new BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build();
        barcodeScanner = BarcodeScanning.getClient(barcodeOptions);

        initUIElements();
        setupNavigation();
        setupSearchLogic();
        setupNumpads();
        setupNetworkMonitoring();
        setupBankSelection();
        
        if (mAuth.getCurrentUser() != null) {
            currentUserPhone = mAuth.getCurrentUser().getPhoneNumber();
            if (currentUserPhone != null) currentUserPhone = currentUserPhone.replace("+91", "");
            switchScreen(screenHome);
            startRealTimeProfileSync();
        } else {
            switchScreen(screenLogin);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() { handleBackPress(); }
        });
    }

    private void initDummyData() {
        dummyUsers.add(new User("Priya Singh", "priya@oksbi", "9876543210"));
        dummyUsers.add(new User("Rahul Sharma", "rahul@okaxis", "8888888888"));
        dummyUsers.add(new User("Amit Agarwal", "amit@paytm", "9999999999"));
        dummyUsers.add(new User("Practical Examiner", "examiner@upi", "9112233445"));
        dummyUsers.add(new User("Ezzy Store", "merchant@ezzypay", "0000000000"));
    }

    private void initUIElements() {
        screenLogin = findViewById(R.id.container_login);
        screenOtp = findViewById(R.id.container_otp);
        screenHome = findViewById(R.id.container_home);
        screenScanner = findViewById(R.id.container_scanner);
        screenSearch = findViewById(R.id.container_search);
        screenConfirm = findViewById(R.id.container_confirm);
        screenBankSelect = findViewById(R.id.container_bank_select);
        screenAmount = findViewById(R.id.container_amount);
        screenPin = findViewById(R.id.container_pin);
        screenProcessing = findViewById(R.id.container_processing);
        screenSuccess = findViewById(R.id.container_success);
        screenFail = findViewById(R.id.container_fail);
        screenWallet = findViewById(R.id.container_wallet);
        screenHistory = findViewById(R.id.container_history);
        screenProfile = findViewById(R.id.container_profile);
        screenUssdConfirm = findViewById(R.id.container_ussd_confirm);
        screenRequest = findViewById(R.id.container_request);
        screenTxnDetail = findViewById(R.id.container_txn_detail);
        screenWpinSetup = findViewById(R.id.container_wpin_setup);
        screenWpinEntry = findViewById(R.id.container_wpin_entry);
        screenSyncQueue = findViewById(R.id.container_sync_queue);

        allScreens.add(screenLogin); allScreens.add(screenOtp); allScreens.add(screenHome);
        allScreens.add(screenScanner); allScreens.add(screenSearch); allScreens.add(screenConfirm);
        allScreens.add(screenBankSelect); allScreens.add(screenAmount); allScreens.add(screenPin);
        allScreens.add(screenProcessing); allScreens.add(screenSuccess); allScreens.add(screenFail);
        allScreens.add(screenWallet); allScreens.add(screenHistory); allScreens.add(screenProfile);
        allScreens.add(screenUssdConfirm); allScreens.add(screenRequest); allScreens.add(screenTxnDetail);
        allScreens.add(screenWpinSetup); allScreens.add(screenWpinEntry); allScreens.add(screenSyncQueue);

        btnSendTrigger = findViewById(R.id.btn_send_trigger);
        btnScanTrigger = findViewById(R.id.action_scan_offline);
        btnBackScanner = findViewById(R.id.btn_back_scanner);
        btnBackSearch = findViewById(R.id.btn_back_search);
        btnBackConfirm = findViewById(R.id.btn_back_confirm);
        btnProceedConfirm = findViewById(R.id.btn_proceed_confirm);
        btnBackBank = findViewById(R.id.btn_back_bank);
        btnConfirmBank = findViewById(R.id.btn_confirm_bank);
        btnConfirmPin = findViewById(R.id.btn_confirm_pin);
        btnConfirmUssd = findViewById(R.id.btn_ussd_confirm_pay);
        btnBackHistory = findViewById(R.id.btn_back_history);
        
        tvAmountVal = findViewById(R.id.tv_amount_val);
        tvBalance = findViewById(R.id.wc_bal);
        tvWalletBalance = findViewById(R.id.wh_bal);
        btnPayAmount = findViewById(R.id.btn_pay_amount);
        tvPinAmount = findViewById(R.id.pin_screen_amount);
        tvUssdAmount = findViewById(R.id.tv_ussd_amount);
        tvUssdReceiverName = findViewById(R.id.tv_ussd_receiver_name);
        tvUssdReceiverUpi = findViewById(R.id.tv_ussd_receiver_upi);
        
        tvProfileName = findViewById(R.id.profile_user_name);
        tvProfileUpi = findViewById(R.id.profile_user_upi);
        tvProfileBalance = findViewById(R.id.profile_balance);
        btnLogout = findViewById(R.id.btn_logout);
        
        pinDotsContainer = findViewById(R.id.pin_dots_container);
        otpDotsContainer = findViewById(R.id.otp_dots_container);
        recentTxnContainer = findViewById(R.id.recent_transactions_container);
        etSearchInput = findViewById(R.id.et_search_input);
        searchResultsContainer = findViewById(R.id.search_results_container);
        previewView = findViewById(R.id.scanner_preview);
        etMobile = findViewById(R.id.et_mobile_number);
        btnGetOtp = findViewById(R.id.btn_get_otp);
        btnVerifyOtp = findViewById(R.id.btn_verify_otp);
    }

    private void setupNavigation() {
        // DEMO BYPASS: Long press logo to skip login
        ImageView logo = findViewById(R.id.login_logo);
        if (logo != null) {
            logo.setOnLongClickListener(v -> {
                handleLoginSuccess("9999999999");
                Toast.makeText(this, "Demo Mode: Skip Login Successful", Toast.LENGTH_SHORT).show();
                return true;
            });
        }

        if (btnGetOtp != null) btnGetOtp.setOnClickListener(v -> { if (etMobile.getText().length() == 10) startPhoneNumberVerification(etMobile.getText().toString()); });
        if (btnVerifyOtp != null) btnVerifyOtp.setOnClickListener(v -> { if (enteredOtp.length() == 6) verifyOtp(enteredOtp.toString()); });
        
        View resend = findViewById(R.id.btn_resend_otp_numpad);
        if (resend != null) resend.setOnClickListener(v -> { if (etMobile.getText().length() == 10) startPhoneNumberVerification(etMobile.getText().toString()); });

        if (btnSendTrigger != null) btnSendTrigger.setOnClickListener(v -> openSearch());
        if (btnScanTrigger != null) btnScanTrigger.setOnClickListener(v -> startScannerScreen());
        if (btnBackScanner != null) btnBackScanner.setOnClickListener(v -> handleBackPress());
        if (btnBackHistory != null) btnBackHistory.setOnClickListener(v -> handleBackPress());
        
        if (btnProceedConfirm != null) btnProceedConfirm.setOnClickListener(v -> { updateReceiverDetails(); switchScreen(screenAmount); });
        if (btnConfirmBank != null) btnConfirmBank.setOnClickListener(v -> { updateReceiverDetails(); switchScreen(screenPin); });
        
        if (btnConfirmUssd != null) btnConfirmUssd.setOnClickListener(v -> {
            try {
                double amt = Double.parseDouble(amountBuilder.toString());
                startUssdPayment(amt);
            } catch (Exception e) { Toast.makeText(this, "Invalid Amount", Toast.LENGTH_SHORT).show(); }
        });

        if (btnPayAmount != null) btnPayAmount.setOnClickListener(v -> {
            try {
                double amt = Double.parseDouble(amountBuilder.toString());
                if (amt > 0 && amt <= currentBalance) {
                    updateReceiverDetails();
                    if (!isOnline()) switchScreen(screenUssdConfirm);
                    else switchScreen(screenBankSelect);
                } else { Toast.makeText(this, "Insufficient Balance", Toast.LENGTH_SHORT).show(); }
            } catch (Exception e) { Toast.makeText(this, "Invalid Amount", Toast.LENGTH_SHORT).show(); }
        });

        if (btnConfirmPin != null) btnConfirmPin.setOnClickListener(v -> {
            if (pinBuilder.toString().equals(DEFAULT_PIN)) {
                switchScreen(screenProcessing);
                startPaymentProcessingAnimations();
                new Handler(Looper.getMainLooper()).postDelayed(this::completePayment, 2500);
            } else { Toast.makeText(this, "Wrong PIN", Toast.LENGTH_SHORT).show(); pinBuilder.setLength(0); updatePinDots(); }
        });

        if (btnBackSearch != null) btnBackSearch.setOnClickListener(v -> handleBackPress());
        if (btnBackConfirm != null) btnBackConfirm.setOnClickListener(v -> handleBackPress());
        if (btnBackBank != null) btnBackBank.setOnClickListener(v -> handleBackPress());
        View bba = findViewById(R.id.btn_back_amount); if (bba != null) bba.setOnClickListener(v -> handleBackPress());
        View bbp = findViewById(R.id.btn_back_pin); if (bbp != null) bbp.setOnClickListener(v -> handleBackPress());
        View bbu = findViewById(R.id.btn_back_ussd); if (bbu != null) bbu.setOnClickListener(v -> handleBackPress());
        
        View btnSuccessHome = findViewById(R.id.btn_success_home);
        if (btnSuccessHome != null) btnSuccessHome.setOnClickListener(v -> switchScreen(screenHome));
        
        View btnAddMoney = findViewById(R.id.btn_add_money);
        if (btnAddMoney != null) btnAddMoney.setOnClickListener(v -> switchScreen(screenWallet));
        View btnAddMoneyConfirm = findViewById(R.id.btn_add_money_confirm);
        if (btnAddMoneyConfirm != null) btnAddMoneyConfirm.setOnClickListener(v -> {
            double topup = 1000.0;
            currentBalance += topup;
            repository.updateBalanceInFirebase(currentUserPhone, currentBalance);
            repository.insertTransaction(new TransactionEntity("Top Up", "self@wallet", topup, new Date(), true, "Success"), currentUserUpi);
            Toast.makeText(this, "₹1000 added for practical demo!", Toast.LENGTH_SHORT).show();
        });

        if (btnLogout != null) btnLogout.setOnClickListener(v -> { mAuth.signOut(); switchScreen(screenLogin); });

        View bbr = findViewById(R.id.btn_back_request); if (bbr != null) bbr.setOnClickListener(v -> handleBackPress());
        View bbt = findViewById(R.id.btn_back_txn_detail); if (bbt != null) bbt.setOnClickListener(v -> handleBackPress());
        View bbw = findViewById(R.id.btn_back_wpin); if (bbw != null) bbw.setOnClickListener(v -> handleBackPress());
        View bbs = findViewById(R.id.btn_back_sync); if (bbs != null) bbs.setOnClickListener(v -> handleBackPress());

        View actionPay = findViewById(R.id.action_pay_offline); if (actionPay != null) actionPay.setOnClickListener(v -> openSearch());
        View actionPending = findViewById(R.id.action_pending); if (actionPending != null) actionPending.setOnClickListener(v -> switchScreen(screenSyncQueue));
        View actionWallet = findViewById(R.id.action_wallet_offline); if (actionWallet != null) actionWallet.setOnClickListener(v -> switchScreen(screenWallet));

        setupBottomNav(findViewById(R.id.bottom_nav), 0);
        setupBottomNav(findViewById(R.id.bottom_nav_wallet), 3);
        setupBottomNav(findViewById(R.id.bottom_nav_history), 1);
        setupBottomNav(findViewById(R.id.bottom_nav_profile), 4);

        View searchScan = findViewById(R.id.btn_search_scan);
        if (searchScan != null) searchScan.setOnClickListener(v -> startScannerScreen());

        View mockScan = findViewById(R.id.btn_mock_scan_trigger);
        if (mockScan != null) mockScan.setOnClickListener(v -> handleUpiQr("upi://pay?pa=examiner@upi&pn=Practical%20Demo%20QR&am=500"));

        setupQuickAmountButtons();
    }

    private void setupQuickAmountButtons() {
        LinearLayout qa = findViewById(R.id.quick_amts);
        if (qa == null) return;
        for (int i = 0; i < qa.getChildCount(); i++) {
            View child = qa.getChildAt(i);
            if (child instanceof TextView) {
                String label = ((TextView) child).getText().toString();
                String val = label.replace("₹", "").replace("K", "000");
                child.setOnClickListener(v -> {
                    amountBuilder.setLength(0);
                    amountBuilder.append(val);
                    updateReceiverDetails();
                });
            }
        }
    }

    private void setupBankSelection() {
        View sbi = findViewById(R.id.row_bank_sbi);
        View hdfc = findViewById(R.id.row_bank_hdfc);
        View icici = findViewById(R.id.row_bank_icici);
        
        if (sbi != null) sbi.setOnClickListener(v -> selectBank("sbi"));
        if (hdfc != null) hdfc.setOnClickListener(v -> selectBank("hdfc"));
        if (icici != null) icici.setOnClickListener(v -> selectBank("icici"));
    }

    private void selectBank(String bank) {
        View cs = findViewById(R.id.check_sbi); if (cs != null) cs.setVisibility(bank.equals("sbi") ? View.VISIBLE : View.GONE);
        View ch = findViewById(R.id.check_hdfc); if (ch != null) ch.setVisibility(bank.equals("hdfc") ? View.VISIBLE : View.GONE);
        View ci = findViewById(R.id.check_icici); if (ci != null) ci.setVisibility(bank.equals("icici") ? View.VISIBLE : View.GONE);
        
        View rs = findViewById(R.id.row_bank_sbi); if (rs != null) rs.setBackgroundColor(ContextCompat.getColor(this, bank.equals("sbi") ? R.color.s3 : R.color.s2));
        View rh = findViewById(R.id.row_bank_hdfc); if (rh != null) rh.setBackgroundColor(ContextCompat.getColor(this, bank.equals("hdfc") ? R.color.s3 : R.color.s2));
        View ri = findViewById(R.id.row_bank_icici); if (ri != null) ri.setBackgroundColor(ContextCompat.getColor(this, bank.equals("icici") ? R.color.s3 : R.color.s2));
    }

    private void handleBackPress() {
        View active = getActiveScreen();
        if (active == screenHome || active == screenLogin) { finish(); }
        else if (active == screenScanner) { stopCamera(); switchScreen(screenHome); }
        else if (active == screenSearch) switchScreen(screenHome);
        else if (active == screenConfirm) switchScreen(screenSearch);
        else if (active == screenAmount) switchScreen(screenConfirm);
        else if (active == screenBankSelect) switchScreen(screenAmount);
        else if (active == screenPin) switchScreen(screenBankSelect);
        else if (active == screenUssdConfirm) switchScreen(screenAmount);
        else if (active == screenProcessing || active == screenSuccess) switchScreen(screenHome);
        else if (active == screenHistory || active == screenWallet || active == screenProfile) switchScreen(screenHome);
        else if (active == screenTxnDetail) switchScreen(screenHistory);
        else if (active == screenSyncQueue) switchScreen(screenHome);
        else switchScreen(screenHome);
    }

    private void handleUpiQr(String upiUrl) {
        isScanning = false;
        Uri uri = Uri.parse(upiUrl);
        String pa = uri.getQueryParameter("pa");
        String pn = uri.getQueryParameter("pn");
        String am = uri.getQueryParameter("am");
        if (pa != null) {
            selectedUser = new User(pn == null ? "UPI Merchant" : pn, pa, "");
            if (am != null) { amountBuilder.setLength(0); amountBuilder.append(am); }
            runOnUiThread(() -> { stopCamera(); updateReceiverDetails(); switchScreen(screenAmount); });
        } else { isScanning = true; }
    }

    private void updateReceiverDetails() {
        if (selectedUser == null) return;
        String amtStr = "₹" + amountBuilder.toString();
        
        TextView tvConfirmName = findViewById(R.id.tv_confirm_receiver_name);
        if (tvConfirmName != null) tvConfirmName.setText(selectedUser.name);
        TextView tvConfirmUpi = findViewById(R.id.tv_confirm_receiver_upi);
        if (tvConfirmUpi != null) tvConfirmUpi.setText(selectedUser.upiId);
        TextView tvConfirmInitials = findViewById(R.id.tv_confirm_receiver_initials);
        if (tvConfirmInitials != null) tvConfirmInitials.setText(selectedUser.getInitials());
        
        TextView tvOfficialName = findViewById(R.id.tv_confirm_official_name);
        if (tvOfficialName != null) tvOfficialName.setText(selectedUser.name.toUpperCase());
        TextView tvBankName = findViewById(R.id.tv_confirm_bank_name);
        if (tvBankName != null) tvBankName.setText("State Bank of India");

        TextView tvAmountName = findViewById(R.id.tv_amount_receiver_name);
        if (tvAmountName != null) tvAmountName.setText(selectedUser.name);
        TextView tvAmountUpi = findViewById(R.id.tv_amount_receiver_upi);
        if (tvAmountUpi != null) tvAmountUpi.setText(selectedUser.upiId);
        TextView tvAmountInitials = findViewById(R.id.tv_amount_receiver_initials);
        if (tvAmountInitials != null) tvAmountInitials.setText(selectedUser.getInitials());
        
        TextView tvPinName = findViewById(R.id.tv_pin_receiver_name);
        if (tvPinName != null) tvPinName.setText(selectedUser.name);
        TextView tvPinUpi = findViewById(R.id.tv_pin_receiver_upi);
        if (tvPinUpi != null) tvPinUpi.setText(selectedUser.upiId);
        
        if (tvPinAmount != null) tvPinAmount.setText(amtStr);
        if (tvAmountVal != null) tvAmountVal.setText(amountBuilder.toString());
        if (btnPayAmount != null) btnPayAmount.setText(String.format("Pay %s via UPI →", amtStr));
        if (tvUssdReceiverName != null) tvUssdReceiverName.setText(selectedUser.name);
        if (tvUssdReceiverUpi != null) tvUssdReceiverUpi.setText(selectedUser.upiId);
        if (tvUssdAmount != null) tvUssdAmount.setText(amtStr);
    }

    private void startPaymentProcessingAnimations() {
        View core = findViewById(R.id.proc_core);
        if (core != null) {
            ScaleAnimation scale = new ScaleAnimation(1f, 1.2f, 1f, 1.2f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            scale.setDuration(800); scale.setRepeatCount(Animation.INFINITE); scale.setRepeatMode(Animation.REVERSE);
            core.startAnimation(scale);
        }
        View r1 = findViewById(R.id.ripple_1), r2 = findViewById(R.id.ripple_2);
        if (r1 != null && r2 != null) {
            r1.setVisibility(View.VISIBLE); r1.setAlpha(1f);
            r2.setVisibility(View.VISIBLE); r2.setAlpha(1f);
            
            AnimationSet anim1 = new AnimationSet(false);
            ScaleAnimation ripple = new ScaleAnimation(1f, 2.5f, 1f, 2.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
            ripple.setDuration(1500); ripple.setRepeatCount(Animation.INFINITE);
            AlphaAnimation alpha = new AlphaAnimation(0.6f, 0f); alpha.setDuration(1500); alpha.setRepeatCount(Animation.INFINITE);
            anim1.addAnimation(ripple); anim1.addAnimation(alpha);
            r1.startAnimation(anim1);
            
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                AnimationSet anim2 = new AnimationSet(false);
                ScaleAnimation ripple2 = new ScaleAnimation(1f, 2.5f, 1f, 2.5f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                ripple2.setDuration(1500); ripple2.setRepeatCount(Animation.INFINITE);
                AlphaAnimation alpha2 = new AlphaAnimation(0.6f, 0f); alpha2.setDuration(1500); alpha2.setRepeatCount(Animation.INFINITE);
                anim2.addAnimation(ripple2); anim2.addAnimation(alpha2);
                r2.startAnimation(anim2);
            }, 750);
        }
    }

    private void switchScreen(View to) {
        if (to == null) return;
        for (View v : allScreens) if (v != null) v.setVisibility(View.GONE);
        to.setVisibility(View.VISIBLE);
        AlphaAnimation fadeIn = new AlphaAnimation(0, 1); fadeIn.setDuration(350); to.startAnimation(fadeIn);
    }

    private View getActiveScreen() {
        for (View v : allScreens) if (v != null && v.getVisibility() == View.VISIBLE) return v;
        return screenHome;
    }

    private void completePayment() {
        try {
            double amt = Double.parseDouble(amountBuilder.toString());
            if (!isOnline()) { startUssdPayment(amt); return; }
            currentBalance -= amt; 
            repository.updateBalanceInFirebase(currentUserPhone, currentBalance);
            TransactionEntity entity = new TransactionEntity(selectedUser.name, selectedUser.upiId, -amt, new Date(), false, "Success");
            repository.insertTransaction(entity, currentUserUpi);
            updateSuccessUI(amt); switchScreen(screenSuccess);
        } catch (Exception e) { Log.e(TAG, "Payment Error", e); Toast.makeText(this, "Payment Failed", Toast.LENGTH_SHORT).show(); switchScreen(screenHome); }
    }

    private void updateSuccessUI(double amount) {
        TextView tvSuccessAmt = findViewById(R.id.success_amount);
        if (tvSuccessAmt != null) tvSuccessAmt.setText(String.format("₹%s", amount));
        TextView tvSuccessInfo = findViewById(R.id.success_to_info);
        if (tvSuccessInfo != null && selectedUser != null) tvSuccessInfo.setText(String.format("to %s · %s", selectedUser.name, selectedUser.upiId));
    }

    private void startUssdPayment(double amount) {
        String ussdCode = "*99*1*1#" ; 
        Intent intent = new Intent(Intent.ACTION_CALL); intent.setData(Uri.parse("tel:" + Uri.encode(ussdCode)));
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            startActivity(intent); updateSuccessUI(amount); switchScreen(screenSuccess);
        } else { ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CALL_PHONE}, 101); }
    }

    private void setupBottomNav(View navView, int activeIndex) {
        if (!(navView instanceof LinearLayout)) return;
        LinearLayout nav = (LinearLayout) navView;
        
        for (int i = 0; i < nav.getChildCount(); i++) {
            View child = nav.getChildAt(i);
            if (child instanceof TextView) {
                ((TextView) child).setTextColor(ContextCompat.getColor(this, i == activeIndex ? R.color.amber : R.color.text_grey));
            }
        }

        if (nav.getChildAt(0) != null) nav.getChildAt(0).setOnClickListener(v -> switchScreen(screenHome));
        if (nav.getChildAt(1) != null) nav.getChildAt(1).setOnClickListener(v -> switchScreen(screenHistory));
        if (nav.getChildAt(2) != null) nav.getChildAt(2).setOnClickListener(v -> openSearch());
        if (nav.getChildAt(3) != null) nav.getChildAt(3).setOnClickListener(v -> switchScreen(screenWallet));
        if (nav.getChildAt(4) != null) nav.getChildAt(4).setOnClickListener(v -> switchScreen(screenProfile));
    }

    private void startRealTimeProfileSync() {
        if (currentUserPhone == null || currentUserPhone.isEmpty()) return;
        repository.syncBalanceFromFirebase(currentUserPhone, balance -> {
            currentBalance = balance;
            runOnUiThread(this::updateBalanceDisplay);
        });
        
        db.collection("users_profile").document(currentUserPhone).addSnapshotListener((doc, e) -> {
            if (doc != null && doc.exists()) {
                currentUserName = doc.getString("name"); 
                String newUpi = doc.getString("upiId");
                if (newUpi != null && !newUpi.equals(lastSyncedUpi)) {
                    currentUserUpi = newUpi;
                    lastSyncedUpi = newUpi;
                    repository.syncTransactionsFromFirebase(currentUserUpi, txns -> runOnUiThread(() -> updateTransactionLists(txns)));
                }
                updateHomeUserDetails();
                updateProfileDetails();
            }
        });
    }

    private void updateProfileDetails() {
        if (tvProfileName != null) tvProfileName.setText(currentUserName);
        if (tvProfileUpi != null) tvProfileUpi.setText(currentUserUpi);
        if (tvProfileBalance != null) tvProfileBalance.setText(NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(currentBalance));
    }

    private void updateTransactionLists(List<TransactionEntity> txns) {
        if (recentTxnContainer != null) {
            recentTxnContainer.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            for (int i = 0; i < Math.min(txns.size(), 5); i++) {
                recentTxnContainer.addView(createTransactionView(inflater, txns.get(i), recentTxnContainer));
            }
        }
        
        View historyList = findViewById(R.id.history_list_container);
        if (historyList instanceof LinearLayout) {
            LinearLayout hl = (LinearLayout) historyList;
            hl.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(this);
            for (TransactionEntity txn : txns) {
                hl.addView(createTransactionView(inflater, txn, hl));
            }
        }

        TextView tvTxnCount = findViewById(R.id.profile_txn_count);
        if (tvTxnCount != null) tvTxnCount.setText(String.valueOf(txns.size()));
    }

    private View createTransactionView(LayoutInflater inflater, TransactionEntity txn, ViewGroup parent) {
        View view = inflater.inflate(R.layout.item_transaction, parent, false);
        ((TextView) view.findViewById(R.id.txn_name)).setText(txn.name);
        String prefix = txn.amount < 0 ? "-" : "+";
        double absAmt = Math.abs(txn.amount);
        TextView tvAmt = view.findViewById(R.id.txn_amount);
        tvAmt.setText(String.format("%s₹%s", prefix, absAmt));
        tvAmt.setTextColor(ContextCompat.getColor(this, txn.amount < 0 ? R.color.error_red : R.color.success_green));
        
        TextView tvDate = view.findViewById(R.id.txn_date);
        if (tvDate != null) tvDate.setText(new SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(txn.date));

        TextView txnIcon = view.findViewById(R.id.txn_icon);
        if (txnIcon != null) txnIcon.setText(txn.amount < 0 ? "💸" : "💰");

        ((TextView) view.findViewById(R.id.txn_status)).setText(txn.status);
        
        view.setOnClickListener(v -> {
            updateTxnDetailUI(txn);
            switchScreen(screenTxnDetail);
        });
        
        return view;
    }

    private void updateTxnDetailUI(TransactionEntity txn) {
        TextView detAmt = findViewById(R.id.detail_amount);
        if (detAmt != null) detAmt.setText(String.format("%s₹%s", txn.amount < 0 ? "−" : "+", Math.abs(txn.amount)));
        if (detAmt != null) detAmt.setTextColor(ContextCompat.getColor(this, txn.amount < 0 ? R.color.error_red : R.color.success_green));
        
        TextView detName = findViewById(R.id.detail_name);
        if (detName != null) detName.setText(txn.name);
        TextView detUpi = findViewById(R.id.detail_upi);
        if (detUpi != null) detUpi.setText(txn.upiId);
        TextView detTime = findViewById(R.id.detail_time);
        if (detTime != null) detTime.setText(new SimpleDateFormat("MMM d, h:mm a", Locale.getDefault()).format(txn.date));
        TextView detAv = findViewById(R.id.detail_av);
        if (detAv != null) {
            String initial = txn.name != null && !txn.name.isEmpty() ? txn.name.substring(0, 1).toUpperCase() : "?";
            detAv.setText(initial);
        }
    }

    private void startScannerScreen() { isScanning = true; switchScreen(screenScanner); startCamera(); startScannerAnimation(); }
    private void startScannerAnimation() {
        View line = findViewById(R.id.scanner_line);
        if (line != null) {
            TranslateAnimation anim = new TranslateAnimation(0, 0, 0, 600);
            anim.setDuration(2000); anim.setRepeatCount(Animation.INFINITE); anim.setRepeatMode(Animation.REVERSE);
            line.startAnimation(anim);
        }
    }
    private void startCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try { bindPreviewAndAnalysis(cameraProviderFuture.get()); } catch (Exception ignored) {}
        }, ContextCompat.getMainExecutor(this));
    }
    private void bindPreviewAndAnalysis(@NonNull ProcessCameraProvider cameraProvider) {
        Preview preview = new Preview.Builder().build();
        CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build();
        preview.setSurfaceProvider(previewView.getSurfaceProvider());
        ImageAnalysis imageAnalysis = new ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();
        imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::processImageProxy);
        cameraProvider.unbindAll(); cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);
    }
    @OptIn(markerClass = ExperimentalGetImage.class)
    private void processImageProxy(ImageProxy imageProxy) {
        if (!isScanning) { imageProxy.close(); return; }
        android.media.Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            barcodeScanner.process(image).addOnSuccessListener(barcodes -> {
                for (Barcode barcode : barcodes) {
                    String rawValue = barcode.getRawValue();
                    if (rawValue != null && rawValue.startsWith("upi://")) { handleUpiQr(rawValue); return; }
                }
            }).addOnCompleteListener(task -> imageProxy.close());
        } else { imageProxy.close(); }
    }
    private void stopCamera() { isScanning = false; if (cameraProviderFuture != null) { try { cameraProviderFuture.get().unbindAll(); } catch (Exception ignored) {} } }

    private void setupNumpads() { setupNumpad(findViewById(R.id.numpad), true); setupNumpad(findViewById(R.id.numpad_pin), false); setupOtpNumpad(); }

    private void setupNetworkMonitoring() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) return;
        NetworkRequest request = new NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build();
        cm.registerNetworkCallback(request, new ConnectivityManager.NetworkCallback() {
            @Override public void onAvailable(@NonNull Network network) { Log.i(TAG, "Internet connection restored"); }
            @Override public void onLost(@NonNull Network network) { Log.w(TAG, "Internet connection lost"); }
        });
    }

    private void setupNumpad(GridLayout grid, boolean isAmount) {
        if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View v = grid.getChildAt(i);
            if (v instanceof TextView) {
                String text = ((TextView) v).getText().toString();
                v.setOnClickListener(view -> { if (isAmount) handleAmountInput(text); else handlePinInput(text); });
            }
        }
    }
    private void handleAmountInput(String text) {
        if (text.equals("⌫")) { if (amountBuilder.length() > 0) { amountBuilder.deleteCharAt(amountBuilder.length() - 1); if (amountBuilder.length() == 0) amountBuilder.append("0"); } }
        else { if (amountBuilder.toString().equals("0")) amountBuilder.setLength(0); amountBuilder.append(text); }
        updateReceiverDetails();
    }
    private void handlePinInput(String text) {
        if (text.equals("⌫")) { if (pinBuilder.length() > 0) pinBuilder.deleteCharAt(pinBuilder.length() - 1); }
        else if (pinBuilder.length() < 6) pinBuilder.append(text);
        updatePinDots();
    }
    private void updatePinDots() {
        if (pinDotsContainer == null) return;
        for (int i = 0; i < pinDotsContainer.getChildCount(); i++) pinDotsContainer.getChildAt(i).setAlpha(i < pinBuilder.length() ? 1.0f : 0.2f);
    }
    private void setupOtpNumpad() {
        GridLayout grid = findViewById(R.id.numpad_otp); if (grid == null) return;
        for (int i = 0; i < grid.getChildCount(); i++) {
            View v = grid.getChildAt(i);
            if (v instanceof TextView) {
                String text = ((TextView) v).getText().toString();
                v.setOnClickListener(view -> {
                    if (text.equals("⌫")) { if (enteredOtp.length() > 0) enteredOtp.deleteCharAt(enteredOtp.length() - 1); }
                    else if (enteredOtp.length() < 6) enteredOtp.append(text);
                    updateOtpDots();
                    if (enteredOtp.length() == 6) verifyOtp(enteredOtp.toString());
                });
            }
        }
    }
    private void updateOtpDots() {
        if (otpDotsContainer == null) return;
        for (int i = 0; i < otpDotsContainer.getChildCount(); i++) {
            View dot = otpDotsContainer.getChildAt(i);
            if (i < enteredOtp.length()) { dot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(ContextCompat.getColor(this, R.color.cyan))); dot.setAlpha(1.0f); }
            else { dot.setBackgroundTintList(null); dot.setAlpha(0.2f); }
        }
    }

    private void startPhoneNumberVerification(String phoneNumber) {
        PhoneAuthOptions options = PhoneAuthOptions.newBuilder(mAuth).setPhoneNumber("+91" + phoneNumber).setTimeout(60L, TimeUnit.SECONDS).setActivity(this).setCallbacks(new PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            @Override public void onVerificationCompleted(@NonNull PhoneAuthCredential c) { mAuth.signInWithCredential(c).addOnCompleteListener(task -> { if (task.isSuccessful()) handleLoginSuccess(phoneNumber); }); }
            @Override public void onVerificationFailed(@NonNull FirebaseException e) { Toast.makeText(MainActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show(); }
            @Override public void onCodeSent(@NonNull String vId, @NonNull PhoneAuthProvider.ForceResendingToken t) { mVerificationId = vId; switchScreen(screenOtp); }
        }).build();
        PhoneAuthProvider.verifyPhoneNumber(options);
    }
    
    private void verifyOtp(String code) {
        if (mVerificationId == null) return;
        PhoneAuthCredential credential = PhoneAuthProvider.getCredential(mVerificationId, code);
        mAuth.signInWithCredential(credential).addOnCompleteListener(this, task -> {
            if (task.isSuccessful()) handleLoginSuccess(etMobile.getText().toString());
            else { Toast.makeText(this, "Verification failed", Toast.LENGTH_SHORT).show(); enteredOtp.setLength(0); updateOtpDots(); }
        });
    }

    private void handleLoginSuccess(String mobile) {
        currentUserPhone = mobile;
        db.collection("users_profile").document(mobile).get().addOnSuccessListener(doc -> {
            if (!doc.exists()) repository.saveProfileToFirebase(new UserEntity("Amit Agarwal", mobile + "@paytm", mobile));
            startRealTimeProfileSync(); switchScreen(screenHome);
        });
    }

    private void openSearch() { if (etSearchInput != null) etSearchInput.setText(""); switchScreen(screenSearch); }
    private void updateBalanceDisplay() { 
        String bal = NumberFormat.getCurrencyInstance(new Locale("en", "IN")).format(currentBalance);
        if (tvBalance != null) tvBalance.setText(bal);
        if (tvWalletBalance != null) tvWalletBalance.setText(bal);
        if (tvProfileBalance != null) tvProfileBalance.setText(bal);
    }
    private void setupSearchLogic() { if (etSearchInput != null) etSearchInput.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {} @Override public void onTextChanged(CharSequence s, int start, int before, int count) { filterUsers(s.toString()); } @Override public void afterTextChanged(Editable s) {} }); }
    
    private void filterUsers(String query) {
        if (searchResultsContainer == null) return;
        searchResultsContainer.removeAllViews();
        if (query.length() < 1) return;

        LayoutInflater inflater = LayoutInflater.from(this);
        
        for (User user : dummyUsers) {
            if (user.name.toLowerCase().contains(query.toLowerCase()) || user.phone.contains(query) || user.upiId.contains(query)) {
                addSearchResultView(inflater, user);
            }
        }

        if (query.length() >= 3) {
            db.collection("users_profile").whereGreaterThanOrEqualTo("phone", query).whereLessThanOrEqualTo("phone", query + "\uf8ff").limit(5).get().addOnSuccessListener(snapshots -> {
                for (DocumentSnapshot doc : snapshots.getDocuments()) {
                    String phone = doc.getString("phone");
                    boolean exists = false;
                    for (User du : dummyUsers) if (du.phone.equals(phone)) { exists = true; break; }
                    if (!exists) {
                        User user = new User(doc.getString("name"), doc.getString("upiId"), phone);
                        addSearchResultView(inflater, user);
                    }
                }
            });
        }
    }

    private void addSearchResultView(LayoutInflater inflater, User user) {
        View view = inflater.inflate(R.layout.item_user_result, searchResultsContainer, false);
        ((TextView) view.findViewById(R.id.user_name)).setText(user.name);
        ((TextView) view.findViewById(R.id.user_upi)).setText(user.upiId);
        view.setOnClickListener(v -> {
            selectedUser = user;
            updateReceiverDetails();
            switchScreen(screenConfirm);
        });
        searchResultsContainer.addView(view);
    }

    private void updateHomeUserDetails() {
        if (currentUserName != null && !currentUserName.isEmpty()) {
            ((TextView) findViewById(R.id.tv_home_user_name)).setText(String.format("%s 👋", currentUserName));
            String initials = "";
            String[] parts = currentUserName.split(" ");
            for (String p : parts) if (!p.isEmpty()) initials += p.charAt(0);
            ((TextView) findViewById(R.id.tv_home_user_initials)).setText(initials.toUpperCase());
        }
        ((TextView) findViewById(R.id.wc_upi)).setText(String.format("%s · Linked to SBI", currentUserUpi));
    }

    private boolean isOnline() {
        ConnectivityManager connectivityManager = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = connectivityManager.getActiveNetwork();
            if (network != null) {
                NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(network);
                return capabilities != null && (capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET));
            }
        }
        return false;
    }

    static class Transaction { String name, upiId, status; double amount; Date date; boolean isCredit; Transaction(String n, String u, double a, Date d, boolean c, String s) { name=n; upiId=u; amount=a; date=d; isCredit=c; status=s; } }
    static class User { String name, upiId, phone; User(String n, String u, String p) { name=n; upiId=u; phone=p; } 
        String getInitials() { if (name == null || name.isEmpty()) return "??"; String[] parts = name.split(" "); StringBuilder sb = new StringBuilder(); for (String p : parts) if (!p.isEmpty()) sb.append(p.charAt(0)); return sb.toString().toUpperCase(); }
    }
}

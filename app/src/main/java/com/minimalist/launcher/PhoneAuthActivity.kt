package com.minimalist.launcher

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseUser
import com.minimalist.launcher.auth.PhoneAuthManager
import com.minimalist.launcher.data.AnalyticsRepository

/**
 * Phone Authentication screen - appears before onboarding
 * 
 * Two-phase flow:
 * 1. Enter phone number → Send OTP
 * 2. Enter OTP → Verify → Proceed to onboarding
 */
class PhoneAuthActivity : AppCompatActivity(), PhoneAuthManager.PhoneAuthCallbacks {

    private lateinit var phoneAuthManager: PhoneAuthManager
    private lateinit var analytics: AnalyticsRepository
    
    // Views
    private lateinit var titleText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var phoneInputContainer: LinearLayout
    private lateinit var otpInputContainer: LinearLayout
    private lateinit var phoneNumberInput: EditText
    private lateinit var otpInput: EditText
    private lateinit var sendOtpButton: Button
    private lateinit var verifyOtpButton: Button
    private lateinit var resendOtpText: TextView
    private lateinit var progressBar: ProgressBar
    private lateinit var errorText: TextView
    
    private var currentPhoneNumber: String = ""
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_phone_auth)
        
        phoneAuthManager = PhoneAuthManager()
        analytics = AnalyticsRepository(this)
        
        initViews()
        setupListeners()
        
        // Check if already authenticated
        if (phoneAuthManager.isAuthenticated()) {
            proceedToOnboarding()
            return
        }
    }
    
    private fun initViews() {
        titleText = findViewById(R.id.authTitle)
        subtitleText = findViewById(R.id.authSubtitle)
        phoneInputContainer = findViewById(R.id.phoneInputContainer)
        otpInputContainer = findViewById(R.id.otpInputContainer)
        phoneNumberInput = findViewById(R.id.phoneNumberInput)
        otpInput = findViewById(R.id.otpInput)
        sendOtpButton = findViewById(R.id.sendOtpButton)
        verifyOtpButton = findViewById(R.id.verifyOtpButton)
        resendOtpText = findViewById(R.id.resendOtpText)
        progressBar = findViewById(R.id.authProgress)
        errorText = findViewById(R.id.errorText)
    }
    
    private fun setupListeners() {
        sendOtpButton.setOnClickListener {
            val phone = phoneNumberInput.text.toString().trim()
            if (validatePhoneNumber(phone)) {
                currentPhoneNumber = "+91$phone"
                sendOtp()
            }
        }
        
        verifyOtpButton.setOnClickListener {
            val otp = otpInput.text.toString().trim()
            if (validateOtp(otp)) {
                verifyOtp(otp)
            }
        }
        
        resendOtpText.setOnClickListener {
            resendOtp()
        }
    }
    
    private fun validatePhoneNumber(phone: String): Boolean {
        return if (phone.length != 10) {
            showError("Please enter a valid 10-digit phone number")
            false
        } else {
            hideError()
            true
        }
    }
    
    private fun validateOtp(otp: String): Boolean {
        return if (otp.length != 6) {
            showError("Please enter the 6-digit OTP")
            false
        } else {
            hideError()
            true
        }
    }
    
    private fun sendOtp() {
        showLoading(true)
        phoneAuthManager.sendOtp(this, currentPhoneNumber, this)
    }
    
    private fun resendOtp() {
        showLoading(true)
        phoneAuthManager.resendOtp(this, currentPhoneNumber, this)
    }
    
    private fun verifyOtp(otp: String) {
        showLoading(true)
        phoneAuthManager.verifyOtp(otp, this)
    }
    
    // PhoneAuthCallbacks implementation
    
    override fun onCodeSent() {
        showLoading(false)
        showOtpScreen()
        Toast.makeText(this, "OTP sent successfully", Toast.LENGTH_SHORT).show()
    }
    
    override fun onSuccess(user: FirebaseUser?) {
        showLoading(false)
        Toast.makeText(this, "Verified successfully!", Toast.LENGTH_SHORT).show()
        proceedToOnboarding()
    }
    
    override fun onError(message: String) {
        showLoading(false)
        showError(message)
    }
    
    private fun showOtpScreen() {
        phoneInputContainer.visibility = View.GONE
        otpInputContainer.visibility = View.VISIBLE
        titleText.text = "Enter OTP"
        subtitleText.text = "We sent a code to $currentPhoneNumber"
    }
    
    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        sendOtpButton.isEnabled = !show
        verifyOtpButton.isEnabled = !show
    }
    
    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }
    
    private fun hideError() {
        errorText.visibility = View.GONE
    }
    
    private fun proceedToOnboarding() {
        // Log auth success
        analytics.logEvent(
            com.minimalist.launcher.data.UsageEvent(
                eventType = com.minimalist.launcher.data.EventType.PERMISSION_GRANTED,
                metadata = mapOf("permissionType" to "phone_auth")
            )
        )
        
        // Start onboarding
        val intent = Intent(this, OnboardingActivity::class.java)
        startActivity(intent)
        finish()
    }
}

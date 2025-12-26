package com.minimalist.launcher.auth

import android.app.Activity
import android.util.Log
import com.google.firebase.FirebaseException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

/**
 * Manages Firebase Phone Authentication (OTP)
 */
class PhoneAuthManager {
    
    private val auth = FirebaseAuth.getInstance()
    private var verificationId: String? = null
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    
    /**
     * Get current authenticated user
     */
    fun getCurrentUser(): FirebaseUser? = auth.currentUser
    
    /**
     * Check if user is authenticated
     */
    fun isAuthenticated(): Boolean = auth.currentUser != null
    
    /**
     * Send OTP to phone number
     * 
     * @param activity The activity context
     * @param phoneNumber Phone number with country code (e.g., "+911234567890")
     * @param callbacks Callback for OTP events
     */
    fun sendOtp(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthCallbacks
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    // Auto-verification (instant verification or auto-retrieval)
                    Log.d("PhoneAuth", "Verification completed automatically")
                    signInWithCredential(credential, callbacks)
                }
                
                override fun onVerificationFailed(e: FirebaseException) {
                    Log.e("PhoneAuth", "Verification failed", e)
                    callbacks.onError(e.message ?: "Verification failed")
                }
                
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    Log.d("PhoneAuth", "OTP sent successfully")
                    this@PhoneAuthManager.verificationId = verificationId
                    this@PhoneAuthManager.resendToken = token
                    callbacks.onCodeSent()
                }
            })
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    /**
     * Resend OTP
     */
    fun resendOtp(
        activity: Activity,
        phoneNumber: String,
        callbacks: PhoneAuthCallbacks
    ) {
        val token = resendToken
        if (token == null) {
            callbacks.onError("Cannot resend OTP. Please try again.")
            return
        }
        
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setForceResendingToken(token)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    signInWithCredential(credential, callbacks)
                }
                
                override fun onVerificationFailed(e: FirebaseException) {
                    callbacks.onError(e.message ?: "Verification failed")
                }
                
                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    this@PhoneAuthManager.verificationId = verificationId
                    this@PhoneAuthManager.resendToken = token
                    callbacks.onCodeSent()
                }
            })
            .build()
        
        PhoneAuthProvider.verifyPhoneNumber(options)
    }
    
    /**
     * Verify OTP code entered by user
     */
    fun verifyOtp(code: String, callbacks: PhoneAuthCallbacks) {
        val verId = verificationId
        if (verId == null) {
            callbacks.onError("Verification session expired. Please request a new OTP.")
            return
        }
        
        val credential = PhoneAuthProvider.getCredential(verId, code)
        signInWithCredential(credential, callbacks)
    }
    
    /**
     * Sign in with the phone credential
     */
    private fun signInWithCredential(
        credential: PhoneAuthCredential,
        callbacks: PhoneAuthCallbacks
    ) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("PhoneAuth", "Sign in successful")
                    callbacks.onSuccess(auth.currentUser)
                } else {
                    Log.e("PhoneAuth", "Sign in failed", task.exception)
                    callbacks.onError(task.exception?.message ?: "Sign in failed")
                }
            }
    }
    
    /**
     * Sign out current user
     */
    fun signOut() {
        auth.signOut()
    }
    
    /**
     * Callbacks for phone auth events
     */
    interface PhoneAuthCallbacks {
        fun onCodeSent()
        fun onSuccess(user: FirebaseUser?)
        fun onError(message: String)
    }
}

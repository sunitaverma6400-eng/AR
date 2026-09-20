package com.ar.messenger.data.repo

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import java.util.concurrent.TimeUnit

class AuthRepository {
    private val auth = FirebaseAuth.getInstance()

    val currentUid: String? get() = auth.currentUser?.uid

    fun sendOtp(
        phoneNumber: String,
        activity: Activity,
        onCodeSent: (String) -> Unit,
        onVerified: (PhoneAuthCredential) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        val options = PhoneAuthOptions.newBuilder(auth)
            .setPhoneNumber(phoneNumber)
            .setTimeout(60L, TimeUnit.SECONDS)
            .setActivity(activity)
            .setCallbacks(object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    onVerified(credential)
                }

                override fun onVerificationFailed(e: com.google.firebase.FirebaseException) {
                    onFailed(e)
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    onCodeSent(verificationId)
                }
            })
            .build()
        PhoneAuthProvider.verifyPhoneNumber(options)
    }

    fun verifyOtp(
        verificationId: String,
        code: String,
        onSuccess: (String) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        val credential = PhoneAuthProvider.getCredential(verificationId, code)
        auth.signInWithCredential(credential)
            .addOnSuccessListener { result ->
                result.user?.uid?.let(onSuccess) ?: onFailed(Exception("No UID returned"))
            }
            .addOnFailureListener(onFailed)
    }

    fun signOut() = auth.signOut()

    /** Signs in without a phone number or SMS — free on the Spark plan, no billing needed. */
    fun signInAnonymously(
        onSuccess: (String) -> Unit,
        onFailed: (Exception) -> Unit
    ) {
        auth.signInAnonymously()
            .addOnSuccessListener { result ->
                result.user?.uid?.let(onSuccess) ?: onFailed(Exception("No UID returned"))
            }
            .addOnFailureListener(onFailed)
    }
}

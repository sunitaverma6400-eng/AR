package com.ar.messenger.data.repo

import com.ar.messenger.data.model.CallSession
import com.ar.messenger.data.model.CallState
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

class CallRepository {
    private val db = FirebaseFirestore.getInstance()
    private val callsCol = db.collection("calls")

    fun createCall(session: CallSession) {
        callsCol.document(session.callId).set(session)
    }

    fun updateCallState(callId: String, state: CallState) {
        callsCol.document(callId).update("state", state.name)
    }

    fun setAnswer(callId: String, answerSdp: String) {
        callsCol.document(callId).update(
            mapOf("answerSdp" to answerSdp, "state" to CallState.ACCEPTED.name)
        )
    }

    fun observeCall(callId: String): Flow<CallSession?> = callbackFlow {
        val reg = callsCol.document(callId).addSnapshotListener { snap, _ ->
            trySend(snap?.toObject(CallSession::class.java))
        }
        awaitClose { reg.remove() }
    }

    /** Incoming calls for a user — listens for new documents where calleeId == myUid and state == RINGING */
    fun observeIncomingCalls(myUid: String): Flow<CallSession?> = callbackFlow {
        val reg = callsCol
            .whereEqualTo("calleeId", myUid)
            .whereEqualTo("state", CallState.RINGING.name)
            .addSnapshotListener { snap, _ ->
                val call = snap?.documents?.firstOrNull()?.toObject(CallSession::class.java)
                trySend(call)
            }
        awaitClose { reg.remove() }
    }

    fun addIceCandidate(callId: String, ownerUid: String, candidateJson: Map<String, Any?>) {
        callsCol.document(callId)
            .collection("candidates_$ownerUid")
            .add(candidateJson)
    }

    fun observeIceCandidates(callId: String, ownerUid: String): Flow<Map<String, Any?>> = callbackFlow {
        val reg = callsCol.document(callId)
            .collection("candidates_$ownerUid")
            .addSnapshotListener { snap, _ ->
                snap?.documentChanges?.forEach { change ->
                    trySend(change.document.data)
                }
            }
        awaitClose { reg.remove() }
    }
}

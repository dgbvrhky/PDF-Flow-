package com.example.data.firebase

import com.example.base.FirestoreEmulatorTestBase
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class PdfFlowRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun upsertAndObserveDocument_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)
        val docId = "doc_${UUID.randomUUID().toString().replace("-", "").take(12)}"

        val upsertResult = withTimeout(TIMEOUT_MS) {
            repo.upsertDocument(
                CloudDocumentRecord(
                    id = docId,
                    userId = uid,
                    title = "Q4 Invoice",
                    fileName = "q4_invoice.pdf",
                    mimeType = "application/pdf",
                    sizeBytes = 40960L,
                    pageCount = 4L,
                    folderName = "Finance"
                )
            )
        }
        assertTrue("Upsert failed: ${upsertResult.exceptionOrNull()}", upsertResult.isSuccess)

        val emitted = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeDocuments(uid).first { list -> list.any { it.id == docId } }
        }
        assertTrue(emitted.any { it.id == docId && it.title == "Q4 Invoice" })
    }

    @Test
    fun profileSettingsFavoriteAndBackup_authenticatedOwner_succeeds() = runBlocking {
        val uid = signInTestUser(ALICE_EMAIL)
        val repo = CloudSyncRepository(firestore, auth)

        val profileRes = withTimeout(TIMEOUT_MS) {
            repo.upsertProfile("Alice Flow", ALICE_EMAIL, null, null, "free", "google")
        }
        assertTrue("Profile upsert failed: ${profileRes.exceptionOrNull()}", profileRes.isSuccess)

        val settingsRes = withTimeout(TIMEOUT_MS) {
            repo.saveSettings(CloudUserSettings(userId = uid, languageCode = "bn", themeMode = "dark"))
        }
        assertTrue("Settings save failed: ${settingsRes.exceptionOrNull()}", settingsRes.isSuccess)

        val favId = "doc_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val favRes = withTimeout(TIMEOUT_MS) {
            repo.setFavorite(
                CloudFavoriteDocument(
                    documentId = favId,
                    title = "Starred",
                    fileName = "starred.pdf",
                    sizeBytes = 1024L,
                    pageCount = 2L
                )
            )
        }
        assertTrue("Favorite save failed: ${favRes.exceptionOrNull()}", favRes.isSuccess)

        val bakId = withTimeout(TIMEOUT_MS) {
            repo.createBackup("Manual Backup", 1L, 1024L, "Test Device", "Initial backup").getOrThrow()
        }
        val completeRes = withTimeout(TIMEOUT_MS) {
            repo.completeBackup(bakId, "completed", 1L, 1024L, "Done")
        }
        assertTrue("Complete backup failed: ${completeRes.exceptionOrNull()}", completeRes.isSuccess)
    }

    @Test
    fun crossUserDocumentAccess_deniedWithPermissionDenied() = runBlocking {
        val aliceUid = signInTestUser(ALICE_EMAIL)
        val aliceRepo = CloudSyncRepository(firestore, auth)
        val docId = "doc_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        withTimeout(TIMEOUT_MS) {
            aliceRepo.upsertDocument(
                CloudDocumentRecord(
                    id = docId,
                    userId = aliceUid,
                    title = "Alice Secret",
                    fileName = "secret.pdf",
                    sizeBytes = 2048L,
                    pageCount = 1L
                )
            ).getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = CloudSyncRepository(firestore, auth)
        val result = withTimeout(TIMEOUT_MS) {
            bobRepo.getDocument(aliceUid, docId)
        }
        assertTrue(result.isFailure)
        val fsEx = result.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    @Test
    fun unauthenticatedObserveDocuments_deniedWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = CloudSyncRepository(firestore, auth)
        var caught: Throwable? = null
        try {
            withTimeout(FLOW_TIMEOUT_MS) {
                repo.observeDocuments("some_uid_123").first()
            }
        } catch (t: Throwable) {
            caught = t
        }
        val fsEx = caught?.unwrapFirestoreException()
        assertNotNull("Expected FirebaseFirestoreException, got $caught", fsEx)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsEx?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pdfflow@test.com"
        const val BOB_EMAIL = "bob_pdfflow@test.com"
        const val TIMEOUT_MS = 5000L
        const val FLOW_TIMEOUT_MS = 4000L
    }
}

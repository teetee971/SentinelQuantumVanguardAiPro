package com.sentinel.quantum.ai

import android.app.Activity
import android.app.KeyguardManager
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import android.os.Bundle
import android.security.NetworkSecurityPolicy
import android.text.InputType
import android.view.WindowManager
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.atomic.AtomicReference

/** Internal experimental entry point. No Phone Core action is registered. No input/proof is saved.
 * Journal/engine/action I/O is serialized off main. A credential prompt is the sole stop exemption;
 * its result still needs a live exact ticket, foreground consent and the authority's expiry checks.
 */
class SentinelAiActivity : ComponentActivity() {
    companion object {
        private val processGate = SentinelAiProcessGate()
        private val pairingGate = SentinelAiPairingCoordinator()
        private val revocationWorker = Executors.newSingleThreadExecutor()
        // The registry and UI acknowledgement are confined to the Android main thread.
        private val activities = mutableSetOf<SentinelAiActivity>()
        private var processAcknowledgementPending = false
    }
    private data class Runtime(val owner: SentinelAiApprovalPorts.Owner,
                               val session: SentinelAiApprovalPorts.Session, val journal: SentinelAiAuditJournal,
                               val lease: SentinelAiProcessGate.Lease)
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var generation = 0L
    private var runtime: Runtime? = null
    private var consentLease: SentinelAiProcessGate.Lease? = null
    private val engine = AtomicReference<SentinelAiLocalEngine?>(null)
    private val pairingLease = AtomicReference<SentinelAiPairingCoordinator.Lease?>(null)
    private val proof = SentinelAiHumanConfirmation(SentinelAiAndroidClock::elapsedRealtimeMs)
    @Volatile private var foreground = false
    @Volatile private var consent = false
    private var busy = false
    private var expiryTask: Runnable? = null
    private var clearing = false
    private var pending: SentinelAiApprovalAuthority.Proposal? = null
    private var ticket: SentinelAiHumanConfirmation.Ticket? = null
    private lateinit var consentView: CheckBox
    private lateinit var rememberPairing: CheckBox
    private lateinit var pairing: EditText
    private lateinit var message: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var send: Button
    private lateinit var approve: Button
    private var credential: ActivityResultLauncher<Intent>? = null
    private fun Runtime.record(kind: SentinelAiAuditJournal.Kind) {
        processGate.execute(lease) { journal.append(kind, SentinelAiAndroidClock.elapsedRealtimeMs()) }
    }
    private fun credentialResult(current: SentinelAiHumanConfirmation.Ticket, resultCode: Int) {
        // The callback captures the issued ticket, never reads a replacement as its result target.
        if (ticket !== current) return
        credential?.unregister(); credential = null
        ticket = null
        val proposal = current.proposal
        val active = synchronized(lock) { runtime }
        val epoch = epoch()
        pending = null
        if (!valid(epoch) || active == null || !proof.complete(current, resultCode == Activity.RESULT_OK)) {
            proof.revoke()
            active?.owner?.decisions?.reject(proposal)
            if (active != null) worker.execute {
                try { active.record(SentinelAiAuditJournal.Kind.REJECTED) }
                catch (_: Exception) { /* No authorization or effect follows a failed audit write. */ }
            }
            busy = false; status.text = "Authentification annulée, expirée ou refusée."; refresh()
            return
        }
        worker.execute {
            val text = try {
                check(valid(epoch))
                active.record(SentinelAiAuditJournal.Kind.AUTHENTICATED)
                if (!active.owner.decisions.approve(proposal)) {
                    active.record(SentinelAiAuditJournal.Kind.REJECTED)
                    "Approbation refusée."
                } else when (active.owner.decisions.execute(proposal)) {
                    SentinelAiApprovalAuthority.ExecutionResult.EXECUTED -> "Point de contrôle enregistré dans le journal IA."
                    SentinelAiApprovalAuthority.ExecutionResult.REJECTED -> {
                        active.record(SentinelAiAuditJournal.Kind.REJECTED)
                        "Action refusée."
                    }
                    SentinelAiApprovalAuthority.ExecutionResult.OUTCOME_UNKNOWN -> {
                        try { active.record(SentinelAiAuditJournal.Kind.ERROR) }
                        catch (_: Exception) { /* Outcome remains unknown. */ }
                        "Résultat du journal incertain ; aucune relance automatique."
                    }
                }
            } catch (_: Exception) {
                active.owner.decisions.reject(proposal)
                "Action refusée : sécurité ou journal indisponible."
            }
            ui(epoch) { busy = false; status.text = text; refresh() }
        }
    }
    private fun epoch(): Long = synchronized(lock) { generation }
    private fun valid(epoch: Long): Boolean = synchronized(lock) {
        foreground && consent && generation == epoch &&
            (consentLease?.let { processGate.isActive(it) } == true) &&
            (runtime?.let { processGate.isActive(it.lease) } ?: true)
    }
    private fun localPolicy(): Boolean = NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted("127.0.0.1")
    private fun secureDevice(): Boolean = getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
    private fun ui(epoch: Long, block: () -> Unit) = runOnUiThread {
        if (!isDestroyed && foreground && this.epoch() == epoch) block()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(24, 24, 24, 24) }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            content.importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        fun label(text: String) = TextView(this).apply { this.text = text; content.addView(this) }
        label("Agent IA local — expérimental, non qualifié")
        label("Le modèle doit fonctionner séparément sur ce téléphone. Aucun accès aux appels, SMS, MMS ou VPN n’est autorisé.")
        consentView = CheckBox(this).apply {
            text = "J’autorise uniquement l’analyse locale du texte saisi pour cette session."
            tag = "sentinel_ai_consent"; isSaveEnabled = false; content.addView(this)
        }
        pairing = EditText(this).apply {
            hint = "Clé d’appariement locale : 64 caractères hexadécimaux"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSaveEnabled = false; content.addView(this)
        }
        rememberPairing = CheckBox(this).apply {
            text = "Mémoriser l’appariement sur cet appareil"
            isSaveEnabled = false
            isChecked = SentinelAiPairingStore.exists(this@SentinelAiActivity)
            content.addView(this)
        }
        Button(this).apply {
            text = "Oublier l’appariement mémorisé"
            content.addView(this)
            setOnClickListener {
                // Invalidate all older requests immediately, before queued deletion.
                val deletion = pairingGate.beginForget()
                revoke()
                val forgetEpoch = epoch()
                rememberPairing.isChecked = false
                busy = true
                refresh()
                worker.execute {
                    val forgotten = try {
                        pairingGate.finishForget(deletion) {
                            SentinelAiPairingStore.forget(this@SentinelAiActivity)
                        }
                        true
                    } catch (_: Exception) { false }
                    ui(forgetEpoch) {
                        busy = false
                        status.text = if (forgotten) "Appariement oublié sur cet appareil ; le secret du service n’est pas révoqué."
                            else "Échec de suppression ; appariement bloqué jusqu’à un nouvel oubli réussi."
                        refresh()
                    }
                }
            }
        }
        message = EditText(this).apply {
            hint = "Demande au modèle local (512 caractères maximum)"
            filters = arrayOf(android.text.InputFilter.LengthFilter(512))
            isSaveEnabled = false; content.addView(this)
        }
        send = Button(this).apply { text = "Demander une proposition"; tag = "sentinel_ai_send"; content.addView(this) }
        label("Réponse du modèle (ne constitue pas une autorisation) :")
        output = label("").apply { setTextIsSelectable(true) }
        Button(this).apply {
            text = "Copier la réponse et le statut"
            tag = "sentinel_ai_copy"
            content.addView(this)
            setOnClickListener {
                val reply = output.text.toString()
                if (reply.isBlank()) {
                    Toast.makeText(this@SentinelAiActivity, "Aucune réponse à copier.", Toast.LENGTH_SHORT).show()
                } else {
                    val clipboard = getSystemService(android.content.ClipboardManager::class.java)
                    clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                        "Réponse IA locale",
                        reply + "\n\nStatut : " + status.text.toString()
                    ))
                    Toast.makeText(this@SentinelAiActivity, "Réponse et statut copiés.", Toast.LENGTH_SHORT).show()
                }
            }
        }
        label("Seule action disponible : enregistrer un point de contrôle sans données personnelles dans le journal IA.")
        approve = Button(this).apply { text = "Confirmer le point de contrôle avec le verrouillage Android"; content.addView(this) }
        val cancel = Button(this).apply { text = "Annuler et révoquer la session"; content.addView(this); setOnClickListener { revoke() } }
        Button(this).apply {
            text = "Révoquer les autorisations IA de tous les écrans (ce processus)"
            tag = "sentinel_ai_revoke_all"; content.addView(this)
            setOnClickListener { revokeProcess() }
        }
        status = label(if (localPolicy()) "Indisponible jusqu’au consentement et à la connexion au moteur local."
                      else "Indisponible : le transport local HTTP n’est pas autorisé par la politique Android actuelle.")
        status.tag = "sentinel_ai_status"
        status.setTextIsSelectable(true)
        setContentView(ScrollView(this).apply { addView(content) })
        consentView.setOnCheckedChangeListener { _, checked ->
            if (clearing) return@setOnCheckedChangeListener
            if (!checked) {
                revoke()
                return@setOnCheckedChangeListener
            }
            // Only the foreground user's consent can capture a lease.
            val lease = if (foreground && !processAcknowledgementPending) {
                try { processGate.capture() } catch (_: IllegalStateException) { null }
            } else null
            synchronized(lock) {
                consentLease = lease
                consent = lease != null
            }
            if (lease == null) {
                clearConsentView()
                if (!processAcknowledgementPending) status.text = "Consentement indisponible."
            }
            refresh()
        }
        send.setOnClickListener { request() }
        approve.setOnClickListener { authenticate() }
        activities.add(this)
        refresh()
    }
    private fun refresh() {
        consentView.isEnabled = !processAcknowledgementPending && !processGate.isRevoking()
        send.isEnabled = valid(epoch()) && !busy && localPolicy()
        approve.isEnabled = valid(epoch()) && !busy && pending != null && secureDevice()
    }
    override fun onStart() { foreground = true; super.onStart(); if (::send.isInitialized) refresh() }
    override fun onStop() {
        foreground = false
        engine.getAndSet(null)?.close()
        // The process request already cleared every activity and blocks new consent.
        if (ticket == null && !processAcknowledgementPending) revoke()
        super.onStop()
    }
    override fun onDestroy() {
        activities.remove(this)
        foreground = false
        revoke()
        worker.shutdown()
        super.onDestroy()
    }
    private fun state(epoch: Long): Runtime {
        synchronized(lock) { runtime?.let { check(valid(epoch)); return it } }
        check(valid(epoch))
        val lease = synchronized(lock) { checkNotNull(consentLease) }
        val journal = processGate.execute(lease) {
            check(valid(epoch))
            SentinelAiAndroidAuditJournal.open(this).also {
                it.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, SentinelAiAndroidClock.elapsedRealtimeMs())
            }
        }
        val owner = SentinelAiAndroidClock.createOwner(mapOf(
            "CREATE_AUDIT_CHECKPOINT" to SentinelAiActionPolicy(
                validate = { it.isEmpty() },
                permitted = { valid(epoch) && secureDevice() && journal.verify().sequence < SentinelAiAuditJournal.MAX_EVENTS },
                execute = {
                    processGate.execute(lease) {
                        check(valid(epoch) && secureDevice())
                        journal.append(SentinelAiAuditJournal.Kind.CHECKPOINT, SentinelAiAndroidClock.elapsedRealtimeMs())
                    }
                    Unit
                }
            )
        ), authenticated = { proposal -> valid(epoch) && secureDevice() && proof.consume(proposal) })
        val result = Runtime(owner, owner.openSessionScope(), journal, lease)
        val installed = synchronized(lock) {
            if (valid(epoch) && processGate.isActive(lease)) {
                runtime = result
                true
            } else false
        }
        if (!installed) {
            owner.revokeAll()
            result.session.close()
            error("Session closed")
        }
        return result
    }
    private fun request() {
        if (!foreground || !consent || busy || !localPolicy()) return
        if (!valid(epoch())) { revoke(); status.text = "Session invalidée ; un nouveau consentement est requis."; return }
        val raw = pairing.text.toString().trim()
        val remember = rememberPairing.isChecked
        val useStored = raw.isEmpty() && remember
        if ((!useStored && !Regex("[0-9a-fA-F]{64}").matches(raw)) || message.text.isEmpty()) {
            status.text = "Clé locale ou demande invalide."; return
        }
        val pairingToken = try { pairingGate.capture() } catch (_: Exception) {
            status.text = "Appariement bloqué : terminer ou renouveler son oubli."; return
        }
        pairingLease.getAndSet(pairingToken)?.let { pairingGate.revoke(it) }
        val key = if (useStored) ByteArray(32) else ByteArray(32) {
            raw.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
        val text = message.text.toString()
        pairing.text.clear(); message.text.clear()
        val epoch = epoch()
        val active = synchronized(lock) { runtime }
        pending?.let { active?.owner?.decisions?.reject(it) }; pending = null
        busy = true; status.text = "Analyse locale en cours…"; refresh()
        worker.execute {
            try {
                val current = state(epoch)
                current.record(SentinelAiAuditJournal.Kind.REQUEST)
                if (useStored) {
                    val saved = pairingGate.use(pairingToken) {
                        processGate.execute(current.lease) {
                            check(valid(epoch))
                            SentinelAiPairingStore.load(this)
                        }
                    }
                    try { saved.copyInto(key) } finally { saved.fill(0) }
                }
                val local = SentinelAiLocalEngine(key)
                synchronized(lock) { if (!valid(epoch)) { local.close(); error("Session closed") }; engine.set(local) }
                val answer = local.use { it.infer(text) }
                engine.compareAndSet(local, null)
                check(valid(epoch))
                if (remember) pairingGate.use(pairingToken) {
                    processGate.execute(current.lease) {
                        check(valid(epoch))
                        SentinelAiPairingStore.save(this, key)
                    }
                }
                check(valid(epoch))
                val proposal = if (answer.action == "CREATE_AUDIT_CHECKPOINT") current.session.propose(answer.action, "") else null
                if (proposal != null) current.record(SentinelAiAuditJournal.Kind.PROPOSED)
                ui(epoch) {
                    busy = false; output.text = answer.reply; pending = proposal
                    status.text = if (proposal == null) "Réponse reçue ; aucune action proposée."
                                  else if (!secureDevice()) "Proposition non exécutable : verrouillage Android sécurisé requis."
                                  else "Proposition en attente de votre confirmation. Aucune action exécutée."
                    if (proposal != null) {
                        expiryTask?.let { send.removeCallbacks(it) }
                        expiryTask = Runnable {
                            if (!isDestroyed && foreground && pending == proposal && ticket == null) {
                                pending = null; proof.revoke(); current.owner.decisions.reject(proposal)
                                status.text = "Proposition expirée ; aucune action exécutée."; refresh()
                            }
                        }.also { send.postDelayed(it, (proposal.expiresAtMs - SentinelAiAndroidClock.elapsedRealtimeMs()).coerceAtLeast(0)) }
                    }
                    refresh()
                }
            } catch (_: Exception) {
                ui(epoch) { revoke(); status.text = "Indisponible : moteur, appariement, sécurité ou journal en échec." }
            } finally {
                key.fill(0)
                pairingLease.compareAndSet(pairingToken, null)
                pairingGate.revoke(pairingToken)
                engine.getAndSet(null)?.close()
            }
        }
    }
    @Suppress("DEPRECATION")
    private fun authenticate() {
        val proposal = pending ?: return
        if (!foreground || !consent || busy || !secureDevice()) return
        if (!valid(epoch())) { revoke(); status.text = "Session invalidée ; authentification refusée."; return }
        val intent = getSystemService(KeyguardManager::class.java)?.createConfirmDeviceCredentialIntent(
            "Confirmer le point de contrôle IA", "Cette action écrit uniquement un événement sans contenu personnel dans le journal IA.")
        if (intent == null) { status.text = "Authentification Android indisponible."; return }
        try {
            val issued = proof.begin(proposal)
            ticket = issued
            // A unique registry key binds every OS result to this exact immutable ticket.
            credential = activityResultRegistry.register("sentinel-ai-" + java.util.UUID.randomUUID(),
                ActivityResultContracts.StartActivityForResult()) { result -> credentialResult(issued, result.resultCode) }
            busy = true; refresh(); credential?.launch(intent)
        } catch (_: Exception) { ticket = null; revoke(); status.text = "Authentification non démarrée ; action refusée." }
    }
    private fun revokeProcess() {
        if (processAcknowledgementPending) return
        val unconfirmed = "Révocation du processus non confirmée ; aucune réussite revendiquée."
        val revocation = try {
            processGate.requestRevocation()
        } catch (_: IllegalStateException) {
            status.text = unconfirmed
            return
        }
        processAcknowledgementPending = true
        val epochs = activities.associateWith { activity ->
            val epoch = try {
                activity.revoke(processRevocation = true)
                activity.epoch()
            } catch (_: Exception) { null }
            activity.status.text = "Révocation des autorisations IA du processus demandée…"
            epoch
        }
        fun acknowledge(result: String) {
            processAcknowledgementPending = false
            activities.forEach { activity ->
                if (activity in epochs && (epochs[activity] == null || activity.epoch() == epochs[activity])) {
                    activity.status.text = result
                }
                activity.refresh()
            }
        }
        if (epochs.containsValue(null)) {
            // Keep the gate closed. Another explicit request can retry this revocation.
            acknowledge(unconfirmed)
            return
        }
        val context = applicationContext
        try {
            revocationWorker.execute {
                val revoked = try { processGate.awaitRevocation(revocation); true } catch (_: Exception) { false }
                // Denial must succeed independently of a full, corrupt or unavailable audit store.
                val recorded = if (revoked) try {
                    SentinelAiAndroidAuditJournal.open(context).append(SentinelAiAuditJournal.Kind.PROCESS_REVOKED,
                        SentinelAiAndroidClock.elapsedRealtimeMs())
                    true
                } catch (_: Exception) { false } else false
                val result = if (revoked && recorded) "Autorisations IA révoquées dans ce processus et événement journalisé."
                             else if (revoked) "Autorisations IA révoquées dans ce processus ; journal indisponible."
                             else unconfirmed
                runOnUiThread { acknowledge(result) }
            }
        } catch (_: RejectedExecutionException) {
            acknowledge(unconfirmed)
        }
    }
    private fun clearConsentView() {
        clearing = true
        try { consentView.isChecked = false } finally { clearing = false }
    }
    private fun revoke(processRevocation: Boolean = false) {
        val oldPairing = pairingLease.getAndSet(null)
        proof.revoke()
        credential?.unregister(); credential = null
        if (::send.isInitialized) expiryTask?.let { send.removeCallbacks(it) }
        expiryTask = null
        val epoch: Long
        val old: Runtime?
        val oldEngine: SentinelAiLocalEngine?
        synchronized(lock) {
            generation++
            epoch = generation
            old = runtime
            runtime = null
            consentLease = null
            consent = false
            oldEngine = engine.getAndSet(null)
        }
        oldEngine?.close()
        // Process revocation has already denied access. Let an engaged storage operation drain off main.
        if (!processRevocation) oldPairing?.let(pairingGate::revoke)
        pending = null; ticket = null; busy = false
        if (::consentView.isInitialized) {
            clearConsentView()
            pairing.text.clear(); message.text.clear(); output.text = ""
            status.text = "Révocation demandée."; refresh()
        }
        worker.execute {
            if (processRevocation) oldPairing?.let(pairingGate::revoke)
            old?.owner?.revokeAll(); old?.session?.close()
            val recorded = try { old?.journal?.append(SentinelAiAuditJournal.Kind.REVOKED, SentinelAiAndroidClock.elapsedRealtimeMs()); true }
                           catch (_: Exception) { false }
            if (!processRevocation) ui(epoch) {
                if (!busy && !processAcknowledgementPending) {
                    status.text = if (recorded) "Session révoquée." else "Session révoquée ; journal indisponible."
                }
            }
        }
    }
}

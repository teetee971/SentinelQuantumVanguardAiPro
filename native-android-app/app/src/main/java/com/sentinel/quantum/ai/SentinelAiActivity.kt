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
import java.util.concurrent.atomic.AtomicReference

/** Internal experimental entry point. No Phone Core action is registered. No input/proof is saved.
 * Journal/engine/action I/O is serialized off main. A credential prompt is the sole stop exemption;
 * its result still needs a live exact ticket, foreground consent and the authority's expiry checks.
 */
class SentinelAiActivity : ComponentActivity() {
    private data class Runtime(val owner: SentinelAiApprovalPorts.Owner,
                               val session: SentinelAiApprovalPorts.Session, val journal: SentinelAiAuditJournal)
    private val worker = Executors.newSingleThreadExecutor()
    private val lock = Any()
    private var generation = 0L
    private var runtime: Runtime? = null
    private val engine = AtomicReference<SentinelAiLocalEngine?>(null)
    private val proof = SentinelAiHumanConfirmation(SentinelAiAndroidClock::elapsedRealtimeMs)
    @Volatile private var foreground = false
    @Volatile private var consent = false
    private var busy = false
    private var expiryTask: Runnable? = null
    private var clearing = false
    private var pending: SentinelAiApprovalAuthority.Proposal? = null
    private var ticket: SentinelAiHumanConfirmation.Ticket? = null
    private lateinit var consentView: CheckBox
    private lateinit var pairing: EditText
    private lateinit var message: EditText
    private lateinit var output: TextView
    private lateinit var status: TextView
    private lateinit var send: Button
    private lateinit var approve: Button
    private var credential: ActivityResultLauncher<Intent>? = null
    private fun credentialResult(current: SentinelAiHumanConfirmation.Ticket, resultCode: Int) {
        // The callback captures the issued ticket, never reads a replacement as its result target.
        if (ticket !== current) return
        credential?.unregister(); credential = null
        ticket = null
        val proposal = current.proposal
        val active = synchronized(lock) { runtime }
        val epoch = epoch()
        pending = null
        if (!foreground || !consent || active == null || !proof.complete(current, resultCode == Activity.RESULT_OK)) {
            proof.revoke()
            active?.owner?.decisions?.reject(proposal)
            if (active != null) worker.execute {
                try { active.journal.append(SentinelAiAuditJournal.Kind.REJECTED, SentinelAiAndroidClock.elapsedRealtimeMs()) }
                catch (_: Exception) { /* No authorization or effect follows a failed audit write. */ }
            }
            busy = false; status.text = "Authentification annulée, expirée ou refusée."; refresh()
            return
        }
        worker.execute {
            val text = try {
                check(valid(epoch))
                active.journal.append(SentinelAiAuditJournal.Kind.AUTHENTICATED, SentinelAiAndroidClock.elapsedRealtimeMs())
                if (!active.owner.decisions.approve(proposal)) {
                    active.journal.append(SentinelAiAuditJournal.Kind.REJECTED, SentinelAiAndroidClock.elapsedRealtimeMs())
                    "Approbation refusée."
                } else when (active.owner.decisions.execute(proposal)) {
                    SentinelAiApprovalAuthority.ExecutionResult.EXECUTED -> "Point de contrôle enregistré dans le journal IA."
                    SentinelAiApprovalAuthority.ExecutionResult.REJECTED -> {
                        active.journal.append(SentinelAiAuditJournal.Kind.REJECTED, SentinelAiAndroidClock.elapsedRealtimeMs())
                        "Action refusée."
                    }
                    SentinelAiApprovalAuthority.ExecutionResult.OUTCOME_UNKNOWN -> {
                        try { active.journal.append(SentinelAiAuditJournal.Kind.ERROR, SentinelAiAndroidClock.elapsedRealtimeMs()) }
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
    private fun valid(epoch: Long): Boolean = synchronized(lock) { foreground && consent && generation == epoch }
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
        message = EditText(this).apply {
            hint = "Demande au modèle local (512 caractères maximum)"
            filters = arrayOf(android.text.InputFilter.LengthFilter(512))
            isSaveEnabled = false; content.addView(this)
        }
        send = Button(this).apply { text = "Demander une proposition"; tag = "sentinel_ai_send"; content.addView(this) }
        label("Réponse du modèle (ne constitue pas une autorisation) :")
        output = label("")
        label("Seule action disponible : enregistrer un point de contrôle sans données personnelles dans le journal IA.")
        approve = Button(this).apply { text = "Confirmer le point de contrôle avec le verrouillage Android"; content.addView(this) }
        val cancel = Button(this).apply { text = "Annuler et révoquer la session"; content.addView(this); setOnClickListener { revoke() } }
        status = label(if (localPolicy()) "Indisponible jusqu’au consentement et à la connexion au moteur local."
                      else "Indisponible : le transport local HTTP n’est pas autorisé par la politique Android actuelle.")
        setContentView(ScrollView(this).apply { addView(content) })
        consentView.setOnCheckedChangeListener { _, checked ->
            consent = checked
            if (!clearing && !checked) revoke()
            refresh()
        }
        send.setOnClickListener { request() }
        approve.setOnClickListener { authenticate() }
        refresh()
    }
    private fun refresh() {
        send.isEnabled = foreground && consent && !busy && localPolicy()
        approve.isEnabled = foreground && consent && !busy && pending != null && secureDevice()
    }
    override fun onStart() { foreground = true; super.onStart(); if (::send.isInitialized) refresh() }
    override fun onStop() {
        foreground = false
        engine.getAndSet(null)?.close()
        if (ticket == null) revoke()
        super.onStop()
    }
    override fun onDestroy() { foreground = false; revoke(); worker.shutdown(); super.onDestroy() }
    private fun state(epoch: Long): Runtime {
        synchronized(lock) { runtime?.let { return it } }
        check(valid(epoch))
        val journal = SentinelAiAndroidAuditJournal.open(this)
        journal.append(SentinelAiAuditJournal.Kind.SESSION_OPEN, SentinelAiAndroidClock.elapsedRealtimeMs())
        val owner = SentinelAiAndroidClock.createOwner(mapOf(
            "CREATE_AUDIT_CHECKPOINT" to SentinelAiActionPolicy(
                validate = { it.isEmpty() },
                permitted = { valid(epoch) && secureDevice() && journal.verify().sequence < SentinelAiAuditJournal.MAX_EVENTS },
                execute = { journal.append(SentinelAiAuditJournal.Kind.CHECKPOINT, SentinelAiAndroidClock.elapsedRealtimeMs()); Unit }
            )
        ), authenticated = { proposal -> valid(epoch) && secureDevice() && proof.consume(proposal) })
        val result = Runtime(owner, owner.openSessionScope(), journal)
        synchronized(lock) {
            if (!valid(epoch)) { owner.revokeAll(); result.session.close(); error("Session closed") }
            runtime = result
        }
        return result
    }
    private fun request() {
        if (!foreground || !consent || busy || !localPolicy()) return
        val raw = pairing.text.toString().trim()
        if (!Regex("[0-9a-fA-F]{64}").matches(raw) || message.text.isEmpty()) {
            status.text = "Clé locale ou demande invalide."; return
        }
        val key = ByteArray(32) { raw.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
        val text = message.text.toString()
        pairing.text.clear(); message.text.clear()
        val epoch = epoch()
        val active = synchronized(lock) { runtime }
        pending?.let { active?.owner?.decisions?.reject(it) }; pending = null
        busy = true; status.text = "Analyse locale en cours…"; refresh()
        worker.execute {
            try {
                val current = state(epoch)
                current.journal.append(SentinelAiAuditJournal.Kind.REQUEST, SentinelAiAndroidClock.elapsedRealtimeMs())
                val local = SentinelAiLocalEngine(key)
                key.fill(0)
                synchronized(lock) { if (!valid(epoch)) { local.close(); error("Session closed") }; engine.set(local) }
                val answer = local.use { it.infer(text) }
                engine.compareAndSet(local, null)
                check(valid(epoch))
                val proposal = if (answer.action == "CREATE_AUDIT_CHECKPOINT") current.session.propose(answer.action, "") else null
                if (proposal != null) current.journal.append(SentinelAiAuditJournal.Kind.PROPOSED, SentinelAiAndroidClock.elapsedRealtimeMs())
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
            } finally { key.fill(0); engine.getAndSet(null)?.close() }
        }
    }
    @Suppress("DEPRECATION")
    private fun authenticate() {
        val proposal = pending ?: return
        if (!foreground || !consent || busy || !secureDevice()) return
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
    private fun revoke() {
        proof.revoke()
        credential?.unregister(); credential = null
        if (::send.isInitialized) expiryTask?.let { send.removeCallbacks(it) }
        expiryTask = null
        val epoch: Long
        val old: Runtime?
        synchronized(lock) { generation++; epoch = generation; old = runtime; runtime = null; engine.getAndSet(null)?.close() }
        pending = null; ticket = null; consent = false; busy = false
        if (::consentView.isInitialized) {
            clearing = true; consentView.isChecked = false; clearing = false
            pairing.text.clear(); message.text.clear(); output.text = ""
            status.text = "Révocation demandée."; refresh()
        }
        worker.execute {
            old?.owner?.revokeAll(); old?.session?.close()
            val recorded = try { old?.journal?.append(SentinelAiAuditJournal.Kind.REVOKED, SentinelAiAndroidClock.elapsedRealtimeMs()); true }
                           catch (_: Exception) { false }
            ui(epoch) { if (!busy) status.text = if (recorded) "Session révoquée." else "Session révoquée ; journal indisponible." }
        }
    }
}

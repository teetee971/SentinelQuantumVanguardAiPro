import React, { useState } from "react";
import axios from "axios";

export default function VerificationProfessionnel() {
  const [form, setForm] = useState({
    companyName: "",
    siret: "",
    vatNumber: "",
    companyAddress: "",
    website: "",
    contactName: "",
    contactEmail: "",
    contactPhone: "",
    role: "",
    consent: false,
  });
  const [files, setFiles] = useState({
    kbis: null,
    signatoryId: null,
    hqProof: null,
  });
  const [status, setStatus] = useState({ loading: false, error: "", ok: false });

  const onChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));
  const onFile = (e) =>
    setFiles((fs) => ({ ...fs, [e.target.name]: e.target.files?.[0] || null }));

  const validate = () => {
    if (!form.companyName || !form.companyAddress || !form.contactName || !form.contactEmail || !form.contactPhone)
      return "Merci de compléter les champs requis.";
    if (form.contactEmail && !/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.contactEmail)) return "Email de contact invalide.";
    if (!files.kbis || !files.signatoryId) return "Merci d’ajouter un extrait Kbis et l’identité du signataire.";
    if (!form.consent) return "Vous devez accepter le traitement des données.";
    return "";
  };

  const submit = async (e) => {
    e.preventDefault();
    const err = validate();
    if (err) return setStatus({ loading: false, error: err, ok: false });

    try {
      setStatus({ loading: true, error: "", ok: false });
      const fd = new FormData();
      Object.entries(form).forEach(([k, v]) => fd.append(k, String(v)));
      fd.append("kbis", files.kbis);
      fd.append("signatoryId", files.signatoryId);
      if (files.hqProof) fd.append("hqProof", files.hqProof);

      await axios.post("/api/verification/professionnel", fd, {
        headers: { "Content-Type": "multipart/form-data" },
      });
      setStatus({ loading: false, error: "", ok: true });
    } catch (e2) {
      setStatus({
        loading: false,
        error: e2?.response?.data?.message || "Une erreur est survenue.",
        ok: false,
      });
    }
  };

  return (
    <main className="max-w-4xl mx-auto p-5 md:p-10">
      <h1 className="text-2xl font-semibold mb-2">Vérification — Compte Professionnel</h1>
      <p className="text-sm text-gray-400 mb-6">
        Fournissez les informations et les documents requis pour activer les fonctionnalités Pro/Business/Enterprise.
      </p>

      {status.ok && (
        <div className="p-3 mb-4 rounded bg-green-900/40 border border-green-600 text-green-200">
          Votre dossier a été soumis. Un retour vous sera fait rapidement.
        </div>
      )}
      {status.error && (
        <div className="p-3 mb-4 rounded bg-red-900/40 border border-red-600 text-red-200">{status.error}</div>
      )}

      <form onSubmit={submit} className="space-y-6">
        <section className="grid md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">Raison sociale</label>
            <input
              name="companyName"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.companyName}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">SIRET (optionnel)</label>
            <input
              name="siret"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.siret}
              onChange={onChange}
            />
          </div>
          <div>
            <label className="block text-sm mb-1">TVA (optionnel)</label>
            <input
              name="vatNumber"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.vatNumber}
              onChange={onChange}
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Site web (optionnel)</label>
            <input
              name="website"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.website}
              onChange={onChange}
            />
          </div>
          <div className="md:col-span-2">
            <label className="block text-sm mb-1">Adresse du siège</label>
            <input
              name="companyAddress"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.companyAddress}
              onChange={onChange}
              required
            />
          </div>
        </section>

        <section className="grid md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm mb-1">Contact — Nom</label>
            <input
              name="contactName"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.contactName}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Contact — Email</label>
            <input
              type="email"
              name="contactEmail"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.contactEmail}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Contact — Téléphone</label>
            <input
              name="contactPhone"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.contactPhone}
              onChange={onChange}
              required
            />
          </div>
          <div className="md:col-span-3">
            <label className="block text-sm mb-1">Rôle/Fonction</label>
            <input
              name="role"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.role}
              onChange={onChange}
            />
          </div>
        </section>

        <section className="grid md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm mb-1">Extrait Kbis</label>
            <input type="file" name="kbis" accept="application/pdf,image/*" onChange={onFile} />
          </div>
          <div>
            <label className="block text-sm mb-1">Pièce identité signataire</label>
            <input type="file" name="signatoryId" accept="application/pdf,image/*" onChange={onFile} />
          </div>
          <div>
            <label className="block text-sm mb-1">Justificatif siège (optionnel)</label>
            <input type="file" name="hqProof" accept="application/pdf,image/*" onChange={onFile} />
          </div>
        </section>

        <div className="flex items-center gap-2">
          <input type="checkbox" id="consent" name="consent" checked={form.consent} onChange={onChange} />
          <label htmlFor="consent" className="text-sm">
            Nous acceptons le traitement des données et confirmons l’exactitude des informations.
          </label>
        </div>

        <button
          disabled={status.loading}
          className="px-4 py-2 rounded bg-sentinel-accent/80 hover:bg-sentinel-accent disabled:opacity-50"
        >
          {status.loading ? "Envoi..." : "Soumettre"}
        </button>
      </form>
    </main>
  );
}
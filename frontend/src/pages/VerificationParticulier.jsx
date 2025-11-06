import React, { useState } from "react";
import axios from "axios";

export default function VerificationParticulier() {
  const [form, setForm] = useState({
    fullName: "",
    birthDate: "",
    address: "",
    email: "",
    phone: "",
    idType: "CNI",
    idNumber: "",
    consent: false,
  });
  const [files, setFiles] = useState({
    idFront: null,
    idBack: null,
    proofAddress: null,
  });
  const [status, setStatus] = useState({ loading: false, error: "", ok: false });

  const onChange = (e) =>
    setForm((f) => ({ ...f, [e.target.name]: e.target.type === "checkbox" ? e.target.checked : e.target.value }));
  const onFile = (e) =>
    setFiles((fs) => ({ ...fs, [e.target.name]: e.target.files?.[0] || null }));

  const validate = () => {
    if (!form.fullName || !form.birthDate || !form.address || !form.email || !form.phone || !form.idNumber)
      return "Tous les champs requis doivent être remplis.";
    if (!/^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(form.email)) return "Email invalide.";
    if (!files.idFront || !files.idBack || !files.proofAddress)
      return "Merci d’ajouter les justificatifs (recto/verso + justificatif de domicile).";
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
      fd.append("idFront", files.idFront);
      fd.append("idBack", files.idBack);
      fd.append("proofAddress", files.proofAddress);

      await axios.post("/api/verification/particulier", fd, {
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
    <main className="max-w-3xl mx-auto p-5 md:p-10">
      <h1 className="text-2xl font-semibold mb-2">Vérification d’identité — Particulier</h1>
      <p className="text-sm text-gray-400 mb-6">
        Vos informations sont traitées conformément à notre politique de confidentialité.
      </p>

      {status.ok && (
        <div className="p-3 mb-4 rounded bg-green-900/40 border border-green-600 text-green-200">
          Votre demande a été soumise. Nous reviendrons vers vous rapidement.
        </div>
      )}
      {status.error && (
        <div className="p-3 mb-4 rounded bg-red-900/40 border border-red-600 text-red-200">{status.error}</div>
      )}

      <form onSubmit={submit} className="space-y-6">
        <section className="grid md:grid-cols-2 gap-4">
          <div>
            <label className="block text-sm mb-1">Nom complet</label>
            <input
              name="fullName"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.fullName}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Date de naissance</label>
            <input
              type="date"
              name="birthDate"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.birthDate}
              onChange={onChange}
              required
            />
          </div>
          <div className="md:col-span-2">
            <label className="block text-sm mb-1">Adresse</label>
            <input
              name="address"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.address}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Email</label>
            <input
              type="email"
              name="email"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.email}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Téléphone</label>
            <input
              name="phone"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.phone}
              onChange={onChange}
              required
            />
          </div>
          <div>
            <label className="block text-sm mb-1">Type de pièce</label>
            <select
              name="idType"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.idType}
              onChange={onChange}
            >
              <option value="CNI">CNI</option>
              <option value="Passeport">Passeport</option>
              <option value="Permis">Permis</option>
            </select>
          </div>
          <div>
            <label className="block text-sm mb-1">Numéro de pièce</label>
            <input
              name="idNumber"
              className="w-full bg-black/40 border border-gray-700 rounded px-3 py-2"
              value={form.idNumber}
              onChange={onChange}
              required
            />
          </div>
        </section>

        <section className="grid md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm mb-1">Pièce d’identité (recto)</label>
            <input type="file" name="idFront" accept="image/*,application/pdf" onChange={onFile} />
          </div>
          <div>
            <label className="block text-sm mb-1">Pièce d’identité (verso)</label>
            <input type="file" name="idBack" accept="image/*,application/pdf" onChange={onFile} />
          </div>
          <div>
            <label className="block text-sm mb-1">Justificatif de domicile</label>
            <input type="file" name="proofAddress" accept="image/*,application/pdf" onChange={onFile} />
          </div>
        </section>

        <div className="flex items-center gap-2">
          <input type="checkbox" id="consent" name="consent" checked={form.consent} onChange={onChange} />
          <label htmlFor="consent" className="text-sm">
            J’accepte le traitement de mes données dans le cadre de la vérification.
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
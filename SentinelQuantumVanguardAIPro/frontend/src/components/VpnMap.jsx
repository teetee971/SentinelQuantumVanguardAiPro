import { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import { collection, onSnapshot } from "firebase/firestore";
import { db } from "../firebaseConfig";
import "leaflet/dist/leaflet.css";

export default function VpnMap() {
  const [servers, setServers] = useState([]);

  useEffect(() => {
    const unsub = onSnapshot(collection(db, "vpn_servers"), (snapshot) => {
      const data = snapshot.docs.map(doc => ({
        id: doc.id,
        ...doc.data(),
      }));
      setServers(data);
    });
    return () => unsub();
  }, []);

  return (
    <div className="rounded-lg overflow-hidden shadow-lg border border-zinc-800 mt-10">
      <MapContainer
        center={[20, 0]}
        zoom={2}
        style={{ height: "400px", width: "100%" }}
      >
        <TileLayer
          url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
          attribution='&copy; OpenStreetMap'
        />
        {servers.map((s, i) => (
          <Marker key={i} position={[s.lat, s.lng]}>
            <Popup>
              <b>{s.name}</b><br />
              <span className={s.active ? "text-green-400" : "text-red-400"}>
                {s.active ? "Actif" : "Inactif"}
              </span>
            </Popup>
          </Marker>
        ))}
      </MapContainer>
    </div>
  );
}

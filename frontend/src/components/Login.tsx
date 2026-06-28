import { useState } from "react";
import { login, register, setToken } from "../api";

export default function Login({ onAuthed }: { onAuthed: () => void }) {
  const [mode, setMode] = useState<"login" | "register">("login");
  const [username, setU] = useState("");
  const [email, setE] = useState("");
  const [password, setP] = useState("");
  const [err, setErr] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit() {
    setErr(""); setBusy(true);
    try {
      const r = mode === "login" ? await login(username, password) : await register(username, email, password);
      setToken(r.token);
      onAuthed();
    } catch (e: any) { setErr(e.message); } finally { setBusy(false); }
  }

  return (
    <div className="auth">
      <h1>POLIS</h1>
      <p>Rise of the Aegean</p>
      {err && <div className="err">{err}</div>}
      <input placeholder="Username" value={username} onChange={e => setU(e.target.value)} />
      {mode === "register" && <input placeholder="Email (optional)" value={email} onChange={e => setE(e.target.value)} />}
      <input placeholder="Password" type="password" value={password} onChange={e => setP(e.target.value)}
             onKeyDown={e => e.key === "Enter" && submit()} />
      <button className="btn" disabled={busy} onClick={submit}>
        {busy ? "…" : mode === "login" ? "Enter the Aegean" : "Found your Polis"}
      </button>
      <div className="switch">
        {mode === "login"
          ? <>New ruler? <a onClick={() => setMode("register")}>Found a city</a></>
          : <>Already ruling? <a onClick={() => setMode("login")}>Log in</a></>}
      </div>
    </div>
  );
}

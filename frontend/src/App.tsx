import { useState } from "react";
import { getToken, clearToken } from "./api";
import Login from "./components/Login";
import Game from "./components/Game";

export default function App() {
  const [authed, setAuthed] = useState(!!getToken());
  if (!authed) return <Login onAuthed={() => setAuthed(true)} />;
  return <Game onLogout={() => { clearToken(); setAuthed(false); }} />;
}

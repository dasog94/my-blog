export function SystemSketch() {
  return <svg className="system-sketch" viewBox="0 0 400 280" fill="none" aria-hidden="true">
    <defs><pattern id="dot-grid" width="18" height="18" patternUnits="userSpaceOnUse"><circle cx="1" cy="1" r=".7" fill="#b8b5aa" /></pattern></defs>
    <rect x="14" y="8" width="372" height="260" fill="url(#dot-grid)" opacity=".65" />
    <path d="M59 147C24 90 96 35 178 49C260 5 367 63 340 131C397 193 310 248 220 223C128 275 41 235 59 147Z" stroke="#bbb8ac" strokeDasharray="4 6" />
    <path d="M103 137L200 79L298 137L200 194L103 137Z" fill="#efede5" stroke="#74776c" />
    <path d="M103 137V168L200 226L298 168V137M200 194V226" stroke="#74776c" />
    <path d="M121 100L200 54L279 100L200 146L121 100Z" fill="#f8f7f1" stroke="#4b5146" strokeWidth="1.3" />
    <path d="M121 100V128L200 175L279 128V100M200 146V175" stroke="#4b5146" strokeWidth="1.3" />
    <path d="M150 77L200 48L250 77L200 107L150 77Z" fill="#df8e69" stroke="#a75132" strokeWidth="1.2" />
    <path d="M150 77V89L200 119L250 89V77M200 107V119" stroke="#a75132" strokeWidth="1.2" />
    <path d="M69 66H115L137 81M273 189L307 211H350M290 91L328 70" stroke="#9e9e91" />
    <circle cx="69" cy="66" r="3" fill="#a75132" /><circle cx="350" cy="211" r="3" fill="#a75132" /><circle cx="328" cy="70" r="3" fill="#a75132" />
    <text x="24" y="51" fill="#77796d" fontFamily="monospace" fontSize="9" letterSpacing="1">AN IDEA</text><text x="292" y="232" fill="#77796d" fontFamily="monospace" fontSize="9" letterSpacing="1">SOMETHING REAL</text><text x="285" y="55" fill="#77796d" fontFamily="monospace" fontSize="9" letterSpacing="1">A LITTLE CLARITY</text>
    <path d="M76 203H88M82 197V209M304 119H314M309 114V124" stroke="#a75132" />
  </svg>;
}

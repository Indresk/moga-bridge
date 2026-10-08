export default function Message({ tone = "info", children }) {
  if (!children) return null;
  return (
    <p className={`message message-${tone}`} role={tone === "error" ? "alert" : "status"}>
      {children}
    </p>
  );
}

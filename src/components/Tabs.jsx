/** Holo-style tab bar (blue underline on the active tab). */
export default function Tabs({ tabs, active, onChange }) {
  return (
    <nav className="tabs" role="tablist" aria-label="Secciones">
      {tabs.map(({ id, label }) => (
        <button
          type="button"
          role="tab"
          key={id}
          aria-selected={active === id}
          className={`tab ${active === id ? "active" : ""}`}
          onClick={() => onChange(id)}
        >
          {label}
        </button>
      ))}
    </nav>
  );
}

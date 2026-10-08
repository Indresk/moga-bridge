export default function Panel({ title, description, actions, children }) {
  return (
    <section className="panel">
      {(title || actions) && (
        <header className="panel-header">
          <div>
            {title && <h2 className="panel-title">{title}</h2>}
            {description && <p className="panel-description">{description}</p>}
          </div>
          {actions && <div className="button-row">{actions}</div>}
        </header>
      )}
      {children}
    </section>
  );
}

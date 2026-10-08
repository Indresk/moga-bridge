/** Small pill that states a status at a glance. `tone`: ok | warning | neutral. */
export default function StatusTag({ tone = "neutral", children }) {
  return <span className={`tag tag-${tone}`}>{children}</span>;
}

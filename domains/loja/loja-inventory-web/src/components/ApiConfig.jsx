export function ApiConfig({ label, value, onChange }) {
  return (
    <label style={{ display: "block", fontSize: "0.85rem", marginBottom: "0.5rem" }}>
      {label}
      <input
        type="url"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        style={{ display: "block", width: "100%", marginTop: "0.25rem" }}
        aria-label={label}
      />
    </label>
  );
}

import { keyOptions } from "../lib/keys";

/** One select per control, grouped. `onChange(control, keyCode)` gets the numeric value. */
export default function MappingGrid({ controls, mapping, onChange }) {
  const groups = [...new Set(controls.map(([, , group]) => group))];
  return (
    <>
      {groups.map((group) => (
        <fieldset className="mapping-group" key={group}>
          <legend>{group}</legend>
          <div className="mapping-grid">
            {controls
              .filter(([, , controlGroup]) => controlGroup === group)
              .map(([control, label]) => (
                <label className="mapping-row" key={control}>
                  <span>{label}</span>
                  <select
                    value={mapping[control]}
                    onChange={(event) => onChange(control, Number(event.target.value))}
                  >
                    {keyOptions.map((option) => (
                      <option value={option.value} key={option.value}>
                        {option.label}
                      </option>
                    ))}
                  </select>
                </label>
              ))}
          </div>
        </fieldset>
      ))}
    </>
  );
}

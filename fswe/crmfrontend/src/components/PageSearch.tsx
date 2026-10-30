import {useRef, useState} from "react";
import {Search, X} from "lucide-react";

export default function PageSearch({label, value, onChange}: {
  label: string;
  value: string;
  onChange: (value: string) => void;
}) {
  const [open, setOpen] = useState(false);
  const trigger = useRef<HTMLButtonElement>(null);
  function close() {
    onChange("");
    setOpen(false);
    requestAnimationFrame(() => trigger.current?.focus());
  }
  return <div className={`task-search page-search ${open ? "open" : ""}`}>
    {!open ? <button ref={trigger} type="button" className="task-search-icon"
      aria-label={label} title={label} aria-expanded={false} onClick={() => setOpen(true)}>
      <Search size={19}/>
    </button> : <div className="task-search-box">
      <Search size={17} className="task-search-symbol"/>
      <input className="task-search-input" aria-label={label} placeholder={`${label}...`}
        value={value} onChange={event => onChange(event.target.value)} autoFocus
        onKeyDown={event => {if (event.key === "Escape") {event.stopPropagation(); close();}}}/>
      <button type="button" className="task-search-close" aria-label="Clear and close search"
        title="Clear and close search" onClick={close}><X size={17}/></button>
    </div>}
  </div>;
}

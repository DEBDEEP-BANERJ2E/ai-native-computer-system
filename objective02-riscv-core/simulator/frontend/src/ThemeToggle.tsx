import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";

type Theme = "dark" | "light";

const STORAGE_KEY = "simulator-theme";

function readTheme(): Theme {
  return document.documentElement.dataset.theme === "light" ? "light" : "dark";
}

export function ThemeToggle() {
  const [theme, setTheme] = useState<Theme>(readTheme);

  useEffect(() => {
    document.documentElement.dataset.theme = theme;
    localStorage.setItem(STORAGE_KEY, theme);
  }, [theme]);

  const isLight = theme === "light";

  return (
    <button
      type="button"
      className={`theme-toggle ${isLight ? "is-light" : "is-dark"}`}
      role="switch"
      aria-checked={isLight}
      aria-label={isLight ? "Switch to dark mode" : "Switch to light mode"}
      title={isLight ? "Switch to dark mode" : "Switch to light mode"}
      onClick={() => setTheme(isLight ? "dark" : "light")}
    >
      <span className="theme-toggle-track">
        <Moon size={12} className="theme-toggle-icon theme-toggle-moon" />
        <Sun size={12} className="theme-toggle-icon theme-toggle-sun" />
        <span className="theme-toggle-thumb" />
      </span>
      <span className="theme-toggle-label">{isLight ? "LIGHT" : "DARK"}</span>
    </button>
  );
}

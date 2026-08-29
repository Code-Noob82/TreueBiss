/*
 * Rechnet aus der Farbe eines Betriebs die ganze Palette.
 *
 * Gesetzt werden nur Farbton, Sättigung und die Marke selbst; alle Flächen,
 * Linien und Textfarben leitet `basis.css` daraus ab. Sonst wäre die
 * Betriebsfarbe wieder nur eine Knopffüllung auf immergleichem Grau.
 */
export function paletteSetzen(farbe, wurzel = document.documentElement) {
  const marke = /^#[0-9A-Fa-f]{6}$/.test(farbe ?? '') ? farbe : '#4CAF50';
  const { h, s } = hexZuHsl(marke);

  // Ein grauer Betrieb darf keine rosa Flächen bekommen: Unterhalb einer
  // Restsättigung wird der Farbton bedeutungslos, dann bleibt es neutral.
  const sProzent = s < 0.08 ? 6 : Math.min(Math.max(s * 100, 18), 52);

  wurzel.style.setProperty('--h', Math.round(h));
  wurzel.style.setProperty('--s', sProzent.toFixed(1) + '%');
  wurzel.style.setProperty('--marke', marke);
  // Weiße Schrift auf hellem Gelb wäre unlesbar. Die Schwelle 0,179 ist der
  // Punkt, an dem Schwarz besser kontrastiert als Weiß (WCAG).
  wurzel.style.setProperty('--marke-text',
    leuchtkraft(marke) > 0.179
      ? `hsl(${Math.round(h)} 45% 11%)`
      : `hsl(${Math.round(h)} 30% 97%)`);

  return marke;
}

export function hexZuHsl(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => parseInt(hex.slice(i, i + 2), 16) / 255);
  const max = Math.max(r, g, b), min = Math.min(r, g, b), l = (max + min) / 2;
  if (max === min) return { h: 0, s: 0, l };
  const d = max - min;
  const s = l > 0.5 ? d / (2 - max - min) : d / (max + min);
  const h = (max === r ? (g - b) / d + (g < b ? 6 : 0)
           : max === g ? (b - r) / d + 2
                       : (r - g) / d + 4) * 60;
  return { h, s, l };
}

/** Relative Leuchtdichte nach WCAG - entscheidet über die Schriftfarbe. */
export function leuchtkraft(hex) {
  const kanal = (i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  };
  return 0.2126 * kanal(1) + 0.7152 * kanal(3) + 0.0722 * kanal(5);
}

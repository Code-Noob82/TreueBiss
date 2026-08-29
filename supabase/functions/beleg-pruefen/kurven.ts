/*
 * ECDSA-Prüfung über BigInt, ohne Fremdbibliothek.
 *
 * Warum nicht WebCrypto: Deutsche TSEn signieren überwiegend über
 * Brainpool-Kurven, und die kennt WebCrypto nicht - `importKey` lehnt
 * `brainpoolP384r1` mit "Unrecognized namedCurve" ab. Node könnte es über
 * OpenSSL, Deno nicht verlässlich. Also selbst gerechnet.
 *
 * Das ist vertretbar, weil hier ausschließlich öffentliche Daten verarbeitet
 * werden: Es gibt keinen geheimen Schlüssel, also auch keine Seitenkanäle,
 * die Laufzeitunterschiede verraten könnten. Bleibt die Richtigkeit - und
 * die ist gegen zwei echte TSE-Signaturen und sieben Manipulationsproben
 * geprüft (siehe `pruefung_test.ts`).
 */
const H = (s: string) => BigInt("0x" + s.replace(/\s/g, ""));

export interface Kurve {
  p: bigint; a: bigint; b: bigint; gx: bigint; gy: bigint; n: bigint;
}

export const KURVEN: Record<string, Kurve> = {
  secp256r1: {
    p: H("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFF"),
    a: H("FFFFFFFF00000001000000000000000000000000FFFFFFFFFFFFFFFFFFFFFFFC"),
    b: H("5AC635D8AA3A93E7B3EBBD55769886BC651D06B0CC53B0F63BCE3C3E27D2604B"),
    gx: H("6B17D1F2E12C4247F8BCE6E563A440F277037D812DEB33A0F4A13945D898C296"),
    gy: H("4FE342E2FE1A7F9B8EE7EB4A7C0F9E162BCE33576B315ECECBB6406837BF51F5"),
    n: H("FFFFFFFF00000000FFFFFFFFFFFFFFFFBCE6FAADA7179E84F3B9CAC2FC632551"),
  },
  brainpoolP256r1: {
    p: H("A9FB57DBA1EEA9BC3E660A909D838D726E3BF623D52620282013481D1F6E5377"),
    a: H("7D5A0975FC2C3057EEF67530417AFFE7FB8055C126DC5C6CE94A4B44F330B5D9"),
    b: H("26DC5C6CE94A4B44F330B5D9BBD77CBF958416295CF7E1CE6BCCDC18FF8C07B6"),
    gx: H("8BD2AEB9CB7E57CB2C4B482FFC81B7AFB9DE27E1E3BD23C23A4453BD9ACE3262"),
    gy: H("547EF835C3DAC4FD97F8461A14611DC9C27745132DED8E545C1D54C72F046997"),
    n: H("A9FB57DBA1EEA9BC3E660A909D838D718C397AA3B561A6F7901E0E82974856A7"),
  },
  secp384r1: {
    p: H("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFF"),
    a: H("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEFFFFFFFF0000000000000000FFFFFFFC"),
    b: H("B3312FA7E23EE7E4988E056BE3F82D19181D9C6EFE8141120314088F5013875AC656398D8A2ED19D2A85C8EDD3EC2AEF"),
    gx: H("AA87CA22BE8B05378EB1C71EF320AD746E1D3B628BA79B9859F741E082542A385502F25DBF55296C3A545E3872760AB7"),
    gy: H("3617DE4A96262C6F5D9E98BF9292DC29F8F41DBD289A147CE9DA3113B5F0B8C00A60B1CE1D7E819D7A431D7C90EA0E5F"),
    n: H("FFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFC7634D81F4372DDF581A0DB248B0A77AECEC196ACCC52973"),
  },
  brainpoolP384r1: {
    p: H("8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B412B1DA197FB71123ACD3A729901D1A71874700133107EC53"),
    a: H("7BC382C63D8C150C3C72080ACE05AFA0C2BEA28E4FB22787139165EFBA91F90F8AA5814A503AD4EB04A8C7DD22CE2826"),
    b: H("04A8C7DD22CE28268B39B55416F0447C2FB77DE107DCD2A62E880EA53EEB62D57CB4390295DBC9943AB78696FA504C11"),
    gx: H("1D1C64F068CF45FFA2A63A81B7C13F6B8847A3E77EF14FE3DB7FCAFE0CBD10E8E826E03436D646AAEF87B2E247D4AF1E"),
    gy: H("8ABE1D7520F9C2A45CB1EB8E95CFD55262B70B29FEEC5864E19C054FF99129280E4646217791811142820341263C5315"),
    n: H("8CB91E82A3386D280F5D6F7E50E641DF152F7109ED5456B31F166E6CAC0425A7CF3AB6AF6B7FC3103B883202E9046565"),
  },
};

const mod = (x: bigint, m: bigint) => ((x % m) + m) % m;

function invers(x: bigint, m: bigint): bigint {
  let [alt, r] = [mod(x, m), m];
  let [s, t] = [1n, 0n];
  while (r !== 0n) {
    const q = alt / r;
    [alt, r] = [r, alt - q * r];
    [s, t] = [t, s - q * t];
  }
  return mod(s, m);
}

/* Jacobi-Koordinaten: eine Inversion am Ende statt einer je Schritt. */
type Punkt = [bigint, bigint, bigint] | null;

function verdoppeln(P: Punkt, k: Kurve): Punkt {
  if (!P) return null;
  const [X, Y, Z] = P, { p, a } = k;
  if (Y === 0n) return null;
  const YY = mod(Y * Y, p);
  const S = mod(4n * X * YY, p);
  const ZZ = mod(Z * Z, p);
  const M = mod(3n * X * X + a * mod(ZZ * ZZ, p), p);
  const X3 = mod(M * M - 2n * S, p);
  return [X3, mod(M * (S - X3) - 8n * YY * YY, p), mod(2n * Y * Z, p)];
}

function addieren(P: Punkt, Q: Punkt, k: Kurve): Punkt {
  if (!P) return Q;
  if (!Q) return P;
  const { p } = k, [X1, Y1, Z1] = P, [X2, Y2, Z2] = Q;
  const Z1Z = mod(Z1 * Z1, p), Z2Z = mod(Z2 * Z2, p);
  const U1 = mod(X1 * Z2Z, p), U2 = mod(X2 * Z1Z, p);
  const S1 = mod(Y1 * Z2Z * Z2, p), S2 = mod(Y2 * Z1Z * Z1, p);
  const Hh = mod(U2 - U1, p), R = mod(S2 - S1, p);
  if (Hh === 0n) return R === 0n ? verdoppeln(P, k) : null;
  const HH = mod(Hh * Hh, p), HHH = mod(HH * Hh, p), V = mod(U1 * HH, p);
  const X3 = mod(R * R - HHH - 2n * V, p);
  return [X3, mod(R * (V - X3) - S1 * HHH, p), mod(Hh * Z1 * Z2, p)];
}

function malSkalar(d: bigint, P: Punkt, k: Kurve): Punkt {
  let R: Punkt = null, Q = P;
  while (d > 0n) {
    if (d & 1n) R = addieren(R, Q, k);
    Q = verdoppeln(Q, k);
    d >>= 1n;
  }
  return R;
}

function affin(P: Punkt, k: Kurve): [bigint, bigint] | null {
  if (!P) return null;
  const { p } = k, [X, Y, Z] = P;
  const zi = invers(Z, p), zi2 = mod(zi * zi, p);
  return [mod(X * zi2, p), mod(Y * zi2 * zi, p)];
}

const aufKurve = (x: bigint, y: bigint, k: Kurve) =>
  mod(y * y, k.p) === mod(x * x * x + k.a * x + k.b, k.p);

export const zahl = (b: Uint8Array): bigint => {
  let n = 0n;
  for (const byte of b) n = (n << 8n) | BigInt(byte);
  return n;
};

/**
 * Selbstkontrolle der Kurvenparameter: G muss auf der Kurve liegen und
 * n*G unendlich sein. Erwischt einen Tippfehler in den Konstanten sofort -
 * ohne das würde eine falsche Kurve einfach jede Signatur ablehnen, und das
 * sähe aus wie ein gefälschter Beleg.
 */
export function kurvePruefen(name: string): string | null {
  const k = KURVEN[name];
  if (!k) return `${name}: unbekannt`;
  if (!aufKurve(k.gx, k.gy, k)) return `${name}: G liegt nicht auf der Kurve`;
  if (affin(malSkalar(k.n, [k.gx, k.gy, 1n], k), k) !== null) {
    return `${name}: n*G ist nicht unendlich`;
  }
  return null;
}

export function pruefeSignatur(
  kurveName: string, schluessel: Uint8Array, r: bigint, s: bigint, hash: Uint8Array,
): boolean {
  const k = KURVEN[kurveName];
  if (!k) return false;
  const { n } = k;
  if (r <= 0n || r >= n || s <= 0n || s >= n) return false;
  if (schluessel[0] !== 0x04) return false;

  const gr = (schluessel.length - 1) / 2;
  const qx = zahl(schluessel.slice(1, 1 + gr));
  const qy = zahl(schluessel.slice(1 + gr));
  if (!aufKurve(qx, qy, k)) return false;

  // e = die linken Bits des Hashwerts, auf die Bitlänge von n gekürzt.
  let e = zahl(hash);
  const ueber = hash.length * 8 - n.toString(2).length;
  if (ueber > 0) e >>= BigInt(ueber);

  const w = invers(s, n);
  const P = addieren(
    malSkalar(mod(e * w, n), [k.gx, k.gy, 1n], k),
    malSkalar(mod(r * w, n), [qx, qy, 1n], k),
    k,
  );
  const A = affin(P, k);
  return A !== null && mod(A[0], n) === mod(r, n);
}

/**
 * AES-CBC password encryption matching oasis.ssu.ac.kr's login JS.
 * Parameters reverse-engineered from the oasis web client.
 * PBKDF2(SHA-1, 5000 iter, 128-bit key) → AES/CBC with fixed salt & IV.
 *
 * Used by both the web library login modal and the MCP library auth page.
 * Never log or persist the raw password; pass only the ciphertext to the backend.
 */
export async function encryptLibraryPassword(raw: string): Promise<string> {
  const enc = new TextEncoder();
  const keyMaterial = await crypto.subtle.importKey(
    "raw",
    enc.encode("M2M2YjcyMmU2OTZlNjU2YjJlNjM2OTcwNjU3MjNl"),
    "PBKDF2",
    false,
    ["deriveKey"],
  );
  const aesKey = await crypto.subtle.deriveKey(
    { name: "PBKDF2", salt: enc.encode("kr.inek.encrypte"), iterations: 5000, hash: "SHA-1" },
    keyMaterial,
    { name: "AES-CBC", length: 128 },
    false,
    ["encrypt"],
  );
  const ciphertext = await crypto.subtle.encrypt(
    { name: "AES-CBC", iv: enc.encode("[kr:inek:solved]") },
    aesKey,
    enc.encode(raw),
  );
  return btoa(String.fromCharCode(...new Uint8Array(ciphertext)));
}

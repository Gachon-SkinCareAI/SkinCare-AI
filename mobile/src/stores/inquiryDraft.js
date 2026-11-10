import { Platform } from "react-native";
import * as SecureStore from "expo-secure-store";
import * as Crypto from "expo-crypto";
const memory = new Map();
const options = {
  keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY,
};
async function key(owner) {
  if (!owner) throw new Error("문의 계정을 확인하지 못했어요.");
  return (
    "skincare.inquiry." +
    (await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, owner))
  );
}
async function get(k) {
  return Platform.OS === "web" ? memory.get(k) : SecureStore.getItemAsync(k);
}
async function put(k, v) {
  if (Platform.OS === "web") {
    memory.set(k, v);
    return;
  }
  await SecureStore.setItemAsync(k, v, options);
}
async function remove(k) {
  if (Platform.OS === "web") {
    memory.delete(k);
    return;
  }
  await SecureStore.deleteItemAsync(k);
}
export async function readInquiryDraft(owner) {
  const base = await key(owner),
    raw = await get(base);
  if (!raw) return null;
  const meta = JSON.parse(raw);
  if (
    ![0, 1].includes(meta.bank) ||
    !Number.isInteger(meta.count) ||
    meta.count < 1 ||
    meta.count > 50
  )
    throw new Error(
      "초안을 읽지 못했어요. 초안을 삭제하고 다시 작성해 주세요.",
    );
  let body = "";
  for (let i = 0; i < meta.count; i++) {
    const chunk = await get(`${base}.${meta.bank}.${i}`);
    if (chunk == null) throw new Error("저장된 초안이 불완전해요.");
    body += chunk;
  }
  return JSON.parse(body);
}
export async function saveInquiryDraft(owner, draft) {
  const base = await key(owner),
    old = JSON.parse((await get(base)) || "null");
  const bank = old?.bank === 0 ? 1 : 0,
    body = JSON.stringify(draft);
  if (body.length > 20000) throw new Error("문의 초안이 너무 길어요.");
  // Small UTF-16 chunks remain below native SecureStore value limits, even for Korean/emoji.
  // Publish the new pointer last so an interrupted write cannot destroy the previous draft.
  const characters = Array.from(body),
    count = Math.ceil(characters.length / 400);
  for (let i = 0; i < count; i++)
    await put(
      `${base}.${bank}.${i}`,
      characters.slice(i * 400, (i + 1) * 400).join(""),
    );
  await put(base, JSON.stringify({ bank, count }));
  for (let i = 0; i < 50; i++) await remove(`${base}.${1 - bank}.${i}`);
}
export async function clearInquiryDraft(owner) {
  const base = await key(owner);
  await remove(base);
  for (let bank = 0; bank < 2; bank++)
    for (let i = 0; i < 50; i++) await remove(`${base}.${bank}.${i}`);
}

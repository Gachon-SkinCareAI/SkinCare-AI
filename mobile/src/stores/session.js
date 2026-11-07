import { Platform } from 'react-native';
import * as SecureStore from 'expo-secure-store';
import * as Crypto from 'expo-crypto';

// Native capability keys and account tokens stay in OS-protected storage. Web demo is memory-only.
const memory = new Map();
const keys = { member: 'skincare.member.token.v1', guest: 'skincare.guest.key.v1' };
export async function readSessionKey(kind) {
  if (Platform.OS === 'web') return memory.get(kind) || null;
  return SecureStore.getItemAsync(keys[kind]);
}
export async function saveSessionKey(kind, value) {
  if (Platform.OS === 'web') { if (value) memory.set(kind, value); else memory.delete(kind); return; }
  if (value) await SecureStore.setItemAsync(keys[kind], value, { keychainAccessible: SecureStore.WHEN_UNLOCKED_THIS_DEVICE_ONLY });
  else await SecureStore.deleteItemAsync(keys[kind]);
}
export async function guestKey() {
  let key = await readSessionKey('guest');
  if (!key) {
    const bytes = await Crypto.getRandomBytesAsync(32);
    key = Array.from(bytes, b => b.toString(16).padStart(2, '0')).join('');
    await saveSessionKey('guest', key); // Persist before upload; never lose access on a lost HTTP response.
  }
  return key;
}
export const requestId = () => Crypto.randomUUID();

import { Platform } from 'react-native';
import * as Crypto from 'expo-crypto';
import * as WebBrowser from 'expo-web-browser';
const PENDING_KEY = 'skincare.oauth.pending';
const NATIVE_REDIRECT = 'skincareai://oauth/callback';
const LOGIN_ERROR = '로그인을 완료하지 못했습니다. 취소했거나 요청이 만료되었을 수 있습니다. 다시 시도해 주세요.';

// Temporary sessionStorage contains a verifier only; access tokens remain memory-only on web.
export async function finishWebLogin(api) {
  if (Platform.OS !== 'web') return null;
  const params = new URLSearchParams(window.location.hash.slice(1));
  if (!params.has('oauth_code') && !params.has('oauth_error')) return null;
  const callback = window.location.href;
  window.history.replaceState(null, '', window.location.pathname + window.location.search);
  let raw;
  try {
    raw = window.sessionStorage.getItem(PENDING_KEY);
    window.sessionStorage.removeItem(PENDING_KEY);
  } catch {
    throw new Error(LOGIN_ERROR);
  }
  let pending;
  try {
    pending = JSON.parse(raw || 'null');
  } catch {
    throw new Error(LOGIN_ERROR);
  }
  return complete(api, callback, pending);
}
async function complete(api, callback, pending) {
  if (!pending || typeof pending.verifier !== 'string' || typeof pending.createdAt !== 'number' || Date.now() - pending.createdAt > 600_000 || pending.createdAt > Date.now() + 5000) throw new Error(LOGIN_ERROR);
  const url = new URL(callback);
  if (url.href.split('#')[0] !== pending.redirectUri) throw new Error(LOGIN_ERROR);
  const params = new URLSearchParams(url.hash.slice(1));
  const code = params.get('oauth_code');
  if (params.has('oauth_error') || !code || !/^[A-Za-z0-9_-]{43}$/.test(code)) throw new Error(LOGIN_ERROR);
  return api.post('/api/wc/auth/social/exchange', {
    code,
    codeVerifier: pending.verifier
  });
}
export async function startSocialLogin(api, provider) {
  const bytes = await Crypto.getRandomBytesAsync(32);
  const verifier = Array.from(bytes, byte => byte.toString(16).padStart(2, '0')).join('');
  const challenge = (await Crypto.digestStringAsync(Crypto.CryptoDigestAlgorithm.SHA256, verifier, {
    encoding: Crypto.CryptoEncoding.BASE64
  })).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
  const redirectUri = Platform.OS === 'web' ? window.location.origin + window.location.pathname : NATIVE_REDIRECT;
  const pending = {
    verifier,
    redirectUri,
    createdAt: Date.now()
  };
  if (Platform.OS === 'web') {
    try {
      window.sessionStorage.setItem(PENDING_KEY, JSON.stringify(pending));
    } catch {
      throw new Error('브라우저의 임시 저장 기능을 허용한 뒤 다시 로그인해 주세요.');
    }
  }
  try {
    const result = await api.post('/api/wc/auth/social/start', {
      provider,
      redirectUri,
      codeChallenge: challenge
    });
    if (!new RegExp('^/oauth2/authorization/' + provider + '\\?flow=[A-Za-z0-9_-]{43}$').test(result.authorizationPath)) throw new Error(LOGIN_ERROR);
    const authorizationUrl = api.base + result.authorizationPath;
    if (Platform.OS === 'web') {
      window.location.assign(authorizationUrl);
      return null;
    }
    const session = await WebBrowser.openAuthSessionAsync(authorizationUrl, NATIVE_REDIRECT);
    if (session.type !== 'success') return null;
    return complete(api, session.url, pending);
  } catch (error) {
    if (Platform.OS === 'web') window.sessionStorage.removeItem(PENDING_KEY);
    throw error;
  }
}

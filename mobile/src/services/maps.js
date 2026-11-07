// External search only; this does not embed the Naver Maps SDK or request GPS.
// Send public facility details, never the user's coordinates or analysis data.
export function naverMapSearchUrl(facility) {
  const query = [facility?.name, facility?.address]
    .filter(value => typeof value === 'string' && value.trim())
    .map(value => value.trim()).join(' ');
  return query ? `https://map.naver.com/p/search/${encodeURIComponent(query)}` : null;
}

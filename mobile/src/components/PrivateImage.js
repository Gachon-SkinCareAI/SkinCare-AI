import React from 'react';
import { T as Text } from './Common';
import { useEffect, useState } from 'react';
import { Image, Platform, View } from 'react-native';
export function PrivateImage({
  url,
  token,
  height = 230,
  label = '저장된 상처 사진'
}) {
  const [uri, setUri] = useState(null);
  const [failed, setFailed] = useState(false);
  useEffect(() => {
    let active = true;
    let objectUrl = null;
    setFailed(false);
    setUri(null);
    if (Platform.OS !== 'web') {
      setUri(url);
      return;
    }
    const controller = new AbortController();
    fetch(url, {
      headers: {
        Authorization: `Bearer ${token}`
      },
      signal: controller.signal
    }).then(response => {
      if (!response.ok) throw new Error('image');
      return response.blob();
    }).then(blob => {
      if (active) {
        objectUrl = URL.createObjectURL(blob);
        setUri(objectUrl);
      }
    }).catch(() => {
      if (active) setFailed(true);
    });
    return () => {
      active = false;
      controller.abort();
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [url, token]);
  return <View style={{
    width: '100%',
    height,
    borderRadius: 14,
    backgroundColor: '#EDF2ED',
    overflow: 'hidden',
    justifyContent: 'center',
    alignItems: 'center'
  }}>
    {failed ? <Text accessibilityLabel={`${label} 불러오기 실패`} style={{
      color: '#617771',
      fontSize: 12,
      textAlign: 'center',
      padding: 6
    }}>사진 로딩 실패</Text> : uri ? <Image accessibilityLabel={label} source={{
      uri,
      headers: Platform.OS === 'web' ? undefined : {
        Authorization: `Bearer ${token}`
      }
    }} style={{
      width: '100%',
      height
    }} resizeMode="contain" onError={() => setFailed(true)} /> : <Text style={{
      color: '#617771',
      fontSize: 12
    }}>사진 준비 중</Text>}
  </View>;
}

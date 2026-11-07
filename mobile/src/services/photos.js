import { Platform } from 'react-native';
import * as ImagePicker from 'expo-image-picker';
import { manipulateAsync, SaveFormat } from 'expo-image-manipulator';
import { File } from 'expo-file-system';
export async function choosePhoto(camera) {
  if (camera) {
    const permission = await ImagePicker.requestCameraPermissionsAsync();
    if (!permission.granted) throw new Error('촬영하려면 카메라 권한을 허용해 주세요. 사진 선택도 사용할 수 있습니다.');
  }
  const result = camera ? await ImagePicker.launchCameraAsync({
    mediaTypes: ['images'],
    quality: 0.9
  }) : await ImagePicker.launchImageLibraryAsync({
    mediaTypes: ['images'],
    quality: 0.9
  });
  if (result.canceled || !result.assets[0]) return null;
  const asset = result.assets[0];
  const width = Math.min(asset.width || 1600, 1600);
  const normalized = await manipulateAsync(asset.uri, [{
    resize: {
      width
    }
  }], {
    compress: 0.88,
    format: SaveFormat.JPEG
  });
  return {
    uri: normalized.uri,
    name: 'wound.jpg',
    type: 'image/jpeg'
  };
}
export async function uploadForm(photo, requestId, parentId, capturedOn) {
  const form = new FormData();
  if (Platform.OS === 'web') {
    const blob = await (await fetch(photo.uri)).blob();
    if (blob.size > 8 * 1024 * 1024) throw new Error('사진은 8MB 이하여야 합니다.');
    form.append('file', blob, photo.name);
  } else {
    // Expo's native fetch encodes File bytes; the legacy { uri, name, type }
    // object fails before the HTTP request reaches the server.
    const file = new File(photo.uri);
    if (!file.exists || file.size === 0) throw new Error('사진을 읽을 수 없습니다. 다시 촬영하거나 사진을 선택해 주세요.');
    if (file.size > 8 * 1024 * 1024) throw new Error('사진은 8MB 이하여야 합니다.');
    form.append('file', file);
  }
  form.append('clientRequestId', requestId);
  if (parentId) form.append('parentId', parentId);
  if (capturedOn) form.append('capturedOn', capturedOn);
  return form;
}

export async function normalizePhoto(asset) {
 const width=Math.min(asset.width||1600,1600);
 const output=await manipulateAsync(asset.uri,[{resize:{width}}],{compress:0.88,format:SaveFormat.JPEG});
 return {uri:output.uri,name:'wound.jpg',type:'image/jpeg'};
}

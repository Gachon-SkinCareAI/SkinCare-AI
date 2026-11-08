import React from 'react';
import {View,TextInput,Pressable,StyleSheet} from 'react-native';
import {useAssets} from 'expo-asset';
import {SvgUri} from 'react-native-svg';
import {C,T} from './Common';
const icons={image:require('../../assets/services/image.svg'),calendar:require('../../assets/services/calendar.svg'),info:require('../../assets/services/info.svg'),down:require('../../assets/services/down.svg'),search:require('../../assets/services/search.svg'),chat:require('../../assets/services/chat.svg'),chevron:require('../../assets/services/chevron.svg')};
export function Icon({name}){const [assets]=useAssets([icons[name]]);const a=assets?.[0];return a?<SvgUri uri={a.localUri||a.uri}/>:null;}
export function SmallTitle({children}){return <T style={styles.title}>{children}</T>}
export function Field({label,...props}){return <TextInput accessibilityLabel={label} placeholderTextColor={C.muted} {...props} style={[styles.input,props.style]}/>}
export function TextAction({children,onPress,label}){return <Pressable accessibilityRole="button" accessibilityLabel={label||children} onPress={onPress} style={{minHeight:30,justifyContent:'center'}}><T style={{fontSize:11,color:C.navy,fontFamily:'NotoSansKR_700Bold'}}>{children}</T></Pressable>}
export function Notice({children}){return <View style={{flexDirection:'row',gap:8,padding:12,backgroundColor:'#EDF2FA',borderRadius:12}}><Icon name="info"/><T style={{flex:1,fontSize:11,lineHeight:18,color:C.muted}}>{children}</T></View>}
export function EmptyPhoto({height=120}){return <View style={{height,backgroundColor:'#EBEFF5',borderRadius:12,alignItems:'center',justifyContent:'center',gap:8}}><Icon name="image"/><T style={{fontSize:11,color:C.muted}}>사진을 추가해주세요</T></View>}
export const styles=StyleSheet.create({title:{fontSize:13,lineHeight:20,fontFamily:'NotoSansKR_700Bold',color:C.navy},input:{backgroundColor:C.bg,borderWidth:1,borderColor:C.line,borderRadius:8,padding:10,fontFamily:'NotoSansKR_400Regular',fontSize:12,color:C.text,minHeight:40},small:{fontSize:11,lineHeight:18,color:C.muted},card:{padding:14,borderWidth:0,borderRadius:16,gap:10}});

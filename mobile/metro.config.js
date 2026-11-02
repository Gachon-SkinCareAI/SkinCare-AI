const { getDefaultConfig } = require('expo/metro-config');
const config = getDefaultConfig(__dirname);
config.resolver.sourceExts = config.resolver.sourceExts.filter(ext => ext !== 'svg');
if (!config.resolver.assetExts.includes('svg')) config.resolver.assetExts.push('svg');
module.exports = config;

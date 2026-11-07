const { withAndroidManifest, withDangerousMod } = require('@expo/config-plugins');
const fs = require('node:fs/promises');
const path = require('node:path');
module.exports = function withUsbNetwork(config, { enabled = false } = {}) {
  config = withAndroidManifest(config, mod => {
    const app = mod.modResults.manifest.application[0];
    app.$['android:usesCleartextTraffic'] = 'false';
    if (enabled) app.$['android:networkSecurityConfig'] = '@xml/skincare_network_security';
    else delete app.$['android:networkSecurityConfig'];
    return mod;
  });
  if (!enabled) return config;
  return withDangerousMod(config, ['android', async mod => {
    const folder = path.join(mod.modRequest.platformProjectRoot, 'app/src/main/res/xml');
    await fs.mkdir(folder, { recursive: true });
    await fs.writeFile(path.join(folder, 'skincare_network_security.xml'), `<?xml version="1.0" encoding="utf-8"?>
<network-security-config>
  <base-config cleartextTrafficPermitted="false" />
  <domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="false">127.0.0.1</domain>
    <domain includeSubdomains="false">localhost</domain>
  </domain-config>
</network-security-config>
`);
    return mod;
  }]);
};

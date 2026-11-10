const { withAppBuildGradle } = require("@expo/config-plugins");
const START = "// SkinCare public signing start";
const END = "// SkinCare public signing end";
module.exports = (config) =>
  withAppBuildGradle(config, (mod) => {
    if (mod.modResults.language !== "groovy")
      throw new Error("Expected the generated Groovy Android build file");
    const contents = mod.modResults.contents.replace(
      new RegExp("\\n?" + START + "[\\s\\S]*?" + END + "\\n?", "g"),
      "",
    );
    mod.modResults.contents =
      contents +
      `
${START}
def signingKeys = ['SKINCARE_ANDROID_KEYSTORE', 'SKINCARE_ANDROID_STORE_PASSWORD', 'SKINCARE_ANDROID_KEY_ALIAS', 'SKINCARE_ANDROID_KEY_PASSWORD']
if (signingKeys.any { !System.getenv(it) }) throw new GradleException('Public build signing configuration is missing')
def releaseKeyFile = file(System.getenv('SKINCARE_ANDROID_KEYSTORE'))
if (!releaseKeyFile.isFile()) throw new GradleException('Public build keystore does not exist')
android {
  signingConfigs {
    skincarePublic {
      storeFile releaseKeyFile
      storePassword System.getenv('SKINCARE_ANDROID_STORE_PASSWORD')
      keyAlias System.getenv('SKINCARE_ANDROID_KEY_ALIAS')
      keyPassword System.getenv('SKINCARE_ANDROID_KEY_PASSWORD')
    }
  }
  buildTypes { release { signingConfig signingConfigs.skincarePublic } }
}
// A tunnel address can change without changing JS files. Include it in the
// bundle task inputs so Gradle cannot reuse an APK bundle pointing at an old API.
tasks.configureEach { task ->
  if (task.name == 'createBundleReleaseJsAndAssets') {
    task.inputs.property('skincarePublicApiOrigin', System.getenv('EXPO_PUBLIC_API_URL') ?: '')
  }
}
${END}
`;
    return mod;
  });

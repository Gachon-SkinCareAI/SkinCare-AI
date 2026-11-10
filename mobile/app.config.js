// USB testing is an explicit native build variant; public builds continue to require HTTPS.
module.exports = ({ config }) => {
  const usb = process.env.SKINCARE_USB_TEST_BUILD === "1";
  const publicDemo = process.env.SKINCARE_PUBLIC_DEMO_BUILD === "1";
  const api = process.env.EXPO_PUBLIC_API_URL || "";
  if (usb && publicDemo)
    throw new Error("Choose either USB or public-demo build");
  if (usb && api !== "http://127.0.0.1:9800")
    throw new Error(
      "USB builds require EXPO_PUBLIC_API_URL=http://127.0.0.1:9800",
    );
  if (publicDemo) {
    let url;
    try {
      url = new URL(api);
    } catch {
      throw new Error("Public builds require a public HTTPS API origin");
    }
    if (
      url.protocol !== "https:" ||
      url.username ||
      url.password ||
      url.search ||
      url.hash ||
      (url.pathname !== "/" && url.pathname !== "") ||
      (url.port && url.port !== "443") ||
      !/^(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}$/.test(
        url.hostname,
      ) ||
      /(?:^|\.)(?:localhost|local|internal|test|invalid|example)$/.test(
        url.hostname,
      ) ||
      /(?:^|\.)example\.(?:com|org|net)$/.test(url.hostname)
    ) {
      throw new Error(
        "Public builds require your public HTTPS API origin, without a path or local address",
      );
    }
  }
  const versionCode = process.env.SKINCARE_ANDROID_VERSION_CODE || "2";
  if (
    publicDemo &&
    (!/^\d+$/.test(versionCode) ||
      Number(versionCode) < 2 ||
      Number(versionCode) > 2100000000)
  )
    throw new Error(
      "Set an integer public Android version code from 2 to 2100000000",
    );
  return {
    ...config,
    ...(usb
      ? {
          name: "SkinCare AI USB",
          android: { ...config.android, package: "com.skincareai.mobile.usb" },
        }
      : {}),
    ...(publicDemo
      ? {
          android: {
            ...config.android,
            package: "com.skincareai.mobile",
            versionCode: Number(versionCode),
          },
        }
      : {}),
    plugins: [
      ...(config.plugins || []),
      ["./plugins/withUsbNetwork", { enabled: usb }],
      ...(publicDemo ? ["./plugins/withPublicSigning"] : []),
    ],
    extra: {
      ...config.extra,
      buildProfile: usb
        ? "usb-device-test"
        : publicDemo
          ? "public-demo"
          : "standard",
    },
  };
};

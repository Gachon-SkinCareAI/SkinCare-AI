import React from "react";
import { Pressable, View, TextInput, Linking } from "react-native";
import { T, C, Button } from "./Common";

export function Check({ label, value, onChange, disabled }) {
  return (
    <Pressable
      accessibilityRole="checkbox"
      accessibilityLabel={label}
      aria-checked={value}
      aria-disabled={!!disabled}
      accessibilityState={{ checked: value, disabled: !!disabled }}
      disabled={disabled}
      onPress={() => onChange(!value)}
      style={{
        flexDirection: "row",
        alignItems: "center",
        gap: 12,
        minHeight: 48,
      }}
    >
      <View
        style={{
          width: 24,
          height: 24,
          borderWidth: 1,
          borderColor: C.navy,
          borderRadius: 6,
          backgroundColor: value ? C.navy : "white",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        <T style={{ color: "white" }}>{value ? "✓" : ""}</T>
      </View>
      <T style={{ flex: 1 }}>{label}</T>
    </Pressable>
  );
}

export function Field({ label, ...props }) {
  return (
    <View style={{ gap: 6 }}>
      <T>{label}</T>
      <TextInput
        accessibilityLabel={label}
        {...props}
        style={[
          {
            borderWidth: 1,
            borderColor: C.line,
            borderRadius: 10,
            padding: 12,
            color: C.text,
            backgroundColor: "white",
            fontFamily: "NotoSansKR_400Regular",
          },
          props.style,
        ]}
      />
    </View>
  );
}

export function ErrorNote({ message }) {
  return message ? (
    <T accessibilityRole="alert" style={{ color: "#AD2936" }}>
      {message}
    </T>
  ) : null;
}

export async function openDocument(base, path) {
  const url = new URL(path, base);
  if (
    !["https:", "http:"].includes(url.protocol) ||
    url.username ||
    url.password
  )
    throw new Error("문서 주소를 확인해 주세요.");
  await Linking.openURL(url.href);
}

export function PolicyLink({ base, url, children }) {
  const [error, setError] = React.useState("");
  return (
    <View>
      <Pressable
        accessibilityRole="link"
        disabled={!url}
        onPress={() => {
          setError("");
          openDocument(base, url).catch(() =>
            setError("문서를 열지 못했습니다. 다시 시도해 주세요."),
          );
        }}
      >
        <T
          style={{
            color: C.navy,
            textDecorationLine: "underline",
            paddingVertical: 8,
          }}
        >
          {children}
        </T>
      </Pressable>
      <ErrorNote message={error} />
    </View>
  );
}
export function EmergencyHelp() {
  const [error, setError] = React.useState("");
  return (
    <View style={{ gap: 6 }}>
      <T style={{ color: "#AD2936" }}>응급 상황에는 분석을 기다리지 마세요.</T>
      <Button
        secondary
        onPress={() =>
          Linking.openURL("tel:119").catch(() =>
            setError("전화 앱에서 119로 연락해 주세요."),
          )
        }
      >
        119 전화 연결
      </Button>
      <ErrorNote message={error} />
    </View>
  );
}

import React, { useEffect, useState } from "react";
import { View } from "react-native";
import { Card, Title, T, Button } from "./Common";
import { Field } from "./ConsentControls";
import { SYMPTOMS } from "../services/progress";

export default function ProgressForm({
  session,
  busy,
  onSave,
  onCompare,
  onFollowup,
}) {
  const [date, setDate] = useState(""),
    [pain, setPain] = useState(""),
    [note, setNote] = useState("");

  const [swelling, setSwelling] = useState("UNRECORDED"),
    [exudate, setExudate] = useState("UNRECORDED");

  useEffect(() => {
    setDate(session.capturedOn || "");
    setPain(
      session.progress?.painScore == null
        ? ""
        : String(session.progress.painScore),
    );
    setNote(session.progress?.note || "");
    setSwelling(session.progress?.swelling || "UNRECORDED");
    setExudate(session.progress?.exudate || "UNRECORDED");
  }, [session.id, session.version]);

  return (
    <Card>
      <Title>경과 기록</Title>
      <T>
        이 사진을 촬영한 날의 증상을 기록해 주세요. 저장해도 기존 문진 결과가
        다시 계산되지는 않아요.
      </T>
      <Field
        label="촬영일 (YYYY-MM-DD, 모르면 비워 두기)"
        value={date}
        onChangeText={setDate}
        editable={!busy}
        maxLength={10}
        placeholder="2026-11-06"
      />
      <Field
        label="통증 점수 0~10 (선택)"
        keyboardType="numeric"
        value={pain}
        onChangeText={setPain}
        editable={!busy}
        maxLength={2}
      />
      {[
        ["붓기", swelling, setSwelling],
        ["진물", exudate, setExudate],
      ].map(([label, value, set]) => (
        <View key={label} style={{ gap: 4 }}>
          <T>{label}</T>
          <View style={{ flexDirection: "row", flexWrap: "wrap", gap: 6 }}>
            {SYMPTOMS.map(([id, text]) => (
              <Button
                key={id}
                secondary={id !== value}
                disabled={busy}
                onPress={() => set(id)}
              >
                {label} {text}
                {id === value ? " ✓" : ""}
              </Button>
            ))}
          </View>
        </View>
      ))}
      <Field
        label="메모 (선택)"
        multiline
        maxLength={2000}
        value={note}
        onChangeText={setNote}
        editable={!busy}
      />
      <Button
        secondary
        disabled={busy}
        onPress={() =>
          onSave({ pain, note, capturedOn: date, swelling, exudate })
        }
      >
        경과 기록 저장
      </Button>
      <Button secondary disabled={busy} onPress={onCompare}>
        촬영일 선택·사진 비교
      </Button>
      <Button disabled={busy} onPress={onFollowup}>
        이 상처의 경과 사진 추가
      </Button>
    </Card>
  );
}

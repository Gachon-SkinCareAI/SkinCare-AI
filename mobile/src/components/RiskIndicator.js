import React from "react";
import { View } from "react-native";
import { Card, T } from "./Common";
import { LEVELS, RISK_STEPS, riskLevel, recordRisk } from "../types/analysis";

export function RiskBadge({ record }) {
  const risk = recordRisk(record);

  return (
    <View
      testID="risk-badge"
      accessibilityLabel={risk.stage ? `위험도 ${risk.label}` : risk.label}
      style={{
        backgroundColor: risk.background,
        borderRadius: 20,
        paddingHorizontal: 10,
        paddingVertical: 5,
        flexShrink: 0,
      }}
    >
      <T
        style={{
          color: risk.color,
          fontSize: 12,
          lineHeight: 18,
          fontFamily: "NotoSansKR_700Bold",
        }}
      >
        {risk.label}
      </T>
    </View>
  );
}

export function RiskSummary({ code, children }) {
  const risk = riskLevel(code);

  return (
    <Card style={{ backgroundColor: risk.background, borderColor: risk.color }}>
      <T>{risk.legacy ? "이전 저장 결과" : "문진 기반 위험도"}</T>
      <View
        testID="risk-summary"
        style={{
          flexDirection: "row",
          alignItems: "baseline",
          flexWrap: "wrap",
          gap: 12,
        }}
      >
        <T
          style={{
            color: risk.color,
            fontSize: 32,
            lineHeight: 44,
            fontFamily: "NotoSansKR_700Bold",
          }}
        >
          {risk.title}
        </T>
        {risk.stage !== null && (
          <T style={{ color: risk.color, fontFamily: "NotoSansKR_700Bold" }}>
            {risk.stage}/3단계
          </T>
        )}
      </View>
      <T style={{ color: risk.color, fontFamily: "NotoSansKR_700Bold" }}>
        {risk.description}
      </T>
      {risk.stage !== null ? (
        <View
          accessibilityLabel="위험도 3단계"
          style={{ flexDirection: "row", gap: 8 }}
        >
          {RISK_STEPS.map((key) => {
            const step = LEVELS[key],
              selected = step.stage === risk.stage;
            return (
              <View
                key={key}
                testID={`risk-step-${key}`}
                accessibilityLabel={`${step.stage}단계 ${step.title}${selected ? ", 현재 단계" : ""}`}
                style={{
                  flex: 1,
                  padding: 8,
                  borderRadius: 10,
                  borderWidth: selected ? 2 : 1,
                  borderColor: selected ? step.color : "#cbd5e1",
                  backgroundColor: selected ? "#ffffff" : "#ffffff80",
                  gap: 5,
                }}
              >
                <View
                  style={{
                    height: 5,
                    borderRadius: 3,
                    backgroundColor: step.color,
                  }}
                />
                <T
                  style={{
                    color: step.color,
                    fontSize: 13,
                    fontFamily: selected
                      ? "NotoSansKR_700Bold"
                      : "NotoSansKR_400Regular",
                  }}
                >
                  {step.stage}. {step.title}
                </T>
                <T style={{ color: step.color, fontSize: 11, lineHeight: 16 }}>
                  {selected ? "현재 단계" : " "}
                </T>
              </View>
            );
          })}
        </View>
      ) : (
        <T>
          {risk.legacy
            ? "현재 위험도 3단계에 포함되지 않는 이전 결과예요."
            : "판단 불가는 위험도 3단계와 별도 상태이며 정상 판정을 뜻하지 않아요."}
        </T>
      )}
      {children}
      <T>연구·시연용 참고 정보이며 의료적 진단이 아니에요.</T>
    </Card>
  );
}

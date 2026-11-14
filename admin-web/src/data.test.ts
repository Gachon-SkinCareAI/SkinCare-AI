import { describe, expect, it } from "vitest";
import {
  csv,
  filterAnalyses,
  filterDiagnoses,
  filterInquiries,
  formatTime,
  parseStore,
  saveReply,
} from "./data";

const emptyStore = {
  version: 3 as const,
  dashboard: {},
  users: [],
  diagnoses: [],
  analyses: [],
  analysisSummary: { counts: {}, daily: [] },
  inquiries: [],
  monitoring: { metrics: {}, pipelines: [], health: {} },
};

describe("분석 기록 (운영 정보만)", () => {
  const rows = [
    { id: "abc-1", status: "COMPLETED", turn: 6, createdAt: "2026-11-14T20:06:19.95", updatedAt: "", aiUnavailable: false },
    { id: "def-2", status: "RETRY_REQUIRED", turn: 0, createdAt: "", updatedAt: "", aiUnavailable: true },
  ];

  it("처리 상태 이름과 기록 번호로 필터링한다", () => {
    expect(filterAnalyses(rows, "", "전체")).toHaveLength(2);
    expect(filterAnalyses(rows, "", "재연결 필요").map((r) => r.id)).toEqual(["def-2"]);
    expect(filterAnalyses(rows, "ABC", "전체").map((r) => r.id)).toEqual(["abc-1"]);
  });

  it("화면 모델에 사용자·분석 결과 항목이 없다", () => {
    expect(Object.keys(rows[0]).sort()).toEqual(
      ["aiUnavailable", "createdAt", "id", "status", "turn", "updatedAt"],
    );
  });

  it("서버 시각을 분 단위로 표시한다", () => {
    expect(formatTime("2026-11-14T20:06:19.95")).toBe("2026-11-14 20:06");
    expect(formatTime("")).toBe("");
  });
});

describe("관리자 API 데이터 유틸리티", () => {
  it("빈 API 저장소를 생성한다", () => {
    expect(emptyStore.users).toEqual([]);
  });

  it("진단과 문의를 필터링한다", () => {
    const store = emptyStore;
    const diagnosis = {
      id: "1",
      userId: "u1",
      time: "",
      condition: "화상",
      area: "",
      risk: "응급" as const,
      confidence: 90,
      status: "완료",
      connection: "",
    };
    const inquiry = {
      id: "1",
      category: "기타",
      userId: "u1",
      author: "사용자",
      subject: "문의",
      content: "",
      diagnosisId: null,
      date: "",
      reply: "",
    };
    expect(filterDiagnoses([diagnosis], "1", "응급")).toHaveLength(1);
    expect(filterInquiries([inquiry], "답변 대기")).toHaveLength(1);
    expect(
      saveReply({ ...store, inquiries: [inquiry] }, "1", "답변").inquiries[0]
        .reply,
    ).toBe("답변");
  });

  it("손상된 API 저장소를 거부한다", () => {
    expect(() => parseStore("{}")).toThrow();
  });

  it("CSV 수식 입력을 안전하게 이스케이프한다", () => {
    expect(csv([["=1+2"]])).toContain(`"'=1+2"`);
  });
});

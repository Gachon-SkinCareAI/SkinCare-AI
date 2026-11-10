import { describe, expect, it } from "vitest";
import {
  csv,
  filterDiagnoses,
  filterInquiries,
  parseStore,
  saveReply,
} from "./data";

const emptyStore = {
  version: 3 as const,
  dashboard: {},
  users: [],
  diagnoses: [],
  inquiries: [],
  monitoring: { metrics: {}, pipelines: [], health: {} },
};

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

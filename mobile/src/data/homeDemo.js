// Figma 시연 데이터입니다. 실제 사용자 기록/의료 분석 결과가 아닙니다.
export const recentAnalyses = [
  {
    id: "leg",
    title: "다리 상처",
    summary: "2도 화상 · 09.20",
    status: "경고",
    image: require("../../assets/home/recent-leg.png"),
  },
  {
    id: "arm",
    title: "팔 부위 피부",
    summary: "찰과상 · 09.27",
    status: "관찰",
    image: require("../../assets/home/recent-arm.png"),
  },
  {
    id: "hand",
    title: "손등",
    summary: "베임 · 09.12",
    status: "관찰",
    image: require("../../assets/home/recent-hand.png"),
  },
];
export const recovery = {
  id: "leg",
  title: "다리 화상 회복 과정",
  latestDate: "09.27",
  status: "안정적으로 회복 중",
  photos: [
    { date: "09.20", image: require("../../assets/home/recovery-1.png") },
    { date: "09.23", image: require("../../assets/home/recovery-2.png") },
    { date: "09.27", image: require("../../assets/home/recovery-3.png") },
  ],
};

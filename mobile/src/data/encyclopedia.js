// Editorial educational notes, not personalized treatment or new vision classes.
// Do not copy source articles/images here. Full detail stays on the original site.
// Sources were read on the stated date; this catalog is not an automatic API feed.
export const SOURCES = {
  "nhsWounds": {
    "organization": "NHS",
    "title": "Cuts and grazes",
    "language": "영문",
    "url": "https://www.nhs.uk/conditions/cuts-and-grazes/",
    "checkedAt": "2026-11-06",
    "updatedAt": "2026-04-02"
  },
  "medlineBruises": {
    "organization": "미국 국립의학도서관 · MedlinePlus",
    "title": "Bruises",
    "language": "영문",
    "url": "https://medlineplus.gov/bruises.html",
    "checkedAt": "2026-11-06"
  },
  "kdcaBurn": {
    "organization": "질병관리청 국가건강정보포털",
    "title": "화상",
    "language": "한국어",
    "url": "https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6584",
    "checkedAt": "2026-11-06",
    "updatedAt": "2026-06-04"
  },
  "kdcaAcne": {
    "organization": "질병관리청 국가건강정보포털",
    "title": "여드름",
    "language": "한국어",
    "url": "https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=3947",
    "checkedAt": "2026-11-06",
    "updatedAt": "2026-08-03"
  },
  "kdcaEczema": {
    "organization": "질병관리청 국가건강정보포털",
    "title": "아토피피부염",
    "language": "한국어",
    "url": "https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=6582",
    "checkedAt": "2026-11-06",
    "updatedAt": "2026-05-06"
  },
  "kdcaContact": {
    "organization": "질병관리청 국가건강정보포털",
    "title": "자극접촉피부염",
    "language": "한국어",
    "url": "https://health.kdca.go.kr/healthinfo/biz/health/gnrlzHealthInfo/gnrlzHealthInfo/gnrlzHealthInfoView.do?cntnts_sn=5689",
    "checkedAt": "2026-11-06"
  },
  "nhsContact": {
    "organization": "NHS",
    "title": "Contact dermatitis",
    "language": "영문",
    "url": "https://www.nhs.uk/conditions/contact-dermatitis/",
    "checkedAt": "2026-11-06"
  }
};

export const ARTICLES = [
  {
    "id": "abrasion",
    "title": "찰과상",
    "category": "상처",
    "tags": "긁힘 쓸림 피부 표면",
    "summary": "쓸리거나 긁힌 상처의 세척·보호와 감염 징후를 알아봐요.",
    "body": "피부가 쓸린 작은 상처라도 오염과 감염 여부를 살펴야 해요.",
    "sourceIds": [
      "nhsWounds"
    ],
    "sections": [
      {
        "title": "관리할 때 참고하세요",
        "bullets": [
          "손을 씻고, 출혈이 멎은 작은 상처는 깨끗한 물로 씻은 뒤 드레싱으로 보호해요.",
          "드레싱은 깨끗하게 유지하고 필요할 때 교체해요."
        ]
      },
      {
        "title": "진료가 필요한 경우",
        "bullets": [
          "씻어도 오염이 남거나 붓기·붉어짐·통증이 심해지고 고름이나 열이 나면 진료를 받아요.",
          "멈추지 않는 출혈, 감각 저하 또는 움직임의 어려움은 즉시 응급진료가 필요해요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "open_wound",
    "title": "벌어진 상처",
    "category": "상처",
    "tags": "열상 베임 출혈",
    "summary": "깊은 베임, 출혈, 이물질이 있는 상처의 주의점을 확인해요.",
    "body": "피부가 열린 상처는 깊이·오염 상태에 따라 세척과 봉합 등의 처치가 달라져요.",
    "sourceIds": [
      "nhsWounds"
    ],
    "sections": [
      {
        "title": "출혈·이물질 확인",
        "bullets": [
          "박힌 물체가 없다면 깨끗한 천으로 압박해요. 박힌 유리 등의 물체는 직접 빼거나 그 위를 누르지 않아요."
        ]
      },
      {
        "title": "진료가 필요한 경우",
        "bullets": [
          "크고 깊은 상처, 멈추지 않는 출혈, 감각·움직임 이상은 즉시 응급진료가 필요해요.",
          "의료진이 감염 위험을 살피고 상처를 닫는 방법과 파상풍 예방 필요성을 판단해요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "bruise",
    "title": "타박상",
    "category": "상처",
    "tags": "멍 부딪힘 붓기",
    "summary": "충격으로 생긴 멍의 변화와 확인이 필요한 상황을 알아봐요.",
    "body": "충격으로 작은 혈관이 손상되면 피부 아래에 피가 고여 멍이 생길 수 있어요. 피부뿐 아니라 근육이나 뼈에도 타박상이 생길 수 있어요.",
    "sourceIds": [
      "medlineBruises"
    ],
    "sections": [
      {
        "title": "흔히 보이는 변화",
        "bullets": [
          "통증과 부기가 동반될 수 있고, 시간이 지나면서 멍의 색이 달라질 수 있어요.",
          "대개 수주 안에 호전되지만 깊이나 손상 부위에 따라 더 오래 남기도 해요."
        ]
      },
      {
        "title": "관리할 때 참고하세요",
        "bullets": [
          "가벼운 멍에는 냉찜질과 다친 부위를 올리는 방법이 통증·부기 완화에 도움이 될 수 있어요."
        ]
      },
      {
        "title": "진료가 필요한 경우",
        "bullets": [
          "이유를 알 수 없는 멍이나 감염이 의심되는 변화가 있으면 의료진의 확인을 받아요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "burn",
    "title": "화상",
    "category": "상처",
    "tags": "열 뜨거움 물 증기 화학 전기",
    "summary": "열·전기·화학물질로 인한 손상과 초기 대응의 주의점을 알아봐요.",
    "body": "화상은 뜨거운 물체나 액체뿐 아니라 전기·화학물질 등으로도 생겨요. 피부 손상의 깊이, 면적, 원인과 부위에 따라 필요한 치료가 달라져요.",
    "sourceIds": [
      "kdcaBurn"
    ],
    "sections": [
      {
        "title": "살펴볼 변화",
        "bullets": [
          "붉어짐과 통증, 물집이 나타날 수 있고 더 깊은 조직까지 손상될 수 있어요.",
          "연기를 마신 상황에서는 피부 외에 호흡기 손상도 확인해야 해요."
        ]
      },
      {
        "title": "열에 데었을 때",
        "bullets": [
          "먼저 열원에서 벗어나 안전을 확보해요. 데인 부위는 흐르는 시원한 물로 약 20분 식혀요.",
          "물집은 일부러 터뜨리지 않아요. 전기·화학물질 화상은 원인에 맞는 별도 처치가 필요해요."
        ]
      },
      {
        "title": "진료가 필요한 경우",
        "bullets": [
          "넓거나 깊은 화상은 의료진의 평가가 필요해요.",
          "얼굴 주변 화상과 함께 목소리가 쉬거나 호흡이 어려우면 즉시 응급진료가 필요해요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "acne",
    "title": "여드름",
    "category": "피부 질환",
    "tags": "뾰루지 피지 모공 면포",
    "summary": "면포·염증성 병변의 차이와 피부 관리 시 주의점을 알아봐요.",
    "body": "피지 분비, 모공 막힘, 염증 등 여러 요인이 관여하는 피부 질환이에요. 청소년뿐 아니라 성인에게도 나타날 수 있어요.",
    "sourceIds": [
      "kdcaAcne"
    ],
    "sections": [
      {
        "title": "흔히 보이는 증상",
        "bullets": [
          "얼굴·가슴·등 등에 흰색이나 검은색 면포, 붉은 구진, 고름이 찬 병변이 나타날 수 있어요.",
          "피부 속 깊은 결절이 생기거나 색소 변화와 흉터가 남기도 해요."
        ]
      },
      {
        "title": "일상 관리",
        "bullets": [
          "미지근한 물로 부드럽게 세안해요. 지나친 세안과 강하게 문지르는 습관은 피부 장벽에 부담을 줄 수 있어요.",
          "피부 상태에 맞는 보습을 유지해요. 치료제 선택은 병변의 종류와 정도에 따라 달라져요."
        ]
      },
      {
        "title": "진료를 고려하세요",
        "bullets": [
          "깊고 아픈 병변이나 흉터가 생긴다면 적절한 치료 시기를 의료진과 상의해요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "eczema",
    "title": "아토피 피부염",
    "category": "피부 질환",
    "tags": "습진 가려움 건조 아토피피부염",
    "summary": "반복되는 가려움·건조함과 생활 관리의 원칙을 살펴봐요.",
    "body": "가려움과 염증이 반복되는 만성 피부 질환이에요. 피부 장벽, 면역, 유전과 환경 요인이 함께 작용하며 나이에 따라 나타나는 부위가 다를 수 있어요.",
    "sourceIds": [
      "kdcaEczema"
    ],
    "sections": [
      {
        "title": "흔히 보이는 증상",
        "bullets": [
          "건조함, 붉은 부위, 물집·진물·딱지가 나타날 수 있어요.",
          "반복해서 긁으면 피부가 두꺼워질 수 있고, 악화와 호전을 반복할 수 있어요."
        ]
      },
      {
        "title": "일상 관리",
        "bullets": [
          "꾸준한 보습과 개인에게 자극이 되는 요인을 줄이는 것이 관리의 기본이에요.",
          "특정 음식이 원인이라고 임의로 단정해 제한하지 말고, 필요한 검사와 식사 관리는 의료진과 상의해요."
        ]
      },
      {
        "title": "진료에서 확인하는 점",
        "bullets": [
          "증상 경과와 피부 진찰 등을 함께 살펴 진단해요. 바르는 약 등 치료 방법은 상태에 따라 정해요."
        ],
        "attention": true
      }
    ]
  },
  {
    "id": "contact",
    "title": "접촉 피부염",
    "category": "피부 질환",
    "tags": "알레르기 자극 습진 세제 금속",
    "summary": "세제·화장품·금속 등과 접촉한 뒤 생기는 피부 반응을 알아봐요.",
    "body": "특정 물질과의 접촉 뒤 피부에 염증이 생기는 질환이에요. 피부를 직접 손상시키는 자극 반응과 면역계가 관여하는 알레르기 반응으로 나뉘어요.",
    "sourceIds": [
      "kdcaContact",
      "nhsContact"
    ],
    "sections": [
      {
        "title": "원인과 증상",
        "bullets": [
          "물·비누·세제에 반복적으로 노출되면 자극접촉피부염이 생길 수 있어요. 화장품이나 니켈 같은 금속은 알레르기 반응의 원인이 되기도 해요.",
          "가려움, 물집, 건조함과 갈라짐이 나타날 수 있으며 접촉 후 수시간에서 수일 뒤 시작되기도 해요."
        ]
      },
      {
        "title": "일상 관리",
        "bullets": [
          "원인으로 의심되는 물질과의 접촉을 줄이고 보습해요. 손을 자극하는 작업에서는 적절한 보호 장갑이 도움이 돼요."
        ]
      },
      {
        "title": "진료를 고려하세요",
        "bullets": [
          "증상이 오래가거나 반복되고 심하면 진료를 받아요. 원인을 모르거나 치료에 반응하지 않으면 피부과 평가가 필요할 수 있어요.",
          "진료 시 최근 사용한 제품과 노출 상황을 알려 주세요. 필요하면 알레르기 여부를 구분하는 검사를 진행해요."
        ],
        "attention": true
      }
    ]
  }
];

export function searchArticles(query, category = '전체') {
  const words = query.trim().toLocaleLowerCase().split(/\s+/).filter(Boolean);
  return ARTICLES.filter(article => {
    if (category !== '전체' && article.category !== category) return false;
    const text = [article.title, article.tags, article.summary, article.body,
      ...article.sections.flatMap(section => [section.title, ...section.bullets])].join(' ').toLocaleLowerCase();
    return words.every(word => text.includes(word));
  });
}

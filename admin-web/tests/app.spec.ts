import { expect, test } from '@playwright/test'

const pages = [
  '대시보드',
  '진단 로그',
  'AI 모니터링',
  '의료기관 관리',
  '사용자 관리',
  '문의 관리',
]

test('대시보드와 6개 메뉴가 와이어프레임 구조로 동작한다', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', (e) => errors.push(e.message))
  await page.goto('/')
  await expect(
    page.getByRole('heading', { name: '대시보드', exact: true }),
  ).toBeVisible()
  await expect(page.getByText('12,847', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('img', { name: /최근 7일 진단 건수/ }),
  ).toBeVisible()
  await expect(page.getByText('289건 (66%)')).toBeVisible()
  await expect(page.locator('.emergency-row')).toHaveCount(3)
  for (const title of pages) {
    await page
      .getByRole('navigation', { name: '주 메뉴' })
      .getByRole('link', { name: title, exact: true })
      .click()
    await expect(
      page.getByRole('heading', { name: title, exact: true }),
    ).toBeVisible()
    await expect(
      page.getByRole('link', { name: title, exact: true }),
    ).toHaveAttribute('aria-current', 'page')
  }
  expect(errors).toEqual([])
})

test('대시보드 응급 전체보기에서 응급 필터로 이동한다', async ({ page }) => {
  await page.goto('/')
  await page.getByRole('button', { name: '전체보기' }).click()
  await expect(
    page.getByRole('heading', { name: '진단 로그', exact: true }),
  ).toBeVisible()
  await expect(
    page.getByRole('button', { name: '응급', exact: true }),
  ).toHaveAttribute('aria-pressed', 'true')
  const rows = page.getByRole('table').getByRole('row')
  await expect(rows).toHaveCount(7)
  for (const row of (await rows.all()).slice(1))
    await expect(row.getByText('응급', { exact: true })).toBeVisible()
})

test('진단 검색과 상태 변경을 저장하고 새로고침해도 유지한다', async ({
  page,
}) => {
  await page.goto('/#diagnoses')
  await page.getByLabel('진단 검색', { exact: true }).fill('dx-20260929-0438')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  await page
    .getByRole('button', { name: 'DX-20260929-0438 진단 상세', exact: true })
    .click()
  const dialog = page.getByRole('dialog')
  await expect(dialog.getByText('3도 화상', { exact: true })).toBeVisible()
  await dialog.getByLabel('처리 상태', { exact: true }).selectOption('진행중')
  await page.keyboard.press('Escape')
  await page.reload()
  await page.getByLabel('진단 검색', { exact: true }).fill('0438')
  await expect(
    page.getByRole('cell', { name: '진행중', exact: true }),
  ).toBeVisible()
})

test('사용자 상세의 계정 상태와 상태 필터가 동작한다', async ({ page }) => {
  await page.goto('/#users')
  await page.getByLabel('사용자 검색', { exact: true }).fill('김민서')
  await page
    .getByRole('button', { name: 'USR-2847 사용자 상세', exact: true })
    .click()
  await page
    .getByRole('button', { name: '비활성으로 변경', exact: true })
    .click()
  await expect(
    page.getByRole('dialog').getByText('비활성', { exact: true }),
  ).toBeVisible()
  await page.keyboard.press('Escape')
  await page.reload()
  await page.getByLabel('사용자 상태').selectOption('비활성')
  await page.getByLabel('사용자 검색', { exact: true }).fill('USR-2847')
  await expect(page.getByRole('table').getByRole('row')).toHaveCount(2)
  await expect(
    page.getByRole('cell', { name: '비활성', exact: true }),
  ).toBeVisible()
})

test('의료기관 검색, 종류 필터와 상세 및 API 미연결 안내를 제공한다', async ({
  page,
}) => {
  await page.goto('/#facilities')
  await page.getByLabel('의료기관 종류').selectOption('약국')
  const rows = page.getByRole('table').getByRole('row')
  await expect(rows).toHaveCount(4)
  await page.getByLabel('의료기관 검색', { exact: true }).fill('새벽')
  await expect(rows).toHaveCount(2)
  await page.getByRole('button', { name: '새벽공공약국 상세 보기' }).click()
  await expect(page.getByRole('dialog')).toContainText('가상의 샘플')
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: 'OpenAPI 데이터 동기화' }).click()
  await expect(page.getByRole('dialog')).toContainText(
    '실제 기관에 전송되지 않습니다',
  )
})

test('문의 답변 검증과 저장, 수정, 완료 필터가 동작한다', async ({ page }) => {
  await page.goto('/#inquiries')
  await page.getByRole('button', { name: '답변 대기', exact: true }).click()
  await page.getByRole('button', { name: '문의 001 상세보기' }).click()
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('button', { name: '답변 저장', exact: true }).click()
  await expect(dialog.getByRole('alert')).toHaveText('답변을 입력해 주세요.')
  await dialog
    .getByLabel('관리자 답변')
    .fill('테스트 답변입니다. 실제 메시지는 발송하지 않습니다.')
  await dialog.getByRole('button', { name: '답변 저장', exact: true }).click()
  await expect(dialog.getByText('답변 완료', { exact: true })).toBeVisible()
  await dialog.getByLabel('관리자 답변').fill('수정한 샘플 답변입니다.')
  await dialog
    .getByRole('button', { name: '답변 수정 저장', exact: true })
    .click()
  await page.keyboard.press('Escape')
  await expect(
    page.getByRole('button', { name: '문의 001 상세보기' }),
  ).toHaveCount(0)
  await page.reload()
  await page.getByRole('button', { name: '답변 완료', exact: true }).click()
  await page.getByRole('button', { name: '문의 001 상세보기' }).click()
  await expect(page.getByRole('dialog').getByLabel('관리자 답변')).toHaveValue(
    '수정한 샘플 답변입니다.',
  )
  await page.getByRole('button', { name: /관련 분석 기록/ }).click()
  await expect(page.getByRole('dialog', { name: '진단 상세' })).toBeVisible()
})

test('AI 성능 카드, Recall 목표선과 파이프라인 상태를 표시한다', async ({
  page,
}) => {
  await page.goto('/#monitoring')
  await expect(page.locator('.metric-card')).toHaveCount(6)
  await expect(page.getByText('96.2%', { exact: true })).toBeVisible()
  await expect(
    page.getByRole('img', { name: /최근 7일 위험군 Recall/ }),
  ).toBeVisible()
  await expect(page.getByText('98% 목표', { exact: true })).toBeVisible()
  await expect(page.locator('.pipeline-row')).toHaveCount(3)
  await expect(page.getByText('샘플 상태', { exact: true })).toBeVisible()
})

test('전체 검색에서 기관 상세를 열고 알림에서 문의를 확인한다', async ({
  page,
}) => {
  await page.goto('/')
  await page.getByRole('button', { name: '전체 검색 열기' }).click()
  await page.getByLabel('전체 검색어').fill('우리동')
  await page
    .getByRole('dialog')
    .getByRole('button', { name: /우리동 종합병원/ })
    .click()
  await expect(
    page.getByRole('dialog', { name: '의료기관 상세' }),
  ).toBeVisible()
  await page.keyboard.press('Escape')
  await page.getByRole('button', { name: '알림 보기' }).click()
  await page.locator('.notification-item').first().click()
  await expect(
    page.getByRole('dialog', { name: '문의 001 상세' }),
  ).toBeVisible()
})

test('현재 검색 조건의 진단 CSV를 다운로드한다', async ({ page }) => {
  await page.goto('/#diagnoses')
  await page.getByLabel('진단 검색', { exact: true }).fill('0438')
  const pending = page.waitForEvent('download')
  await page
    .getByRole('button', { name: '현재 진단 목록 CSV 다운로드' })
    .click()
  const download = await pending
  expect(download.suggestedFilename()).toBe('diagnoses.csv')
  const stream = await download.createReadStream()
  const chunks: Buffer[] = []
  for await (const chunk of stream!) chunks.push(Buffer.from(chunk))
  const text = Buffer.concat(chunks).toString('utf8')
  expect(text).toContain('DX-20260929-0438')
  expect(text).not.toContain('DX-20260929-0437')
  expect(text.split('\r\n')).toHaveLength(2)
})

test('페이지 이동과 빈 검색 결과를 처리한다', async ({ page }) => {
  await page.goto('/#users')
  await page.getByRole('button', { name: '다음 페이지' }).click()
  await expect(
    page.getByRole('button', { name: 'USR-3341 사용자 상세' }),
  ).toBeVisible()
  await page
    .getByLabel('사용자 검색', { exact: true })
    .fill('존재하지않는사용자')
  await expect(
    page.getByText('검색 결과가 없습니다', { exact: true }),
  ).toBeVisible()
  await expect(page.getByRole('button', { name: '다음 페이지' })).toBeDisabled()
  await page.getByRole('button', { name: '사용자 검색 지우기' }).click()
  await expect(
    page.getByRole('button', { name: 'USR-2847 사용자 상세' }),
  ).toBeVisible()
})

test('모바일 6개 화면에서 메뉴와 가로 스크롤 범위가 정상이다', async ({
  page,
}) => {
  await page.setViewportSize({ width: 390, height: 844 })
  await page.goto('/')
  for (const title of pages) {
    await page.getByRole('button', { name: '메뉴 열기' }).click()
    await page
      .getByRole('navigation', { name: '주 메뉴' })
      .getByRole('link', { name: title, exact: true })
      .click()
    await expect(
      page.getByRole('heading', { name: title, exact: true }),
    ).toBeVisible()
    await expect
      .poll(() =>
        page
          .locator('main')
          .evaluate((e) => Math.round(e.getBoundingClientRect().width)),
      )
      .toBe(390)
    expect(
      await page.evaluate(
        () => document.documentElement.scrollWidth <= innerWidth,
      ),
    ).toBe(true)
  }
})

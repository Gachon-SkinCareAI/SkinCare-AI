import { expect, test } from '@playwright/test'

test('미로그인 관리자는 로그인 화면만 볼 수 있고 입력값을 검증한다', async ({ page }) => {
  await page.goto('/admin/dashboard')
  await expect(page.getByRole('heading', { name: '관리자 로그인' })).toBeVisible()
  await expect(page.getByRole('navigation', { name: '주 메뉴' })).toHaveCount(0)

  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByText('관리자 아이디를 입력해 주세요.')).toBeVisible()
  await expect(page.getByText('비밀번호를 입력해 주세요.')).toBeVisible()

  const password = page.getByLabel('비밀번호', { exact: true })
  await expect(password).toHaveAttribute('type', 'password')
  await page.getByRole('button', { name: '비밀번호 표시' }).click()
  await expect(password).toHaveAttribute('type', 'text')
  await page.getByRole('button', { name: '비밀번호 숨기기' }).click()
  await expect(password).toHaveAttribute('type', 'password')
})

test('관리자 로그인, 아이디 기억하기 및 로그아웃이 동작한다', async ({ page }) => {
  await page.route('**/api/wc/admin/**', (route) => {
    const path = new URL(route.request().url()).pathname
    if (path.endsWith('/auth/login')) {
      return route.fulfill({ json: { adminId: 'operator', name: '테스트 관리자', token: 'test-admin-token' } })
    }
    if (path.endsWith('/auth/logout')) return route.fulfill({ json: { success: true } })
    if (path.endsWith('/dashboard')) return route.fulfill({ json: {} })
    if (path.endsWith('/ai/metrics')) return route.fulfill({ json: { metrics_summary: {} } })
    if (path.endsWith('/ai/pipelines')) return route.fulfill({ json: { pipeline_status: [] } })
    if (path.endsWith('/ai/health')) return route.fulfill({ json: { ai_health: {} } })
    return route.fulfill({ json: { items: [], page: 0, size: 20, hasNext: false, total: 0 } })
  })

  await page.goto('/admin/dashboard')
  await page.getByLabel('관리자 아이디').fill('operator')
  await page.getByLabel('비밀번호', { exact: true }).fill('test-password')
  await page.getByLabel('아이디 기억하기').check()
  await page.getByRole('button', { name: '로그인', exact: true }).click()

  await expect(page.getByRole('heading', { name: '대시보드', exact: true })).toBeVisible()
  await expect(page.getByText('테스트 관리자')).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('skincare-admin-token'))).toBe('test-admin-token')
  expect(await page.evaluate(() => localStorage.getItem('skincare-admin-remembered-id'))).toBe('operator')

  await page.getByRole('button', { name: '로그아웃' }).click()
  await expect(page.getByRole('heading', { name: '관리자 로그인' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('skincare-admin-token'))).toBeNull()
  await page.reload()
  await expect(page.getByLabel('관리자 아이디')).toHaveValue('operator')
})

test('로그인 실패와 세션 만료 시 관리자 화면 접근을 막는다', async ({ page }) => {
  await page.route('**/api/wc/admin/**', (route) => route.fulfill({ status: 401, json: {} }))
  await page.goto('/admin/dashboard')
  await page.getByLabel('관리자 아이디').fill('wrong')
  await page.getByLabel('비밀번호', { exact: true }).fill('wrong')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.getByRole('alert')).toContainText('아이디 또는 비밀번호')
  await expect(page.getByRole('navigation', { name: '주 메뉴' })).toHaveCount(0)

  await page.evaluate(() => localStorage.setItem('skincare-admin-token', 'expired'))
  await page.reload()
  await expect(page.getByRole('heading', { name: '관리자 로그인' })).toBeVisible()
  expect(await page.evaluate(() => localStorage.getItem('skincare-admin-token'))).toBeNull()
})

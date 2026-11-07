import { test, expect } from '@playwright/test'
import { createServer, type Server } from 'node:http'
import { readFile } from 'node:fs/promises'
import { resolve, sep, extname } from 'node:path'

let server: Server
test.beforeAll(async () => {
  const root = resolve('operational')
  server = createServer(async (request, response) => {
    try {
      const url = new URL(request.url || '/', 'http://localhost')
      const file = resolve(root, '.' + (url.pathname === '/' ? '/index.html' : url.pathname))
      if (!file.startsWith(root + sep)) throw new Error('Invalid path')
      const data = await readFile(file)
      const type = { '.js': 'text/javascript', '.css': 'text/css', '.html': 'text/html' }[extname(file)] || 'application/octet-stream'
      response.writeHead(200, { 'Content-Type': type }); response.end(data)
    } catch { response.writeHead(404); response.end() }
  })
  await new Promise<void>(resolve => server.listen(5182, '127.0.0.1', resolve))
})
test.afterAll(async () => {
  server.closeAllConnections()
  await new Promise<void>((resolve, reject) => server.close(error => error ? reject(error) : resolve()))
})

test('modules load, protected tabs work, inquiry reply is sent, and expired sessions clear state', async ({ page }) => {
  const errors: string[] = []
  page.on('pageerror', error => errors.push(error.message))
  let expired = false
  let answer: { content: string } | null = null
  const item = { id: 'fixture-inquiry', title: '합성 문의', content: '테스트 내용', status: 'WAITING', version: 1, createdAt: '2026-11-07T00:00:00' }
  const list = (items: unknown[]) => ({ items, total: items.length, page: 0, hasNext: false })
  await page.route('**/api/wc/**', async route => {
    const request = route.request()
    const path = new URL(request.url()).pathname
    if (path.endsWith('/auth/login')) return route.fulfill({ json: { token: 'synthetic-session-token' } })
    expect(request.headers().authorization).toBe('Bearer synthetic-session-token')
    if (expired) return route.fulfill({ status: 401, json: { message: '세션 만료' } })
    let response: unknown
    if (path.endsWith('/dashboard')) response = { waitingInquiries: 1, completedInquiries: 0, activeHospitals: 0, inactiveHospitals: 0 }
    else if (path.endsWith('/inquiries')) response = list([item])
    else if (path.endsWith('/answer')) {
      answer = request.postDataJSON()
      expect(request.method()).toBe('PUT')
      expect(request.postDataJSON().expectedVersion).toBe(1)
      expect(request.postDataJSON().clientRequestId).toMatch(/^[0-9a-f-]{36}$/)
      response = { id: item.id }
    } else if (path.endsWith('/inquiries/' + item.id)) response = { ...item, answer }
    else if (path.endsWith('/hospitals') || path.endsWith('/reviews') || path.endsWith('/audit')) response = list([])
    else if (path.endsWith('/operations')) response = { ...list([]), counts: { COMPLETED: 3 } }
    else if (path.endsWith('/ai-status')) response = { status: 'READY' }
    else if (path.endsWith('/readiness')) response = { checks: [], checkedAt: '2026-11-07T00:00:00Z' }
    else if (path.endsWith('/capabilities')) response = { actorId: 'synthetic-admin', clinicalReviewer: false }
    else throw new Error('Unhandled API route: ' + path)
    await route.fulfill({ json: response })
  })
  await page.goto('/')
  await page.getByLabel('관리자 아이디').fill('synthetic-admin')
  await page.getByLabel('비밀번호', { exact: true }).fill('synthetic-password')
  await page.getByRole('button', { name: '로그인', exact: true }).click()
  await expect(page.locator('#workspace')).toBeVisible()
  await page.getByRole('button', { name: /합성 문의/ }).click()
  await page.getByLabel('운영팀 답변').fill('합성 답변')
  await page.getByRole('button', { name: '답변 등록' }).click()
  await expect(page.locator('#inquiry-detail')).toContainText('합성 답변')
  for (const tab of ['hospitals', 'operations', 'reviews', 'audit', 'account']) {
    await page.locator(`[data-tab="${tab}"]`).click()
    await expect(page.locator(`#${tab}-panel`)).toBeVisible()
    await expect(page.locator('#progress')).toBeHidden()
  }
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0])
  expired = true
  await page.locator('[data-tab="inquiries"]').click()
  await expect(page.locator('#workspace')).toBeHidden()
  await expect(page.locator('#login-panel')).toBeVisible()
  await expect(page.locator('#inquiry-detail')).toBeEmpty()
  expect(errors).toEqual([])
})

// 리캐치 백엔드 (시제품). 단말이 파싱한 승인·취소 이벤트를 받아 혜택 계산·재결제 판정·정산을 한다.
// 원문 알림은 받지 않는다. 받는 것: 종류·카드사·끝자리·금액·가맹점명·시각·누적 실적.
//
// 인증: 등록 때 발급한 토큰(Bearer). 서버에는 토큰의 SHA-256만 저장.

import { randomBytes, createHash, randomUUID } from 'node:crypto';
import { createReCatch, monthlyStatement, billingCycle, feeFor, localDate, BILLING_THRESHOLD } from '../core/index.js';

// 웹 시연과 같은 바코드 번호 규칙
import { validate as validateBarcode, FORMATS as BARCODE_FORMATS } from './barcode-rules.js';
const PROFILE_KEYS = ['student', 'region', 'age'];

const MAX_BODY = 32 * 1024;
const sha256 = s => createHash('sha256').update(s).digest('hex');
const clone = o => JSON.parse(JSON.stringify(o));

class HttpError extends Error {
  constructor(status, code, message) { super(message ?? code); this.status = status; this.code = code; }
}
const bad = (code, message) => new HttpError(400, code, message);

// ---------- 입력 검사 ----------
const isInt = (v, min, max) => Number.isInteger(v) && v >= min && v <= max;
const optStr = (v, max) => v == null || (typeof v === 'string' && v.length <= max);
const isoOrNull = v => {
  if (v == null) return null;
  if (typeof v !== 'string' || v.length > 40 || Number.isNaN(Date.parse(v))) throw bad('invalid_time');
  return new Date(v);
};

function readEvent(b) {
  if (!b || typeof b !== 'object') throw bad('invalid_body');
  if (b.type !== 'approve' && b.type !== 'cancel') throw bad('invalid_type');
  if (!isInt(b.amount, 1, 100_000_000)) throw bad('invalid_amount');
  if (!optStr(b.issuer, 10)) throw bad('invalid_issuer');
  if (b.last4 != null && !/^\d{4}$/.test(b.last4)) throw bad('invalid_last4');
  if (!optStr(b.merchantRaw, 60)) throw bad('invalid_merchant');
  if (b.cumulative != null && !isInt(b.cumulative, 0, 1_000_000_000)) throw bad('invalid_cumulative');
  return {
    parsed: {
      type: b.type, issuer: b.issuer ?? null, last4: b.last4 ?? null, amount: b.amount,
      merchantRaw: b.merchantRaw ?? null, at: isoOrNull(b.at)?.toISOString() ?? null,
      cumulative: b.cumulative ?? null
    },
    receivedAt: isoOrNull(b.receivedAt)
  };
}

function readWallet(b, catalog) {
  if (!b || !Array.isArray(b.cards) || !Array.isArray(b.memberships)) throw bad('invalid_wallet');
  if (b.cards.length > 20 || b.memberships.length > 20) throw bad('too_many_items');
  const cards = b.cards.map(c => {
    const p = catalog.cards.find(x => x.id === c?.productId);
    if (!p) throw bad('unknown_card_product');
    if (!/^\d{4}$/.test(c.last4 ?? '')) throw bad('invalid_last4');
    if (!isInt(c.prevMonthSpend ?? 0, 0, 1_000_000_000)) throw bad('invalid_spend');
    if (c.monthSpend != null && !isInt(c.monthSpend, -1_000_000_000, 1_000_000_000)) throw bad('invalid_spend');
    const usage = {};
    for (const [k, v] of Object.entries(c.usage ?? {})) {
      if (!p.benefits.some(x => x.id === k) || !isInt(v, 0, 100_000_000)) throw bad('invalid_usage');
      usage[k] = v;
    }
    return { issuer: p.issuer, productId: p.id, last4: c.last4, prevMonthSpend: c.prevMonthSpend ?? 0, monthSpend: c.monthSpend ?? null, usage };
  });
  if (new Set(cards.map(c => c.last4)).size !== cards.length) throw bad('duplicate_last4');
  const memberships = b.memberships.map(m => {
    if (!m || typeof m !== 'object') throw bad('invalid_membership');
    let out;
    if (m.productId != null) {
      const p = catalog.memberships.find(x => x.id === m.productId);
      if (!p) throw bad('unknown_membership');
      if (p.tiers && !Object.hasOwn(p.tiers, m.tier ?? '')) throw bad('invalid_tier');
      out = { productId: p.id, ...(p.tiers ? { tier: m.tier } : {}), usage: {} };
    } else {
      // 혜택 정보가 없는 멤버십도 바코드 지갑으로는 쓸 수 있다 (추천 계산에는 안 쓰임)
      if (typeof m.name !== 'string' || !m.name.trim() || m.name.trim().length > 40) throw bad('invalid_name');
      if (!['membership', 'telecom'].includes(m.kind ?? 'membership')) throw bad('invalid_kind');
      if (m.code == null || m.code === '') throw bad('code_required');
      out = { productId: null, name: m.name.trim(), kind: m.kind ?? 'membership', usage: {} };
    }
    if (m.code != null && m.code !== '') {
      const format = m.format ?? 'numeric';
      if (typeof m.code !== 'string' || !Object.hasOwn(BARCODE_FORMATS, format)) throw bad('invalid_barcode');
      const r = validateBarcode({ name: out.name ?? 'x', number: m.code, format });
      if (r.error) throw bad('invalid_barcode', r.error);
      out.code = r.raw;
      out.format = format;
    }
    return out;
  });
  const codes = memberships.filter(m => m.code).map(m => m.code);
  if (new Set(codes).size !== codes.length) throw bad('duplicate_code');
  if (new Set(memberships.filter(m => m.productId).map(m => m.productId)).size !== memberships.filter(m => m.productId).length) throw bad('duplicate_membership');

  const profile = {};
  if (b.profile != null) {
    if (typeof b.profile !== 'object') throw bad('invalid_profile');
    for (const k of PROFILE_KEYS) {
      const v = b.profile[k];
      if (v == null) continue;
      if (typeof v !== 'string' || v.length > 30) throw bad('invalid_profile');
      profile[k] = v.trim();
    }
  }
  return { cards, memberships, profile };
}

async function readBody(req) {
  let size = 0;
  const chunks = [];
  for await (const c of req) {
    size += c.length;
    if (size > MAX_BODY) throw new HttpError(413, 'body_too_large');
    chunks.push(c);
  }
  if (!size) return {};
  try { return JSON.parse(Buffer.concat(chunks).toString('utf8')); } catch { throw bad('invalid_json'); }
}

// ---------- 앱 ----------
/**
 * @param store   createFileStore()
 * @param catalog { merchants, categories, products, defaultWallet }
 * @param clock   () => Date  (테스트에서 시각 고정)
 */
export function createApp({ store, catalog, clock = () => new Date(), invite = null }) {
  const byToken = new Map(Object.values(store.users).map(u => [u.tokenHash, u]));
  const engines = new Map();

  function engineFor(user) {
    let e = engines.get(user.id);
    if (!e) {
      e = createReCatch({ merchants: catalog.merchants, products: catalog.products, wallet: user.wallet, state: user.state });
      engines.set(user.id, e);
    }
    return e;
  }

  function recView(o) {
    const { rec, result } = o;
    const delta = result.delta ?? rec.delta;
    const m = rec.payment.merchant;
    return {
      id: o.id,
      status: result.status,
      store: m.brand ?? m.key,
      branch: m.branch,
      category: m.category,
      categoryName: catalog.categories[m.category] ?? null,
      amount: rec.payment.amount,
      at: rec.payment.at,
      expiresAt: rec.expiresAt,
      delta,
      fee: ['done', 'pending', 'waiting'].includes(result.status) ? feeFor(delta) : 0,
      net: delta - (['done', 'pending', 'waiting'].includes(result.status) ? feeFor(delta) : 0),
      baseline: { cardName: rec.baseline.cardName, cardLast4: rec.baseline.cardLast4, total: rec.baseline.total },
      best: {
        cardName: rec.best.cardName,
        cardLast4: rec.best.cardLast4,
        total: rec.best.total,
        atPOS: rec.best.atPOS,
        later: rec.best.later,
        membership: rec.best.membership ? { productId: rec.best.membership.productId, name: rec.best.membership.name, label: rec.best.membership.label } : null,
        cardBenefit: rec.best.card.label ?? null
      },
      steps: rec.steps,
      warnings: rec.warnings,
      awaitingConfirmation: result.awaitingConfirmation ?? 0
    };
  }

  function auth(req) {
    const h = req.headers.authorization ?? '';
    const token = h.startsWith('Bearer ') ? h.slice(7) : '';
    const user = token && byToken.get(sha256(token));
    if (!user) throw new HttpError(401, 'unauthorized');
    return user;
  }

  const routes = [
    ['GET', /^\/api\/health$/, () => ({ ok: true, time: clock().toISOString() })],

    ['POST', /^\/api\/register$/, (req, body) => {
      if (invite && body.invite !== invite) throw new HttpError(403, 'invalid_invite');
      const token = randomBytes(24).toString('base64url');
      const user = {
        id: randomUUID(),
        tokenHash: sha256(token),
        createdAt: clock().toISOString(),
        wallet: clone(catalog.defaultWallet),
        state: { events: [], open: [], nextId: 1 }
      };
      store.users[user.id] = user;
      byToken.set(user.tokenHash, user);
      store.save();
      return { userId: user.id, token };
    }, { public: true }],

    ['GET', /^\/api\/catalog$/, () => ({
      categories: catalog.categories,
      barcodeFormats: Object.fromEntries(Object.entries(BARCODE_FORMATS).map(([k, v]) => [k, v.label])),
      cards: catalog.products.cards.map(p => ({
        id: p.id, issuer: p.issuer, name: p.name, kind: p.kind, prevMonthMin: p.prevMonthMin, verified: p.verified,
        benefits: p.benefits.map(b => ({ id: b.id, label: b.label, monthlyCap: b.monthlyCap ?? null }))
      })),
      memberships: catalog.products.memberships.map(p => ({
        id: p.id, name: p.name, kind: p.kind, verified: p.verified,
        tiers: p.tiers ? Object.keys(p.tiers) : null,
        benefits: (p.tiers ? Object.values(p.tiers)[0].benefits : p.benefits).map(b => b.label)
      }))
    })],

    ['GET', /^\/api\/wallet$/, (req, body, user) => user.wallet],

    ['PUT', /^\/api\/wallet$/, (req, body, user) => {
      const w = readWallet(body, catalog.products);
      // 엔진이 같은 지갑 객체를 참조하므로 내용만 바꾼다.
      user.wallet.cards = w.cards;
      user.wallet.memberships = w.memberships;
      user.wallet.profile = w.profile;
      store.save();
      return user.wallet;
    }],

    ['POST', /^\/api\/events$/, (req, body, user) => {
      const { parsed, receivedAt } = readEvent(body);
      const now = clock();
      // 단말이 알림을 받은 시각을 쓰되, 미래 시각은 서버 시각으로 대신한다.
      const at = receivedAt && receivedAt.getTime() <= now.getTime() + 120_000 ? receivedAt : now;
      const r = engineFor(user).onParsed(parsed, { now, at: at.toISOString() });
      store.save();
      const open = user.state.open;
      return {
        event: { type: r.event.type, amount: r.event.amount, store: r.event.merchant.brand ?? r.event.merchant.key, category: r.event.merchant.category, cardLast4: r.event.last4 },
        recommendation: r.recommendation ? recView(open.find(o => o.id === r.recommendation.id)) : null,
        reason: r.reason,
        updates: r.updates.map(u => recView(open.find(o => o.id === u.id)))
      };
    }],

    ['GET', /^\/api\/recommendations$/, (req, body, user) => {
      const changed = engineFor(user).tick({ now: clock() });
      if (changed.length) store.save();
      return [...user.state.open].reverse().map(recView);
    }],

    ['GET', /^\/api\/recommendations\/(\d+)$/, (req, body, user, [, id]) => {
      engineFor(user).tick({ now: clock() });
      const o = user.state.open.find(x => x.id === +id);
      if (!o) throw new HttpError(404, 'not_found');
      return recView(o);
    }],

    ['POST', /^\/api\/recommendations\/(\d+)\/baseline$/, (req, body, user, [, id]) => {
      if (!optStr(body.membershipId, 40)) throw bad('invalid_membership');
      const o = engineFor(user).rebaseline(+id, body.membershipId ?? null, { now: clock() });
      if (!o) throw new HttpError(409, 'not_waiting');
      store.save();
      return recView(o);
    }],

    ['POST', /^\/api\/recommendations\/(\d+)\/skip$/, (req, body, user, [, id]) => {
      // "이번엔 넘어갈게요": 놓친 혜택으로 기록 (수수료 없음)
      const o = user.state.open.find(x => x.id === +id);
      if (!o) throw new HttpError(404, 'not_found');
      if (o.result.status !== 'waiting') throw new HttpError(409, 'not_waiting');
      o.result = { status: 'missed', skipped: true };
      store.save();
      return recView(o);
    }],

    ['POST', /^\/api\/recommendations\/(\d+)\/confirm$/, (req, body, user, [, id]) => {
      if (!isInt(body.confirmedLater, 0, 100_000_000)) throw bad('invalid_amount');
      const o = user.state.open.find(x => x.id === +id);
      if (!o) throw new HttpError(404, 'not_found');
      if (o.result.status !== 'pending') throw new HttpError(409, 'not_pending');
      engineFor(user).confirm(+id, body.confirmedLater);
      store.save();
      return recView(o);
    }],

    ['GET', /^\/api\/statement$/, (req, body, user, m, url) => {
      const month = url.searchParams.get('month') ?? localDate(clock().toISOString()).slice(0, 7);
      if (!/^\d{4}-\d{2}$/.test(month)) throw bad('invalid_month');
      const e = engineFor(user);
      if (e.tick({ now: clock() }).length) store.save();
      const first = localDate(user.createdAt).slice(0, 7);
      const statementOf = mo => monthlyStatement(
        user.state.open.filter(o => localDate(o.rec.payment.at).startsWith(mo)).map(o => e.toServerRecord(o.id)),
        { firstMonth: mo === first });
      // 가입한 달부터 이 달까지 청구액을 이어 붙여 1만 원 미만 이월을 계산
      const months = [];
      for (let [y, mm] = first.split('-').map(Number); `${y}-${String(mm).padStart(2, '0')}` <= month; mm === 12 ? (y++, mm = 1) : mm++) {
        months.push(`${y}-${String(mm).padStart(2, '0')}`);
      }
      const s = statementOf(month);
      const cycle = billingCycle(months.map(mo => ({ month: mo, charge: mo === month ? s.charge : statementOf(mo).charge })));
      const bill = cycle.at(-1) ?? { carriedIn: 0, totalDue: s.charge, billed: s.charge >= BILLING_THRESHOLD };
      const [y, mm] = month.split('-').map(Number);
      const dueDate = `${month}-${String(new Date(y, mm, 0).getDate()).padStart(2, '0')}`;
      return { month, ...s, carriedIn: bill.carriedIn, totalDue: bill.totalDue, billed: bill.billed, threshold: BILLING_THRESHOLD, dueDate };
    }]
  ];

  async function handle(req, res) {
    const url = new URL(req.url, 'http://localhost');
    let status = 200;
    let payload;
    try {
      const route = routes.find(([method, re]) => method === req.method && re.test(url.pathname));
      if (!route) throw new HttpError(404, 'not_found');
      const [, re, fn, opts = {}] = route;
      const body = req.method === 'GET' ? {} : await readBody(req);
      const user = opts.public || url.pathname === '/api/health' ? null : auth(req);
      payload = await fn(req, body, user, url.pathname.match(re), url);
    } catch (err) {
      status = err.status ?? 500;
      payload = { error: err.code ?? 'internal_error', ...(err.message && err.message !== err.code && status < 500 ? { message: err.message } : {}) };
      if (status === 500) console.error(err);
    }
    const out = JSON.stringify(payload);
    res.writeHead(status, { 'content-type': 'application/json; charset=utf-8', 'cache-control': 'no-store', 'content-length': Buffer.byteLength(out) });
    res.end(out);
  }

  // 모든 사용자의 대기 중 추천을 주기적으로 판정 (시간이 지나 missed가 되는 것 등)
  handle.tickAll = () => {
    let changed = 0;
    for (const user of Object.values(store.users)) {
      if (user.state.open.some(o => o.result.status === 'waiting')) changed += engineFor(user).tick({ now: clock() }).length;
    }
    if (changed) store.save();
    return changed;
  };
  return handle;
}

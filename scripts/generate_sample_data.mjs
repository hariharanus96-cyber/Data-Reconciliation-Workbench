import { mkdirSync, writeFileSync } from 'node:fs'
import { join } from 'node:path'

function option(name, fallback) {
  const index = process.argv.indexOf(name)
  return index >= 0 ? process.argv[index + 1] : fallback
}

const records = Number(option('--records', '12000'))
const seed = Number(option('--seed', '20261023')) >>> 0
const output = option('--output', 'sample-data')
if (!Number.isInteger(records) || records < 10000) {
  throw new Error('--records must be an integer of at least 10000')
}

let state = seed
function random() {
  state += 0x6D2B79F5
  let value = state
  value = Math.imul(value ^ (value >>> 15), value | 1)
  value ^= value + Math.imul(value ^ (value >>> 7), value | 61)
  return ((value ^ (value >>> 14)) >>> 0) / 4294967296
}

const currencies = ['USD', 'EUR', 'GBP', 'CAD']
const start = Date.UTC(2026, 0, 1)
const iso = milliseconds => new Date(milliseconds).toISOString()
const orders = ['order_id,customer_id,amount,currency,created_at']
const payments = ['payment_id,order_reference,amount,currency,paid_at']
let paymentNumber = 0

for (let index = 1; index <= records; index += 1) {
  const orderId = `ORD-${String(index).padStart(7, '0')}`
  const currency = currencies[(index - 1) % currencies.length]
  const orderCents = 500 + Math.floor(random() * 249500)
  const createdAt = start + index * 11 * 60_000
  orders.push([
    orderId,
    `CUS-${String(1 + Math.floor(random() * Math.max(1000, Math.floor(records / 2)))).padStart(6, '0')}`,
    (orderCents / 100).toFixed(2),
    currency,
    iso(createdAt),
  ].join(','))

  if (index % 97 === 0) continue

  paymentNumber += 1
  const paidAt = createdAt + (1 + Math.floor(random() * 2400)) * 60_000
  let paymentCents = orderCents
  if (index % 173 === 0) paymentCents += 127
  else if (index % 13 === 0) paymentCents += 1
  payments.push([
    `PAY-${String(paymentNumber).padStart(7, '0')}`, orderId,
    (paymentCents / 100).toFixed(2), currency, iso(paidAt),
  ].join(','))

  if (index % 211 === 0) {
    paymentNumber += 1
    payments.push([
      `PAY-${String(paymentNumber).padStart(7, '0')}`, orderId,
      (orderCents / 100).toFixed(2), currency, iso(paidAt + 60_000),
    ].join(','))
  }

  if (index % 307 === 0) {
    paymentNumber += 1
    payments.push([
      `PAY-${String(paymentNumber).padStart(7, '0')}`, `NO-ORDER-${String(index).padStart(7, '0')}`,
      (orderCents / 100).toFixed(2), currency, iso(paidAt + 120_000),
    ].join(','))
  }
}

while (paymentNumber < records) {
  paymentNumber += 1
  const cents = 500 + Math.floor(random() * 249500)
  payments.push([
    `PAY-${String(paymentNumber).padStart(7, '0')}`,
    `NO-ORDER-FILLER-${String(paymentNumber).padStart(7, '0')}`,
    (cents / 100).toFixed(2), currencies[paymentNumber % currencies.length],
    iso(start + paymentNumber * 13 * 60_000),
  ].join(','))
}

mkdirSync(output, { recursive: true })
writeFileSync(join(output, 'orders.csv'), `${orders.join('\n')}\n`, 'utf8')
writeFileSync(join(output, 'payments.csv'), `${payments.join('\n')}\n`, 'utf8')
console.log(`Seed: ${seed}`)
console.log(`Orders: ${join(output, 'orders.csv')} (${records.toLocaleString()} rows)`)
console.log(`Payments: ${join(output, 'payments.csv')} (${paymentNumber.toLocaleString()} rows)`)
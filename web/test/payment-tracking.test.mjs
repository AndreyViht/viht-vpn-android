import test from 'node:test';
import assert from 'node:assert/strict';
import {normalizePayment,paymentPollDelay,archivePayment,PAYMENT_POLL_LIMIT} from '../src/services/paymentTracking.js';
test('old references gain a stable tracking timestamp without changing the payment',()=>{
 const order=normalizePayment({label:'pay_ref',paymentUrl:'https://yoomoney.ru/quickpay/confirm.xml',price:149},1000);
 assert.equal(order.createdAt,1000);assert.equal(normalizePayment(order,2000).createdAt,1000);assert.equal(order.price,149);
 assert.equal(paymentPollDelay(order,2000),10000);
 assert.equal(paymentPollDelay(order,121001),30000);
 assert.equal(paymentPollDelay(order,1000+PAYMENT_POLL_LIMIT),null);
});
test('changing a tariff preserves old references for later payment verification',()=>{
 const first=normalizePayment({label:'old',paymentUrl:'https://yoomoney.ru/'},1000);
 const next=normalizePayment({label:'new',paymentUrl:'https://yoomoney.ru/'},2000);
 let history=archivePayment([],first);history=archivePayment(history,next);history=archivePayment(history,first);
 assert.deepEqual(history.map(p=>p.label),['old','new']);assert.equal(first.createdAt,1000);
 assert.equal(normalizePayment(null),null);
});

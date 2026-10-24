import test from 'node:test';
import assert from 'node:assert/strict';
import {EncryptedEnvelopeRouter} from './router.mjs';
const example = (overrides={})=>({id:'a'.repeat(32),ciphertext:new Uint8Array([4,5]),expiresAt:1500,remainingHops:3,...overrides});
test('accept, decrement hops, deep copy and reject replays',()=>{const r=new EncryptedEnvelopeRouter({now:()=>1000});const a=r.accept(example());assert.equal(a.action,'RELAY_CANDIDATE');assert.equal(a.envelope.remainingHops,2);a.envelope.ciphertext[0]=99;assert.equal(r.accept(example()).reason,'DUPLICATE')});
test('expired, overlong, plaintext, wrong hops rejected',()=>{for(const v of [example({expiresAt:1000}),example({remainingHops:8}),example({ciphertext:'plaintext'}),example({ciphertext:new Uint8Array(5000)})])assert.equal(new EncryptedEnvelopeRouter({now:()=>1000}).accept(v).action,'REJECT')});
test('zero hop is local only',()=>assert.equal(new EncryptedEnvelopeRouter({now:()=>1000}).accept(example({remainingHops:0})).action,'DELIVER_ONLY'));
test('dedup entries expire',()=>{let now=1000;const r=new EncryptedEnvelopeRouter({now:()=>now});r.accept(example());now=1500;assert.equal(r.accept(example({expiresAt:1800})).action,'RELAY_CANDIDATE')});

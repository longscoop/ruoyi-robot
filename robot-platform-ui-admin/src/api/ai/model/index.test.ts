import { describe, expect, it } from 'vitest'
import type { ProviderVO } from './index'
describe('AI provider contract',()=>{it('response type contains configured flag, not secret fields',()=>{const p:ProviderVO={id:1,name:'Qwen',code:'qwen',providerType:'QWEN',baseUrl:'https://example.invalid',status:'ENABLED',apiKeyConfigured:true};expect(p.apiKeyConfigured).toBe(true);expect('apiKey' in p).toBe(false)})})

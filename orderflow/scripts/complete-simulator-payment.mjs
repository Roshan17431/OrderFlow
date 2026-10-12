#!/usr/bin/env node
// Trusted local CLI only. This file is never imported by Next.js/browser code.
import {execFileSync} from 'node:child_process';
import {createHmac,randomUUID} from 'node:crypto';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
if(process.env.ORDERFLOW_SIMULATOR_HELPER!=='true'){console.error('Disabled. Set ORDERFLOW_SIMULATOR_HELPER=true only for a local test stack.');process.exit(1);}
const [kind,id]=process.argv.slice(2);
if(!['payment.succeeded','payment.failed','refund.succeeded','refund.failed'].includes(kind)||!/^[0-9a-f-]{36}$/i.test(id??'')){console.error('Usage: complete-simulator-payment.mjs payment.succeeded|payment.failed|refund.succeeded|refund.failed UUID');process.exit(1);}
const url=new URL(process.env.ORDERFLOW_GATEWAY_URL??'http://localhost:8080');
if(!['localhost','127.0.0.1'].includes(url.hostname)||!['http:','https:'].includes(url.protocol)||url.username||url.password){console.error('Helper is limited to a local gateway.');process.exit(1);}
const cwd=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const localEnv=execFileSync('docker',['compose','exec','-T','payment-service','sh','-c','printf "%s\\n%s\\n%s\\n%s" "$APP_PAYMENT_PROVIDER" "$APP_PAYMENT_SIMULATOR_ENABLED" "$SPRING_PROFILES_ACTIVE" "$APP_PAYMENT_SIMULATOR_WEBHOOK_SECRET"'],{cwd,encoding:'utf8'}).split('\n');
const [provider,enabled,profiles,secret]=localEnv;
if(provider!=='simulator'||enabled!=='true'||/production|prod/i.test(profiles)||!secret){console.error('Helper refused: local simulator configuration is required.');process.exit(1);}
const body=JSON.stringify(kind.startsWith('refund.')?{type:kind,providerRefundId:`sim_ref_${id}`}:{type:kind,providerPaymentId:`sim_pay_${id}`});
const timestamp=String(Math.floor(Date.now()/1000));const signature=createHmac('sha256',secret).update(`${timestamp}.${body}`).digest('hex');
const response=await fetch(new URL('/api/payments/webhooks/simulator',url),{method:'POST',headers:{'Content-Type':'application/json','X-Payment-Event-ID':'frontend-test-'+randomUUID(),'X-Payment-Timestamp':timestamp,'X-Payment-Signature':signature,'X-Correlation-ID':randomUUID()},body,signal:AbortSignal.timeout(15000)});
if(!response.ok){console.error(`Simulator callback rejected: HTTP ${response.status}`);process.exit(1);}console.log('Signed local simulator callback accepted. Confirm final status through the normal API/UI.');

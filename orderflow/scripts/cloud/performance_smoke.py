#!/usr/bin/env python3
"""Small, explicitly authorized HTTPS availability/timing sample, not a load benchmark."""
from concurrent.futures import ThreadPoolExecutor
import json
import math
import os
import sys
import time
from cloud_common import Client, SafetyError, VerificationError, performance_limits, target


def percentile(values, quantile):
    if not values:
        raise VerificationError('No successful timing samples')
    ordered=sorted(values)
    return ordered[min(len(ordered)-1,max(0,math.ceil(quantile*len(ordered))-1))]


def sample(configuration, index):
    start=time.monotonic()
    client=Client(configuration)
    with client.request('/api/products?size=10' if index%2 else '/',raw=True,auth=False,timeout=10) as response:
        if len(response.read(1048577))>1048576:
            raise VerificationError('Public response exceeded smoke-test bound')
    return (time.monotonic()-start)*1000


def run(configuration, count, workers):
    if not 1<=count<=30 or not 1<=workers<=2:
        raise SafetyError('Performance request/concurrency cap exceeded')
    started=time.monotonic()
    with ThreadPoolExecutor(max_workers=workers) as pool:
        samples=list(pool.map(lambda i:sample(configuration,i),range(count)))
    return {'result':'PASS','scope':'local-verified-TLS-simulation' if configuration['local'] else 'authorized-deployed-cloud','requests':count,'concurrency':workers,'errors':0,'durationSeconds':round(time.monotonic()-started,3),'p50Ms':round(percentile(samples,.5),3),'p95Ms':round(percentile(samples,.95),3),'p99Ms':round(percentile(samples,.99),3),'interpretation':'Bounded availability/timing smoke sample; no capacity or production latency claim'}


def main():
    try:
        if os.environ.get('ORDERFLOW_PERFORMANCE_SMOKE')!='true':
            raise SafetyError('ORDERFLOW_PERFORMANCE_SMOKE=true is required')
        configuration=target()
        count,workers=performance_limits()
        print(json.dumps(run(configuration,count,workers),indent=2))
        return 0
    except (SafetyError,VerificationError) as error:
        print('FAIL '+str(error),file=sys.stderr);return 1
    except Exception:
        print('FAIL unexpected bounded performance check error',file=sys.stderr);return 1

if __name__=='__main__':raise SystemExit(main())

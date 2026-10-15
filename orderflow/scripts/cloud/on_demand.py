#!/usr/bin/env python3
"""Explicitly targeted on-demand EC2 operations; no provisioning or credentials stored."""
import argparse
import json
import os
import re
import shlex
import shutil
import subprocess
import sys
import time

class SafetyError(Exception):
    pass

def require(ok, message):
    if not ok:
        raise SafetyError(message)

class Aws:
    def __init__(self, region):
        require(shutil.which('aws'), 'AWS CLI v2 is required; authenticate with your approved SSO/CLI session')
        self.region = region

    def call(self, *args):
        try:
            result = subprocess.run(['aws', *args, '--region', self.region, '--output', 'json', '--no-cli-pager',
                                     '--cli-connect-timeout', '5', '--cli-read-timeout', '20'],
                                    capture_output=True, text=True, timeout=30,
                                    env={**os.environ, 'AWS_PAGER':'', 'AWS_MAX_ATTEMPTS':'2'})
            require(result.returncode == 0, 'AWS operation failed; inspect identity/permissions privately (output suppressed)')
            return json.loads(result.stdout)
        except (OSError, subprocess.TimeoutExpired, ValueError):
            raise SafetyError('AWS unavailable, timed out, or returned invalid JSON') from None

def validate_config(c):
    patterns = {'account':r'[0-9]{12}', 'region':r'[a-z]{2}(?:-[a-z]+)+-[0-9]+',
                'instance':r'i-[0-9a-f]{8,17}', 'project':r'[a-z][a-z0-9-]{2,19}'}
    require(all(re.fullmatch(pattern, c.get(key, '')) for key, pattern in patterns.items()), 'Explicit valid account, region, instance ID and project are required')
    require(c.get('environment') == 'cloud-demo', 'On-demand controls are restricted to cloud-demo')

def validate_instance(item, c):
    require(item.get('InstanceId') == c['instance'] and item.get('RootDeviceType') == 'ebs', 'Unexpected instance identity or nonpersistent root device')
    tags = {t['Key']:t['Value'] for t in item.get('Tags', [])}
    expected = {'Project':c['project'], 'Environment':c['environment'], 'ManagedBy':'Terraform',
                'Purpose':'PortfolioDemo', 'OrderFlowOnDemand':'true'}
    require(all(tags.get(k) == v for k,v in expected.items()), 'Instance lacks exact dedicated OrderFlow on-demand tags')
    return item

def guard(aws, c):
    validate_config(c)
    require(aws.call('sts','get-caller-identity').get('Account') == c['account'], 'Authenticated AWS account does not match approved target')
    records = aws.call('ec2','describe-instances','--instance-ids',c['instance']).get('Reservations', [])
    require(len(records) == 1 and records[0].get('OwnerId') == c['account'] and len(records[0].get('Instances', [])) == 1, 'Expected exactly one instance in the approved account')
    item = validate_instance(records[0]['Instances'][0], c)
    attr = aws.call('ec2','describe-instance-attribute','--instance-id',c['instance'],'--attribute','instanceInitiatedShutdownBehavior')
    require(attr.get('InstanceInitiatedShutdownBehavior', {}).get('Value') == 'stop', 'Instance-initiated shutdown must be STOP; refusing unsafe configuration')
    return item

def confirm(action, c, supplied):
    expected = action+' '+c['instance']
    if supplied is None:
        try:
            supplied = input('Type '+expected+' to confirm: ')
        except EOFError:
            supplied = ''
    require(supplied == expected, 'Exact confirmation required; no operation performed')

def wait_for(probe, timeout, label):
    deadline = time.monotonic()+timeout
    while time.monotonic() < deadline:
        result = probe()
        if result:
            return result
        time.sleep(min(3, max(0, deadline-time.monotonic())))
    raise SafetyError(label+' timed out; inspect the approved instance before retrying')

def remote(aws, c, action, timeout=300, extra=()):
    require(action in ('status','quiesce','extend'), 'Unsupported remote operational action')
    command = 'sudo -n /usr/local/sbin/orderflow-host '+shlex.join([action, *extra])
    response = aws.call('ssm','send-command','--instance-ids',c['instance'],'--document-name','AWS-RunShellScript',
                        '--parameters',json.dumps({'commands':[command], 'executionTimeout':[str(timeout)]}), '--timeout-seconds','60')
    command_id = response['Command']['CommandId']
    def poll():
        try:
            result = aws.call('ssm','get-command-invocation','--command-id',command_id,'--instance-id',c['instance'])
        except SafetyError:
            return False # eventual registration is bounded by the caller
        status = result.get('Status')
        if status in ('Pending','InProgress','Delayed'):
            return False
        require(status == 'Success' and result.get('ResponseCode') == 0, 'Remote operational check failed (private output suppressed)')
        try:
            value = json.loads(result.get('StandardOutputContent', ''))
        except ValueError:
            raise SafetyError('Remote check did not return its bounded status contract') from None
        require(isinstance(value, dict) and value.get('instance') == c['instance'], 'Remote host identity mismatch')
        return value
    return wait_for(poll, timeout+35, 'SSM '+action)

def wait_ready(aws, c, timeout):
    def checks():
        result=aws.call('ec2','describe-instance-status','--instance-ids',c['instance'],'--include-all-instances')
        rows=result.get('InstanceStatuses', [])
        return len(rows)==1 and all(rows[0].get(k,{}).get('Status')=='ok' for k in ('InstanceStatus','SystemStatus'))
    wait_for(checks, timeout, 'EC2 status checks')
    def connected():
        rows=aws.call('ssm','describe-instance-information','--filters',json.dumps([{'Key':'InstanceIds','Values':[c['instance']]}])).get('InstanceInformationList',[])
        return len(rows)==1 and rows[0].get('PingStatus')=='Online'
    wait_for(connected, timeout, 'SSM connection')
    def application_ready():
        value=remote(aws,c,'status',60)
        require(value.get('remainingSeconds',0)>0 and value.get('timerActive') is True,'Runtime safety timer is not active')
        return value if value.get('ready') is True else False
    result=wait_for(application_ready,timeout,'Application/gateway/frontend/HTTPS readiness')
    print(json.dumps({k:result[k] for k in ('instance','ready','release','httpsUrl','stopDeadlineUtc','remainingSeconds') if k in result}))
    return result

def start(aws, c, confirmation, timeout):
    item=guard(aws,c); state=item['State']['Name']
    print('Account '+c['account']+'; region '+c['region']+'; instance '+c['instance']+'; state '+state)
    if state=='running':
        print('Already running; the automatic stop deadline is unchanged.')
    elif state=='stopped':
        confirm('start',c,confirmation)
        guard(aws,c) # recheck immediately before mutation
        aws.call('ec2','start-instances','--instance-ids',c['instance'])
    elif state=='pending':
        print('Already starting; the automatic stop deadline is unchanged.')
    else:
        raise SafetyError('Instance must be stopped, pending or running; no mutation performed')
    return wait_ready(aws,c,timeout)

def stop(aws, c, confirmation, timeout):
    item=guard(aws,c); state=item['State']['Name']
    print('Account '+c['account']+'; region '+c['region']+'; instance '+c['instance']+'; state '+state)
    if state=='stopped':
        print('Already stopped; retained EBS and release data are preserved.'); return
    require(state in ('running','stopping'), 'Instance must be running or stopping')
    if state=='running':
        try:
            report=remote(aws,c,'status',60)
            print(json.dumps({k:report[k] for k in ('ready','release','remainingSeconds') if k in report}))
        except SafetyError:
            print('Application status unavailable; EC2 stop still permits normal OS shutdown.')
        confirm('stop',c,confirmation)
        guard(aws,c)
        try:
            remote(aws,c,'quiesce',240)
        except SafetyError:
            print('SSM graceful stop unavailable; requesting normal EC2 stop without force.')
        aws.call('ec2','stop-instances','--instance-ids',c['instance'])
    wait_for(lambda: guard(aws,c)['State']['Name']=='stopped',timeout,'EC2 stop')
    print('STOPPED. EBS, data, keys, certificates and release metadata retained; backups are still required.')

def main():
    p=argparse.ArgumentParser(description=__doc__)
    p.add_argument('action',choices=('start','stop','status','extend'))
    for key,env in [('account','AWS_ACCOUNT_ID'),('region','AWS_REGION'),('instance','ORDERFLOW_INSTANCE_ID'),('project','ORDERFLOW_PROJECT'),('environment','ORDERFLOW_ENVIRONMENT')]:
        p.add_argument('--'+key,default=os.environ.get(env, 'orderflow' if key=='project' else 'cloud-demo' if key=='environment' else ''))
    p.add_argument('--confirm',help='exact action and instance phrase; otherwise prompt interactively')
    p.add_argument('--hours',type=float,default=8)
    p.add_argument('--timeout',type=int,default=600)
    args=p.parse_args();c={key:getattr(args,key) for key in ('account','region','instance','project','environment')}
    validate_config(c);require(30<=args.timeout<=1800,'Timeout must be30..1800 seconds')
    aws=Aws(c['region'])
    if args.action=='start':start(aws,c,args.confirm,args.timeout)
    elif args.action=='stop':stop(aws,c,args.confirm,args.timeout)
    else:
        item=guard(aws,c)
        print('Account '+c['account']+'; region '+c['region']+'; instance '+c['instance']+'; state '+item['State']['Name'])
        require(item['State']['Name']=='running','Host is not running')
        if args.action=='extend':
            require(0.25<=args.hours<=24,'Extension must be0.25..24 hours from now')
            confirm('extend',c,args.confirm)
            result=remote(aws,c,'extend',60,('--hours',str(args.hours)))
        else: result=remote(aws,c,'status',args.timeout)
        print(json.dumps({k:result[k] for k in ('instance','ready','release','httpsUrl','stopDeadlineUtc','remainingSeconds','timerActive') if k in result}))

if __name__=='__main__':
    try:main()
    except (SafetyError,KeyError,TypeError) as error:
        print('ERROR: '+(str(error) if isinstance(error,SafetyError) else 'Malformed AWS response'),file=sys.stderr);sys.exit(1)

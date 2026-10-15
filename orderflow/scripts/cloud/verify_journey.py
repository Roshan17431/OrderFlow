#!/usr/bin/env python3
"""Real HTTPS demo journey. No successful result is synthesized or mocked here."""
import json
import os
from pathlib import Path
import secrets
import sys
import threading
import time
import uuid
from cloud_common import Client, Metrics, SafetyError, VerificationError, accounts, compare_metrics, identity, poll, target, write_report


def page_items(body):
    result = body.get('items', body.get('content')) if isinstance(body, dict) else None
    if not isinstance(result, list):
        raise VerificationError('Unexpected page contract')
    return result


class NotificationStream:
    def __init__(self, client):
        self.response = client.request('/api/notifications/stream', raw=True, headers={'Accept': 'text/event-stream'}, timeout=120)
        if 'text/event-stream' not in self.response.headers.get('Content-Type', ''):
            self.response.close()
            raise VerificationError('Authenticated SSE endpoint did not return an event stream')
        self.notification = None
        self.failure = None
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()
    def read(self):
        kind, data, total = None, [], 0
        deadline = time.monotonic()+120
        try:
            while time.monotonic()<deadline:
                line = self.response.readline(65537)
                total += len(line)
                if not line or len(line)>65536 or total>262144:
                    raise VerificationError('SSE event missing or outside its bounded size')
                text = line.decode('utf-8').rstrip('\r\n')
                if not text:
                    if kind == 'notification':
                        self.notification = json.loads('\n'.join(data))
                        identity(self.notification['id'])
                        return
                    kind, data = None, []
                elif text.startswith('event:'):
                    kind = text[6:].strip()
                elif text.startswith('data:'):
                    data.append(text[5:].strip())
            raise VerificationError('SSE notification deadline exceeded')
        except Exception:
            self.failure = 'Authenticated SSE did not deliver a valid actual notification'
        finally:
            self.response.close()
    def verify(self, notification_ids):
        self.thread.join(timeout=5)
        if self.failure or not self.notification or self.notification['id'] not in notification_ids:
            raise VerificationError(self.failure or 'SSE event could not be tied to the customer notification list')
    def close(self):
        self.response.close()


def state(client, path, field, value, expected=(200,)):
    body = client.request(path, expected=expected)
    return body if isinstance(body, dict) and body.get(field)==value else False


def stock_state(client, product_id, on_hand, reserved, available):
    value=client.request('/api/inventory/products/'+product_id)
    expected={'onHandQuantity':on_hand,'reservedQuantity':reserved,'availableQuantity':available}
    return value if isinstance(value,dict) and all(value.get(k)==v for k,v in expected.items()) else False


def assert_cookie_rotation(client, original_user):
    old = next(c.value for c in client.jar if c.name=='ORDERFLOW_REFRESH')
    client.request('/api/auth/refresh', 'POST', csrf=False, auth=False, expected=(403,))
    client.csrf_token()
    body = client.request('/api/auth/refresh', 'POST', auth=False)
    client.authenticated(body, 'CUSTOMER')
    new = next(c.value for c in client.jar if c.name=='ORDERFLOW_REFRESH')
    if old == new or client.user['id'] != original_user:
        raise VerificationError('Refresh token rotation did not preserve the expected customer identity')
    if client.request('/api/auth/me')['id'] != original_user:
        raise VerificationError('Refreshed bearer token failed identity ownership')


def ensure_exclusive_pending_order(admin, order_id):
    # The actual availability API assigns the oldest pending delivery. Avoid
    # taking a different customer's work with this operator fixture agent.
    page = admin.request('/api/admin/orders?size=100')
    rows = page_items(page)
    if page.get('totalElements', len(rows)) > len(rows):
        raise VerificationError('Exclusive demo assignment cannot be proven from a bounded order page')
    if any(o['id'] != order_id and o.get('fulfillmentStatus')=='DELIVERY_PENDING' for o in rows):
        raise VerificationError('Other pending delivery exists; refusing fixture agent activation')


def journey(configuration, fixture_accounts, metrics):
    started = time.monotonic()
    run = uuid.uuid4().hex[:16]
    clients = {role: Client(configuration) for role in fixture_accounts}
    stream = None
    agent_profile = None
    agent_activated = False
    before_metrics = metrics.snapshot() # private monitoring must work before mutation
    try:
        for role, client in clients.items():
            client.login(fixture_accounts[role], role)
        vendor, agent, admin = (clients[r] for r in ('VENDOR','DELIVERY_AGENT','ADMIN'))
        before = admin.request('/api/admin/operations/overview')
        jobs = admin.request('/api/admin/operations/jobs')
        if not isinstance(jobs, list) or any(j.get('failureCount', 0)>0 for j in jobs):
            raise VerificationError('Operational job contract/failure checks failed')
        anonymous = Client(configuration)
        anonymous.request('/api/cart', auth=False, expected=(401,))
        anonymous.request('/api/orders', auth=False, headers={'Authorization':'Bearer invalid-verification-token'}, expected=(401,))
        for path in ('/actuator/health','/api/auth/actuator/prometheus','/eureka/'):
            anonymous.request(path, auth=False, expected=(404,), raw=True).close()
        # Provision/retain only the dedicated idle operator agent profile.
        agent_profile = agent.request('/api/delivery/agents/me', expected=(200,404))
        if isinstance(agent_profile, dict) and agent_profile.get('activeDeliveryId'):
            raise VerificationError('Dedicated fixture agent is already busy; no unrelated work will be changed')
        agent_profile = agent.request('/api/delivery/agents/me/availability','PUT',{'availability':'OFFLINE'})
        identity(agent_profile['profileId'])
        category = admin.request('/api/admin/categories','POST',{'name':'Cloud verification '+run,'slug':'cloud-check-'+run,'description':'Synthetic verification category'},expected=(201,))
        category_id = identity(category['id'])
        if category.get('status')!='ACTIVE':
            category=admin.request('/api/admin/categories/'+category_id+'/activate','PATCH',{'version':category['version']})
        product = vendor.request('/api/vendor/products','POST',{'sku':'CLOUD-'+run,'name':'Synthetic demo item '+run,'description':'Verification only; no real commerce','priceAmount':'12.50','currency':'USD','categoryId':category_id,'slug':'cloud-demo-'+run},expected=(201,))
        product_id = identity(product['id'])
        if product['vendorId'] != vendor.user['id']:
            raise VerificationError('Product vendor ownership mismatch')
        product = vendor.request('/api/vendor/products/'+product_id+'/activate','PATCH',{'version':product['version']})
        stock = vendor.request('/api/inventory/products/'+product_id+'/adjustments','POST',{'quantityDelta':5,'reason':'Synthetic cloud verification stock','reference':'cloud-check-'+run},expected=(200,201))
        if stock.get('onHandQuantity') != 5:
            raise VerificationError('Fresh fixture stock was not exactly five')
        customer = Client(configuration)
        customer_email = 'cloud-customer-'+run+'@example.test'
        customer_password = secrets.token_urlsafe(32)
        customer.register(customer_email, customer_password, 'Synthetic cloud customer')
        customer_id = customer.user['id']
        customer.request('/api/auth/logout','POST',expected=(204,))
        customer=Client(configuration)
        customer.login({'email':customer_email,'password':customer_password},'CUSTOMER')
        if customer.user['id'] != customer_id:
            raise VerificationError('Customer login did not preserve registered identity')
        assert_cookie_rotation(customer, customer_id)
        customer.request('/api/admin/orders',expected=(403,))
        agent.request('/api/admin/orders',expected=(403,))
        customer.request('/api/products/'+product_id)
        customer.request('/api/cart/items','POST',{'productId':product_id,'quantity':1})
        customer.request('/api/cart/items/'+product_id,'PATCH',{'quantity':2})
        validated = customer.request('/api/cart/validate','POST')
        if validated.get('valid') is not True or validated['cart'].get('totalQuantity') != 2:
            raise VerificationError('Cart validation did not match the actual fixture quantities')
        stream = NotificationStream(customer) # bearer header, no URL token
        order_body = {'expectedCartVersion':validated['cart']['version'],'shippingAddress':{'recipientName':'Synthetic verification customer','addressLine1':'123 Test Fixture Street','city':'Test City','stateOrRegion':'Test State','postalCode':'00000','countryCode':'US','deliveryInstructions':'Synthetic demo; no real delivery'}}
        order_key = 'cloud-order-'+run
        order = customer.request('/api/orders','POST',order_body,expected=(202,),headers={'Idempotency-Key':order_key})
        order_id = identity(order['id'])
        if order['customerId'] != customer_id:
            raise VerificationError('Order customer ownership mismatch')
        repeated = customer.request('/api/orders','POST',order_body,expected=(202,),headers={'Idempotency-Key':order_key})
        if repeated.get('id') != order_id:
            raise VerificationError('Repeated order idempotency key created a different order')
        vendor.request('/api/orders/'+order_id,expected=(404,))
        poll('Payment pending order',lambda:state(customer,'/api/orders/'+order_id,'status','PAYMENT_PENDING'))
        poll('Inventory reserved',lambda:state(admin,'/api/inventory/reservations/order/'+order_id,'status','RESERVED'))
        poll('Reserved stock quantities',lambda:stock_state(vendor,product_id,5,2,3))
        payment = customer.request('/api/payments/orders/'+order_id)
        payment_id = identity(payment['id'])
        if payment.get('provider') != 'SIMULATOR' or payment.get('orderId') != order_id:
            raise VerificationError('Only the backend demo simulator may be exercised')
        processing = customer.request('/api/payments/orders/'+order_id+'/confirm','POST',{'paymentMethodToken':'tok_success'},expected=(202,),headers={'Idempotency-Key':'cloud-confirm-'+run})
        if processing.get('status') != 'PROCESSING':
            raise VerificationError('Simulator confirmation must first return PROCESSING')
        poll('Backend signed simulator completion',lambda:state(customer,'/api/orders/'+order_id,'status','PAID'))
        poll('Payment succeeded',lambda:state(customer,'/api/payments/'+payment_id,'status','SUCCEEDED'))
        poll('Inventory committed',lambda:state(admin,'/api/inventory/reservations/order/'+order_id,'status','COMMITTED'))
        def delivery_present():
            result=customer.request('/api/deliveries/orders/'+order_id,expected=(200,404))
            return result if isinstance(result,dict) and result.get('orderId')==order_id else False
        delivery=poll('Delivery creation',delivery_present)
        delivery_id=identity(delivery['id'])
        if delivery.get('status') != 'PENDING_ASSIGNMENT':
            raise VerificationError('Fixture delivery was already assigned to another agent; exclusive demo queue required')
        ensure_exclusive_pending_order(admin,order_id)
        agent_profile=agent.request('/api/delivery/agents/me/availability','PUT',{'availability':'AVAILABLE'})
        agent_activated=True
        if agent_profile.get('activeDeliveryId') != delivery_id:
            raise VerificationError('Fixture agent did not receive the exact synthetic delivery')
        assigned=agent.request('/api/delivery/agents/me/deliveries/'+delivery_id)
        if assigned.get('deliveryAgentId') != agent_profile['profileId'] or assigned.get('status')!='ASSIGNED':
            raise VerificationError('Agent delivery ownership/assignment contract mismatch')
        for action, expected in [('pickup','PICKED_UP'),('out-for-delivery','OUT_FOR_DELIVERY'),('delivered','DELIVERED')]:
            value=agent.request('/api/delivery/agents/me/deliveries/'+delivery_id+'/'+action,'POST')
            if value.get('status') != expected:
                raise VerificationError('Delivery transition did not reach '+expected)
        final_order=poll('Customer delivery projection',lambda:state(customer,'/api/orders/'+order_id,'fulfillmentStatus','DELIVERED'))
        if final_order.get('status') != 'PAID':
            raise VerificationError('Delivered synthetic order lost its confirmed PAID financial state')
        poll('Inventory finalized',lambda:state(admin,'/api/inventory/reservations/order/'+order_id,'status','FULFILLED'))
        final_stock=poll('Confirmed inventory quantities',lambda:stock_state(vendor,product_id,3,0,3))
        def notifications_present():
            value=page_items(customer.request('/api/notifications?size=100'))
            return value or False
        notifications=poll('Customer notifications',notifications_present)
        stream.verify({identity(n['id']) for n in notifications})
        customer.request('/api/notifications/'+notifications[0]['id']+'/read','PATCH')
        def operations_updated():
            after=admin.request('/api/admin/operations/overview')
            if after.get('date') != before.get('date'):
                raise VerificationError('Operations date changed; rerun during a stable UTC day')
            return after if all(after.get(k,0)>=before.get(k,0)+1 for k in ('ordersCreated','paymentsSucceeded','deliveriesCompleted')) else False
        overview=poll('Operations event projections',operations_updated)
        final_scrape_cutoff=time.time()
        def drained():
            snapshot=metrics.snapshot();compare_metrics(before_metrics,snapshot)
            return snapshot if all(v==0 for v in snapshot['backlog'].values()) and all(stamp>=final_scrape_cutoff for stamp in snapshot['scrapes'].values()) else False
        final_metrics=poll('Published outbox drain',drained,timeout=90,interval=3)
        old_bearer=customer.token
        customer.request('/api/auth/logout','POST',expected=(204,))
        customer.token=old_bearer
        customer.request('/api/auth/me',expected=(401,)) # actual Redis revocation
        return {'result':'PASS','scope':'local-verified-TLS-simulation' if configuration['local'] else 'authorized-deployed-cloud','awsAccountId':configuration['account'],'environment':configuration['environment'],'run':run,'customerId':customer_id,'reservationStatus':'FULFILLED','categoryId':category_id,'productId':product_id,'orderId':order_id,'paymentId':payment_id,'deliveryId':delivery_id,'financialStatus':final_order['status'],'fulfillmentStatus':final_order['fulfillmentStatus'],'onHandQuantity':final_stock['onHandQuantity'],'secureCookieAndCsrf':True,'refreshRotation':True,'logoutRevocation':True,'authenticatedActualSseEvent':True,'outboxBacklog':sum(final_metrics['backlog'].values()),'dltPublications':sum(final_metrics['dlt'].values()),'durationSeconds':round(time.monotonic()-started,3)}
    finally:
        if stream:
            stream.close()
        if agent_activated:
            # Never cancel or transition an unrelated/busy delivery in cleanup.
            try:
                current=clients['DELIVERY_AGENT'].request('/api/delivery/agents/me')
                if current.get('activeDeliveryId') is None and current.get('availability')=='AVAILABLE':
                    clients['DELIVERY_AGENT'].request('/api/delivery/agents/me/availability','PUT',{'availability':'OFFLINE'})
            except VerificationError:
                pass


def main():
    try:
        configuration=target()
        operators=accounts(os.environ.get('ORDERFLOW_CLOUD_ACCOUNTS_FILE'))
        metrics=Metrics(os.environ.get('ORDERFLOW_PROMETHEUS_URL','http://127.0.0.1:9090'))
        report=journey(configuration,operators,metrics)
        output=os.environ.get('ORDERFLOW_JOURNEY_REPORT',str(Path('output/verification')/('cloud-journey-'+report['run']+'.json')))
        write_report(output,report)
        print('PASS actual '+report['scope']+' journey: authentication/CSRF/ownership, cart/order, signed demo payment, delivery, authenticated SSE, operations/outbox/DLT and logout revocation')
        return 0
    except (SafetyError,VerificationError) as error:
        print('FAIL '+str(error),file=sys.stderr)
        return 1
    except Exception:
        print('FAIL unexpected verification error; inspect locally without printing credentials',file=sys.stderr)
        return 1

if __name__=='__main__':
    raise SystemExit(main())

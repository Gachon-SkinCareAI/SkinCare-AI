"""Read-only DNS/TLS readiness check after the user owns a domain and static IPv4."""
import argparse
import ipaddress
import json
from pathlib import Path
import re
import socket
import urllib.error
import urllib.request

def hostname(value):
    value=value.lower().rstrip('.')
    if (not re.fullmatch(r'(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}',value)
        or re.search(r'(?:^|\.)(?:localhost|local|internal|test|invalid|example)$',value)
        or re.search(r'(?:^|\.)example\.(?:com|org|net)$',value)):
        raise argparse.ArgumentTypeError('Provide the real public DNS hostname you control, without https:// or a path.')
    return value

def public_ipv4(value):
    try:
        address=ipaddress.IPv4Address(value)
    except ipaddress.AddressValueError as error:
        raise argparse.ArgumentTypeError('Provide the assigned static public IPv4.') from error
    if not address.is_global:
        raise argparse.ArgumentTypeError('A public IPv4 address is required.')
    return str(address)

class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None

def check(args):
    report={'hostname':args.hostname,'expectedIpv4':args.ipv4,'dnsMatches':False,
            'httpsChecked':args.https,'httpsVerified':False,'publicDeploymentComplete':False}
    try:
        addresses={r[4][0] for r in socket.getaddrinfo(args.hostname,443,type=socket.SOCK_STREAM)}
    except socket.gaierror:
        report['error']='DNS lookup failed; no request was sent to the application.'
        return report
    report['ipv4']=sorted(a for a in addresses if ipaddress.ip_address(a).version==4)
    report['ipv6']=sorted(a for a in addresses if ipaddress.ip_address(a).version==6)
    # Initial deployment uses one IPv4. Unexpected AAAA records must not route
    # mobile clients or certificate checks to a different server.
    report['dnsMatches']=report['ipv4']==[args.ipv4] and not report['ipv6']
    if not report['dnsMatches'] or not args.https:
        return report
    opener=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect())
    try:
        request=urllib.request.Request('https://'+args.hostname+'/api/wc/auth/social/providers')
        with opener.open(request,timeout=15) as response:
            providers=json.load(response)
            report['httpsVerified']=response.status==200
        report['socialProvidersEnabled']=(isinstance(providers,list)
            and {p.get('id') for p in providers}=={'google','kakao','naver'}
            and all(p.get('enabled') is True for p in providers))
    except (urllib.error.URLError,TimeoutError,ValueError,TypeError,AttributeError):
        report['error']='HTTPS, certificate or provider response check failed.'
    return report

if __name__=='__main__':
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--hostname',type=hostname,required=True)
    parser.add_argument('--ipv4',type=public_ipv4,required=True)
    parser.add_argument('--https',action='store_true',help='Also check the certificate and API after deployment.')
    parser.add_argument('--output',help='New JSON report path; existing files are not overwritten.')
    args=parser.parse_args()
    if args.output and Path(args.output).exists():
        parser.error('Choose a new report filename; existing reports are preserved.')
    report=check(args)
    text=json.dumps(report,ensure_ascii=False,indent=2)
    if args.output:
        with Path(args.output).open('x',encoding='utf-8') as stream:
            stream.write(text+'\n')
    print(text)
    raise SystemExit(0 if report['dnsMatches'] and (not args.https or (report['httpsVerified'] and report.get('socialProvidersEnabled'))) else 1)
